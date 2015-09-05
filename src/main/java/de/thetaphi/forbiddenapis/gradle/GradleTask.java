package de.thetaphi.forbiddenapis.gradle;

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

import static de.thetaphi.forbiddenapis.Checker.Option.FAIL_ON_MISSING_CLASSES;
import static de.thetaphi.forbiddenapis.Checker.Option.FAIL_ON_UNRESOLVABLE_SIGNATURES;
import static de.thetaphi.forbiddenapis.Checker.Option.FAIL_ON_VIOLATION;
import static de.thetaphi.forbiddenapis.Checker.Option.INTERNAL_RUNTIME_FORBIDDEN;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.codehaus.plexus.util.DirectoryScanner;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import de.thetaphi.forbiddenapis.Checker;
import de.thetaphi.forbiddenapis.ForbiddenApiException;
import de.thetaphi.forbiddenapis.Logger;
import de.thetaphi.forbiddenapis.ParseException;

/**
 * Forbiddenapis Gradle Task
 * @since 1.9
 */
public class GradleTask extends DefaultTask {
  
  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  /**
   * Directory with the class files to check.
   */
  @InputDirectory
  public File classesDir;

  /**
   * A {@link FileCollection} used to configure the classpath.
   */
  @InputFiles
  public FileCollection classpath;

  /**
   * {@link FileCollection} containing all files, which contain signatures and comments for forbidden API calls.
   * The signatures are resolved against the compile classpath.
   * @since 1.0
   */
  @Optional
  @InputFiles
  public FileCollection signaturesFiles;

  /**
   * Gives multiple API signatures that are joined with newlines and
   * parsed like a single {@link #signaturesFiles}.
   * The signatures are resolved against the compile classpath.
   * @since 1.0
   */
  @Optional
  @Input
  public List<String> signatures;

  /**
   * Specifies <a href="bundled-signatures.html">built-in signatures</a> files (e.g., deprecated APIs for specific Java versions,
   * unsafe method calls using default locale, default charset,...)
   * @since 1.0
   */
  @Optional
  @Input
  public List<String> bundledSignatures;

  /**
   * Forbids calls to classes from the internal java runtime (like sun.misc.Unsafe)
   * @since 1.0
   */
  @Input
  public boolean internalRuntimeForbidden = false;

  /**
   * Fail the build, if the bundled ASM library cannot read the class file format
   * of the runtime library or the runtime library cannot be discovered.
   * @since 1.0
   */
  @Input
  public boolean failOnUnsupportedJava = false;
  
  /**
   * Fail the build, if a class referenced in the scanned code is missing. This requires
   * that you pass the whole classpath including all dependencies to this task
   * (Gradle does this by default).
   * @since 1.0
   */
  @Input
  public boolean failOnMissingClasses = true;
  
  /**
   * Fail the build if a signature is not resolving. If this parameter is set to
   * to false, then such signatures are silently ignored. This is useful in multi-module Maven
   * projects where only some modules have the dependency to which the signature file(s) apply.
   * @since 1.4
   */
  @Input
  public boolean failOnUnresolvableSignatures = true;

  /**
   * Fail the build if violations have been found. Defaults to {@code true}.
   * @since 1.9
   */
  @Input
  public boolean failOnViolation = true;

  /**
   * The default compiler target version used to expand references to bundled JDK signatures.
   * E.g., if you use "jdk-deprecated", it will expand to this version.
   * This setting should be identical to the target version used in the compiler plugin.
   * <p>
   * If undefined, it is taken from the project property {@code targetCompatibility}.
   * @since 1.0
   */
  @Optional
  @Input
  public JavaVersion targetVersion = null;

  /**
   * List of patterns matching all class files to be parsed from the classesDirectory.
   * Can be changed to e.g. exclude several files (using excludes).
   * The default is a single include with pattern '**&#47;*.class'
   * @see #excludes
   * @since 1.0
   */
  @Input
  public List<String> includes = new ArrayList<String>(Arrays.asList("**/*.class"));

  /**
   * List of patterns matching class files to be excluded from checking.
   * @see #includes
   * @since 1.0
   */
  @Optional
  @Input
  public List<String> excludes = new ArrayList<String>();

  /**
   * List of a custom Java annotations (full class names) that are used in the checked
   * code to suppress errors. Those annotations must have at least
   * {@link RetentionPolicy#CLASS}. They can be applied to classes, their methods,
   * or fields. By default, {@code @de.thetaphi.forbiddenapis.SuppressForbidden}
   * can always be used, but needs the {@code forbidden-apis.jar} file in classpath
   * of compiled project, which may not be wanted.
   * Instead of a full class name, a glob pattern may be used (e.g.,
   * {@code **.SuppressForbidden}).
   * @since 1.8
   */
  @Optional
  @Input
  public List<String> suppressAnnotations;

  private JavaVersion getTargetVersion() {
    return (targetVersion != null) ?
        targetVersion : (JavaVersion) getProject().property("targetCompatibility");
  }

  @TaskAction
  public void checkForbidden() {
    final Logger log = new Logger() {
      public void error(String msg) {
        getLogger().error(msg);
      }
      
      public void warn(String msg) {
        getLogger().warn(msg);
      }
      
      public void info(String msg) {
        getLogger().info(msg);
      }
    };
    
    final Collection<File> cpElements = classpath.getFiles();
    final URL[] urls = new URL[cpElements.size() + 1];
    try {
      int i = 0;
      for (final File cpElement : cpElements) {
        urls[i++] = cpElement.toURI().toURL();
      }
      urls[i++] = classesDir.toURI().toURL();
      assert i == urls.length;
    } catch (MalformedURLException mfue) {
      throw new InvalidUserDataException("Failed to build classpath URLs.", mfue);
    }

    URLClassLoader urlLoader = null;
    final ClassLoader loader = (urls.length > 0) ?
      (urlLoader = URLClassLoader.newInstance(urls, ClassLoader.getSystemClassLoader())) :
      ClassLoader.getSystemClassLoader();
    
    try {
      final EnumSet<Checker.Option> options = EnumSet.noneOf(Checker.Option.class);
      if (internalRuntimeForbidden) options.add(INTERNAL_RUNTIME_FORBIDDEN);
      if (failOnMissingClasses) options.add(FAIL_ON_MISSING_CLASSES);
      if (failOnViolation) options.add(FAIL_ON_VIOLATION);
      if (failOnUnresolvableSignatures) options.add(FAIL_ON_UNRESOLVABLE_SIGNATURES);
      final Checker checker = new Checker(log, loader, options);
      
      if (!checker.isSupportedJDK) {
        final String msg = String.format(Locale.ENGLISH, 
          "Your Java runtime (%s %s) is not supported by the forbiddenapis plugin. Please run the checks with a supported JDK!",
          System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version"));
        if (failOnUnsupportedJava) {
          throw new GradleException(msg);
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
      if (!classesDir.exists()) {
        log.warn("Classes directory does not exist, forbiddenapis check skipped: " + classesDir);
        return;
      }
      final DirectoryScanner ds = new DirectoryScanner();
      ds.setBasedir(classesDir);
      ds.setCaseSensitive(true);
      ds.setIncludes(includes == null ? null : includes.toArray(EMPTY_STRING_ARRAY));
      ds.setExcludes(excludes == null ? null : excludes.toArray(EMPTY_STRING_ARRAY));
      ds.addDefaultExcludes();
      ds.scan();
      final String[] files = ds.getIncludedFiles();
      if (files.length == 0) {
        log.warn(String.format(Locale.ENGLISH,
          "No classes found in '%s' (includes=%s, excludes=%s), forbiddenapis check skipped.",
          classesDir, includes, excludes));
        return;
      }
      
      try {
        if (signatures != null && !signatures.isEmpty()) {
          log.info("Reading inline API signatures...");
          final StringBuilder sb = new StringBuilder();
          for (String line : signatures) {
            sb.append(line).append('\n');
          }
          checker.parseSignaturesString(sb.toString());
        }
        if (bundledSignatures != null) {
          JavaVersion targetVersion = getTargetVersion();
          if (targetVersion == null) {
            log.warn("The 'targetVersion' parameter or 'targetCompatibility' project property is missing. " +
              "Trying to read bundled JDK signatures without compiler target. " +
              "You have to explicitely specify the version in the resource name.");
          }
          for (String bs : bundledSignatures) {
            log.info("Reading bundled API signatures: " + bs);
            checker.parseBundledSignatures(bs, targetVersion == null ? null : targetVersion.toString());
          }
        }
        if (signaturesFiles != null) for (final File f : signaturesFiles) {
          log.info("Reading API signatures: " + f);
          checker.parseSignaturesFile(new FileInputStream(f));
        }
      } catch (IOException ioe) {
        throw new ResourceException("IO problem while reading files with API signatures.", ioe);
      } catch (ParseException pe) {
        throw new InvalidUserDataException("Parsing signatures failed: " + pe.getMessage());
      }

      if (checker.hasNoSignatures()) {
        if (failOnUnresolvableSignatures) {
          throw new InvalidUserDataException("No API signatures found; use parameters 'signatures', 'bundledSignatures', and/or 'signaturesFiles' to define those!");
        } else {
          log.info("Skipping execution because no API signatures are available.");
          return;
        }
      }

      log.info("Loading classes to check...");
      try {
        for (String f : files) {
          checker.addClassToCheck(new FileInputStream(new File(classesDir, f)));
        }
      } catch (IOException ioe) {
        throw new ResourceException("Failed to load one of the given class files.", ioe);
      }

      log.info("Scanning for API signatures and dependencies...");
      try {
        checker.run();
      } catch (ForbiddenApiException fae) {
        throw new GradleForbiddenApiException(fae.getMessage());
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
