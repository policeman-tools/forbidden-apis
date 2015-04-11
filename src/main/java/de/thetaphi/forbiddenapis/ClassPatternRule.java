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
  
  /** the message printed if rule is violated */
  public final String printout;
  
  /** Create new rule for class glob and given printout */
  public ClassPatternRule(String glob, String printout) {
    if (glob == null || printout == null) {
      throw new NullPointerException();
    }
    this.pattern = AsmUtils.glob2Pattern(glob);
    assert this.pattern.flags() == 0 : "the pattern should have no flags (must be case-sensitive,...)";
    this.printout = printout;
  }
  
  /** returns true, if the given class (binary name, dotted) matches this rule. */
  public boolean matches(String className) {
    return pattern.matcher(className).matches();
  }

  // equals() / hashCode() use the string representation of pattern for comparisons
  // (Pattern unfortunately does not implement equals/hashCode)
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + pattern.pattern().hashCode();
    result = prime * result + printout.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ClassPatternRule other = (ClassPatternRule) obj;
    if (!pattern.pattern().equals(other.pattern.pattern())) return false;
    if (!printout.equals(other.printout)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "ClassPatternRule [pattern=" + pattern + ", printout=" + printout + "]";
  }

}
