package de.thetaphi.forbiddenapis;

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

import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/** Some static utilities for analyzing with ASM, also constants. */
public final class AsmUtils {

  private AsmUtils() {}
  
  private static final String REGEX_META_CHARS = ".^$+{}[]|()\\";
  
  /** Package prefixes of documented Java API (extracted from Javadocs of Java 8). */
  private static final Pattern PORTABLE_RUNTIME_PACKAGE_PATTERN = makePkgPrefixPattern("java", "javax", "org.ietf.jgss", "org.omg", "org.w3c.dom", "org.xml.sax");
  
  /** Pattern that matches all module names, which are shipped by default in Java.
   * (see: {@code http://openjdk.java.net/projects/jigsaw/spec/sotms/}):
   * The remaining platform modules will share the 'java.' name prefix and are likely to include,
   * e.g., java.sql for database connectivity, java.xml for XML processing, and java.logging for
   * logging. Modules that are not defined in the Java SE 9 Platform Specification but instead
   * specific to the JDK will, by convention, share the 'jdk.' name prefix.
   */
  private static final Pattern RUNTIME_MODULES_PATTERN = makePkgPrefixPattern("java", "jdk");
  
  private static Pattern makePkgPrefixPattern(String... prefixes) {
    final StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (final String p : prefixes) {
      sb.append(first ? '(' : '|').append(Pattern.quote(p));
      first = false;
    }
    sb.append(")").append(Pattern.quote(".")).append(".*");
    return Pattern.compile(sb.toString());
  }
  
  private static boolean isRegexMeta(char c) {
    return REGEX_META_CHARS.indexOf(c) != -1;
  }

  /** Returns true, if the given binary class name (dotted) is part of the documented and portable Java APIs. */
  public static boolean isPortableRuntimeClass(String className) {
    return PORTABLE_RUNTIME_PACKAGE_PATTERN.matcher(className).matches();
  }
  
  /** Returns true, if the given Java 9 module name is part of the runtime (no custom 3rd party module).
   * @param module the module name or {@code null}, if in unnamed module
   */
  public static boolean isRuntimeModule(String module) {
    return module != null && RUNTIME_MODULES_PATTERN.matcher(module).matches();
  }
  
  /** Converts a binary class name (dotted) to the JVM internal one (slashed). Only accepts valid class names, no arrays. */
  public static String binaryToInternal(String clazz) {
    if (clazz.indexOf('/') >= 0 || clazz.indexOf('[') >= 0) {
      throw new IllegalArgumentException(String.format(Locale.ENGLISH, "'%s' is not a valid binary class name.", clazz));
    }
    return clazz.replace('.', '/');
  }
  
  /** Converts a binary class name to a &quot;{@code .class}&quot; file resource name. */
  public static String getClassResourceName(String clazz) {
    return binaryToInternal(clazz).concat(".class");
  }
  
  /** Returns true is a string is a glob pattern */
  public static boolean isGlob(String s) {
    return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
  }
  
  /** Returns a regex pattern that matches on any of the globs on class names (e.g., "sun.misc.**") */
  public static Pattern glob2Pattern(String... globs) {
    final StringBuilder regex = new StringBuilder();
    boolean needOr = false;
    for (String glob : globs) {
      if (needOr) {
        regex.append('|');
      }
      int i = 0, len = glob.length();
      while (i < len) {
        char c = glob.charAt(i++);
        switch (c) {
          case '*':
            if (i < len && glob.charAt(i) == '*') {
              // crosses package boundaries
              regex.append(".*");
              i++;
            } else {
              // do not cross package boundaries
              regex.append("[^.]*");
            }
            break;
            
          case '?':
            // do not cross package boundaries
            regex.append("[^.]");
            break;
          
          default:
            if (isRegexMeta(c)) {
              regex.append('\\');
            }
            regex.append(c);
        }
      }
      needOr = true;
    }
    return Pattern.compile(regex.toString(), 0);
  }
  
  /** Returns the module name from a {@code jrt:/} URL. */
  public static String getModuleName(URL jrtUrl) {
    String mod = jrtUrl.getPath();
    if (mod != null) {
      mod = mod.substring(1);
      int p = mod.indexOf('/');
      if (p >= 0) {
        mod = mod.substring(0, p);
      }
      return mod.isEmpty() ? null : mod;
    }
    return null;
  }

}
