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
 * Mojo to generate a report of all calls to forbidden APIs in project generated class files (compile scope),
 * to be included in the Maven site. It uses the same parameters like the {@code check} goal,
 * but instead of failing the build, it lists all violations with their severity in a report.
 * <p>
 * Add the plugin to the {@code <reporting>} section of your POM (the configuration is not shared with
 * the {@code <build>} section, so you have to repeat it):
 * <pre>
 * &lt;reporting&gt;
 *   &lt;plugins&gt;
 *     &lt;plugin&gt;
 *       &lt;groupId&gt;de.thetaphi&lt;/groupId&gt;
 *       &lt;artifactId&gt;forbiddenapis&lt;/artifactId&gt;
 *       &lt;version&gt;<i>(same as in build section)</i>&lt;/version&gt;
 *       &lt;configuration&gt;
 *         &lt;bundledSignatures&gt;
 *           &lt;bundledSignature&gt;jdk-unsafe&lt;/bundledSignature&gt;
 *           &lt;bundledSignature&gt;jdk-deprecated&lt;/bundledSignature&gt;
 *         &lt;/bundledSignatures&gt;
 *       &lt;/configuration&gt;
 *       &lt;reportSets&gt;
 *         &lt;reportSet&gt;
 *           &lt;reports&gt;
 *             &lt;report&gt;report&lt;/report&gt;
 *             &lt;report&gt;testReport&lt;/report&gt;
 *           &lt;/reports&gt;
 *         &lt;/reportSet&gt;
 *       &lt;/reportSets&gt;
 *     &lt;/plugin&gt;
 *   &lt;/plugins&gt;
 * &lt;/reporting&gt;
 * </pre>
 * The report is created from the compiled classes, so they must be available when the site is generated
 * (e.g., run {@code mvn verify site}); without class files, no report is generated.
 * The report never fails the build because of violations; parameter {@code failOnViolation} only decides
 * if violations are reported as errors (default) or warnings. Violations of signatures with severity
 * {@code SUPPRESS} are not listed.
 * <p>
 * If you execute this goal directly (outside of site generation), Maven uses the plugin configuration
 * of the {@code <build>} section, and the goal writes a standalone HTML page {@code forbiddenapis.html}
 * into the reporting output directory.
 * @since 3.12
 */
@Mojo(name = "report", threadSafe = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public final class ReportMojo extends AbstractReportMojo {

  /**
   * Injected compile classpath.
   */
  @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
  private List<String> classpathElements;

  /**
   * Directory with the class files to check.
   */
  @Parameter(required = false, defaultValue = "${project.build.outputDirectory}")
  private File classesDirectory;

  @Override
  protected List<String> getClassPathElements() {
    return this.classpathElements;
  }

  @Override
  protected File getClassesDirectory() {
    return this.classesDirectory;
  }

  @Override
  public String getOutputName() {
    return "forbiddenapis";
  }

  @Override
  public String getName(Locale locale) {
    return "Forbidden APIs";
  }

  @Override
  public String getDescription(Locale locale) {
    return "Calls to forbidden APIs in the main classes, detected by Policeman's Forbidden API Checker.";
  }

  @Override
  String getScopeDescription() {
    return "main classes";
  }

}
