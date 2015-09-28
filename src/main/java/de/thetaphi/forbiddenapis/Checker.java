package de.thetaphi.forbiddenapis;

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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.File;
import java.io.StringReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * Forbidden APIs checker class.
 */
public final class Checker implements RelatedClassLookup {
  
  public static enum Option {
    INTERNAL_RUNTIME_FORBIDDEN,
    FAIL_ON_MISSING_CLASSES,
    FAIL_ON_VIOLATION,
    FAIL_ON_UNRESOLVABLE_SIGNATURES
  }

  public final boolean isSupportedJDK;
  
  private final long start;
  
  final Logger logger;
  
  final Set<File> bootClassPathJars;
  final Set<String> bootClassPathDirs;
  final ClassLoader loader;
  final java.lang.reflect.Method method_Class_getModule, method_Module_getResourceAsStream;
  final EnumSet<Option> options;
  
  // key is the internal name (slashed):
  final Map<String,ClassSignature> classesToCheck = new HashMap<String,ClassSignature>();
  // key is the binary name (dotted):
  final Map<String,ClassSignature> classpathClassCache = new HashMap<String,ClassSignature>();
  
  // key is the internal name (slashed), followed by \000 and the field name:
  final Map<String,String> forbiddenFields = new HashMap<String,String>();
  // key is the internal name (slashed), followed by \000 and the method signature:
  final Map<String,String> forbiddenMethods = new HashMap<String,String>();
  // key is the internal name (slashed):
  final Map<String,String> forbiddenClasses = new HashMap<String,String>();
  // key is pattern to binary class name:
  final Set<ClassPatternRule> forbiddenClassPatterns = new LinkedHashSet<ClassPatternRule>();
  // descriptors (not internal names) of all annotations that suppress:
  final Set<String> suppressAnnotations = new LinkedHashSet<String>();
    
  public Checker(Logger logger, ClassLoader loader, Option... options) {
    this(logger, loader, (options.length == 0) ? EnumSet.noneOf(Option.class) : EnumSet.copyOf(Arrays.asList(options)));
  }
  
  public Checker(Logger logger, ClassLoader loader, EnumSet<Option> options) {
    this.logger = logger;
    this.loader = loader;
    this.options = options;
    this.start = System.currentTimeMillis();
    
    // default (always available)
    addSuppressAnnotation(SuppressForbidden.class);
    
    boolean isSupportedJDK = false;
    
    // first try Java 9 mdoule system (Jigsaw)
    // Please note: This code is not guaranteed to work with final Java 9 version. This is just for testing!
    java.lang.reflect.Method method_Class_getModule, method_Module_getResourceAsStream;
    try {
      method_Class_getModule = Class.class.getMethod("getModule");
      method_Module_getResourceAsStream = method_Class_getModule
          .getReturnType().getMethod("getResourceAsStream", String.class);
      isSupportedJDK = true;
    } catch (NoSuchMethodException e) {
      method_Class_getModule = method_Module_getResourceAsStream = null;
    }
    this.method_Class_getModule = method_Class_getModule;
    this.method_Module_getResourceAsStream = method_Module_getResourceAsStream;
    
    final Set<File> bootClassPathJars = new LinkedHashSet<File>();
    final Set<String> bootClassPathDirs = new LinkedHashSet<String>();
    
    // fall back to legacy behavior:
    if (!isSupportedJDK) {
      try {
        final URL objectClassURL = loader.getResource(AsmUtils.getClassResourceName(Object.class.getName()));
        if (objectClassURL != null && "jrt".equalsIgnoreCase(objectClassURL.getProtocol())) {
          // this is Java 9 with modules!
          isSupportedJDK = true;
        } else {
          final RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
          if (rb.isBootClassPathSupported()) {
            final String cp = rb.getBootClassPath();
            final StringTokenizer st = new StringTokenizer(cp, File.pathSeparator);
            while (st.hasMoreTokens()) {
              final File f = new File(st.nextToken());
              if (f.isFile()) {
                bootClassPathJars.add(f.getCanonicalFile());
              } else if (f.isDirectory()) {
                String fp = f.getCanonicalPath();
                if (!fp.endsWith(File.separator)) {
                  fp += File.separator;
                }
                bootClassPathDirs.add(fp);
              }
            }
          }
          isSupportedJDK = !(bootClassPathJars.isEmpty() && bootClassPathDirs.isEmpty());
          // logInfo("JARs in boot-classpath: " + bootClassPathJars + "; dirs in boot-classpath: " + bootClassPathDirs);
        }
      } catch (IOException ioe) {
        isSupportedJDK = false;
        bootClassPathJars.clear();
        bootClassPathDirs.clear();
      }
    }
    this.bootClassPathJars = Collections.unmodifiableSet(bootClassPathJars);
    this.bootClassPathDirs = Collections.unmodifiableSet(bootClassPathDirs);
    
    if (isSupportedJDK) {
      try {
        isSupportedJDK = getClassFromClassLoader(Object.class.getName()).isRuntimeClass;
      } catch (IllegalArgumentException iae) {
        logger.warn("Bundled version of ASM cannot parse bytecode of java.lang.Object class; marking runtime as not suppported.");
        isSupportedJDK = false;
      } catch (ClassNotFoundException cnfe) {
        logger.warn("Bytecode of java.lang.Object not found; marking runtime as not suppported.");
        isSupportedJDK = false;
      } catch (IOException ioe) {
        logger.warn("IOException while loading java.lang.Object class from classloader; marking runtime as not suppported: " + ioe);
        isSupportedJDK = false;
      }
    }
    
    // finally set the latest value to final field:
    this.isSupportedJDK = isSupportedJDK;
  }
  
  /** Loads the bytecode from Java9's module system.
   * <p>
   * This code is not guaranteed to work with final Java 9 version.
   * This is just for testing!
   **/
  private InputStream getBytecodeFromJigsaw(String classname) {
    if (method_Class_getModule == null || method_Module_getResourceAsStream == null) {
      return null; // not Java 9 JIGSAW
    }
    try {
      final Class<?> clazz = Class.forName(classname, false, loader);
      final Object module = method_Class_getModule.invoke(clazz);
      return (InputStream) method_Module_getResourceAsStream.invoke(module, AsmUtils.getClassResourceName(classname));
    } catch (Exception e) {
      return null; // not found
    }
  }
  
  private boolean isRuntimeClass(URLConnection conn) throws IOException {
    final URL url = conn.getURL();
    if ("file".equalsIgnoreCase(url.getProtocol())) {
      try {
        final String path = new File(url.toURI()).getCanonicalPath();
        for (final String bcpDir : bootClassPathDirs) {
          if (path.startsWith(bcpDir)) {
            return true;
          }
        }
      } catch (URISyntaxException use) {
        // ignore (should not happen, but if it's happening, it's definitely not a runtime class)
      }
    } else if ("jar".equalsIgnoreCase(url.getProtocol()) && conn instanceof JarURLConnection) {
      final URL jarUrl = ((JarURLConnection) conn).getJarFileURL();
      if ("file".equalsIgnoreCase(jarUrl.getProtocol())) try {
        final File jarFile = new File(jarUrl.toURI()).getCanonicalFile();
        return bootClassPathJars.contains(jarFile);
      } catch (URISyntaxException use) {
        // ignore (should not happen, but if it's happening, it's definitely not a runtime class)
      }
    } else if ("jrt".equalsIgnoreCase(url.getProtocol())) {
      // all 'jrt:' URLs refer to a module in the Java 9+ runtime (see http://openjdk.java.net/jeps/220)
      // This may still be different with module system. We support both variants for now.
      // Please note: This code is not guaranteed to work with final Java 9 version. This is just for testing!
      return true;
    }
    return false;
  }
  
  /** Reads a class (binary name) from the given {@link ClassLoader}. */
  private ClassSignature getClassFromClassLoader(final String clazz) throws ClassNotFoundException,IOException {
    final ClassSignature c;
    if (classpathClassCache.containsKey(clazz)) {
      c = classpathClassCache.get(clazz);
      if (c == null) {
        throw new ClassNotFoundException(clazz);
      }
      return c;
    } else {
      final URL url = loader.getResource(AsmUtils.getClassResourceName(clazz));
      if (url != null) {
        final URLConnection conn = url.openConnection();
        final boolean isRuntimeClass = isRuntimeClass(conn);
        final InputStream in = conn.getInputStream();
        try {
          classpathClassCache.put(clazz, c = new ClassSignature(new ClassReader(in), isRuntimeClass, false));
        } finally {
          in.close();
        }
        return c;
      } else {
        final InputStream in = getBytecodeFromJigsaw(clazz);
        if (in != null) {
          try {
            // we mark it as runtime class because it was derived from the module system:
            classpathClassCache.put(clazz, c = new ClassSignature(new ClassReader(in), true, false));
          } finally {
            in.close();
          }
          return c;
        }
      }
      // all failed => the class does not exist!
      classpathClassCache.put(clazz, null);
      throw new ClassNotFoundException(clazz);
    }
  }
  
  @Override
  public ClassSignature lookupRelatedClass(String internalName) {
    final Type type = Type.getObjectType(internalName);
    if (type.getSort() != Type.OBJECT) {
      return null;
    }
    ClassSignature c = classesToCheck.get(internalName);
    if (c == null) try {
      // use binary name, so we need to convert:
      c = getClassFromClassLoader(type.getClassName());
    } catch (ClassNotFoundException cnfe) {
      if (options.contains(Option.FAIL_ON_MISSING_CLASSES)) {
        throw new WrapperRuntimeException(cnfe);
      } else {
        logger.warn(String.format(Locale.ENGLISH,
          "The referenced class '%s' cannot be loaded. Please fix the classpath!",
          type.getClassName()
        ));
      }
    } catch (IOException ioe) {
      throw new WrapperRuntimeException(ioe);
    }
    return c;
  }
  
  private void reportParseFailed(boolean failOnUnresolvableSignatures, String message, String signature) throws ParseException {
    if (failOnUnresolvableSignatures) {
      throw new ParseException(String.format(Locale.ENGLISH, "%s while parsing signature: %s", message, signature));
    } else {
      logger.warn(String.format(Locale.ENGLISH, "%s while parsing signature: %s [signature ignored]", message, signature));
    }
  }
 
  /** Adds the method signature to the list of disallowed methods. The Signature is checked against the given ClassLoader. */
  private void addSignature(final String line, final String defaultMessage, final boolean failOnUnresolvableSignatures) throws ParseException,IOException {
    final String clazz, field, signature, message;
    final Method method;
    int p = line.indexOf('@');
    if (p >= 0) {
      signature = line.substring(0, p).trim();
      message = line.substring(p + 1).trim();
    } else {
      signature = line;
      message = defaultMessage;
    }
    p = signature.indexOf('#');
    if (p >= 0) {
      clazz = signature.substring(0, p);
      final String s = signature.substring(p + 1);
      p = s.indexOf('(');
      if (p >= 0) {
        if (p == 0) {
          throw new ParseException("Invalid method signature (method name missing): " + signature);
        }
        // we ignore the return type, its just to match easier (so return type is void):
        try {
          method = Method.getMethod("void " + s, true);
        } catch (IllegalArgumentException iae) {
          throw new ParseException("Invalid method signature: " + signature);
        }
        field = null;
      } else {
        field = s;
        method = null;
      }
    } else {
      clazz = signature;
      method = null;
      field = null;
    }
    // create printout message:
    final String printout = (message != null && message.length() > 0) ?
      (signature + " [" + message + "]") : signature;
    // check class & method/field signature, if it is really existent (in classpath), but we don't really load the class into JVM:
    if (AsmUtils.isGlob(clazz)) {
      if (method != null || field != null) {
        throw new ParseException(String.format(Locale.ENGLISH, "Class level glob pattern cannot be combined with methods/fields: %s", signature));
      }
      forbiddenClassPatterns.add(new ClassPatternRule(clazz, printout));
    } else {
      final ClassSignature c;
      try {
        c = getClassFromClassLoader(clazz);
      } catch (ClassNotFoundException cnfe) {
        reportParseFailed(failOnUnresolvableSignatures, String.format(Locale.ENGLISH, "Class '%s' not found on classpath", cnfe.getMessage()), signature);
        return;
      }
      if (method != null) {
        assert field == null;
        // list all methods with this signature:
        boolean found = false;
        for (final Method m : c.methods) {
          if (m.getName().equals(method.getName()) && Arrays.equals(m.getArgumentTypes(), method.getArgumentTypes())) {
            found = true;
            forbiddenMethods.put(c.className + '\000' + m, printout);
            // don't break when found, as there may be more covariant overrides!
          }
        }
        if (!found) {
          reportParseFailed(failOnUnresolvableSignatures, "Method not found", signature);
          return;
        }
      } else if (field != null) {
        assert method == null;
        if (!c.fields.contains(field)) {
          reportParseFailed(failOnUnresolvableSignatures, "Field not found", signature);
          return;
        }
        forbiddenFields.put(c.className + '\000' + field, printout);
      } else {
        assert field == null && method == null;
        // only add the signature as class name
        forbiddenClasses.put(c.className, printout);
      }
    }
  }

  /** Reads a list of bundled API signatures from classpath. */
  public void parseBundledSignatures(String name, String jdkTargetVersion) throws IOException,ParseException {
    parseBundledSignatures(name, jdkTargetVersion, true);
  }
  
  private void parseBundledSignatures(String name, String jdkTargetVersion, boolean logging) throws IOException,ParseException {
    if (!name.matches("[A-Za-z0-9\\-\\.]+")) {
      throw new ParseException("Invalid bundled signature reference: " + name);
    }
    // use Checker.class hardcoded (not getClass) so we have a fixed package name:
    InputStream in = Checker.class.getResourceAsStream("signatures/" + name + ".txt");
    // automatically expand the compiler version in here (for jdk-* signatures without version):
    if (in == null && jdkTargetVersion != null && name.startsWith("jdk-") && !name.matches(".*?\\-\\d\\.\\d")) {
      name = name + "-" + jdkTargetVersion;
      in = Checker.class.getResourceAsStream("signatures/" + name + ".txt");
    }
    if (in == null) {
      throw new FileNotFoundException("Bundled signatures resource not found: " + name);
    }
    if (logging) logger.info("Reading bundled API signatures: " + name);
    parseSignaturesFile(in, true);
  }
  
  /** Reads a list of API signatures. Closes the Reader when done (on Exception, too)! */
  public void parseSignaturesFile(InputStream in, String name) throws IOException,ParseException {
    logger.info("Reading API signatures: " + name);
    parseSignaturesFile(in, false);
  }
  
  /** Reads a list of API signatures from the given URL. */
  public void parseSignaturesFile(URL url) throws IOException,ParseException {
    parseSignaturesFile(url.openStream(), url.toString());
  }
  
  /** Reads a list of API signatures from the given file. */
  public void parseSignaturesFile(File f) throws IOException,ParseException {
    parseSignaturesFile(new FileInputStream(f), f.toString());
  }
  
  /** Reads a list of API signatures from a String. */
  public void parseSignaturesString(String signatures) throws IOException,ParseException {
    logger.info("Reading inline API signatures...");
    parseSignaturesFile(new StringReader(signatures), false);
  }
  
  private void parseSignaturesFile(InputStream in, boolean allowBundled) throws IOException,ParseException {
    parseSignaturesFile(new InputStreamReader(in, "UTF-8"), allowBundled);
  }

  private static final String BUNDLED_PREFIX = "@includeBundled ";
  private static final String DEFAULT_MESSAGE_PREFIX = "@defaultMessage ";
  private static final String IGNORE_UNRESOLVABLE_LINE = "@ignoreUnresolvable";

  private void parseSignaturesFile(Reader reader, boolean allowBundled) throws IOException,ParseException {
    final BufferedReader r = new BufferedReader(reader);
    try {
      String line, defaultMessage = null;
      boolean failOnUnresolvableSignatures = options.contains(Option.FAIL_ON_UNRESOLVABLE_SIGNATURES);
      while ((line = r.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#"))
          continue;
        if (line.startsWith("@")) {
          if (allowBundled && line.startsWith(BUNDLED_PREFIX)) {
            final String name = line.substring(BUNDLED_PREFIX.length()).trim();
            parseBundledSignatures(name, null, false);
          } else if (line.startsWith(DEFAULT_MESSAGE_PREFIX)) {
            defaultMessage = line.substring(DEFAULT_MESSAGE_PREFIX.length()).trim();
            if (defaultMessage.length() == 0) defaultMessage = null;
          } else if (line.equals(IGNORE_UNRESOLVABLE_LINE)) {
            failOnUnresolvableSignatures = false;
          } else {
            throw new ParseException("Invalid line in signature file: " + line);
          }
        } else {
          addSignature(line, defaultMessage, failOnUnresolvableSignatures);
        }
      }
    } finally {
      r.close();
    }
  }
  
  /** Parses and adds a class from the given stream to the list of classes to check. Closes the stream when parsed (on Exception, too)! Does not log anything. */
  public void addClassToCheck(final InputStream in) throws IOException {
    final ClassReader reader;
    try {
      reader = new ClassReader(in);
    } finally {
      in.close();
    }
    classesToCheck.put(reader.getClassName(), new ClassSignature(reader, false, true));
  }
  
  /** Parses and adds a class from the given file to the list of classes to check. Does not log anything. */
  public void addClassToCheck(File f) throws IOException {
    addClassToCheck(new FileInputStream(f));
  }

  /** Parses and adds a multiple class files. */
  public void addClassesToCheck(Iterable<File> files) throws IOException {
    logger.info("Loading classes to check...");
    for (final File f : files) {
      addClassToCheck(f);
    }
  }

  /** Parses and adds a multiple class files. */
  public void addClassesToCheck(File... files) throws IOException {
    addClassesToCheck(Arrays.asList(files));
  }

  /** Parses and adds a multiple class files. */
  public void addClassesToCheck(File basedir, Iterable<String> relativeNames) throws IOException {
    logger.info("Loading classes to check...");
    for (final String f : relativeNames) {
      addClassToCheck(new File(basedir, f));
    }
  }

  /** Parses and adds a multiple class files. */
  public void addClassesToCheck(File basedir, String... relativeNames) throws IOException {
    addClassesToCheck(basedir, Arrays.asList(relativeNames));
  }

  public boolean hasNoSignatures() {
    return forbiddenMethods.isEmpty() && forbiddenFields.isEmpty() && forbiddenClasses.isEmpty() && forbiddenClassPatterns.isEmpty() && (!options.contains(Option.INTERNAL_RUNTIME_FORBIDDEN));
  }
  
  /** Adds the given annotation class for suppressing errors. */
  public void addSuppressAnnotation(Class<? extends Annotation> anno) {
    suppressAnnotations.add(anno.getName());
  }
  
  /** Adds suppressing annotation name in binary form (dotted). It may also be a glob pattern. The class name is not checked for existence. */
  public void addSuppressAnnotation(String annoName) {
    suppressAnnotations.add(annoName);
  }
  
  /** Parses a class and checks for valid method invocations */
  private int checkClass(final ClassReader reader, Pattern suppressAnnotationsPattern) {
    final String className = Type.getObjectType(reader.getClassName()).getClassName();
    final ClassScanner scanner = new ClassScanner(this, forbiddenClasses, forbiddenClassPatterns, forbiddenMethods, forbiddenFields, suppressAnnotationsPattern, options.contains(Option.INTERNAL_RUNTIME_FORBIDDEN)); 
    reader.accept(scanner, ClassReader.SKIP_FRAMES);
    final List<ForbiddenViolation> violations = scanner.getSortedViolations();
    final Pattern splitter = Pattern.compile(Pattern.quote(ForbiddenViolation.SEPARATOR));
    for (final ForbiddenViolation v : violations) {
      for (final String line : splitter.split(v.format(className, scanner.getSourceFile()))) {
        logger.error(line);
      }
    }
    return violations.size();
  }
  
  public void run() throws ForbiddenApiException {
    logger.info("Scanning classes for violations...");
    int errors = 0;
    final Pattern suppressAnnotationsPattern = AsmUtils.glob2Pattern(suppressAnnotations.toArray(new String[suppressAnnotations.size()]));
    try {
      for (final ClassSignature c : classesToCheck.values()) {
        errors += checkClass(c.getReader(), suppressAnnotationsPattern);
      }
    } catch (WrapperRuntimeException wre) {
      final Throwable cause = wre.getCause();
      if (cause != null) {
        throw new ForbiddenApiException("Check for forbidden API calls failed: " + cause.toString(), cause);
      } else {
        throw new ForbiddenApiException("Check for forbidden API calls failed.");
      }
    }
    
    final String message = String.format(Locale.ENGLISH, 
        "Scanned %d (and %d related) class file(s) for forbidden API invocations (in %.2fs), %d error(s).",
        classesToCheck.size(), classesToCheck.isEmpty() ? 0 : classpathClassCache.size(), (System.currentTimeMillis() - start) / 1000.0, errors);
    if (options.contains(Option.FAIL_ON_VIOLATION) && errors > 0) {
      logger.error(message);
      throw new ForbiddenApiException("Check for forbidden API calls failed, see log.");
    } else {
      logger.info(message);
    }
  }
  
}
