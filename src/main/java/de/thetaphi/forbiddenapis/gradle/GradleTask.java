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

import java.io.File;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/**
 * Forbiddenapis Gradle Task
 * @since 1.9
 */
public class GradleTask extends DefaultTask {

  /**
   * If the code changed, then it needs to be re-run.
   */
  @InputDirectory
  public File classesDir;

  /**
   * The {@link FileCollection}(s) used to configure the classpath.
   */
  @InputFiles
  public List<FileCollection> classpath = new ArrayList<FileCollection>();

  /**
   * Lists all files, which contain signatures and comments for forbidden API calls.
   * The signatures are resolved against the compile classpath.
   * @since 1.0
   */
  @Optional
  @InputFiles
  public List<File> signaturesFiles;

  /**
   * Gives a multiline list of signatures, inline in the pom.xml. Use an XML CDATA section to do that!
   * The signatures are resolved against the compile classpath.
   * @since 1.0
   */
  @Optional
  @Input
  public String signatures;

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
   * that you pass the whole classpath including all dependencies to this Mojo
   * (Maven does this by default).
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
  public List<String> includes = new ArrayList<String>();

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

  public GradleTask() {
    includes.add("**/*.class");
  }

  private String getTargetVersion() {
    return (targetVersion != null) ?
        targetVersion.toString() : getProject().property("targetCompatibility").toString();
  }

  @TaskAction
  public void checkForbidden() {
    // TODO
  }
  
}