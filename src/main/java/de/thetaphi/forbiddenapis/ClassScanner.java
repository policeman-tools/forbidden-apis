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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.Method;

final class ClassScanner extends ClassVisitor {
  static final Type DEPRECATED_TYPE = Type.getType(Deprecated.class);
  static final String DEPRECATED_DESCRIPTOR = DEPRECATED_TYPE.getDescriptor();
  
  static final String LAMBDA_META_FACTORY_INTERNALNAME = "java/lang/invoke/LambdaMetafactory";
  static final String LAMBDA_METHOD_NAME_PREFIX = "lambda$";
  static final String CLASS_CONSTRUCTOR_METHOD_NAME = "<clinit>";
  static final String CONSTRUCTOR_METHOD_NAME = "<init>";

  private final boolean forbidNonPortableRuntime;
  final RelatedClassLookup lookup;
  final List<ForbiddenViolation> violations = new ArrayList<ForbiddenViolation>();
  
  // key is the internal name (slashed), followed by \000 and the field name:
  final Map<String,String> forbiddenFields;
  // key is the internal name (slashed), followed by \000 and the method signature:
  final Map<String,String> forbiddenMethods;
  // key is the internal name (slashed):
  final Map<String,String> forbiddenClasses;
  // key is pattern to binary class name:
  final Iterable<ClassPatternRule> forbiddenClassPatterns;
  // pattern that matches binary (dotted) class name of all annotations that suppress:
  final Pattern suppressAnnotations;
  
  private String source = null;
  private boolean isDeprecated = false;
  private boolean done = false;
  String internalMainClassName = null;
  int currentGroupId = 0;
  
  // Mapping from a (possible) lambda Method to groupId of declaring method
  final Map<Method,Integer> lambdas = new HashMap<Method,Integer>();
  
  // all groups that were disabled due to suppressing annotation
  final BitSet suppressedGroups = new BitSet();
  boolean classSuppressed = false;
  
  public ClassScanner(RelatedClassLookup lookup,
      final Map<String,String> forbiddenClasses, final Iterable<ClassPatternRule> forbiddenClassPatterns,
      final Map<String,String> forbiddenMethods, final Map<String,String> forbiddenFields,
      final Pattern suppressAnnotations,
      final boolean forbidNonPortableRuntime) {
    super(Opcodes.ASM5);
    this.lookup = lookup;
    this.forbiddenClasses = forbiddenClasses;
    this.forbiddenClassPatterns = forbiddenClassPatterns;
    this.forbiddenMethods = forbiddenMethods;
    this.forbiddenFields = forbiddenFields;
    this.suppressAnnotations = suppressAnnotations;
    this.forbidNonPortableRuntime = forbidNonPortableRuntime;
  }
  
  private void checkDone() {
    if (done) return;
    throw new IllegalStateException("Class not fully scanned.");
  }
  
  public List<ForbiddenViolation> getSortedViolations() {
    checkDone();
    return classSuppressed ? Collections.<ForbiddenViolation>emptyList() : Collections.unmodifiableList(violations);
  }
  
  public String getSourceFile() {
    checkDone();
    return source;
  }
  
  String checkClassUse(Type type, String what, boolean deep) {
    while (type.getSort() == Type.ARRAY) {
      type = type.getElementType();
    }
    if (type.getSort() != Type.OBJECT) {
      return null; // we don't know this type, just pass!
    }
    final String internalName = type.getInternalName();
    final String printout = forbiddenClasses.get(internalName);
    if (printout != null) {
      return String.format(Locale.ENGLISH, "Forbidden %s use: %s", what, printout);
    }
    final String binaryClassName = type.getClassName();
    for (final ClassPatternRule r : forbiddenClassPatterns) {
      if (r.matches(binaryClassName)) {
        return String.format(Locale.ENGLISH, "Forbidden %s use: %s", what, r.getPrintout(binaryClassName));
      }
    }
    if (deep && forbidNonPortableRuntime) {
      final ClassSignature c = lookup.lookupRelatedClass(internalName);
      if (c != null && c.isRuntimeClass && !AsmUtils.isPortableRuntimeClass(binaryClassName)) {
        return String.format(Locale.ENGLISH,
          "Forbidden %s use: %s [non-portable or internal runtime class]",
          what, binaryClassName
        );
      }
    }
    return null;
  }
  
  String checkClassUse(String internalName, String what) {
    return checkClassUse(Type.getObjectType(internalName), what, true);
  }
  
  private String checkClassDefinition(String superName, String[] interfaces) {
    if (superName != null) {
      String violation = checkClassUse(superName, "class");
      if (violation != null) {
        return violation;
      }
      final ClassSignature c = lookup.lookupRelatedClass(superName);
      if (c != null && (violation = checkClassDefinition(c.superName, c.interfaces)) != null) {
        return violation;
      }
    }
    if (interfaces != null) {
      for (String intf : interfaces) {
        String violation = checkClassUse(intf, "interface");
        if (violation != null) {
          return violation;
        }
        final ClassSignature c = lookup.lookupRelatedClass(intf);
        if (c != null && (violation = checkClassDefinition(c.superName, c.interfaces)) != null) {
          return violation;
        }
      }
    }
    return null;
  }
  
  String checkType(Type type) {
    while (type != null) {
      String violation;
      switch (type.getSort()) {
        case Type.OBJECT:
          violation = checkClassUse(type, "class/interface", true);
          if (violation != null) {
            return violation;
          }
          final ClassSignature c = lookup.lookupRelatedClass(type.getInternalName());
          if (c == null) return null;
          return checkClassDefinition(c.superName, c.interfaces);
        case Type.ARRAY:
          type = type.getElementType();
          break;
        case Type.METHOD:
          final ArrayList<String> violations = new ArrayList<String>();
          violation = checkType(type.getReturnType());
          if (violation != null) {
            violations.add(violation);
          }
          for (final Type t : type.getArgumentTypes()) {
            violation = checkType(t);
            if (violation != null) {
              violations.add(violation);
            }
          }
          if (violations.isEmpty()) {
            return null;
          } else if (violations.size() == 1) {
            return violations.get(0);
          } else {
            final StringBuilder sb = new StringBuilder();
            boolean nl = false;
            for (final String v : violations) {
              if (nl) sb.append(ForbiddenViolation.SEPARATOR);
              sb.append(v);
              nl = true;
            }
            return sb.toString();
          }
        default:
          return null;
      }
    }
    return null;
  }
  
  String checkDescriptor(String desc) {
    return checkType(Type.getType(desc));
  }
  
  String checkAnnotationDescriptor(Type type, boolean visible) {
    // for annotations, we don't need to look into super-classes, interfaces,...
    // -> we just check if its disallowed or internal runtime (only if visible)!
    return checkClassUse(type, "annotation", visible);
  }
  
  void maybeSuppressCurrentGroup(Type annotation) {
    if (suppressAnnotations.matcher(annotation.getClassName()).matches()) {
      suppressedGroups.set(currentGroupId);
    }
  }
  
  private void reportClassViolation(String violation, String where) {
    if (violation != null) {
      violations.add(new ForbiddenViolation(currentGroupId, violation, where, -1));
    }
  }
  
  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    this.internalMainClassName = name;
    this.isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
    reportClassViolation(checkClassDefinition(superName, interfaces), "class declaration");
    if (this.isDeprecated) {
      classSuppressed |= suppressAnnotations.matcher(DEPRECATED_TYPE.getClassName()).matches();
      reportClassViolation(checkType(DEPRECATED_TYPE), "deprecation on class declaration");
    }
  }
  
  @Override
  public void visitSource(String source, String debug) {
    this.source = source;
  }
  
  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    if (this.isDeprecated && DEPRECATED_DESCRIPTOR.equals(desc)) {
      // don't report 2 times!
      return null;
    }
    final Type type = Type.getType(desc);
    classSuppressed |= suppressAnnotations.matcher(type.getClassName()).matches();
    reportClassViolation(checkAnnotationDescriptor(type, visible), "annotation on class declaration");
    return null;
  }
  
  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
    reportClassViolation(checkAnnotationDescriptor(Type.getType(desc), visible), "type annotation on class declaration");
    return null;
  }
  
  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, String signature, Object value) {
    currentGroupId++;
    if (classSuppressed) {
      return null;
    }
    return new FieldVisitor(Opcodes.ASM5) {
      final boolean isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
      {
        // only check signature, if field is not synthetic
        if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
          reportFieldViolation(checkDescriptor(desc), "field declaration");
        }
        if (this.isDeprecated) {
          maybeSuppressCurrentGroup(DEPRECATED_TYPE);
          reportFieldViolation(checkType(DEPRECATED_TYPE), "deprecation on field declaration");
        }
      }
      
      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (this.isDeprecated && DEPRECATED_DESCRIPTOR.equals(desc)) {
          // don't report 2 times!
          return null;
        }
        final Type type = Type.getType(desc);
        maybeSuppressCurrentGroup(type);
        reportFieldViolation(checkAnnotationDescriptor(type, visible), "annotation on field declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        reportFieldViolation(checkAnnotationDescriptor(Type.getType(desc), visible), "type annotation on field declaration");
        return null;
      }
      
      private void reportFieldViolation(String violation, String where) {
        if (violation != null) {
          violations.add(new ForbiddenViolation(currentGroupId, violation, String.format(Locale.ENGLISH, "%s of '%s'", where, name), -1));
        }
      }
    };
  }
  
  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, String signature, String[] exceptions) {
    currentGroupId++;
    if (classSuppressed) {
      return null;
    }
    return new MethodVisitor(Opcodes.ASM5) {
      private final Method myself = new Method(name, desc);
      private final boolean isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
      private int lineNo = -1;
      
      {
        // only check signature, if method is not synthetic
        if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
          reportMethodViolation(checkDescriptor(desc), "method declaration");
        }
        if (this.isDeprecated) {
          maybeSuppressCurrentGroup(DEPRECATED_TYPE);
          reportMethodViolation(checkType(DEPRECATED_TYPE), "deprecation on method declaration");
        }
      }
      
      private String checkMethodAccess(String owner, Method method) {
        String violation = checkClassUse(owner, "class/interface");
        if (violation != null) {
          return violation;
        }
        if  (CLASS_CONSTRUCTOR_METHOD_NAME.equals(method.getName())) {
          // we don't check for violations on class constructors
          return null;
        }
        return checkMethodAccessRecursion(owner, method, true);
      }
      
      private String checkMethodAccessRecursion(String owner, Method method, boolean checkClassUse) {
        final String printout = forbiddenMethods.get(owner + '\000' + method);
        if (printout != null) {
          return "Forbidden method invocation: " + printout;
        }
        final ClassSignature c = lookup.lookupRelatedClass(owner);
        if (c != null) {
          String violation;
          if (checkClassUse && c.methods.contains(method)) {
            violation = checkClassUse(owner, "class/interface");
            if (violation != null) {
              return violation;
            }
          }
          if (CONSTRUCTOR_METHOD_NAME.equals(method.getName())) {
            return null; // don't look into superclasses or interfaces to find constructors!
          }
          if (c.superName != null && (violation = checkMethodAccessRecursion(c.superName, method, true)) != null) {
            return violation;
          }
          // JVM spec says: interfaces after superclasses
          if (c.interfaces != null) {
            for (String intf : c.interfaces) {
              // for interfaces we don't check the class use (it is too strict, if just the interface is implemented, but nothing more!):
              if (intf != null && (violation = checkMethodAccessRecursion(intf, method, false)) != null) {
                return violation;
              }
            }
          }
        }
        return null;
      }
      
      private String checkFieldAccess(String owner, String field) {
        String violation = checkClassUse(owner, "class/interface");
        if (violation != null) {
          return violation;
        }
        final String printout = forbiddenFields.get(owner + '\000' + field);
        if (printout != null) {
          return "Forbidden field access: " + printout;
        }
        final ClassSignature c = lookup.lookupRelatedClass(owner);
        // if we have seen the field already, no need to look into superclasses (fields cannot override)
        if (c != null && !c.fields.contains(field)) {
          if (c.interfaces != null) {
            for (String intf : c.interfaces) {
              if (intf != null && (violation = checkFieldAccess(intf, field)) != null) {
                return violation;
              }
            }
          }
          // JVM spec says: superclasses after interfaces
          if (c.superName != null && (violation = checkFieldAccess(c.superName, field)) != null) {
            return violation;
          }
        }
        return null;
      }

      private String checkHandle(Handle handle, boolean checkLambdaHandle) {
        switch (handle.getTag()) {
          case Opcodes.H_GETFIELD:
          case Opcodes.H_PUTFIELD:
          case Opcodes.H_GETSTATIC:
          case Opcodes.H_PUTSTATIC:
            return checkFieldAccess(handle.getOwner(), handle.getName());
          case Opcodes.H_INVOKEVIRTUAL:
          case Opcodes.H_INVOKESTATIC:
          case Opcodes.H_INVOKESPECIAL:
          case Opcodes.H_NEWINVOKESPECIAL:
          case Opcodes.H_INVOKEINTERFACE:
            final Method m = new Method(handle.getName(), handle.getDesc());
            if (checkLambdaHandle && handle.getOwner().equals(internalMainClassName) && handle.getName().startsWith(LAMBDA_METHOD_NAME_PREFIX)) {
              // as described in <http://cr.openjdk.java.net/~briangoetz/lambda/lambda-translation.html>,
              // we will record this metafactory call as "lambda" invokedynamic,
              // so we can assign the called lambda with the same groupId like *this* method:
              lambdas.put(m, currentGroupId);
            }
            return checkMethodAccess(handle.getOwner(), m);
        }
        return null;
      }
      
      private String checkConstant(Object cst, boolean checkLambdaHandle) {
        if (cst instanceof Type) {
          return checkType((Type) cst);
        } else if (cst instanceof Handle) {
          return checkHandle((Handle) cst, checkLambdaHandle);
        }
        return null;
      }

      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (this.isDeprecated && DEPRECATED_DESCRIPTOR.equals(desc)) {
          // don't report 2 times!
          return null;
        }
        final Type type = Type.getType(desc);
        maybeSuppressCurrentGroup(type);
        reportMethodViolation(checkAnnotationDescriptor(type, visible), "annotation on method declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(Type.getType(desc), visible), "parameter annotation on method declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(Type.getType(desc), visible), "type annotation on method declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(Type.getType(desc), visible), "annotation in method body");
        return null;
      }

      @Override
      public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(Type.getType(desc), visible), "annotation in method body");
        return null;
      }
      
      @Override
      public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(Type.getType(desc), visible), "annotation in method body");
        return null;
      }
      
      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        reportMethodViolation(checkMethodAccess(owner, new Method(name, desc)), "method body");
      }
      
      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        reportMethodViolation(checkFieldAccess(owner, name), "method body");
      }
      
      @Override
      public void visitTypeInsn(int opcode, String type) {
        if (opcode == Opcodes.ANEWARRAY) {
          reportMethodViolation(checkType(Type.getObjectType(type)), "method body");
        }
      }
      
      @Override
      public void visitMultiANewArrayInsn(String desc, int dims) {
        reportMethodViolation(checkDescriptor(desc), "method body");
      }
      
      @Override
      public void visitLdcInsn(Object cst) {
        reportMethodViolation(checkConstant(cst, false), "method body");
      }
      
      @Override
      public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
        final boolean isLambdaMetaFactory = LAMBDA_META_FACTORY_INTERNALNAME.equals(bsm.getOwner());
        reportMethodViolation(checkHandle(bsm, false), "method body");
        for (final Object cst : bsmArgs) {
          reportMethodViolation(checkConstant(cst, isLambdaMetaFactory), "method body");
        }
      }
      
      private String getHumanReadableMethodSignature() {
        final Type[] args = Type.getType(myself.getDescriptor()).getArgumentTypes();
        final StringBuilder sb = new StringBuilder(myself.getName()).append('(');
        boolean comma = false;
        for (final Type t : args) {
          if (comma) sb.append(',');
          sb.append(t.getClassName());
          comma = true;
        }
        sb.append(')');
        return sb.toString();
      }

      private void reportMethodViolation(String violation, String where) {
        if (violation != null) {
          violations.add(new ForbiddenViolation(currentGroupId, myself, violation, String.format(Locale.ENGLISH, "%s of '%s'", where, getHumanReadableMethodSignature()), lineNo));
        }
      }
      
      @Override
      public void visitLineNumber(int lineNo, Label start) {
        this.lineNo = lineNo;
      }
    };
  }

  @Override
  public void visitEnd() {
    // fixup lambdas by assigning them the groupId where they were originally declared:
    for (final ForbiddenViolation v : violations) {
      if (v.targetMethod != null) {
        final Integer newGroupId = lambdas.get(v.targetMethod);
        if (newGroupId != null) {
          v.setGroupId(newGroupId.intValue());
        }
      }
    }
    // filter out suppressed groups
    if (!suppressedGroups.isEmpty()) {
      for (final Iterator<ForbiddenViolation> it = violations.iterator(); it.hasNext();) {
        final ForbiddenViolation v = it.next();
        if (suppressedGroups.get(v.getGroupId())) {
          it.remove();
        }
      }
    }
    // sort the violations by group id and later by line number:
    Collections.sort(violations);
    done = true;
  }
  
}