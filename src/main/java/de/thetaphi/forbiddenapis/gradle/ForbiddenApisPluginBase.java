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

package de.thetaphi.forbiddenapis.gradle;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.GradleVersion;

abstract class ForbiddenApisPluginBase implements Plugin<Project> {
  
  private static final Logger LOG = Logging.getLogger(ForbiddenApisPluginBase.class);
  
  /** Name of the base task that depends on one for every SourceSet. */
  public static final String FORBIDDEN_APIS_TASK_NAME = "forbiddenApis";
  
  /** Name of the extension to define defaults for all tasks of this module. */
  public static final String FORBIDDEN_APIS_EXTENSION_NAME = "forbiddenApis";
  
  /**
   * Default value for {@link CheckForbiddenApis#getDisableClassloadingCache}.
   * <p>
   * The default is {@code false}, unless the plugin detects that your build is
   * running in the <em>Gradle Daemon</em> (which has this problem), setting the
   * default to {@code true} as a consequence.
   * @see CheckForbiddenApis#getDisableClassloadingCache
   */
  public static final boolean DEFAULT_DISABLE_CLASSLOADING_CACHE = detectAndLogGradleDaemon();
  
  /** Minimum Gradle version this plugin requires to run (v3.2). */
  public static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("3.2");
  
  /** True, if this version of Gradle supports task avoidance API (&gt;=v4.9). */
  public static final boolean TASK_AVOIDANCE_AVAILABLE = GradleVersion.current().compareTo(GradleVersion.version("4.9")) >= 0;

  /** All properties that our ForbiddenApisExtension provides. Used to create convention mapping. */
  protected static final List<String> FORBIDDEN_APIS_EXTENSION_PROPS = determineExtensionProps();
  
  /** Java Package that contains the Gradle Daemon (needed to detect it on startup). */
  private static final String GRADLE_DAEMON_PACKAGE = "org.gradle.launcher.daemon.";

  private static List<String> determineExtensionProps() {
    final List<String> props = new ArrayList<>();
    for (final Field f : CheckForbiddenApisExtension.class.getDeclaredFields()) {
      final int mods = f.getModifiers();
      if (Modifier.isPublic(mods) && !f.isSynthetic() && !Modifier.isStatic(mods)) {
        props.add(f.getName());
      }
    }
    return Collections.unmodifiableList(props);
  }
  
  private static boolean isGradleDaemon() {
    // see: http://stackoverflow.com/questions/23265217/how-to-know-whether-you-are-running-inside-a-gradle-daemon
    if (System.getProperty("sun.java.command", "").startsWith(GRADLE_DAEMON_PACKAGE)) {
      return true;
    }
    for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
      if (e.getClassName().startsWith(GRADLE_DAEMON_PACKAGE)) {
        return true;
      }
    }
    return false;
  }
  
  private static boolean detectAndLogGradleDaemon() {
    final boolean daemon = isGradleDaemon();
    if (daemon) {
      LOG.info("You are running forbidden-apis in the Gradle Daemon; disabling classloading cache by default to work around resource leak.");
    }
    return daemon;
  }
  
}
