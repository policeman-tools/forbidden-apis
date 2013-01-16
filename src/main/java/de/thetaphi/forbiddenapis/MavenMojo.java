package de.thetaphi.forbiddenapis;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mojo to check if no project generated class files (compile scope) contains calls to forbidden APIs
 * from the project classpath and a list of API signatures (either inline or as pointer to files or bundled signatures).
 */
@Mojo(name = "forbiddenapis", requiresProject = true, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class MavenMojo extends AbstractMojo {

  /**
   * Lists all files, which contain signatures and comments for forbidden API calls.
   * The signatures are resolved against the compile classpath.
   */
  @Parameter(required = false, defaultValue = "")
  private File[] signaturesFiles;

  /**
   * Gives a multiline list of signatures, inline in the pom.xml. Use an XML CDATA section to do that!
   * The signatures are resolved against the compile classpath.
   */
  @Parameter(required = false, defaultValue = "")
  private String signatures;

  /**
   * Specifies built in signatures files (e.g., deprecated APIs for specific Java versions,
   * unsafe method calls using default locale, default charset,...)
   */
  @Parameter(required = false, defaultValue = "")
  private String[] bundledSignatures;

  /**
   * Specifies built in signatures files (e.g., deprecated APIs for specific Java versions,
   * unsafe method calls using default locale, default charset,...)
   */
  @Parameter(required = false, defaultValue = "false")
  private boolean failOnUnsupportedJava;

  @Component
  private MavenProject project;

  private List<File> findClassFiles(File dir) throws MojoExecutionException, MojoFailureException {
    List<File> classFiles = new ArrayList<File>();
    findClassFiles(dir, classFiles);
    return classFiles;
  }

  private void findClassFiles(File dir, List<File> classFiles) throws MojoExecutionException, MojoFailureException {
    if (!dir.exists()) {
      throw new MojoExecutionException("Did not find project output directory: " + dir);
    }
    
    if (dir.isDirectory()) {
      final File[] files = dir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            findClassFiles(file, classFiles);
          } else if (file.getName().endsWith(".class")) {
            classFiles.add(file);
          }
        }
      }
    } else {
      throw new MojoExecutionException("Not a dir?: " + dir.getAbsolutePath());
    }
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    final Log log = getLog();
    
    final URL[] urls;
    try {
      @SuppressWarnings("unchecked") final List<String> cp = (List<String>) project.getCompileClasspathElements();
      urls = new URL[cp.size()];
      int i = 0;
      for (final String cpElement : cp) {
        urls[i++] = new File(cpElement).toURI().toURL();
      }
      assert i == urls.length;
    } catch (DependencyResolutionRequiredException e) {
      throw new MojoExecutionException("Failed to build classpath:" + e);
    } catch (MalformedURLException e) {
      throw new MojoExecutionException("Failed to build classpath:" + e);
    }

    URLClassLoader urlLoader = null;
    final ClassLoader loader = (urls.length > 0) ?
      (urlLoader = URLClassLoader.newInstance(urls, ClassLoader.getSystemClassLoader())) :
      ClassLoader.getSystemClassLoader();
    
    try {
      final Checker checker = new Checker(loader) {
        @Override
        protected void logError(String msg) {
          log.error(msg);
        }
        
        @Override
        protected void logInfo(String msg) {
          log.info(msg);
        }
      };
      
      if (!checker.isSupportedJDK) {
        final String msg = String.format(Locale.ENGLISH, 
          "Your Java version (%s) is not supported by the forbiddenapis MOJO. Please run the checks with a supported JDK!",
          System.getProperty("java.version"));
        if (failOnUnsupportedJava) {
          throw new MojoExecutionException(msg);
        } else {
          log.warn(msg);
          return;
        }
      }
      
      try {
        final String sig = (signatures != null) ? signatures.trim() : null;
        if (sig != null && sig.length() != 0) {
          log.info("Reading inline API signatures...");
          checker.parseSignaturesString(sig);
        }
        for (final String bs : bundledSignatures) {
          log.info("Reading bundled API signatures: " + bs);
          checker.parseBundledSignatures(bs);
        }
        for (final File f : signaturesFiles) {
          log.info("Reading API signatures: " + f);
          checker.parseSignaturesFile(new FileInputStream(f));
        }
      } catch (IOException ioe) {
        throw new MojoExecutionException("IO problem while reading files with API signatures: " + ioe);
      } catch (ParseException pe) {
        throw new MojoExecutionException("Parsing signatures failed: " + pe.getMessage());
      }

      if (checker.hasNoSignatures()) {
        throw new MojoExecutionException("No API signatures found; use parameters 'signatures', 'bundledSignatures', and/or 'signaturesFiles' to define those!");
      }

      log.info("Loading classes to check...");
      final List<File> files = findClassFiles(new File(project.getBuild().getOutputDirectory()).getAbsoluteFile());
      if (files.isEmpty()) {
        throw new MojoExecutionException("There is no <fileset/> given or the fileset does not contain any class files to check.");
      }
      try {
        for (File f : files) {
          checker.addClassToCheck(new FileInputStream(f));
        }
      } catch (IOException ioe) {
        throw new MojoExecutionException("Failed to load one of the given class files:" + ioe);
      }

      try {
        log.info("Scanning for API signatures and dependencies...");
        checker.run();
      } catch (ForbiddenApiException fae) {
        throw new MojoFailureException(fae.getMessage());
      }
    } finally {
      // Java 7 supports closing URLClassLoader, so check for Closeable interface:
      if (urlLoader instanceof Closeable) try {
        ((Closeable) urlLoader).close();
      } catch (IOException ioe) {
        // ignore
      }
    }
  }
  
}