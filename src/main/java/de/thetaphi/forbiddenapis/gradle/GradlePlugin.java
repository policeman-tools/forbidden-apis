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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.tasks.TaskContainer;

/**
 * Forbiddenapis Gradle Plugin
 * @since 1.9
 */
public class GradlePlugin implements Plugin<Project> {
  
  public static final String FORBIDDEN_APIS_TASK_NAME = "forbiddenApis";
  public static final String TEST_FORBIDDEN_APIS_TASK_NAME = "testForbiddenApis";
  
  public void apply(final Project project) {
    if (project.getPlugins().findPlugin("java") == null) {
      throw new PluginInstantiationException("Forbiddenapis only works in projects using the 'java' plugin.");
    }
    
    final ConfigurationContainer configurations = project.getConfigurations();
    final TaskContainer tasks = project.getTasks();
    
    // Get classes directories for main and test
    final File mainClassesDir = getClassesDirByName(project, "main"),
        testClassesDir = getClassesDirByName(project, "test");
    
    // Create the tasks of the plugin:
    final Task forbiddenTask = tasks.create(FORBIDDEN_APIS_TASK_NAME, GradleTask.class, new Action<GradleTask>() {
      public void execute(GradleTask task) {
        task.setClassesDir(mainClassesDir);
        task.setClasspath(configurations.getByName("compile"));
        task.dependsOn(tasks.getByName("compileJava"));
      }
    });
    final Task testForbiddenTask = tasks.create(TEST_FORBIDDEN_APIS_TASK_NAME, GradleTask.class, new Action<GradleTask>() {
      public void execute(GradleTask task) {
        task.setClassesDir(testClassesDir);
        task.setClasspath(configurations.getByName("testCompile").plus(project.files(mainClassesDir)));
        task.dependsOn(tasks.getByName("compileTestJava"));
      }
    });
    
    // Add our tasks as dependencies to chain
    tasks.getByName("classes").dependsOn(forbiddenTask);
    tasks.getByName("testClasses").dependsOn(testForbiddenTask);
  }
  
  private File getClassesDirByName(Project project, String sourceSetName) {
    final Object sourceSet = ((NamedDomainObjectCollection<?>) project.property("sourceSets")).getByName(sourceSetName);
    try {
      final Object output = sourceSet.getClass().getMethod("getOutput").invoke(sourceSet);
      return (File) output.getClass().getMethod("getClassesDir").invoke(output);
    } catch (Exception e) {
      throw new PluginInstantiationException("Forbiddenapis was not able to initialize classesDir.", e);
    }
  }
  
}
