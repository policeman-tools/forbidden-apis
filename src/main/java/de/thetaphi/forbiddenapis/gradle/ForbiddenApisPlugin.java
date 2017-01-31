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

import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.util.DelegatingScript;

import java.net.URL;
import java.util.Collections;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginInstantiationException;

/**
 * Forbiddenapis Gradle Plugin (requires at least Gradle 2.3)
 * @since 2.0
 */
public class ForbiddenApisPlugin implements Plugin<Project> {
  
  /** Resource with Groovy script that initializes the plugin. */
  private static final String PLUGIN_INIT_SCRIPT = "plugin-init.groovy";
  
  /** Name of the base task that depends on one for every SourceSet */
  public static final String FORBIDDEN_APIS_TASK_NAME = "forbiddenApis";
  
  /** Name of the extension to define defaults for all tasks of this module. */
  public static final String FORBIDDEN_APIS_EXTENSION_NAME = "forbiddenApis";
  
  private final Script script;
  
  public ForbiddenApisPlugin() {
    final ImportCustomizer importCustomizer = new ImportCustomizer().addStarImports(ForbiddenApisPlugin.class.getPackage().getName());
    final CompilerConfiguration configuration = new CompilerConfiguration().addCompilationCustomizers(importCustomizer);
    configuration.setScriptBaseClass(DelegatingScript.class.getName());
    configuration.setSourceEncoding("UTF-8");
    final GroovyShell shell = new GroovyShell(ForbiddenApisPlugin.class.getClassLoader(), new Binding(), configuration);
    final URL scriptUrl = ForbiddenApisPlugin.class.getResource(PLUGIN_INIT_SCRIPT);
    if (scriptUrl == null) {
      throw new PluginInstantiationException("Cannot find resource with script: " + PLUGIN_INIT_SCRIPT);
    }
    final GroovyCodeSource csrc = new GroovyCodeSource(scriptUrl);
    final DelegatingScript script = (DelegatingScript) shell.parse(csrc);
    script.setDelegate(this);
    this.script = script;
  }
  
  // synchronized because we change the property "project" in the binding
  @Override
  public synchronized void apply(Project project) {
    script.setProperty("project", project);
    script.run();
    script.setProperty("project", null);
  }
  
}
