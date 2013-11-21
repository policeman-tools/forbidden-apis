package de.thetaphi.forbiddenapis;

/*
 * (C) Copyright 2013 Uwe Schindler (Generics Policeman) and others.
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

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.List;

/**
 * Mojo to check if no project generated class files (test scope) contain calls to forbidden APIs
 * from the project classpath and a list of API signatures (either inline or as pointer to files or bundled signatures).
 */
@Mojo(name = "testCheck", threadSafe = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES)
public final class TestCheckMojo extends AbstractCheckMojo {

  /**
   * Injected test classpath.
   */
  @Parameter(defaultValue = "${project.testClasspathElements}", readonly = true, required = true)
  private List<String> classpathElements;

  /**
   * Directory with the class files to check.
   */
  @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true)
  private File classesDirectory;

  protected List<String> getClassPathElements() {
    return this.classpathElements;
  }
  
  protected File getClassesDirectory() {
    return this.classesDirectory;
  }
  
}