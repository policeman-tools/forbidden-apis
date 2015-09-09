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

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.DelegatingScript;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginInstantiationException;

/**
 * Forbiddenapis Gradle Plugin (requires at least Gradle 2.3)
 * @since 2.0
 */
public class ForbiddenApisPlugin implements Plugin<Project> {
  
  /** Resource with Groovy script that initializes the plugin. */
  public static final String PLUGIN_INIT_SCRIPT = "plugin-init.groovy";
  
  /** Name of the base task that depends on one for every SourceSet */
  public static final String FORBIDDEN_APIS_TASK_NAME = "forbiddenApis";
  
  /** Name of the base task that depends on one for every SourceSet */
  public static final String FORBIDDEN_APIS_EXTENSION_NAME = "forbiddenApis";
  
  @Override
  public void apply(final Project project) {
    final String scriptText;
    try {
      final URL scriptUrl = ForbiddenApisPlugin.class.getResource(PLUGIN_INIT_SCRIPT);
      if (scriptUrl == null) {
        throw new PluginInstantiationException("Cannot find resource with " + PLUGIN_INIT_SCRIPT + " script.");
      }
      scriptText = ResourceGroovyMethods.getText(scriptUrl, "UTF-8");
    } catch (IOException ioe) {
      throw new PluginInstantiationException("Cannot load " + PLUGIN_INIT_SCRIPT + " script.", ioe);
    }    
    final ImportCustomizer importCustomizer = new ImportCustomizer().addStarImports(ForbiddenApisPlugin.class.getPackage().getName());
    final CompilerConfiguration configuration = new CompilerConfiguration().addCompilationCustomizers(importCustomizer);
    configuration.setScriptBaseClass(DelegatingScript.class.getName());
    final GroovyShell shell = new GroovyShell(ForbiddenApisPlugin.class.getClassLoader(), new Binding(Collections.singletonMap("project", project)), configuration);
    final DelegatingScript script = (DelegatingScript) shell.parse(scriptText, PLUGIN_INIT_SCRIPT);
    script.setDelegate(this);
    script.run();
  }
  
}
