package de.thetaphi.forbiddenapis;

/*
 * (C) Copyright 2013 Uwe Schindler (Generics Policeman) and others.
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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.net.JarURLConnection;
import java.net.URLConnection;
import java.net.URLClassLoader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.MalformedURLException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import org.codehaus.plexus.util.DirectoryScanner;

/**
 * CLI class with a static main() method
 */
public final class CliMain {

  private final Option classpathOpt, dirOpt, includesOpt, excludesOpt, signaturesfileOpt, bundledsignaturesOpt,
    internalruntimeforbiddenOpt, nofailonmissingclassesOpt, versionOpt, helpOpt;
  private final CommandLine cmd;
  
  public static final int EXIT_SUCCESS = 0;
  public static final int EXIT_VIOLATION = 1;
  public static final int EXIT_ERR_CMDLINE = 2;
  public static final int EXIT_UNSUPPORTED_JDK = 3;
  public static final int EXIT_ERR_OTHER = 4;

  @SuppressWarnings("static-access")
  public CliMain(String... args) throws ExitException {
    final OptionGroup required = new OptionGroup();
    required.setRequired(true);
    required.addOption(dirOpt = OptionBuilder
        .withDescription("directory with class files to check for forbidden api usage; this directory is also added to classpath")
        .withLongOpt("dir")
        .hasArg()
        .withArgName("directory")
        .create('d'));
    required.addOption(versionOpt = OptionBuilder
        .withDescription("print product version and exit")
        .withLongOpt("version")
        .create('V'));
    required.addOption(helpOpt = OptionBuilder
        .withDescription("print this help")
        .withLongOpt("help")
        .create('h'));
        
    final Options options = new Options();
    options.addOptionGroup(required);
    options.addOption(classpathOpt = OptionBuilder
        .withDescription("class search path of directories and zip/jar files")
        .withLongOpt("classpath")
        .hasArgs()
        .withValueSeparator(File.pathSeparatorChar)
        .withArgName("path")
        .create('c'));
    options.addOption(includesOpt = OptionBuilder
        .withDescription("ANT-style pattern to select class files (separated by commas or option can be given multiple times, defaults to '**/*.class')")
        .withLongOpt("includes")
        .hasArgs()
        .withValueSeparator(',')
        .withArgName("pattern")
        .create('i'));
    options.addOption(excludesOpt = OptionBuilder
        .withDescription("ANT-style pattern to exclude some files from checks (separated by commas or option can be given multiple times)")
        .withLongOpt("excludes")
        .hasArgs()
        .withValueSeparator(',')
        .withArgName("pattern")
        .create('e'));
    options.addOption(signaturesfileOpt = OptionBuilder
        .withDescription("path to a file containing signatures (option can be given multiple times)")
        .withLongOpt("signaturesfile")
        .hasArg()
        .withArgName("file")
        .create('f'));
    options.addOption(bundledsignaturesOpt = OptionBuilder
        .withDescription("name of a bundled signatures definition (separated by commas or option can be given multiple times)")
        .withLongOpt("bundledsignatures")
        .hasArgs()
        .withValueSeparator(',')
        .withArgName("name")
        .create('b'));
    options.addOption(internalruntimeforbiddenOpt = OptionBuilder
        .withDescription("forbids calls to classes from the internal java runtime (like sun.misc.Unsafe)")
        .withLongOpt("internalruntimeforbidden")
        .create());
    options.addOption(nofailonmissingclassesOpt = OptionBuilder
        .withDescription("don't fail if a referenced class is missing on classpath")
        .withLongOpt("nofailonmissingclasses")
        .create());

    try {
      this.cmd = new PosixParser().parse(options, args);
      if (cmd.hasOption(helpOpt.getLongOpt())) {
        throw new org.apache.commons.cli.ParseException("");
      }
      if (cmd.hasOption(versionOpt.getLongOpt())) {
        printVersion();
        throw new ExitException(EXIT_SUCCESS);
      }
    } catch (org.apache.commons.cli.ParseException pe) {
      final HelpFormatter formatter = new HelpFormatter();
      String cmdline = "java " + getClass().getName();
      try {
        final URLConnection conn = getClass().getResource(getClass().getSimpleName() + ".class").openConnection();
        if (conn instanceof JarURLConnection) {
          final URL jarUrl = ((JarURLConnection) conn).getJarFileURL();
          if ("file".equalsIgnoreCase(jarUrl.getProtocol())) {
            final String cwd = new File(".").getCanonicalPath(), path = new File(jarUrl.toURI()).getCanonicalPath();
            cmdline = "java -jar " + (path.startsWith(cwd) ? path.substring(cwd.length() + File.separator.length()) : path);
          }
        }
      } catch (IOException ioe) {
        // ignore, use default cmdline value
      } catch (URISyntaxException use) {
        // ignore, use default cmdline value
      }
      formatter.printHelp(cmdline + " [options]",
        "Scans a set of class files for forbidden API usage.",
        options,
        String.format(Locale.ENGLISH,
          "Exit codes: %d = SUCCESS, %d = forbidden API detected, %d = invalid command line, %d = unsupported JDK version, %d = other error (I/O,...)",
          EXIT_SUCCESS, EXIT_VIOLATION, EXIT_ERR_CMDLINE, EXIT_UNSUPPORTED_JDK, EXIT_ERR_OTHER
        )
      );
      throw new ExitException(EXIT_ERR_CMDLINE);
    }
  }
  
  void logError(String msg) {
    System.err.println("ERROR: " + msg);
  }
  
  void logWarn(String msg) {
    System.err.println("WARNING: " + msg);
  }
  
  void logInfo(String msg) {
    System.out.println(msg);
  }
  
  private void printVersion() {
    final Package pkg = this.getClass().getPackage();
    logInfo(String.format(Locale.ENGLISH,
      "%s %s",
      pkg.getImplementationTitle(), pkg.getImplementationVersion()
    ));
  }
  
  public void run() throws ExitException {
    final File classesDirectory = new File(cmd.getOptionValue(dirOpt.getLongOpt())).getAbsoluteFile();
    
    // parse classpath given as argument; add -d to classpath, too
    final String[] classpath = cmd.getOptionValues(classpathOpt.getLongOpt());
    final URL[] urls;
    try {
      if (classpath == null) {
        urls = new URL[] { classesDirectory.toURI().toURL() };
      } else {
        urls = new URL[classpath.length + 1];
        int i = 0;
        for (final String cpElement : classpath) {
          urls[i++] = new File(cpElement).toURI().toURL();
        }
        urls[i++] = classesDirectory.toURI().toURL();
        assert i == urls.length;
      }
    } catch (MalformedURLException mfue) {
      throw new ExitException(EXIT_ERR_OTHER, "The given classpath is invalid: " + mfue);
    }
    // System.err.println("Classpath: " + Arrays.toString(urls));

    final URLClassLoader loader = URLClassLoader.newInstance(urls, ClassLoader.getSystemClassLoader());
    try {
      final Checker checker = new Checker(loader, cmd.hasOption(internalruntimeforbiddenOpt.getLongOpt()), !cmd.hasOption(nofailonmissingclassesOpt.getLongOpt())) {
        @Override
        protected void logError(String msg) {
          CliMain.this.logError(msg);
        }
        
        @Override
        protected void logWarn(String msg) {
          CliMain.this.logWarn(msg);
        }
        
        @Override
        protected void logInfo(String msg) {
          CliMain.this.logInfo(msg);
        }
      };
      
      if (!checker.isSupportedJDK) {
        throw new ExitException(EXIT_UNSUPPORTED_JDK, String.format(Locale.ENGLISH, 
          "Your Java runtime (%s %s) is not supported by forbiddenapis. Please run the checks with a supported JDK!",
          System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version")));
      }
      
      try {
        final String[] bundledSignatures = cmd.getOptionValues(bundledsignaturesOpt.getLongOpt());
        if (bundledSignatures != null) for (String bs : bundledSignatures) {
          logInfo("Reading bundled API signatures: " + bs);
          checker.parseBundledSignatures(bs, null);
        }
        final String[] signaturesFiles = cmd.getOptionValues(signaturesfileOpt.getLongOpt());
        if (signaturesFiles != null) for (String sf : signaturesFiles) {
          final File f = new File(sf).getAbsoluteFile();
          logInfo("Reading API signatures: " + f);
          checker.parseSignaturesFile(new FileInputStream(f));
        }
      } catch (IOException ioe) {
        throw new ExitException(EXIT_ERR_OTHER, "IO problem while reading files with API signatures: " + ioe);
      } catch (ParseException pe) {
        throw new ExitException(EXIT_ERR_OTHER, "Parsing signatures failed: " + pe.getMessage());
      }

      if (checker.hasNoSignatures()) {
        throw new ExitException(EXIT_ERR_CMDLINE, String.format(Locale.ENGLISH,
          "No API signatures found; use parameters '--%s', '--%s', and/or '--%s' to define those!",
          bundledsignaturesOpt.getLongOpt(), signaturesfileOpt.getLongOpt(), internalruntimeforbiddenOpt.getLongOpt()
        ));
      }

      logInfo("Loading classes to check...");
      if (!classesDirectory.exists()) {
        throw new ExitException(EXIT_ERR_OTHER, "Directory with class files does not exist: " + classesDirectory);
      }
      
      String[] includes = cmd.getOptionValues(includesOpt.getLongOpt());
      if (includes == null || includes.length == 0) {
        includes = new String[] { "**/*.class" };
      }
      final String[] excludes = cmd.getOptionValues(excludesOpt.getLongOpt());
      final DirectoryScanner ds = new DirectoryScanner();
      ds.setBasedir(classesDirectory);
      ds.setCaseSensitive(true);
      ds.setIncludes(includes);
      ds.setExcludes(excludes);
      ds.addDefaultExcludes();
      ds.scan();
      final String[] files = ds.getIncludedFiles();
      
      if (files.length == 0) {
        throw new ExitException(EXIT_ERR_OTHER, String.format(Locale.ENGLISH,
          "No classes found in directory %s (includes=%s, excludes=%s).",
          classesDirectory, Arrays.toString(includes), Arrays.toString(excludes)));
      }
      
      try {
        for (String f : files) {
          checker.addClassToCheck(new FileInputStream(new File(classesDirectory, f)));
        }
      } catch (IOException ioe) {
        throw new ExitException(EXIT_ERR_OTHER, "Failed to load one of the given class files: " + ioe);
      }

      try {
        logInfo("Scanning for API signatures and dependencies...");
        checker.run();
      } catch (ForbiddenApiException fae) {
        throw new ExitException(EXIT_VIOLATION, fae.getMessage());
      }
    } finally {
      // Java 7 supports closing URLClassLoader, so check for Closeable interface:
      if (loader instanceof Closeable) try {
        ((Closeable) loader).close();
      } catch (IOException ioe) {
        // ignore
      }
    }
  }
  
  @SuppressWarnings("serial")
  public static final class ExitException extends Exception {
    public final int exitCode;
    
    public ExitException(int exitCode) {
      this(exitCode, null);
    }
    
    public ExitException(int exitCode, String message) {
      super(message);
      this.exitCode = exitCode;
    }
  }

  public static void main(String... args) {
    try {
      new CliMain(args).run();
    } catch (ExitException e) {
      if (e.getMessage() != null) System.err.println("ERROR: " + e.getMessage());
      if (e.exitCode != 0) System.exit(e.exitCode);
    }
  }
  
}