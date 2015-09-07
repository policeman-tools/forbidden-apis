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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginInstantiationException;

/**
 * Forbiddenapis Gradle Plugin
 * @since 1.9
 */
public class ForbiddenApisPlugin implements Plugin<Project> {
  
  /** Resource with Groovy script that initializes the plugin. */
  public static final String PLUGIN_INIT_SCRIPT = "plugin-init.groovy";
  
  /** Name of the base task that depends on one for every SourceSet */
  public static final String FORBIDDEN_APIS_TASK_NAME = "forbiddenApis";
  
  /** Name of the base task that depends on one for every SourceSet */
  public static final String FORBIDDEN_APIS_EXTENSION_NAME = "forbiddenApis";
  
  // Not before Java 6: @Override
  public void apply(final Project project) {
    try {
      final InputStream in = ForbiddenApisPlugin.class.getResourceAsStream(PLUGIN_INIT_SCRIPT);
      if (in == null) {
        throw new PluginInstantiationException("Cannot find resource with " + PLUGIN_INIT_SCRIPT + " script.");
      }
      try {
        final ImportCustomizer importCustomizer = new ImportCustomizer().addStarImports(ForbiddenApisPlugin.class.getPackage().getName());
        final CompilerConfiguration configuration = new CompilerConfiguration().addCompilationCustomizers(importCustomizer);
        configuration.setScriptBaseClass(DelegatingScript.class.getName());
        final GroovyShell shell = new GroovyShell(ForbiddenApisPlugin.class.getClassLoader(), new Binding(Collections.singletonMap("project", project)), configuration);
        final DelegatingScript script = (DelegatingScript) shell.parse(new InputStreamReader(in, "UTF-8"), PLUGIN_INIT_SCRIPT);
        script.setDelegate(this);
        script.run();
      } finally {
        in.close();
      }
    } catch (IOException ioe) {
      throw new PluginInstantiationException("Cannot execute " + PLUGIN_INIT_SCRIPT + " script.", ioe);
    }    
  }
  
}
