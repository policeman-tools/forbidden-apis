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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * Forbidden APIs checker class.
 */
public final class Checker implements RelatedClassLookup, Constants {
  
  public static enum Option {
    FAIL_ON_MISSING_CLASSES,
    FAIL_ON_VIOLATION,
    FAIL_ON_UNRESOLVABLE_SIGNATURES,
    DISABLE_CLASSLOADING_CACHE
  }

  public final boolean isSupportedJDK;
  
  private final long start;
  private final NavigableSet<String> runtimePaths;
    
  final Logger logger;
  
  final ClassLoader loader;
  final java.lang.reflect.Method method_Class_getModule, method_Module_getName;
  final EnumSet<Option> options;
  
  /** Classes to check: key is the binary name (dotted) */
  final Map<String,ClassSignature> classesToCheck = new HashMap<String,ClassSignature>();
  /** Cache of loaded classes: key is the binary name (dotted) */
  final Map<String,ClassSignature> classpathClassCache = new HashMap<String,ClassSignature>();
  
  /** if enabled, the bundled signature to enable heuristics for detection of non-portable runtime calls is used */
  private boolean forbidNonPortableRuntime = false;
  
  /** Key is used to lookup forbidden signature in following formats:
   * <ul>
   * <li>methods: key is the internal name (slashed), followed by \000 and the method signature
   * <li>fields: key is the internal name (slashed), followed by \000 and the field name
   * <li>classes: key is the internal name (slashed)
   * </ul>
   */
  final Map<String,String> forbiddenSignatures = new HashMap<String,String>();
  
  /** set of patterns of forbidden classes */
  final Set<ClassPatternRule> forbiddenClassPatterns = new LinkedHashSet<ClassPatternRule>();
  /** descriptors (not internal names) of all annotations that suppress */
  final Set<String> suppressAnnotations = new LinkedHashSet<String>();
    
  private static enum UnresolvableReporting {
    FAIL(true) {
      @Override
      public void parseFailed(Logger logger, String message, String signature) throws ParseException {
        throw new ParseException(String.format(Locale.ENGLISH, "%s while parsing signature: %s", message, signature));
      }
    },
    WARNING(false) {
      @Override
      public void parseFailed(Logger logger, String message, String signature) throws ParseException {
        logger.warn(String.format(Locale.ENGLISH, "%s while parsing signature: %s [signature ignored]", message, signature));
      }
    },
    SILENT(true) {
      @Override
      public void parseFailed(Logger logger, String message, String signature) throws ParseException {
        // keep silent
      }
    };
    
    private UnresolvableReporting(boolean reportClassNotFound) {
      this.reportClassNotFound = reportClassNotFound;
    }
    
    public final boolean reportClassNotFound;
    public abstract void parseFailed(Logger logger, String message, String signature) throws ParseException;
  }

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
    
    // Try to figure out entry points to Java 9+ module system (Jigsaw)
    java.lang.reflect.Method method_Class_getModule, method_Module_getName;
    try {
      method_Class_getModule = Class.class.getMethod("getModule");
      method_Module_getName = method_Class_getModule
          .getReturnType().getMethod("getName");
      isSupportedJDK = true;
    } catch (NoSuchMethodException e) {
      method_Class_getModule = method_Module_getName = null;
    }
    this.method_Class_getModule = method_Class_getModule;
    this.method_Module_getName = method_Module_getName;
    
    final NavigableSet<String> runtimePaths = new TreeSet<String>();
    
    // fall back to legacy behavior:
    if (!isSupportedJDK) {
      try {
        final URL objectClassURL = loader.getResource(AsmUtils.getClassResourceName(Object.class.getName()));
        if (objectClassURL != null && "jrt".equalsIgnoreCase(objectClassURL.getProtocol())) {
          // this is Java 9+ allowing direct access to .class file resources - we do not need to deal with modules!
          isSupportedJDK = true;
        } else {
          String javaHome = System.getProperty("java.home");
          if (javaHome != null) {
            javaHome = new File(javaHome).getCanonicalPath();
            if (!javaHome.endsWith(File.separator)) {
              javaHome += File.separator;
            }
            runtimePaths.add(javaHome);
          }
          // Scan the runtime's bootclasspath, too! This is needed because
          // Apple's JDK 1.6 has the main rt.jar outside ${java.home}!
          final RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
          if (rb.isBootClassPathSupported()) {
            final String cp = rb.getBootClassPath();
            final StringTokenizer st = new StringTokenizer(cp, File.pathSeparator);
            while (st.hasMoreTokens()) {
              File f = new File(st.nextToken().trim());
              if (f.isFile()) {
                f = f.getParentFile();
              }
              if (f.exists()) {
                String fp = f.getCanonicalPath();
                if (!fp.endsWith(File.separator)) {
                  fp += File.separator;
                }
                runtimePaths.add(fp);
              }
            }
          }
          isSupportedJDK = !runtimePaths.isEmpty();
          if (!isSupportedJDK) {
            logger.warn("Boot classpath appears to be empty or ${java.home} not defined; marking runtime as not suppported.");
          }
        }
      } catch (IOException ioe) {
        logger.warn("Cannot scan boot classpath and ${java.home} due to IO exception; marking runtime as not suppported: " + ioe);
        isSupportedJDK = false;
        runtimePaths.clear();
      }
    }
    this.runtimePaths = runtimePaths;
    // logger.info("Runtime paths: " + runtimePaths);
    
    if (isSupportedJDK) {
      try {
        isSupportedJDK = getClassFromClassLoader(Object.class.getName()).isRuntimeClass;
        if (!isSupportedJDK) {
          logger.warn("Bytecode of java.lang.Object does not seem to come from runtime library; marking runtime as not suppported.");
        }
      } catch (IllegalArgumentException iae) {
        logger.warn("Bundled version of ASM cannot parse bytecode of java.lang.Object class; marking runtime as not suppported.");
        isSupportedJDK = false;
      } catch (ClassNotFoundException cnfe) {
        logger.warn("Bytecode or Class<?> instance of java.lang.Object not found; marking runtime as not suppported.");
        isSupportedJDK = false;
      } catch (IOException ioe) {
        logger.warn("IOException while loading java.lang.Object class from classloader; marking runtime as not suppported: " + ioe);
        isSupportedJDK = false;
      }
    }
    
    // finally set the latest value to final field:
    this.isSupportedJDK = isSupportedJDK;
  }
  
  /** Loads the class from Java9's module system and uses reflection to get methods and fields. */
  private ClassSignature loadClassFromJigsaw(String classname) throws IOException {
    if (method_Class_getModule == null || method_Module_getName == null) {
      return null; // not Jigsaw Module System
    }
    
    final Class<?> clazz;
    final String moduleName;
    try {
      clazz = Class.forName(classname, false, loader);
      final Object module = method_Class_getModule.invoke(clazz);
      moduleName = (String) method_Module_getName.invoke(module);
    } catch (Exception e) {
      return null; // not found
    }
    
    return new ClassSignature(clazz, AsmUtils.isRuntimeModule(moduleName));
  }
  
  private boolean isRuntimePath(URL url) throws IOException {
    if (!"file".equalsIgnoreCase(url.getProtocol())) {
      return false;
    }
    try {
      final String path = new File(url.toURI()).getCanonicalPath();
      final String lookup = runtimePaths.floor(path);
      return lookup != null && path.startsWith(lookup);
    } catch (URISyntaxException e) {
      // should not happen, but if it's happening, it's definitely not a below our paths
      return false;
    }
  }
  
  private boolean isRuntimeClass(URLConnection conn) throws IOException {
    final URL url = conn.getURL();
    if (isRuntimePath(url)) {
       return true;
    } else if ("jar".equalsIgnoreCase(url.getProtocol()) && conn instanceof JarURLConnection) {
      final URL jarUrl = ((JarURLConnection) conn).getJarFileURL();
      return isRuntimePath(jarUrl);
    } else if ("jrt".equalsIgnoreCase(url.getProtocol())) {
      // all 'jrt:' URLs refer to a module in the Java 9+ runtime (see http://openjdk.java.net/jeps/220)
      return AsmUtils.isRuntimeModule(AsmUtils.getModuleName(url));
    }
    return false;
  }
  
  /** Reads a class (binary name) from the given {@link ClassLoader}. If not found there, falls back to the list of classes to be checked. */
  private ClassSignature getClassFromClassLoader(final String clazz) throws ClassNotFoundException,IOException {
    if (classpathClassCache.containsKey(clazz)) {
      final ClassSignature c = classpathClassCache.get(clazz);
      if (c == null) {
        throw new ClassNotFoundException(clazz);
      }
      return c;
    } else {
      final URL url = loader.getResource(AsmUtils.getClassResourceName(clazz));
      if (url != null) {
        final URLConnection conn = url.openConnection();
        final boolean isRuntimeClass = isRuntimeClass(conn);
        if (!isRuntimeClass && options.contains(Option.DISABLE_CLASSLOADING_CACHE)) {
          conn.setUseCaches(false);
        }
        final InputStream in = conn.getInputStream();
        final ClassReader cr;
        try {
          cr = AsmUtils.readAndPatchClass(in);
        } catch (IllegalArgumentException iae) {
          // if class is too new for this JVM, we try to load it as Class<?> via Jigsaw
          // (only if it's a runtime class):
          if (isRuntimeClass) {
            final ClassSignature c = loadClassFromJigsaw(clazz);
            if (c != null) {
              classpathClassCache.put(clazz, c);
              return c;
            }
          }
          // unfortunately the ASM IAE has no message, so add good info!
          throw new IllegalArgumentException(String.format(Locale.ENGLISH,
              "The class file format of '%s' is too recent to be parsed by ASM.", clazz));
        } finally {
          in.close();
        }
        final ClassSignature c = new ClassSignature(cr, isRuntimeClass, false);
        classpathClassCache.put(clazz, c);
        return c;
      } else {
        final ClassSignature c = loadClassFromJigsaw(clazz);
        if (c != null) {
          classpathClassCache.put(clazz, c);
          return c;
        }
      }
      // try to get class from our list of classes we are checking:
      final ClassSignature c = classesToCheck.get(clazz);
      if (c != null) {
        classpathClassCache.put(clazz, c);
        return c;
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
    try {
      // use binary name, so we need to convert:
      return getClassFromClassLoader(type.getClassName());
    } catch (ClassNotFoundException cnfe) {
      if (options.contains(Option.FAIL_ON_MISSING_CLASSES)) {
        throw new WrapperRuntimeException(cnfe);
      } else {
        logger.warn(String.format(Locale.ENGLISH,
          "The referenced class '%s' cannot be loaded. Please fix the classpath!",
          type.getClassName()
        ));
        return null;
      }
    } catch (IOException ioe) {
      throw new WrapperRuntimeException(ioe);
    }
  }
  
  /** Adds the method signature to the list of disallowed methods. The Signature is checked against the given ClassLoader. */
  private void addSignature(final String line, final String defaultMessage, final UnresolvableReporting report, final Set<String> missingClasses) throws ParseException,IOException {
    final String clazz, field, signature;
    String message = null;
    final Method method;
    int p = line.indexOf('@');
    if (p >= 0) {
      signature = line.substring(0, p).trim();
      message = line.substring(p + 1).trim();
    } else {
      signature = line;
      message = defaultMessage;
    }
    if (line.isEmpty()) {
      throw new ParseException("Empty signature");
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
    if (message != null && message.isEmpty()) {
      message = null;
    }
    // create printout message:
    final String printout = (message != null) ? (signature + " [" + message + "]") : signature;
    // check class & method/field signature, if it is really existent (in classpath), but we don't really load the class into JVM:
    if (AsmUtils.isGlob(clazz)) {
      if (method != null || field != null) {
        throw new ParseException(String.format(Locale.ENGLISH, "Class level glob pattern cannot be combined with methods/fields: %s", signature));
      }
      forbiddenClassPatterns.add(new ClassPatternRule(clazz, message));
    } else {
      final ClassSignature c;
      try {
        c = getClassFromClassLoader(clazz);
      } catch (ClassNotFoundException cnfe) {
        if (report.reportClassNotFound) {
          report.parseFailed(logger, String.format(Locale.ENGLISH, "Class '%s' not found on classpath", cnfe.getMessage()), signature);
        } else {
          missingClasses.add(clazz);
        }
        return;
      }
      if (method != null) {
        assert field == null;
        // list all methods with this signature:
        boolean found = false;
        for (final Method m : c.methods) {
          if (m.getName().equals(method.getName()) && Arrays.equals(m.getArgumentTypes(), method.getArgumentTypes())) {
            found = true;
            forbiddenSignatures.put(c.className + '\000' + m, printout);
            // don't break when found, as there may be more covariant overrides!
          }
        }
        if (!found) {
          report.parseFailed(logger, "Method not found", signature);
          return;
        }
      } else if (field != null) {
        assert method == null;
        if (!c.fields.contains(field)) {
          report.parseFailed(logger, "Field not found", signature);
          return;
        }
        forbiddenSignatures.put(c.className + '\000' + field, printout);
      } else {
        assert field == null && method == null;
        // only add the signature as class name
        forbiddenSignatures.put(c.className, printout);
      }
    }
  }
  
  private void reportMissingSignatureClasses(Set<String> missingClasses) {
    if (missingClasses.isEmpty()) {
      return;
    }
    logger.warn("Some signatures were ignored because the following classes were not found on classpath:");
    final StringBuilder sb = new StringBuilder();
    int count = 0;
    for (String s : missingClasses) {
      sb.append(count == 0 ? "  " : ", ").append(s);
      count++;
      if (sb.length() >= 70) {
        sb.append(",... (and ").append(missingClasses.size() - count).append(" more).");
        break;
      }
    }
    logger.warn(sb.toString());
  }

  /** Reads a list of bundled API signatures from classpath. */
  public void addBundledSignatures(String name, String jdkTargetVersion) throws IOException,ParseException {
    final Set<String> missingClasses = new TreeSet<String>();
    addBundledSignatures(name, jdkTargetVersion, true, missingClasses);
    reportMissingSignatureClasses(missingClasses);
  }
  
  public static String fixTargetVersion(String name) throws ParseException {
    final Matcher m = JDK_SIG_PATTERN.matcher(name);
    if (m.matches()) {
      if (m.group(4) == null) {
        final String prefix = m.group(1);
        final int major = Integer.parseInt(m.group(2));
        final int minor = m.group(3) != null ? Integer.parseInt(m.group(3).substring(1)) : 0;
        if (major == 1 && minor >= 1 && minor < 9) {
          // Java 1.1 till 1.8 (aka 8):
          return prefix + "1." + minor;
        } else if (major > 1 && major < 9) {
          // fix pre-Java9 major version to use "1.x" syntax:
          if (minor == 0) {
            return prefix + "1." + major;
          }
        } else if (major >= 9 && minor > 0) {
          return prefix + major + "." + minor;
        } else  if (major >= 9 && minor == 0) {
          return prefix + major;
        }
      }
      throw new ParseException("Invalid bundled signature reference (JDK version is invalid): " + name);
    }
    return name;
  }
  
  private void addBundledSignatures(String name, String jdkTargetVersion, boolean logging, Set<String> missingClasses) throws IOException,ParseException {
    if (!name.matches("[A-Za-z0-9\\-\\.]+")) {
      throw new ParseException("Invalid bundled signature reference: " + name);
    }
    if (BS_JDK_NONPORTABLE.equals(name)) {
      if (logging) logger.info("Reading bundled API signatures: " + name);
      forbidNonPortableRuntime = true;
      return;
    }
    name = fixTargetVersion(name);
    // use Checker.class hardcoded (not getClass) so we have a fixed package name:
    InputStream in = Checker.class.getResourceAsStream("signatures/" + name + ".txt");
    // automatically expand the compiler version in here (for jdk-* signatures without version):
    if (in == null && jdkTargetVersion != null && name.startsWith("jdk-") && !name.matches(".*?\\-\\d+(\\.\\d+)*")) {
      name = name + "-" + jdkTargetVersion;
      name = fixTargetVersion(name);
      in = Checker.class.getResourceAsStream("signatures/" + name + ".txt");
    }
    if (in == null) {
      throw new FileNotFoundException("Bundled signatures resource not found: " + name);
    }
    if (logging) logger.info("Reading bundled API signatures: " + name);
    parseSignaturesStream(in, true, missingClasses);
  }
  
  /** Reads a list of API signatures. Closes the Reader when done (on Exception, too)! */
  public void parseSignaturesFile(InputStream in, String name) throws IOException,ParseException {
    logger.info("Reading API signatures: " + name);
    final Set<String> missingClasses = new TreeSet<String>();
    parseSignaturesStream(in, false, missingClasses);
    reportMissingSignatureClasses(missingClasses);
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
    final Set<String> missingClasses = new TreeSet<String>();
    parseSignaturesFile(new StringReader(signatures), false, missingClasses);
    reportMissingSignatureClasses(missingClasses);
  }
  
  private void parseSignaturesStream(InputStream in, boolean allowBundled, Set<String> missingClasses) throws IOException,ParseException {
    parseSignaturesFile(new InputStreamReader(in, "UTF-8"), allowBundled, missingClasses);
  }

  private static final String BUNDLED_PREFIX = "@includeBundled ";
  private static final String DEFAULT_MESSAGE_PREFIX = "@defaultMessage ";
  private static final String IGNORE_UNRESOLVABLE_LINE = "@ignoreUnresolvable";

  private void parseSignaturesFile(Reader reader, boolean isBundled, Set<String> missingClasses) throws IOException,ParseException {
    final BufferedReader r = new BufferedReader(reader);
    try {
      String line, defaultMessage = null;
      UnresolvableReporting reporter = options.contains(Option.FAIL_ON_UNRESOLVABLE_SIGNATURES) ? UnresolvableReporting.FAIL : UnresolvableReporting.WARNING;
      while ((line = r.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#"))
          continue;
        if (line.startsWith("@")) {
          if (isBundled && line.startsWith(BUNDLED_PREFIX)) {
            final String name = line.substring(BUNDLED_PREFIX.length()).trim();
            addBundledSignatures(name, null, false, missingClasses);
          } else if (line.startsWith(DEFAULT_MESSAGE_PREFIX)) {
            defaultMessage = line.substring(DEFAULT_MESSAGE_PREFIX.length()).trim();
            if (defaultMessage.length() == 0) defaultMessage = null;
          } else if (line.equals(IGNORE_UNRESOLVABLE_LINE)) {
            reporter = isBundled ? UnresolvableReporting.SILENT : UnresolvableReporting.WARNING;
          } else {
            throw new ParseException("Invalid line in signature file: " + line);
          }
        } else {
          addSignature(line, defaultMessage, reporter, missingClasses);
        }
      }
    } finally {
      r.close();
    }
  }
  
  /** Parses and adds a class from the given stream to the list of classes to check. Closes the stream when parsed (on Exception, too)! Does not log anything. */
  public void addClassToCheck(final InputStream in, String name) throws IOException {
    final ClassReader reader;
    try {
      reader = AsmUtils.readAndPatchClass(in);
    } catch (IllegalArgumentException iae) {
      // unfortunately the ASM IAE has no message, so add good info!
      throw new IllegalArgumentException(String.format(Locale.ENGLISH,
          "The class file format of '%s' is too recent to be parsed by ASM.", name));
    } finally {
      in.close();
    }
    final String binaryName = Type.getObjectType(reader.getClassName()).getClassName();
    classesToCheck.put(binaryName, new ClassSignature(reader, false, true));
  }
  
  /** Parses and adds a class from the given file to the list of classes to check. Does not log anything. */
  public void addClassToCheck(File f) throws IOException {
    addClassToCheck(new FileInputStream(f), f.toString());
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
    return 0 == forbiddenSignatures.size() + 
        forbiddenClassPatterns.size() +
        (forbidNonPortableRuntime ? 1 : 0);
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
    final ClassScanner scanner = new ClassScanner(this, forbiddenSignatures, forbiddenClassPatterns, suppressAnnotationsPattern, forbidNonPortableRuntime); 
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
        "Scanned %d class file(s) for forbidden API invocations (in %.2fs), %d error(s).",
        classesToCheck.size(), (System.currentTimeMillis() - start) / 1000.0, errors);
    if (options.contains(Option.FAIL_ON_VIOLATION) && errors > 0) {
      logger.error(message);
      throw new ForbiddenApiException("Check for forbidden API calls failed, see log.");
    } else {
      logger.info(message);
    }
  }
  
}
