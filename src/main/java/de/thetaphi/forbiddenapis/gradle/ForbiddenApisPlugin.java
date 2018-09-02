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

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.util.DelegatingScript;

import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;

/**
 * Forbiddenapis Gradle Plugin (requires at least Gradle 2.3)
 * @since 2.0
 */
public class ForbiddenApisPlugin implements Plugin<Project> {
  
  /** Resource with Groovy script that initializes the plugin. */
  private static final String PLUGIN_INIT_SCRIPT = "plugin-init.groovy";
  
  /** Name of the base task that depends on one for every SourceSet. */
  public static final String FORBIDDEN_APIS_TASK_NAME = "forbiddenApis";
  
  /** Name of the extension to define defaults for all tasks of this module. */
  public static final String FORBIDDEN_APIS_EXTENSION_NAME = "forbiddenApis";
  
  /** Minimum Gradle version this plugin requires to run (v2.3). */
  public static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("2.3");
  
  private static final Class<? extends DelegatingScript> compiledScript;
  static {
    final ImportCustomizer importCustomizer = new ImportCustomizer().addStarImports(ForbiddenApisPlugin.class.getPackage().getName());
    final CompilerConfiguration configuration = new CompilerConfiguration().addCompilationCustomizers(importCustomizer);
    configuration.setScriptBaseClass(DelegatingScript.class.getName());
    configuration.setSourceEncoding("UTF-8");
    final URL scriptUrl = ForbiddenApisPlugin.class.getResource(PLUGIN_INIT_SCRIPT);
    if (scriptUrl == null) {
      throw new RuntimeException("Cannot find resource with script: " + PLUGIN_INIT_SCRIPT);
    }
    compiledScript = AccessController.doPrivileged(new PrivilegedAction<Class<? extends DelegatingScript>>() {
      @Override
      public Class<? extends DelegatingScript> run() {
        try {
          final GroovyClassLoader loader = new GroovyClassLoader(ForbiddenApisPlugin.class.getClassLoader(), configuration);
          final GroovyCodeSource csrc = new GroovyCodeSource(scriptUrl);
          @SuppressWarnings("unchecked") final Class<? extends DelegatingScript> clazz =
              loader.parseClass(csrc, false).asSubclass(DelegatingScript.class);
          return clazz;
        } catch (Exception e) {
          throw new RuntimeException("Cannot compile Groovy script: " + PLUGIN_INIT_SCRIPT);
        }
      }
    });
  }
  
  @Override
  public void apply(Project project) {
    if (GradleVersion.current().compareTo(MIN_GRADLE_VERSION) < 0) {
      throw new GradleException("Forbiddenapis plugin requires at least " + MIN_GRADLE_VERSION + ", running version is " + GradleVersion.current());
    }
    final DelegatingScript script;
    try {
      script = compiledScript.newInstance();
    } catch (Exception e) {
      throw new GradleException("Cannot instantiate Groovy script to apply forbiddenapis plugin.", e);
    }
    script.setDelegate(this);
    script.setProperty("project", project);
    script.run();
  }
  
}
