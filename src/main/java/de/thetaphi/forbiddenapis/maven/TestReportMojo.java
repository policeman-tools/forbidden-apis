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

package de.thetaphi.forbiddenapis.maven;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Mojo to generate a report of all calls to forbidden APIs in project generated class files (test scope),
 * to be included in the Maven site. It uses the same parameters like the {@code testCheck} goal,
 * but instead of failing the build, it lists all violations with their severity in a report.
 * See the {@code report} goal for an example how to configure it in the {@code <reporting>} section
 * of your POM.
 * <p>
 * The report is created from the compiled test classes, so they must be available when the site is generated
 * (e.g., run {@code mvn verify site}); without class files, no report is generated.
 * The report never fails the build because of violations; parameter {@code failOnViolation} only decides
 * if violations are reported as errors (default) or warnings. Violations of signatures with severity
 * {@code SUPPRESS} are not listed.
 * <p>
 * If you execute this goal directly (outside of site generation), Maven uses the plugin configuration
 * of the {@code <build>} section, and the goal writes a standalone HTML page {@code forbiddenapis-test.html}
 * into the reporting output directory.
 * @since 3.12
 */
@Mojo(name = "testReport", threadSafe = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST)
public final class TestReportMojo extends AbstractReportMojo {

  /**
   * Injected test classpath.
   */
  @Parameter(defaultValue = "${project.testClasspathElements}", readonly = true, required = true)
  private List<String> classpathElements;

  /**
   * Directory with the class files to check.
   */
  @Parameter(required = false, defaultValue = "${project.build.testOutputDirectory}")
  private File classesDirectory;

  /**
   * The default compiler target version used to expand references to bundled JDK signatures.
   * This setting falls back to "targetVersion" if undefined. This can be used to override
   * the target version solely used for tests.
   * E.g., if you use "jdk-deprecated", it will expand to this version.
   * This setting should be identical to the target version used in the compiler plugin.
   * @since 3.12
   */
  @Parameter(required = false, defaultValue = "${maven.compiler.testTarget}")
  private String testTargetVersion;

  /**
   * The default compiler release version used to expand references to bundled JDK signatures.
   * This setting falls back to "releaseVersion" if undefined. This can be used to override
   * the release version solely used for tests.
   * E.g., if you use "jdk-deprecated", it will expand to this version.
   * This setting should be identical to the release version used in the compiler plugin.
   * If given, this setting is used in preference to {@link #testTargetVersion}.
   * @since 3.12
   */
  @Parameter(required = false, defaultValue = "${maven.compiler.testRelease}")
  private String testReleaseVersion;

  @Override
  protected List<String> getClassPathElements() {
    return this.classpathElements;
  }

  @Override
  protected File getClassesDirectory() {
    return this.classesDirectory;
  }

  @Override
  protected String getTargetVersion() {
    return (testReleaseVersion != null) ?
            testReleaseVersion : (testTargetVersion != null) ? testTargetVersion : super.getTargetVersion();
  }

  @Override
  public String getOutputName() {
    return "forbiddenapis-test";
  }

  @Override
  public String getName(Locale locale) {
    return "Forbidden APIs (Tests)";
  }

  @Override
  public String getDescription(Locale locale) {
    return "Calls to forbidden APIs in the test classes, detected by Policeman's Forbidden API Checker.";
  }

  @Override
  String getScopeDescription() {
    return "test classes";
  }

}
