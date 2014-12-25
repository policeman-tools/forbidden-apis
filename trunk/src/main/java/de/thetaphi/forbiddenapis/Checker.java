package de.thetaphi.forbiddenapis;

/*
 * (C) Copyright 2013 Uwe Schindler (Generics Policeman) and others.
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * Task to check if a set of class files contains calls to forbidden APIs
 * from a given classpath and list of API signatures (either inline or as pointer to files).
 * In contrast to other ANT tasks, this tool does only visit the given classpath
 * and the system classloader. It uses the local classpath in preference to the system classpath
 * (which violates the spec).
 */
public abstract class Checker {

  public final boolean isSupportedJDK;
  
  private final long start;
  
  final Set<File> bootClassPathJars;
  final Set<String> bootClassPathDirs;
  final ClassLoader loader;
  final boolean internalRuntimeForbidden, failOnMissingClasses, defaultFailOnUnresolvableSignatures;
  
  // key is the internal name (slashed):
  final Map<String,ClassSignatureLookup> classesToCheck = new HashMap<String,ClassSignatureLookup>();
  // key is the binary name (dotted):
  final Map<String,ClassSignatureLookup> classpathClassCache = new HashMap<String,ClassSignatureLookup>();
  
  // key is the internal name (slashed), followed by \000 and the field name:
  final Map<String,String> forbiddenFields = new HashMap<String,String>();
  // key is the internal name (slashed), followed by \000 and the method signature:
  final Map<String,String> forbiddenMethods = new HashMap<String,String>();
  // key is the internal name (slashed):
  final Map<String,String> forbiddenClasses = new HashMap<String,String>();
  
  protected abstract void logError(String msg);
  protected abstract void logWarn(String msg);
  protected abstract void logInfo(String msg);
  
  public Checker(ClassLoader loader, boolean internalRuntimeForbidden, boolean failOnMissingClasses, boolean defaultFailOnUnresolvableSignatures) {
    this.loader = loader;
    this.internalRuntimeForbidden = internalRuntimeForbidden;
    this.failOnMissingClasses = failOnMissingClasses;
    this.defaultFailOnUnresolvableSignatures = defaultFailOnUnresolvableSignatures;
    this.start = System.currentTimeMillis();
    
    boolean isSupportedJDK = false;
    final Set<File> bootClassPathJars = new LinkedHashSet<File>();
    final Set<String> bootClassPathDirs = new LinkedHashSet<String>();
    try {
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
    } catch (IOException ioe) {
      isSupportedJDK = false;
      bootClassPathJars.clear();
      bootClassPathDirs.clear();
    }
    this.bootClassPathJars = Collections.unmodifiableSet(bootClassPathJars);
    this.bootClassPathDirs = Collections.unmodifiableSet(bootClassPathDirs);
    
    if (isSupportedJDK) {
      // check if we can load runtime classes (e.g. java.lang.Object).
      // If this fails, we have a newer Java version than ASM supports:
      try {
        isSupportedJDK = getClassFromClassLoader(Object.class.getName()).isRuntimeClass;
      } catch (IllegalArgumentException iae) {
        isSupportedJDK = false;
      } catch (ClassNotFoundException cnfe) {
        isSupportedJDK = false;
      }
    }
    
    // finally set the latest value to final field:
    this.isSupportedJDK = isSupportedJDK;
  }
  
  /** Reads a class (binary name) from the given {@link ClassLoader}. */
  ClassSignatureLookup getClassFromClassLoader(final String clazz) throws ClassNotFoundException {
    final ClassSignatureLookup c;
    if (classpathClassCache.containsKey(clazz)) {
      c = classpathClassCache.get(clazz);
      if (c == null) {
        throw new ClassNotFoundException("Class '" + clazz + "' not found on classpath");
      }
    } else {
      try {
        final URL url = loader.getResource(clazz.replace('.', '/') + ".class");
        if (url == null) {
          classpathClassCache.put(clazz, null);
          throw new ClassNotFoundException("Class '" + clazz + "' not found on classpath");
        }
        boolean isRuntimeClass = false;
        final URLConnection conn = url.openConnection();
        if ("file".equalsIgnoreCase(url.getProtocol())) {
          try {
            final String path = new File(url.toURI()).getCanonicalPath();
            for (final String bcpDir : bootClassPathDirs) {
              if (path.startsWith(bcpDir)) {
                isRuntimeClass = true;
                break;
              }
            }
          } catch (URISyntaxException use) {
            // ignore (should not happen, but if it's happening, it's definitely not a runtime class)
          }
        } else {
          if (conn instanceof JarURLConnection) {
            final URL jarUrl = ((JarURLConnection) conn).getJarFileURL();
            if ("file".equalsIgnoreCase(jarUrl.getProtocol())) try {
              final File jarFile = new File(jarUrl.toURI()).getCanonicalFile();
              isRuntimeClass = bootClassPathJars.contains(jarFile);
            } catch (URISyntaxException use) {
              // ignore (should not happen, but if it's happening, it's definitely not a runtime class)
            }
          }
        }
        final InputStream in = conn.getInputStream();
        try {
          classpathClassCache.put(clazz, c = new ClassSignatureLookup(new ClassReader(in), isRuntimeClass, false));
        } finally {
          in.close();
        }
      } catch (IOException ioe) {
        classpathClassCache.put(clazz, null);
        throw new ClassNotFoundException("I/O error while loading of class '" + clazz + "'", ioe);
      }
    }
    return c;
  }
  
  private void reportParseFailed(boolean failOnUnresolvableSignatures, String message, String signature) throws ParseException {
    if (failOnUnresolvableSignatures) {
      throw new ParseException(String.format(Locale.ENGLISH, "%s while parsing signature: %s", message, signature));
    } else {
      logWarn(String.format(Locale.ENGLISH, "%s while parsing signature: %s [signature ignored]", message, signature));
    }
  }
 
  /** Adds the method signature to the list of disallowed methods. The Signature is checked against the given ClassLoader. */
  private void addSignature(final String line, final String defaultMessage, final boolean failOnUnresolvableSignatures) throws ParseException {
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
    final ClassSignatureLookup c;
    try {
      c = getClassFromClassLoader(clazz);
    } catch (ClassNotFoundException cnfe) {
      reportParseFailed(failOnUnresolvableSignatures, cnfe.getMessage(), signature);
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

  /** Reads a list of bundled API signatures from classpath. */
  public final void parseBundledSignatures(String name, String jdkTargetVersion) throws IOException,ParseException {
    if (!name.matches("[A-Za-z0-9\\-\\.]+")) {
      throw new ParseException("Invalid bundled signature reference: " + name);
    }
    InputStream in = this.getClass().getResourceAsStream("signatures/" + name + ".txt");
    // automatically expand the compiler version in here (for jdk-* signatures without version):
    if (in == null && jdkTargetVersion != null && name.startsWith("jdk-") && !name.matches(".*?\\-\\d\\.\\d")) {
      name = name + "-" + jdkTargetVersion;
      in = this.getClass().getResourceAsStream("signatures/" + name + ".txt");
    }
    if (in == null) {
      throw new FileNotFoundException("Bundled signatures resource not found: " + name);
    }
    parseSignaturesFile(in, true);
  }
  
  /** Reads a list of API signatures. Closes the Reader when done (on Exception, too)! */
  public final void parseSignaturesFile(InputStream in) throws IOException,ParseException {
    parseSignaturesFile(in, false);
  }
  
  /** Reads a list of API signatures from a String. */
  public final void parseSignaturesString(String signatures) throws IOException,ParseException {
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
      boolean failOnUnresolvableSignatures = this.defaultFailOnUnresolvableSignatures;
      while ((line = r.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#"))
          continue;
        if (line.startsWith("@")) {
          if (allowBundled && line.startsWith(BUNDLED_PREFIX)) {
            final String name = line.substring(BUNDLED_PREFIX.length()).trim();
            parseBundledSignatures(name, null);
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
  
  /** Parses and adds a class from the given stream to the list of classes to check. Closes the stream when parsed (on Exception, too)! */
  public final void addClassToCheck(final InputStream in) throws IOException {
    final ClassReader reader;
    try {
      reader = new ClassReader(in);
    } finally {
      in.close();
    }
    classesToCheck.put(reader.getClassName(), new ClassSignatureLookup(reader, false, true));
  }
  
  public final boolean hasNoSignatures() {
    return forbiddenMethods.isEmpty() && forbiddenClasses.isEmpty() && forbiddenFields.isEmpty() && (!internalRuntimeForbidden);
  }
  
  /** Parses a class and checks for valid method invocations */
  private int checkClass(final ClassReader reader) {
    final int[] violations = new int[1];
    final String className = Type.getObjectType(reader.getClassName()).getClassName();
    reader.accept(new ClassScanner(this, className, violations), ClassReader.SKIP_FRAMES);
    return violations[0];
  }
  
  public final void run() throws ForbiddenApiException {
    int errors = 0;
    try {
      for (final ClassSignatureLookup c : classesToCheck.values()) {
        errors += checkClass(c.getReader());
      }
    } catch (WrapperRuntimeException wre) {
      Throwable cause = wre.getCause();
      throw new ForbiddenApiException("Check for forbidden API calls failed: " + cause.toString());
    }
    
    final String message = String.format(Locale.ENGLISH, 
        "Scanned %d (and %d related) class file(s) for forbidden API invocations (in %.2fs), %d error(s).",
        classesToCheck.size(), classesToCheck.isEmpty() ? 0 : classpathClassCache.size(), (System.currentTimeMillis() - start) / 1000.0, errors);
    if (errors > 0) {
      logError(message);
      throw new ForbiddenApiException("Check for forbidden API calls failed, see log.");
    } else {
      logInfo(message);
    }
  }
  
}
