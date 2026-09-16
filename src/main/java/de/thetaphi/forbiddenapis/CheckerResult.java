/*
 * (C) Copyright Uwe Schindler (Generics Policeman) and others.
 * Parts of this work are licensed to the Apache Software Foundation (ASF)
 * under one or more contributor license agreements.
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

package de.thetaphi.forbiddenapis;

import java.util.List;

/**
 * Result of a single class check.
 */
public final class CheckerResult {

  private final String className;
  private final String sourceFile;
  private final List<ForbiddenViolation> violations;

  CheckerResult(String className, String sourceFile, List<ForbiddenViolation> violations) {
    this.className = className;
    this.sourceFile = sourceFile;
    this.violations = violations;
  }

  public String getClassName() {
    return className;
  }

  public String getSourceFile() {
    return sourceFile;
  }

  public List<ForbiddenViolation> getViolations() {
    return violations;
  }
}
