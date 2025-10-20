package de.thetaphi.forbiddenapis;

import java.util.List;

/**
 * Result of a single class scan.
 */
public final class ScanResult {

  private final String className;
  private final String sourceFile;
  private final List<ForbiddenViolation> violations;

  ScanResult(String className, String sourceFile, List<ForbiddenViolation> violations) {
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
