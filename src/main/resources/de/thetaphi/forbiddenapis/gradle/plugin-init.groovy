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

/** Initializes the plugin and binds it to project lifecycle. */

import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.PluginInstantiationException;

if (project.plugins.withType(JavaBasePlugin.class).isEmpty()) {
  throw new PluginInstantiationException('Forbidden-apis only works in projects using the java plugin.');
}

// create Extension for defaults:
def extension = project.extensions.create(FORBIDDEN_APIS_EXTENSION_NAME, CheckForbiddenApisExtension.class);
extension.with {
  signaturesFiles = project.files()
}

// Define our tasks (one for each SourceSet):
def forbiddenTasks = project.sourceSets.collect { sourceSet ->
  project.tasks.create(sourceSet.getTaskName(FORBIDDEN_APIS_TASK_NAME, null), CheckForbiddenApis.class) {
    description = "Runs forbidden-apis checks on '${sourceSet.name}' classes.";
    conventionMapping.with {
      CheckForbiddenApisExtension.PROPS.each { key ->
        map(key, { extension[key] });
      }
      classesDir = { sourceSet.output.classesDir }
      classpath = { sourceSet.compileClasspath }
      targetCompatibility = { project.targetCompatibility?.toString() }
    }
    // add dependency to compile task after evaluation, if the classesDir is from our SourceSet:
    project.afterEvaluate {
      if (classesDir == sourceSet.output.classesDir) {
        dependsOn(sourceSet.output);
      }
    }
  }
}

// Create a convenience task for all checks (this does not conflict with extension, as it has higher priority in DSL):
def forbiddenTask = project.tasks.create(FORBIDDEN_APIS_TASK_NAME) {
  description = "Runs forbidden-apis checks.";
  group = JavaBasePlugin.VERIFICATION_GROUP;
  dependsOn(forbiddenTasks);
}

// Add our task as dependency to chain
project.tasks[JavaBasePlugin.CHECK_TASK_NAME].dependsOn(forbiddenTask);
