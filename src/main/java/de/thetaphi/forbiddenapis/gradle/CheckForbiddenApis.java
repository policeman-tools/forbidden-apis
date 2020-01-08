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

package de.thetaphi.forbiddenapis.gradle;

import static de.thetaphi.forbiddenapis.Checker.Option.*;
import groovy.lang.Closure;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import de.thetaphi.forbiddenapis.Checker;
import de.thetaphi.forbiddenapis.Constants;
import de.thetaphi.forbiddenapis.ForbiddenApiException;
import de.thetaphi.forbiddenapis.Logger;
import de.thetaphi.forbiddenapis.ParseException;

/**
 * <h3>ForbiddenApis Gradle Task (requires at least Gradle v2.3)</h3>
 * <p>
 * The plugin registers a separate task for each defined {@code sourceSet} using
 * the default task naming convention. For default Java projects, two tasks are created:
 * {@code forbiddenApisMain} and {@code forbiddenApisTest}. Additional source sets
 * will produce a task with similar names ({@code 'forbiddenApis' + nameOfSourceSet}).
 * All tasks are added as dependencies to the {@code check} default Gradle task.
 * For convenience, the plugin also defines an additional task {@code forbiddenApis}
 * that runs checks on all source sets.
 * <p>
 * Installation can be done from your {@code build.gradle} file using the Gradle {@code plugin} DSL:
 * <pre>
 * plugins {
 *  id 'de.thetaphi.forbiddenapis' version '@VERSION@'
 * }
 * </pre>
 * Alternatively, you can use the following script snippet if dynamic configuration is required (e.g., for own tasks):
 * <pre>
 * buildscript {
 *  repositories {
 *   mavenCentral()
 *  }
 *  dependencies {
 *   classpath '@GROUPID@:@ARTIFACTID@:@VERSION@'
 *  }
 * }
 * 
 * apply plugin: 'de.thetaphi.forbiddenapis'
 * </pre>
 * After that you can add the following task configuration closures:
 * <pre>
 * forbiddenApisMain {
 *  bundledSignatures += 'jdk-system-out'
 * }
 * </pre>
 * <em>(using the {@code '+='} notation, you can add additional bundled signatures to the defaults).</em>
 * <p>
 * To define those defaults, which are used by all source sets, you can use the
 * extension / convention mapping provided by {@link CheckForbiddenApisExtension}:
 * <pre>
 * forbiddenApis {
 *  bundledSignatures = [ 'jdk-unsafe', 'jdk-deprecated' ]
 *  signaturesFiles = files('path/to/my/signatures.txt')
 *  ignoreFailures = false
 * }
 * </pre>
 * 
 * @since 2.0
 */
@ParallelizableTask
public class CheckForbiddenApis extends DefaultTask implements PatternFilterable,VerificationTask,Constants {
  
  private static final String NL = System.getProperty("line.separator", "\n");
  
  private final CheckForbiddenApisExtension data = new CheckForbiddenApisExtension();
  private final PatternSet patternSet = new PatternSet().include("**/*.class");
  private FileCollection classesDirs;
  private FileCollection classpath;
  private String targetCompatibility;
  
  /**
   * Directories with the class files to check.
   * Defaults to current sourseSet's output directory (Gradle 2/3) or output directories (Gradle 4.0+).
   */
  @OutputDirectories
  // no @InputDirectories, we use separate getter for a list of all input files
  public FileCollection getClassesDirs() {
    return classesDirs;
  }

  /** @see #getClassesDirs */
  public void setClassesDirs(FileCollection classesDirs) {
    if (classesDirs == null) throw new NullPointerException("classesDirs");
    this.classesDirs = classesDirs;
  }

  /**
   * Directory with the class files to check.
   * Defaults to current sourseSet's output directory (Gradle 2/3 only).
   * @deprecated use {@link #getClassesDirs()} instead. If there are more than one
   *  {@code classesDir} set by {@link #setClassesDirs(FileCollection)}, this getter may
   *  throw an exception!
   */
  @Deprecated
  public File getClassesDir() {
    final FileCollection col = getClassesDirs();
    return (col == null) ? null : col.getSingleFile();
  }

  /** Sets the directory where to look for classes. Overwrites any value set by {@link #setClassesDirs(FileCollection)}!
   * @deprecated use {@link #setClassesDirs(FileCollection)} instead.
   * @see #getClassesDir
   */
  @Deprecated
  public void setClassesDir(File classesDir) {
    if (classesDir == null) throw new NullPointerException("classesDir");
    getLogger().warn("The 'classesDir' property on the '{}' task is deprecated. Use 'classesDirs' of type FileCollection instead!",
        getName());
    setClassesDirs(getProject().files(classesDir));
  }

  /** Returns the pattern set to match against class files in {@link #getClassesDir()}. */
  public PatternSet getPatternSet() {
    return patternSet;
  }
  
  /** @see #getPatternSet() */
  public void setPatternSet(PatternSet patternSet) {
    patternSet.copyFrom(patternSet);
  }

  /**
   * A {@link FileCollection} used to configure the classpath.
   * Defaults to current sourseSet's compile classpath.
   */
  @InputFiles
  @Classpath
  public FileCollection getClasspath() {
    return classpath;
  }

  /** @see #getClasspath */
  public void setClasspath(FileCollection classpath) {
    if (classpath == null) throw new NullPointerException("classpath");
    this.classpath = classpath;
  }

  /**
   * A {@link FileCollection} containing all files, which contain signatures and comments for forbidden API calls.
   * The signatures are resolved against {@link #getClasspath()}.
   */
  @InputFiles
  @Optional
  public FileCollection getSignaturesFiles() {
    return data.signaturesFiles;
  }

  /** @see #getSignaturesFiles */
  public void setSignaturesFiles(FileCollection signaturesFiles) {
    data.signaturesFiles = signaturesFiles;
  }

  /**
   * A list of references to URLs, which contain signatures and comments for forbidden API calls.
   * The signatures are resolved against {@link #getClasspath()}.
   * <p>
   * This property is useful to refer to resources in plugin classpath, e.g., using
   * {@link Class#getResource(String)}. It is not useful for general gradle builds. Especially,
   * don't use it to refer to resources on foreign servers!
   */
  @Input
  @Optional
  @Incubating
  public Set<URL> getSignaturesURLs() {
    return data.signaturesURLs;
  }

  /** @see #getSignaturesURLs */
  public void setSignaturesURLs(Set<URL> signaturesURLs) {
    data.signaturesURLs = signaturesURLs;
  }

  /**
   * Gives multiple API signatures that are joined with newlines and
   * parsed like a single {@link #getSignaturesFiles()}.
   * The signatures are resolved against {@link #getClasspath()}.
   */
  @Input
  @Optional
  public List<String> getSignatures() {
    return data.signatures;
  }

  /** @see #getSignatures */
  public void setSignatures(List<String> signatures) {
    data.signatures = signatures;
  }

  /**
   * Specifies <a href="bundled-signatures.html">built-in signatures</a> files (e.g., deprecated APIs for specific Java versions,
   * unsafe method calls using default locale, default charset,...)
   */
  @Input
  @Optional
  public Set<String> getBundledSignatures() {
    return data.bundledSignatures;
  }

  /** @see #getBundledSignatures */
  public void setBundledSignatures(Set<String> bundledSignatures) {
    data.bundledSignatures = bundledSignatures;
  }

  /**
   * Forbids calls to non-portable runtime APIs (like {@code sun.misc.Unsafe}).
   * <em>Please note:</em> This enables {@code "jdk-non-portable"} bundled signatures for backwards compatibility.
   * Defaults to {@code false}. 
   * @deprecated Use <a href="bundled-signatures.html">bundled signatures</a> {@code "jdk-non-portable"} or {@code "jdk-internal"} instead.
   */
  @Deprecated
  @Input
  public boolean getInternalRuntimeForbidden() {
    return data.internalRuntimeForbidden;
  }

  /** @see #getInternalRuntimeForbidden
   * @deprecated Use bundled signatures {@code "jdk-non-portable"} or {@code "jdk-internal"} instead.
   */
  @Deprecated
  public void setInternalRuntimeForbidden(boolean internalRuntimeForbidden) {
    data.internalRuntimeForbidden = internalRuntimeForbidden;
  }

  /**
   * Fail the build, if the bundled ASM library cannot read the class file format
   * of the runtime library or the runtime library cannot be discovered.
   * Defaults to {@code false}.
   */
  @Input
  public boolean getFailOnUnsupportedJava() {
    return data.failOnUnsupportedJava;
  }

  /** @see #getFailOnUnsupportedJava */
  public void setFailOnUnsupportedJava(boolean failOnUnsupportedJava) {
    data.failOnUnsupportedJava = failOnUnsupportedJava;
  }

  /**
   * Fail the build, if a class referenced in the scanned code is missing. This requires
   * that you pass the whole classpath including all dependencies to this task
   * (Gradle does this by default).
   * Defaults to {@code true}.
   */
  @Input
  public boolean getFailOnMissingClasses() {
    return data.failOnMissingClasses;
  }

  /** @see #getFailOnMissingClasses */
  public void setFailOnMissingClasses(boolean failOnMissingClasses) {
    data.failOnMissingClasses = failOnMissingClasses;
  }

  /**
   * Fail the build if a signature is not resolving. If this parameter is set to
   * to false, then such signatures are silently ignored. This is useful in multi-module Maven
   * projects where only some modules have the dependency to which the signature file(s) apply.
   * Defaults to {@code true}.
   */
  @Input
  public boolean getFailOnUnresolvableSignatures() {
    return data.failOnUnresolvableSignatures;
  }

  /** @see #getFailOnUnresolvableSignatures */
  public void setFailOnUnresolvableSignatures(boolean failOnUnresolvableSignatures) {
    data.failOnUnresolvableSignatures = failOnUnresolvableSignatures;
  }

  /**
   * {@inheritDoc}
   * <p>
   * This setting is to conform with {@link VerificationTask} interface.
   * Other ForbiddenApis implementations use another name: {@code failOnViolation}
   * Default is {@code false}.
   */
  @Override
  @Input
  public boolean getIgnoreFailures() {
    return data.ignoreFailures;
  }

  @Override
  public void setIgnoreFailures(boolean ignoreFailures) {
    data.ignoreFailures = ignoreFailures;
  }

  /**
   * Disable the internal JVM classloading cache when getting bytecode from
   * the classpath. This setting slows down checks, but <em>may</em> work around
   * issues with other plugin, that do not close their class loaders.
   * If you get {@code FileNotFoundException}s related to non-existent JAR entries
   * you can try to work around using this setting.
   * <p>
   * The default is {@code false}, unless the plugin detects that your build is
   * running in the <em>Gradle Daemon</em> (which has this problem), setting the
   * default to {@code true} as a consequence.
   * @since 2.2
   */
  @Input
  public boolean getDisableClassloadingCache() {
    return data.disableClassloadingCache;
  }

  /** @see #getDisableClassloadingCache */
  public void setDisableClassloadingCache(boolean disableClassloadingCache) {
    data.disableClassloadingCache = disableClassloadingCache;
  }

  /**
   * List of a custom Java annotations (full class names) that are used in the checked
   * code to suppress errors. Those annotations must have at least
   * {@link RetentionPolicy#CLASS}. They can be applied to classes, their methods,
   * or fields. By default, {@code @de.thetaphi.forbiddenapis.SuppressForbidden}
   * can always be used, but needs the {@code forbidden-apis.jar} file in classpath
   * of compiled project, which may not be wanted.
   * Instead of a full class name, a glob pattern may be used (e.g.,
   * {@code **.SuppressForbidden}).
   */
  @Input
  @Optional
  public Set<String> getSuppressAnnotations() {
    return data.suppressAnnotations;
  }

  /** @see #getSuppressAnnotations */
  public void setSuppressAnnotations(Set<String> suppressAnnotations) {
    data.suppressAnnotations = suppressAnnotations;
  }
  
  /**
   * The default compiler target version used to expand references to bundled JDK signatures.
   * E.g., if you use "jdk-deprecated", it will expand to this version.
   * This setting should be identical to the target version used in the compiler task.
   * Defaults to {@code project.targetCompatibility}.
   */
  @Input
  @Optional
  public String getTargetCompatibility() {
    return targetCompatibility;
  }

  /** @see #getTargetCompatibility */
  public void setTargetCompatibility(String targetCompatibility) {
    this.targetCompatibility = targetCompatibility;
  }
  
  // PatternFilterable implementation:
  
  /**
   * {@inheritDoc}
   * <p>
   * Set of patterns matching all class files to be parsed from the classesDirectory.
   * Can be changed to e.g. exclude several files (using excludes).
   * The default is a single include with pattern '**&#47;*.class'
   */
  @Override
  @Input
  public Set<String> getIncludes() {
    return getPatternSet().getIncludes();
  }

  @Override
  public CheckForbiddenApis setIncludes(Iterable<String> includes) {
    getPatternSet().setIncludes(includes);
    return this;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Set of patterns matching class files to be excluded from checking.
   */
  @Override
  @Input
  public Set<String> getExcludes() {
    return getPatternSet().getExcludes();
  }

  @Override
  public CheckForbiddenApis setExcludes(Iterable<String> excludes) {
    getPatternSet().setExcludes(excludes);
    return this;
  }

  @Override
  public CheckForbiddenApis exclude(String... arg0) {
    getPatternSet().exclude(arg0);
    return this;
  }

  @Override
  public CheckForbiddenApis exclude(Iterable<String> arg0) {
    getPatternSet().exclude(arg0);
    return this;
  }

  @Override
  public CheckForbiddenApis exclude(Spec<FileTreeElement> arg0) {
    getPatternSet().exclude(arg0);
    return this;
  }

  @Override
  public CheckForbiddenApis exclude(@SuppressWarnings("rawtypes") Closure arg0) {
    getPatternSet().exclude(arg0);
    return this;
  }

  @Override
  public CheckForbiddenApis include(String... arg0) {
    getPatternSet().include(arg0);
    return this;
  }

  @Override
  public CheckForbiddenApis include(Iterable<String> arg0) {
    getPatternSet().include(arg0);
    return this;
  }

  @Override
  public CheckForbiddenApis include(Spec<FileTreeElement> arg0) {
    getPatternSet().include(arg0);
    return this;
  }

  @Override
  public CheckForbiddenApis include(@SuppressWarnings("rawtypes") Closure arg0) {
    getPatternSet().include(arg0);
    return this;
  }

  /** Returns the classes to check. */
  @InputFiles
  @SkipWhenEmpty
  public FileTree getClassFiles() {
    return getClassesDirs().getAsFileTree().matching(getPatternSet());
  }

  /** Executes the forbidden apis task. */
  @TaskAction
  public void checkForbidden() throws ForbiddenApiException {
    final FileCollection classesDirs = getClassesDirs();
    final FileCollection classpath = getClasspath();
    if (classesDirs == null || classpath == null) {
      throw new InvalidUserDataException("Missing 'classesDirs' or 'classpath' property.");
    }
    
    final Logger log = new Logger() {
      @Override
      public void error(String msg) {
        getLogger().error(msg);
      }
      
      @Override
      public void warn(String msg) {
        getLogger().warn(msg);
      }
      
      @Override
      public void info(String msg) {
        getLogger().info(msg);
      }
    };
    
    final Set<File> cpElements = new LinkedHashSet<File>();
    cpElements.addAll(classpath.getFiles());
    cpElements.addAll(classesDirs.getFiles());
    final URL[] urls = new URL[cpElements.size()];
    try {
      int i = 0;
      for (final File cpElement : cpElements) {
        urls[i++] = cpElement.toURI().toURL();
      }
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
      if (getFailOnMissingClasses()) options.add(FAIL_ON_MISSING_CLASSES);
      if (!getIgnoreFailures()) options.add(FAIL_ON_VIOLATION);
      if (getFailOnUnresolvableSignatures()) options.add(FAIL_ON_UNRESOLVABLE_SIGNATURES);
      if (getDisableClassloadingCache()) options.add(DISABLE_CLASSLOADING_CACHE);
      final Checker checker = new Checker(log, loader, options);
      
      if (!checker.isSupportedJDK) {
        final String msg = String.format(Locale.ENGLISH, 
          "Your Java runtime (%s %s) is not supported by the forbiddenapis plugin. Please run the checks with a supported JDK!",
          System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version"));
        if (getFailOnUnsupportedJava()) {
          throw new GradleException(msg);
        } else {
          log.warn(msg);
          return;
        }
      }
      
      final Set<String> suppressAnnotations = getSuppressAnnotations();
      if (suppressAnnotations != null) {
        for (String a : suppressAnnotations) {
          checker.addSuppressAnnotation(a);
        }
      }
      
      try {
        final Set<String> bundledSignatures = getBundledSignatures();
        if (bundledSignatures != null) {
          final String bundledSigsJavaVersion = getTargetCompatibility();
          if (bundledSigsJavaVersion == null) {
            log.warn("The 'targetCompatibility' project or task property is missing. " +
              "Trying to read bundled JDK signatures without compiler target. " +
              "You have to explicitly specify the version in the resource name.");
          }
          for (String bs : bundledSignatures) {
            checker.addBundledSignatures(bs, bundledSigsJavaVersion);
          }
        }
        if (getInternalRuntimeForbidden()) {
          log.warn(DEPRECATED_WARN_INTERNALRUNTIME);
          checker.addBundledSignatures(BS_JDK_NONPORTABLE, null);
        }
        
        final FileCollection signaturesFiles = getSignaturesFiles();
        if (signaturesFiles != null) for (final File f : signaturesFiles) {
          checker.parseSignaturesFile(f);
        }
        final Set<URL> signaturesURLs = getSignaturesURLs();
        if (signaturesURLs != null) for (final URL url : signaturesURLs) {
          checker.parseSignaturesFile(url);
        }
        final List<String> signatures = getSignatures();
        if (signatures != null && !signatures.isEmpty()) {
          final StringBuilder sb = new StringBuilder();
          for (String line : signatures) {
            sb.append(line).append(NL);
          }
          checker.parseSignaturesString(sb.toString());
        }
      } catch (IOException ioe) {
        throw new GradleException("IO problem while reading files with API signatures.", ioe);
      } catch (ParseException pe) {
        throw new InvalidUserDataException("Parsing signatures failed: " + pe.getMessage(), pe);
      }

      if (checker.hasNoSignatures()) {
        if (options.contains(FAIL_ON_UNRESOLVABLE_SIGNATURES)) {
          throw new InvalidUserDataException("No API signatures found; use properties 'signatures', 'bundledSignatures', 'signaturesURLs', and/or 'signaturesFiles' to define those!");
        } else {
          log.info("Skipping execution because no API signatures are available.");
          return;
        }
      }

      try {
        checker.addClassesToCheck(getClassFiles());
      } catch (IOException ioe) {
        throw new GradleException("Failed to load one of the given class files.", ioe);
      }

      checker.run();
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
