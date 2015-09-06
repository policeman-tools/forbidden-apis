// initializes the plugin and binds it to the lifecycle

import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.tasks.TaskContainer;

if (!project.plugins.findPlugin("java")) {
  throw new PluginInstantiationException("Forbiddenapis only works in projects using the 'java' plugin.");
}

def tasks = project.getTasks();
def checkTask = tasks.getByName("check");

// Define our tasks (one for each SourceSet):
def forbiddenTasks = project.sourceSets.collect { sourceSet ->
  tasks.create(sourceSet.getTaskName(FORBIDDEN_APIS_TASK_NAME_PREFIX, null), CheckForbiddenApis.class) { task ->
    task.classesDir = sourceSet.output.classesDir;
    task.classpath = sourceSet.compileClasspath;
    task.description = "Runs forbiddenApis checks on '" + sourceSet.name + "' classes.";
    // task.group = checkTask.group;
    task.dependsOn(sourceSet.output);
  }
}

// Add our tasks as dependencies to chain
checkTask.dependsOn(forbiddenTasks);
