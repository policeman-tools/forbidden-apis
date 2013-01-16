package de.thetaphi.forbiddenapis;

/*
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Echos an object string to the output screen.
 */
@Mojo(name = "echo", requiresProject = false, threadSafe = true)
public class MavenMojo extends AbstractMojo {

  /**
   * Any Object to print out.
   */
  @Parameter(property = "echo.message", defaultValue = "Hello World...", required = true)
  private Object message;

  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().info(message.toString());
  }
  
}