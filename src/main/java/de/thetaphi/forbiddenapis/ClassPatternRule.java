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

import java.util.regex.Pattern;

public final class ClassPatternRule {

  private final Pattern pattern;
  private final String glob, message;
  
  /** Create new rule for class glob and given printout */
  public ClassPatternRule(String glob, String message) {
    if (glob == null) {
      throw new NullPointerException("glob");
    }
    this.glob = glob;
    this.pattern = AsmUtils.glob2Pattern(glob);
    this.message = message;
  }
  
  /** returns true, if the given class (binary name, dotted) matches this rule. */
  public boolean matches(String className) {
    return pattern.matcher(className).matches();
  }
  
  /** returns the printout using the message and the given class name */
  public String getPrintout(String className) {
    return message == null ? className : (className + " [" + message + "]");
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + glob.hashCode();
    result = prime * result + ((message == null) ? 0 : message.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ClassPatternRule other = (ClassPatternRule) obj;
    if (!glob.equals(other.glob)) return false;
    if (message == null) {
      if (other.message != null) return false;
    } else if (!message.equals(other.message)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ClassPatternRule [glob=" + glob + ", message=" + message + "]";
  }

}
