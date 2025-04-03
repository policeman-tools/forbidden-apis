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

package de.thetaphi.forbiddenapis.ant;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ProjectComponent;

import de.thetaphi.forbiddenapis.Checker.ViolationSeverity;

public final class SeverityOverrideType extends ProjectComponent {

  private final StringBuilder signatures = new StringBuilder();
  
  ViolationSeverity severity = null;
  
  List<String> getSignatures() {
    return Arrays.asList(signatures.toString().trim().split("\\s*[\r\n]+\\s*"));
  }
  
  public void addText(String signature) {
    this.signatures.append('\n').append(signature);
  }

  public void setSeverity(String severity) {
    try {
      this.severity = ViolationSeverity.valueOf(severity.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException iae) {
      throw new BuildException("Severity is not a valid enum value, use one of " + Arrays.toString(ViolationSeverity.values()));
    }
  }

}
