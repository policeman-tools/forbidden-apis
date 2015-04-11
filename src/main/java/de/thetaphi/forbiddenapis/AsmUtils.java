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

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/** Some static utilities for analyzing with ASM, also constants. */
public final class AsmUtils {

  private AsmUtils() {}
  
  private static final String REGEX_META_CHARS = ".^$+{}[]|()\\";
  private static final Pattern INTERNAL_PACKAGE_PATTERN;
  static {
    final StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (final String pkg : Arrays.asList("sun.", "oracle.", "com.sun.", "com.oracle.", "jdk.", "sunw.")) {
      sb.append(first ? '(' : '|').append(Pattern.quote(pkg));
      first = false;
    }
    INTERNAL_PACKAGE_PATTERN = Pattern.compile(sb.append(").*").toString());
  }
  
  private static boolean isRegexMeta(char c) {
    return REGEX_META_CHARS.indexOf(c) != -1;
  }

  /** Returns true, if the given binary class name (dotted) is likely a internal class (like sun.misc.Unsafe) */
  public static boolean isInternalClass(String className) {
    return INTERNAL_PACKAGE_PATTERN.matcher(className).matches();
  }
  
  /** Converts a binary class name (dotted) to the JVM internal one (slashed). Only accepts valid class names, no arrays. */
  public static String binaryToInternal(String clazz) {
    if (clazz.indexOf('/') >= 0 || clazz.indexOf('[') >= 0) {
      throw new IllegalArgumentException(String.format(Locale.ENGLISH, "'%s' is not a valid binary class name.", clazz));
    }
    return clazz.replace('.', '/');
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

}
