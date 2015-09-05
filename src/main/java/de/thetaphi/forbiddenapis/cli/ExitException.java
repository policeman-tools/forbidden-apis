package de.thetaphi.forbiddenapis.cli;

/**
 * Used by the CLI to signal process exit with a specific exit code
 */
@SuppressWarnings("serial")
public final class ExitException extends Exception {
  public final int exitCode;
  
  public ExitException(int exitCode) {
    this(exitCode, null);
  }
  
  public ExitException(int exitCode, String message) {
    super(message);
    this.exitCode = exitCode;
  }
}