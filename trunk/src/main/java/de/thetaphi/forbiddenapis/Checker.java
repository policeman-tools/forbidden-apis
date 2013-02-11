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
import org.objectweb.asm.Label;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.File;
import java.io.StringReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
  
  final String javaRuntimeLibPath, javaRuntimeExtensionsPath;
  final ClassLoader loader;
  final boolean internalRuntimeForbidden, failOnMissingClasses;
  
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
  
  public Checker(ClassLoader loader, boolean internalRuntimeForbidden, boolean failOnMissingClasses) {
    this.loader = loader;
    this.internalRuntimeForbidden = internalRuntimeForbidden;
    this.failOnMissingClasses = failOnMissingClasses;
    this.start = System.currentTimeMillis();
    
    boolean isSupportedJDK = false;
    File javaRuntimeLibPath = null;
    try {
      javaRuntimeLibPath = new File(System.getProperty("java.home"), "lib").getCanonicalFile();
      if (javaRuntimeLibPath.exists()) {
        isSupportedJDK = true;
      }
    } catch (IOException ioe) {
      isSupportedJDK = false;
    }
    
    // we have to initialize lib paths here, otherwise getClassFromClassLoader() fails!
    if (isSupportedJDK) {
      this.javaRuntimeLibPath = javaRuntimeLibPath.getPath() + File.separator;
      this.javaRuntimeExtensionsPath = new File(javaRuntimeLibPath, "ext").getPath() + File.separator;
    } else {
      this.javaRuntimeLibPath = this.javaRuntimeExtensionsPath = null;
    }

    if (isSupportedJDK) {
      // check if we can load runtime classes (e.g. java.lang.String).
      // If this fails, we have a newer Java version than ASM supports:
      try {
        isSupportedJDK = getClassFromClassLoader(String.class.getName(), true).isRuntimeClass;
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
  private ClassSignatureLookup getClassFromClassLoader(final String clazz, boolean throwCNFE) throws ClassNotFoundException {
    final ClassSignatureLookup c;
    if (classpathClassCache.containsKey(clazz)) {
      c = classpathClassCache.get(clazz);
      if (throwCNFE && c == null) {
        throw new ClassNotFoundException("Loading of class " + clazz + " failed: Not found");
      }
    } else {
      try {
        final URL url = loader.getResource(clazz.replace('.', '/') + ".class");
        if (url == null) {
          classpathClassCache.put(clazz, null);
          if (throwCNFE) {
            throw new ClassNotFoundException("Loading of class " + clazz + " failed: Not found");
          } else {
            return null;
          }
        }
        final URLConnection conn = url.openConnection();
        boolean isRuntimeClass = false;
        if (javaRuntimeLibPath != null && conn instanceof JarURLConnection) {
          final URL jarUrl = ((JarURLConnection) conn).getJarFileURL();
          if ("file".equalsIgnoreCase(jarUrl.getProtocol())) try {
            final String path = new File(jarUrl.toURI()).getCanonicalPath();
            if (path.startsWith(javaRuntimeLibPath) && !path.startsWith(javaRuntimeExtensionsPath)) {
              // logInfo(clazz + " is a runtime class.");
              isRuntimeClass = true;
            }
          } catch (URISyntaxException use) {
            // ignore (should not happen, but if it's happening, it's definitely not a runtime class)
          }
        }
        final InputStream in = conn.getInputStream();
        try {
          classpathClassCache.put(clazz, c = new ClassSignatureLookup(new ClassReader(in), isRuntimeClass));
        } finally {
          in.close();
        }
      } catch (IOException ioe) {
        classpathClassCache.put(clazz, null);
        if (throwCNFE) {
          throw new ClassNotFoundException("Loading of class " + clazz + " failed.", ioe);
        } else {
          return null;
        }
      }
    }
    return c;
  }
 
  /** Adds the method signature to the list of disallowed methods. The Signature is checked against the given ClassLoader. */
  private void addSignature(final String line, final String defaultMessage) throws ParseException {
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
      c = getClassFromClassLoader(clazz, true);
    } catch (ClassNotFoundException cnfe) {
      throw new ParseException(cnfe.getMessage());
    }
    if (method != null) {
      assert field == null;
      // list all methods with this signature:
      boolean found = false;
      for (final Method m : c.methods) {
        if (m.getName().equals(method.getName()) && Arrays.equals(m.getArgumentTypes(), method.getArgumentTypes())) {
          found = true;
          forbiddenMethods.put(c.reader.getClassName() + '\000' + m, printout);
          // don't break when found, as there may be more covariant overrides!
        }
      }
      if (!found) {
        throw new ParseException("No method found with following signature: " + signature);
      }
    } else if (field != null) {
      assert method == null;
      if (!c.fields.contains(field)) {
        throw new ParseException("No field found with following name: " + signature);
      }
      forbiddenFields.put(c.reader.getClassName() + '\000' + field, printout);
    } else {
      assert field == null && method == null;
      // only add the signature as class name
      forbiddenClasses.put(c.reader.getClassName(), printout);
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
      in = this.getClass().getResourceAsStream("signatures/" + name + "-" + jdkTargetVersion + ".txt");
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

  private void parseSignaturesFile(Reader reader, boolean allowBundled) throws IOException,ParseException {
    final BufferedReader r = new BufferedReader(reader);
    try {
      String line, defaultMessage = null;
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
          } else {
            throw new ParseException("Invalid line in signature file: " + line);
          }
        } else {
          addSignature(line, defaultMessage);
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
    classesToCheck.put(reader.getClassName(), new ClassSignatureLookup(reader, false));
  }
  
  public final boolean hasNoSignatures() {
    return forbiddenMethods.isEmpty() && forbiddenClasses.isEmpty() && forbiddenFields.isEmpty() && (!internalRuntimeForbidden);
  }
  
  /** Parses a class and checks for valid method invocations */
  private int checkClass(final ClassReader reader) {
    final int[] violations = new int[1];
    reader.accept(new ClassVisitor(Opcodes.ASM4) {
      final String className = Type.getObjectType(reader.getClassName()).getClassName();
      String source = null;
      
      ClassSignatureLookup lookupRelatedClass(String internalName) {
        final Type type = Type.getObjectType(internalName);
        if (type.getSort() != Type.OBJECT) {
          return null;
        }
        ClassSignatureLookup c = classesToCheck.get(internalName);
        if (c == null) try {
          // use binary name, so we need to convert:
          c = getClassFromClassLoader(type.getClassName(), failOnMissingClasses);
          if (c == null) {
            logWarn(String.format(Locale.ENGLISH,
              "The referenced class '%s' cannot be loaded. Please fix the classpath!",
              type.getClassName()
            ));
          }
        } catch (ClassNotFoundException cnfe) {
          throw new WrapperRuntimeException(cnfe);
        }
        return c;
      }
      
      boolean checkClassUse(String internalName) {
        final String printout = forbiddenClasses.get(internalName);
        if (printout != null) {
          logError("Forbidden class/interface use: " + printout);
          return true;
        }
        if (internalRuntimeForbidden && (internalName.startsWith("sun/") || internalName.startsWith("com/sun/"))) {
          final String referencedClassName = Type.getObjectType(internalName).getClassName();
          final ClassSignatureLookup c = lookupRelatedClass(internalName);
          if (c == null || c.isRuntimeClass) {
            logError(String.format(Locale.ENGLISH,
              "Forbidden class/interface use: %s [non-public internal runtime class]",
              referencedClassName
            ));
            return true;
          }
        }
        return false;
      }

      private boolean checkClassDefinition(String superName, String[] interfaces) {
        if (superName != null) {
          if (checkClassUse(superName)) {
            return true;
          }
          final ClassSignatureLookup c = lookupRelatedClass(superName);
          if (c != null && checkClassDefinition(c.reader.getSuperName(), c.reader.getInterfaces())) {
            return true;
          }
        }
        if (interfaces != null) {
          for (String intf : interfaces) {
            if (checkClassUse(intf)) {
              return true;
            }
            final ClassSignatureLookup c = lookupRelatedClass(intf);
            if (c != null && checkClassDefinition(c.reader.getSuperName(), c.reader.getInterfaces())) {
              return true;
            }
          }
        }
        return false;
      }
      
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (checkClassDefinition(superName, interfaces)) {
          violations[0]++;
          logError("  in " + className + " (class declaration)");
        }
      }

      @Override
      public void visitSource(String source, String debug) {
        this.source = source;
      }
      
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM4) {
          private int lineNo = -1;
          
          private boolean checkMethodAccess(String owner, Method method) {
            if (checkClassUse(owner)) {
              return true;
            }
            final String printout = forbiddenMethods.get(owner + '\000' + method);
            if (printout != null) {
              logError("Forbidden method invocation: " + printout);
              return true;
            }
            final ClassSignatureLookup c = lookupRelatedClass(owner);
            if (c != null && !c.methods.contains(method)) {
              final String superName = c.reader.getSuperName();
              if (superName != null && checkMethodAccess(superName, method)) {
                return true;
              }
              final String[] interfaces = c.reader.getInterfaces();
              if (interfaces != null) {
                for (String intf : interfaces) {
                  if (intf != null && checkMethodAccess(intf, method)) {
                    return true;
                  }
                }
              }
            }
            return false;
          }
          
          private boolean checkFieldAccess(String owner, String field) {
            if (checkClassUse(owner)) {
              return true;
            }
            final String printout = forbiddenFields.get(owner + '\000' + field);
            if (printout != null) {
              logError("Forbidden field access: " + printout);
              return true;
            }
            final ClassSignatureLookup c = lookupRelatedClass(owner);
            if (c != null && !c.fields.contains(field)) {
              final String superName = c.reader.getSuperName();
              if (superName != null && checkFieldAccess(superName, field)) {
                return true;
              }
              final String[] interfaces = c.reader.getInterfaces();
              if (interfaces != null) {
                for (String intf : interfaces) {
                  if (intf != null && checkFieldAccess(intf, field)) {
                    return true;
                  }
                }
              }
            }
            return false;
          }

          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (checkMethodAccess(owner, new Method(name, desc))) {
              violations[0]++;
              reportSourceAndLine();
            }
          }
          
          @Override
          public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (checkFieldAccess(owner, name)) {
             violations[0]++;
             reportSourceAndLine();
            }
          }

          private void reportSourceAndLine() {
            final StringBuilder sb = new StringBuilder("  in ").append(className);
            if (source != null && lineNo >= 0) {
              new Formatter(sb, Locale.ENGLISH).format(" (%s:%d)", source, lineNo).flush();
            }
            logError(sb.toString());
          }
          
          @Override
          public void visitLineNumber(int lineNo, Label start) {
            this.lineNo = lineNo;
          }
        };
      }
    }, ClassReader.SKIP_FRAMES);
    return violations[0];
  }
  
  public final void run() throws ForbiddenApiException {
    int errors = 0;
    try {
      for (final ClassSignatureLookup c : classesToCheck.values()) {
        errors += checkClass(c.reader);
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
