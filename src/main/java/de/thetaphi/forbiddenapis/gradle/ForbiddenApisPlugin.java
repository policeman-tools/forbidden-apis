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

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.util.DelegatingScript;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.GradleVersion;

/**
 * Forbiddenapis Gradle Plugin (requires at least Gradle v3.2)
 * @since 2.0
 */
public class ForbiddenApisPlugin implements Plugin<Project> {
  
  private static final Logger LOG = Logging.getLogger(ForbiddenApisPlugin.class);
  
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

  /** All properties that our ForbiddenApisExtension provides. Used by plugin init script to create convention mapping. */
  public static final List<String> FORBIDDEN_APIS_EXTENSION_PROPS = determineExtensionProps();
  
  /** Java Package that contains the Gradle Daemon (needed to detect it on startup). */
  private static final String GRADLE_DAEMON_PACKAGE = "org.gradle.launcher.daemon.";

  /** Resource with Groovy script that initializes the plugin. */
  private static final String PLUGIN_INIT_SCRIPT = "plugin-init.groovy";
  
  /** Compiled class instance of the plugin init script, an instance is executed per {@link #apply(Project)} */
  private static final Class<? extends DelegatingScript> COMPILED_SCRIPT = loadScript();
  
  private static Class<? extends DelegatingScript> loadScript() {
    final ImportCustomizer importCustomizer = new ImportCustomizer().addStarImports(ForbiddenApisPlugin.class.getPackage().getName());
    final CompilerConfiguration configuration = new CompilerConfiguration().addCompilationCustomizers(importCustomizer);
    configuration.setScriptBaseClass(DelegatingScript.class.getName());
    configuration.setSourceEncoding(StandardCharsets.UTF_8.name());
    final URL scriptUrl = ForbiddenApisPlugin.class.getResource(PLUGIN_INIT_SCRIPT);
    if (scriptUrl == null) {
      throw new RuntimeException("Cannot find resource with script: " + PLUGIN_INIT_SCRIPT);
    }
    return AccessController.doPrivileged(new PrivilegedAction<Class<? extends DelegatingScript>>() {
      @Override
      public Class<? extends DelegatingScript> run() {
        try {
          // We don't close the classloader, as we may need it later when loading other classes from inside script:
          @SuppressWarnings("resource") final GroovyClassLoader loader =
              new GroovyClassLoader(ForbiddenApisPlugin.class.getClassLoader(), configuration);
          final GroovyCodeSource csrc = new GroovyCodeSource(scriptUrl);
          @SuppressWarnings("unchecked") final Class<? extends DelegatingScript> clazz =
              loader.parseClass(csrc, false).asSubclass(DelegatingScript.class);
          return clazz;
        } catch (Exception e) {
          throw new RuntimeException("Cannot compile Groovy script: " + PLUGIN_INIT_SCRIPT, e);
        }
      }
    });
  }
  
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
  
  @Override
  public void apply(Project project) {
    if (GradleVersion.current().compareTo(MIN_GRADLE_VERSION) < 0) {
      throw new GradleException("Forbiddenapis plugin requires at least " + MIN_GRADLE_VERSION + ", running version is " + GradleVersion.current());
    }
    final DelegatingScript script;
    try {
      script = COMPILED_SCRIPT.newInstance();
    } catch (Exception e) {
      throw new GradleException("Cannot instantiate Groovy script to apply forbiddenapis plugin.", e);
    }
    script.setDelegate(this);
    script.setProperty("project", project);
    script.run();
  }
  
}
