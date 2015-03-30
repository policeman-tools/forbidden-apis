package de.thetaphi.forbiddenapis;

import java.util.Arrays;
import java.util.regex.Pattern;

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

/** Some static utilities for analyzing with ASM, also constants. */
public final class AsmUtils {

  private AsmUtils() {}
  
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
  
  /** Returns true, if the given binary class name (dotted) is likely a internal class (like sun.misc.Unsafe) */
  public static boolean isInternalClass(String className) {
    return INTERNAL_PACKAGE_PATTERN.matcher(className).matches();
  }
    
}
