package de.thetaphi.forbiddenapis;

/*
 * (C) Copyright Uwe Schindler (Generics Policeman) and others.
 *
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Base class for forbiddenapis Mojos.
 * @since 1.0
 */
public abstract class AbstractCheckMojo extends AbstractMojo {

  /**
   * Lists all files, which contain signatures and comments for forbidden API calls.
   * The signatures are resolved against the compile classpath.
   * @since 1.0
   */
  @Parameter(required = false)
  private File[] signaturesFiles;

  /**
   * Gives a multiline list of signatures, inline in the pom.xml. Use an XML CDATA section to do that!
   * The signatures are resolved against the compile classpath.
   * @since 1.0
   */
  @Parameter(required = false)
  private String signatures;

  /**
   * Specifies <a href="bundled-signatures.html">built-in signatures</a> files (e.g., deprecated APIs for specific Java versions,
   * unsafe method calls using default locale, default charset,...)
   * @since 1.0
   */
  @Parameter(required = false)
  private String[] bundledSignatures;

  /**
   * Forbids calls to classes from the internal java runtime (like sun.misc.Unsafe)
   * @since 1.0
   */
  @Parameter(required = false, defaultValue = "false")
  private boolean internalRuntimeForbidden;

  /**
   * Fail the build, if the bundled ASM library cannot read the class file format
   * of the runtime library or the runtime library cannot be discovered.
   * @since 1.0
   */
  @Parameter(required = false, defaultValue = "false")
  private boolean failOnUnsupportedJava;
  
  /**
   * Fail the build, if a class referenced in the scanned code is missing. This requires
   * that you pass the whole classpath including all dependencies to this Mojo
   * (Maven does this by default).
   * @since 1.0
   */
  @Parameter(required = false, defaultValue = "true")
  private boolean failOnMissingClasses;
  
  /**
   * Fail the build if a signature is not resolving. If this parameter is set to
   * to false, then such signatures are silently ignored. This is useful in multi-module Maven
   * projects where only some modules have the dependency to which the signature file(s) apply.
   * @since 1.4
   */
  @Parameter(required = false, defaultValue = "true")
  private boolean failOnUnresolvableSignatures;
  
  /**
   * The default compiler target version used to expand references to bundled JDK signatures.
   * E.g., if you use "jdk-deprecated", it will expand to this version.
   * This setting should be identical to the target version used in the compiler plugin.
   * @since 1.0
   */
  @Parameter(required = false, defaultValue = "${maven.compiler.target}")
  private String targetVersion;

  /**
   * List of patterns matching all class files to be parsed from the classesDirectory.
   * Can be changed to e.g. exclude several files (using excludes).
   * The default is a single include with pattern '**&#47;*.class'
   * @see #excludes
   * @since 1.0
   */
  @Parameter(required = false)
  private String[] includes;

  /**
   * List of patterns matching class files to be excluded from checking.
   * @see #includes
   * @since 1.0
   */
  @Parameter(required = false)
  private String[] excludes;

  /**
   * List of a custom Java annotations (full class names) that are used in the checked
   * code to suppress errors. Those annotations must have at least
   * {@link RetentionPolicy#CLASS}. They can be applied to classes, their methods,
   * or fields. By default, {@code @de.thetaphi.forbiddenapis.SuppressForbidden}
   * can always be used, but needs the {@code forbidden-apis.jar} file in classpath
   * of compiled project, which may not be wanted.
   * @since 1.8
   */
  @Parameter(required = false)
  private String[] suppressAnnotations;

  /**
   * Skip entire check. Most useful on the command line via "-Dforbiddenapis.skip=true".
   * @since 1.6
   */
  @Parameter(required = false, property="forbiddenapis.skip", defaultValue="false")
  private boolean skip;

  /** The project packaging (pom, jar, etc.). */
  @Parameter(defaultValue = "${project.packaging}", readonly = true, required = true)
  private String packaging;

  /** provided by the concrete Mojos for compile and test classes processing */
  protected abstract List<String> getClassPathElements();
  
  /** provided by the concrete Mojos for compile and test classes processing */
  protected abstract File getClassesDirectory();

  /** gets overridden for test, because it uses testTargetVersion as optional name to override */
  protected String getTargetVersion() {
    return targetVersion;
  }

  // Not in Java 5: @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    final Log log = getLog();
    
    if (skip) {
      log.info("Skipping forbidden-apis checks.");
      return;
    }
    
    // In multi-module projects, one may want to configure the plugin in the parent/root POM.
    // However, it should not be executed for this type of POMs.
    if ("pom".equals(packaging)) {
      log.info("Skipping execution for packaging \"" + packaging + "\"");
      return;
    }

    // set default param:
    if (includes == null) includes = new String[] {"**/*.class"};
    
    final URL[] urls;
    try {
      final List<String> cp = getClassPathElements();
      urls = new URL[cp.size()];
      int i = 0;
      for (final String cpElement : cp) {
        urls[i++] = new File(cpElement).toURI().toURL();
      }
      assert i == urls.length;
      if (log.isDebugEnabled()) log.debug("Compile Classpath: " + Arrays.toString(urls));
    } catch (MalformedURLException e) {
      throw new MojoExecutionException("Failed to build classpath: " + e);
    }

    URLClassLoader urlLoader = null;
    final ClassLoader loader = (urls.length > 0) ?
      (urlLoader = URLClassLoader.newInstance(urls, ClassLoader.getSystemClassLoader())) :
      ClassLoader.getSystemClassLoader();
    
    try {
      final Checker checker = new Checker(loader, internalRuntimeForbidden, failOnMissingClasses, failOnUnresolvableSignatures) {
        @Override
        protected void logError(String msg) {
          log.error(msg);
        }
        
        @Override
        protected void logWarn(String msg) {
          log.warn(msg);
        }
        
        @Override
        protected void logInfo(String msg) {
          log.info(msg);
        }
      };
      
      if (!checker.isSupportedJDK) {
        final String msg = String.format(Locale.ENGLISH, 
          "Your Java runtime (%s %s) is not supported by the forbiddenapis MOJO. Please run the checks with a supported JDK!",
          System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version"));
        if (failOnUnsupportedJava) {
          throw new MojoExecutionException(msg);
        } else {
          log.warn(msg);
          return;
        }
      }
      
      if (suppressAnnotations != null) {
        for (String a : suppressAnnotations) {
          checker.addSuppressAnnotation(a);
        }
      }
      
      log.info("Scanning for classes to check...");
      final File classesDirectory = getClassesDirectory();
      if (!classesDirectory.exists()) {
        log.warn("Classes directory does not exist, forbiddenapis check skipped: " + classesDirectory);
        return;
      }
      final DirectoryScanner ds = new DirectoryScanner();
      ds.setBasedir(classesDirectory);
      ds.setCaseSensitive(true);
      ds.setIncludes(includes);
      ds.setExcludes(excludes);
      ds.addDefaultExcludes();
      ds.scan();
      final String[] files = ds.getIncludedFiles();
      if (files.length == 0) {
        log.warn(String.format(Locale.ENGLISH,
          "No classes found in '%s' (includes=%s, excludes=%s), forbiddenapis check skipped.",
          classesDirectory.toString(), Arrays.toString(includes), Arrays.toString(excludes)));
        return;
      }
      
      try {
        final String sig = (signatures != null) ? signatures.trim() : null;
        if (sig != null && sig.length() != 0) {
          log.info("Reading inline API signatures...");
          checker.parseSignaturesString(sig);
        }
        if (bundledSignatures != null) {
          String targetVersion = getTargetVersion();
          if ("".equals(targetVersion)) targetVersion = null;
          if (targetVersion == null) {
            log.warn("The 'targetVersion' parameter or '${maven.compiler.target}' property is missing. " +
              "Trying to read bundled JDK signatures without compiler target. " +
              "You have to explicitely specify the version in the resource name.");
          }
          for (String bs : bundledSignatures) {
            log.info("Reading bundled API signatures: " + bs);
            checker.parseBundledSignatures(bs, targetVersion);
          }
        }
        if (signaturesFiles != null) for (final File f : signaturesFiles) {
          log.info("Reading API signatures: " + f);
          checker.parseSignaturesFile(new FileInputStream(f));
        }
      } catch (IOException ioe) {
        throw new MojoExecutionException("IO problem while reading files with API signatures: " + ioe);
      } catch (ParseException pe) {
        throw new MojoExecutionException("Parsing signatures failed: " + pe.getMessage());
      }

      if (checker.hasNoSignatures()) {
        if (failOnUnresolvableSignatures) {
          throw new MojoExecutionException("No API signatures found; use parameters 'signatures', 'bundledSignatures', and/or 'signaturesFiles' to define those!");
        } else {
          log.info("Skipping execution because no API signatures are available.");
          return;
        }
      }

      log.info("Loading classes to check...");
      try {
        for (String f : files) {
          checker.addClassToCheck(new FileInputStream(new File(classesDirectory, f)));
        }
      } catch (IOException ioe) {
        throw new MojoExecutionException("Failed to load one of the given class files: " + ioe);
      }

      log.info("Scanning for API signatures and dependencies...");
      try {
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