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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.TaskContainer;

/**
 * Forbiddenapis Gradle Plugin
 * @since 1.9
 */
public class GradlePlugin implements Plugin<Project> {
  
  static final String FORBIDDEN_APIS_TASK_NAME = "forbiddenApis";
  static final String TEST_FORBIDDEN_APIS_TASK_NAME = "testForbiddenApis";
  
  public void apply(final Project project) {
    final ConfigurationContainer configurations = project.getConfigurations();
    final TaskContainer tasks = project.getTasks();
    
    // TODO: How to import the JavaPlugin and its tasks from Maven!?
    final Task classesJavaTask = tasks.getByName("classes"),
        testClassesJavaTask = tasks.getByName("testClasses");
    
    // Create the tasks of the plugin:
    tasks.create(FORBIDDEN_APIS_TASK_NAME, GradleTask.class, new Action<GradleTask>() {
      public void execute(GradleTask task) {
        task.javaClassesDir = (File) project.property("sourceSets.main.output.classesDir");
        task.javaClasspath.add(configurations.getByName("compile"));
        task.dependsOn(classesJavaTask);
      }
    });
    tasks.create(TEST_FORBIDDEN_APIS_TASK_NAME, GradleTask.class, new Action<GradleTask>() {
      public void execute(GradleTask task) {
        task.javaClassesDir = (File) project.property("sourceSets.test.output.classesDir");
        task.javaClasspath.add(configurations.getByName("testCompile"));
        task.dependsOn(testClassesJavaTask);
      }
    });
  }
}