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

project.plugins.apply(JavaBasePlugin.class);

// create Extension for defaults:
def extension = project.extensions.create(FORBIDDEN_APIS_EXTENSION_NAME, CheckForbiddenApisExtension.class, project);

// Create a convenience task for all checks (this does not conflict with extension, as it has higher priority in DSL):
def forbiddenTask = TASK_AVOIDANCE_AVAILABLE ? project.tasks.register(FORBIDDEN_APIS_TASK_NAME) : project.tasks.create(FORBIDDEN_APIS_TASK_NAME)
forbiddenTask.configure {
  description = "Runs forbidden-apis checks.";
  group = JavaBasePlugin.VERIFICATION_GROUP;
}

// Gradle is buggy with it's JavaVersion enum: We use majorVersion property before Java 11 (6,7,8,9,10) and for later we use toString() to be future-proof:
Closure targetCompatibilityGetter = { (project.targetCompatibility?.hasProperty('java11Compatible') && project.targetCompatibility?.java11Compatible) ?
    project.targetCompatibility.toString() : project.targetCompatibility?.majorVersion };

// Define our tasks (one for each SourceSet):
project.sourceSets.all{ sourceSet ->
  String sourceSetTaskName = sourceSet.getTaskName(FORBIDDEN_APIS_TASK_NAME, null);
  def sourceSetTask = TASK_AVOIDANCE_AVAILABLE ? project.tasks.register(sourceSetTaskName, CheckForbiddenApis.class) :
          project.tasks.create(sourceSetTaskName, CheckForbiddenApis.class);
  sourceSetTask.configure {
    description = "Runs forbidden-apis checks on '${sourceSet.name}' classes.";
    dependsOn(sourceSet.output);
    outputs.upToDateWhen { true }
    conventionMapping.with{
      FORBIDDEN_APIS_EXTENSION_PROPS.each{ key ->
        map(key, { extension[key] });
      }
      classesDirs = { sourceSet.output.hasProperty('classesDirs') ? sourceSet.output.classesDirs : project.files(sourceSet.output.classesDir) }
      classpath = { sourceSet.compileClasspath }
      targetCompatibility = targetCompatibilityGetter
    }
  }
  forbiddenTask.configure {
    dependsOn(sourceSetTask)
  }
}

// Add our task as dependency to chain
def checkTask = TASK_AVOIDANCE_AVAILABLE ? project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME) : project.tasks.getByName(JavaBasePlugin.CHECK_TASK_NAME);
checkTask.configure { it.dependsOn(forbiddenTask) };
