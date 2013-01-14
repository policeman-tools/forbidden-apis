package de.thetaphi.forbiddenapis;

/*
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

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
  final ClassLoader loader;
    
  final Map<String,ClassSignatureLookup> classesToCheck = new HashMap<String,ClassSignatureLookup>();
  final Map<String,ClassSignatureLookup> classpathClassCache = new HashMap<String,ClassSignatureLookup>();
  
  final Map<String,String> forbiddenFields = new HashMap<String,String>();
  final Map<String,String> forbiddenMethods = new HashMap<String,String>();
  final Map<String,String> forbiddenClasses = new HashMap<String,String>();
  
  protected abstract void logError(String msg);
  protected abstract void logInfo(String msg);
  
  public Checker(ClassLoader loader) {
    this.loader = loader;
    this.start = System.currentTimeMillis();
  
    // check if we can load runtime classes (e.g. java.lang.String).
    // If this fails, we have a newer Java version than ASM supports:
    boolean b;
    try {
      getClassFromClassLoader(String.class.getName());
      b = true;
    } catch (IllegalArgumentException iae) {
      b = false;
    } catch (ClassNotFoundException cnfe) {
      throw new Error("FATAL PROBLEM: Cannot find java.lang.String on classpath.");
    }
    this.isSupportedJDK = b;
  }
  
  /** Reads a class (binary name) from the given {@link ClassLoader}. */
  public ClassSignatureLookup getClassFromClassLoader(final String clazz) throws ClassNotFoundException {
    ClassSignatureLookup c = classpathClassCache.get(clazz);
    if (c == null) {
      try {
        final InputStream in = loader.getResourceAsStream(clazz.replace('.', '/') + ".class");
        if (in == null) {
          throw new ClassNotFoundException("Loading of class " + clazz + " failed: Not found");
        }
        try {
          classpathClassCache.put(clazz, c = new ClassSignatureLookup(new ClassReader(in)));
        } finally {
          in.close();
        }
      } catch (IOException ioe) {
        throw new ClassNotFoundException("Loading of class " + clazz + " failed.", ioe);
      }
    }
    return c;
  }
 
  /** Adds the method signature to the list of disallowed methods. The Signature is checked against the given ClassLoader. */
  private void addSignature(final String signature) throws ParseException {
    final String clazz, field;
    final Method method;
    int p = signature.indexOf('#');
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
    // check class & method/field signature, if it is really existent (in classpath), but we don't really load the class into JVM:
    final ClassSignatureLookup c;
    try {
      c = getClassFromClassLoader(clazz);
    } catch (ClassNotFoundException cnfe) {
      throw new ParseException("Class not found on classpath: " + cnfe.getMessage());
    }
    if (method != null) {
      assert field == null;
      // list all methods with this signature:
      boolean found = false;
      for (final Method m : c.methods) {
        if (m.getName().equals(method.getName()) && Arrays.equals(m.getArgumentTypes(), method.getArgumentTypes())) {
          found = true;
          forbiddenMethods.put(c.reader.getClassName() + '\000' + m, signature);
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
      forbiddenFields.put(c.reader.getClassName() + '\000' + field, signature);
    } else {
      assert field == null && method == null;
      // only add the signature as class name
      forbiddenClasses.put(c.reader.getClassName(), signature);
    }
  }

  /** Reads a list of bundled API signatures from classpath. */
  public void parseBundledSignatures(String name) throws IOException,ParseException {
    if (!name.matches("[A-Za-z0-9\\-\\.]+")) {
      throw new ParseException("Invalid bundled signature reference: " + name);
    }
    final InputStream in = this.getClass().getResourceAsStream("signatures/" + name + ".txt");
    if (in == null) {
      throw new FileNotFoundException("Bundled signatures resource not found: " + name);
    }
    parseSignaturesFile(in, true);
  }
  
  /** Reads a list of API signatures. Closes the Reader when done (on Exception, too)! */
  public void parseSignaturesFile(InputStream in) throws IOException,ParseException {
    parseSignaturesFile(in, false);
  }
  
  /** Reads a list of API signatures. Closes the Reader when done (on Exception, too)! */
  public  void parseSignaturesString(String signatures) throws IOException,ParseException {
    parseSignaturesFile(new StringReader(signatures), false);
  }
  
  private void parseSignaturesFile(InputStream in, boolean allowBundled) throws IOException,ParseException {
    parseSignaturesFile(new InputStreamReader(in, "UTF-8"), allowBundled);
  }

  private static final String BUNDLED_PREFIX = "@includeBundled ";

  private void parseSignaturesFile(Reader reader, boolean allowBundled) throws IOException,ParseException {
    final BufferedReader r = new BufferedReader(reader);
    try {
      String line;
      while ((line = r.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#"))
          continue;
        if (line.startsWith("@")) {
          if (allowBundled && line.startsWith(BUNDLED_PREFIX)) {
            final String name = line.substring(BUNDLED_PREFIX.length()).trim();
            parseBundledSignatures(name);
          } else {
            throw new ParseException("Invalid line in signature file: " + line);
          }
        } else {
          addSignature(line);
        }
      }
    } finally {
      r.close();
    }
  }
  
  /** Parses and adds a class from the given stream to the list of classes to check. Closes the stream when parsed (on Exception, too)! */
  public void addClassToCheck(final InputStream in) throws IOException {
    final ClassReader reader;
    try {
      reader = new ClassReader(in);
    } finally {
      in.close();
    }
    classesToCheck.put(reader.getClassName(), new ClassSignatureLookup(reader));
  }
  
  public boolean hasNoSignatures() {
    return forbiddenMethods.isEmpty() && forbiddenClasses.isEmpty();
  }
  
  /** Parses a class and checks for valid method invocations */
  public int checkClass(final ClassReader reader) {
    final int[] violations = new int[1];
    reader.accept(new ClassVisitor(Opcodes.ASM4) {
      final String className = Type.getObjectType(reader.getClassName()).getClassName();
      String source = null;
      
      @Override
      public void visitSource(String source, String debug) {
        this.source = source;
      }
      
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM4) {
          private int lineNo = -1;
          
          private ClassSignatureLookup lookupRelatedClass(String internalName) {
            ClassSignatureLookup c = classesToCheck.get(internalName);
            if (c == null) try {
              c = getClassFromClassLoader(internalName);
            } catch (ClassNotFoundException cnfe) {
              // we ignore lookup errors and simply ignore this related class
              c = null;
            }
            return c;
          }
          
          private boolean checkClassUse(String owner) {
            final String printout = forbiddenClasses.get(owner);
            if (printout != null) {
              logError("Forbidden class use: " + printout);
              return true;
            }
            return false;
          }
          
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
  
  public void run() throws ForbiddenApiException {
    int errors = 0;
    for (final ClassSignatureLookup c : classesToCheck.values()) {
      errors += checkClass(c.reader);
    }
    
    final String message = String.format(Locale.ENGLISH, 
        "Scanned %d (and %d related) class file(s) for forbidden API invocations (in %.2fs), %d error(s).",
        classesToCheck.size(), classpathClassCache.size(), (System.currentTimeMillis() - start) / 1000.0, errors);
    if (errors > 0) {
      logError(message);
      throw new ForbiddenApiException("Check for forbidden API calls failed, see log.");
    } else {
      logInfo(message);
    }
  }
  
}
