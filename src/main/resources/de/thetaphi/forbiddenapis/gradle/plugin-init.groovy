// initializes the plugin and binds it to the lifecycle

import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.tasks.TaskContainer;

if (!project.plugins.findPlugin("java")) {
  throw new PluginInstantiationException("Forbiddenapis only works in projects using the 'java' plugin.");
}

ConfigurationContainer configurations = project.getConfigurations();
TaskContainer tasks = project.getTasks();

// Get classes directories for main and test
File mainClassesDir = project.sourceSets.main.output.classesDir;
File testClassesDir = project.sourceSets.test.output.classesDir;

// Create the tasks of the plugin:
def forbiddenTask = tasks.create(plugin.FORBIDDEN_APIS_TASK_NAME, CheckForbiddenApis.class) {
  it.setClassesDir(mainClassesDir);
  it.setClasspath(configurations.getByName("compile"));
  it.dependsOn(tasks.getByName("classes"));
}
def testForbiddenTask = tasks.create(plugin.TEST_FORBIDDEN_APIS_TASK_NAME, CheckForbiddenApis.class) {
  it.setClassesDir(testClassesDir);
  it.setClasspath(configurations.getByName("testCompile").plus(project.files(mainClassesDir)));
  it.dependsOn(tasks.getByName("testClasses"));
}

// Add our tasks as dependencies to chain
tasks.getByName("check").dependsOn(forbiddenTask, testForbiddenTask);
