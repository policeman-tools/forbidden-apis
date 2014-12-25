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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

  private final boolean internalRuntimeForbidden;
  final RelatedClassLookup lookup;
  final List<ForbiddenViolation> violations = new ArrayList<ForbiddenViolation>();
  
  // key is the internal name (slashed), followed by \000 and the field name:
  final Map<String,String> forbiddenFields;
  // key is the internal name (slashed), followed by \000 and the method signature:
  final Map<String,String> forbiddenMethods;
  // key is the internal name (slashed):
  final Map<String,String> forbiddenClasses;
  
  private String source = null;
  private boolean isDeprecated = false;
  private boolean done = false;
  
  public ClassScanner(RelatedClassLookup lookup,
      final Map<String,String> forbiddenClasses, Map<String,String> forbiddenMethods, Map<String,String> forbiddenFields,
      boolean internalRuntimeForbidden) {
    super(Opcodes.ASM5);
    this.lookup = lookup;
    this.forbiddenClasses = forbiddenClasses;
    this.forbiddenMethods = forbiddenMethods;
    this.forbiddenFields = forbiddenFields;
    this.internalRuntimeForbidden = internalRuntimeForbidden;
  }
  
  private void checkDone() {
    if (done) return;
    throw new IllegalStateException("Class not fully scanned.");
  }
  
  public List<ForbiddenViolation> getSortedViolations() {
    checkDone();
    return Collections.unmodifiableList(violations);
  }
  
  public String getSourceFile() {
    checkDone();
    return source;
  }
  
  private boolean isInternalClass(String className) {
    return className.startsWith("sun.") || className.startsWith("com.sun.") || className.startsWith("com.oracle.") || className.startsWith("jdk.")  || className.startsWith("sunw.");
  }
  
  String checkClassUse(String internalName) {
    final String printout = forbiddenClasses.get(internalName);
    if (printout != null) {
      return "Forbidden class/interface/annotation use: " + printout;
    }
    if (internalRuntimeForbidden) {
      final String referencedClassName = Type.getObjectType(internalName).getClassName();
      if (isInternalClass(referencedClassName)) {
        final ClassSignature c = lookup.lookupRelatedClass(internalName);
        if (c == null || c.isRuntimeClass) {
          return String.format(Locale.ENGLISH,
            "Forbidden class/interface/annotation use: %s [non-public internal runtime class]",
            referencedClassName
          );
        }
      }
    }
    return null;
  }
  
  private String checkClassDefinition(String superName, String[] interfaces) {
    if (superName != null) {
      String violation = checkClassUse(superName);
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
        String violation = checkClassUse(intf);
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
          violation = checkClassUse(type.getInternalName());
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
              if (nl) sb.append('\n');
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
  
  private void reportClassViolation(String violation, String where) {
    if (violation != null) {
      violations.add(new ForbiddenViolation(violation, where, -1));
    }
  }
  
  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    this.isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
    reportClassViolation(checkClassDefinition(superName, interfaces), "class declaration");
    if (this.isDeprecated) {
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
    if (visible) {
      reportClassViolation(checkDescriptor(desc), "annotation on class declaration");
    }
    return null;
  }
  
  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
    if (visible) reportClassViolation(checkDescriptor(desc), "type annotation on class declaration");
    return null;
  }
  
  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, String signature, Object value) {
    return new FieldVisitor(Opcodes.ASM5) {
      final boolean isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
      {
        // only check signature, if field is not synthetic
        if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
          reportFieldViolation(checkDescriptor(desc), "field declaration");
        }
        if (this.isDeprecated) {
          reportFieldViolation(checkType(DEPRECATED_TYPE), "deprecation on field declaration");
        }
      }
      
      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (this.isDeprecated && DEPRECATED_DESCRIPTOR.equals(desc)) {
          // don't report 2 times!
          return null;
        }
        if (visible) {
          reportFieldViolation(checkDescriptor(desc), "annotation on field declaration");
        }
        return null;
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        if (visible) reportFieldViolation(checkDescriptor(desc), "type annotation on field declaration");
        return null;
      }
      
      private void reportFieldViolation(String violation, String where) {
        if (violation != null) {
          violations.add(new ForbiddenViolation(violation, String.format(Locale.ENGLISH, "%s of '%s'", where, name), -1));
        }
      }
    };
  }
  
  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, String signature, String[] exceptions) {
    return new MethodVisitor(Opcodes.ASM5) {
      final boolean isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
      private int lineNo = -1;
      
      {
        // only check signature, if method is not synthetic
        if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
          reportMethodViolation(checkDescriptor(desc), "method declaration");
        }
        if (this.isDeprecated) {
          reportMethodViolation(checkType(DEPRECATED_TYPE), "deprecation on method declaration");
        }
      }
      
      private String checkMethodAccess(String owner, Method method) {
        String violation = checkClassUse(owner);
        if (violation != null) {
          return violation;
        }
        final String printout = forbiddenMethods.get(owner + '\000' + method);
        if (printout != null) {
          return "Forbidden method invocation: " + printout;
        }
        final ClassSignature c = lookup.lookupRelatedClass(owner);
        if (c != null && !c.methods.contains(method)) {
          if (c.superName != null && (violation = checkMethodAccess(c.superName, method)) != null) {
            return violation;
          }
          // JVM spec says: interfaces after superclasses
          if (c.interfaces != null) {
            for (String intf : c.interfaces) {
              if (intf != null && (violation = checkMethodAccess(intf, method)) != null) {
                return violation;
              }
            }
          }
        }
        return null;
      }
      
      private String checkFieldAccess(String owner, String field) {
        String violation = checkClassUse(owner);
        if (violation != null) {
          return violation;
        }
        final String printout = forbiddenFields.get(owner + '\000' + field);
        if (printout != null) {
          return "Forbidden field access: " + printout;
        }
        final ClassSignature c = lookup.lookupRelatedClass(owner);
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

      private String checkHandle(Handle handle) {
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
            return checkMethodAccess(handle.getOwner(), new Method(handle.getName(), handle.getDesc()));
        }
        return null;
      }
      
      private String checkConstant(Object cst) {
        if (cst instanceof Type) {
          return checkType((Type) cst);
        } else if (cst instanceof Handle) {
          return checkHandle((Handle) cst);
        }
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
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (this.isDeprecated && DEPRECATED_DESCRIPTOR.equals(desc)) {
          // don't report 2 times!
          return null;
        }
        if (visible) {
          reportMethodViolation(checkDescriptor(desc), "annotation on method declaration");
        }
        return null;
      }

      @Override
      public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
        if (visible) reportMethodViolation(checkDescriptor(desc), "parameter annotation on method declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        if (visible) reportMethodViolation(checkDescriptor(desc), "type annotation on method declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        if (visible) reportMethodViolation(checkDescriptor(desc), "annotation in method body");
        return null;
      }

      @Override
      public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
        if (visible) reportMethodViolation(checkDescriptor(desc), "annotation in method body");
        return null;
      }
      
      @Override
      public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        if (visible) reportMethodViolation(checkDescriptor(desc), "annotation in method body");
        return null;
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
        reportMethodViolation(checkConstant(cst), "method body");
      }
      
      @Override
      public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
        reportMethodViolation(checkHandle(bsm), "method body");
        for (final Object cst : bsmArgs) {
          reportMethodViolation(checkConstant(cst), "method body");
        }
      }
      
      private String getHumanReadableMethodSignature() {
        final Type[] args = Type.getType(desc).getArgumentTypes();
        final StringBuilder sb = new StringBuilder(name).append('(');
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
          violations.add(new ForbiddenViolation(violation, String.format(Locale.ENGLISH, "%s of '%s'", where, getHumanReadableMethodSignature()), lineNo));
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
    // don't sort yet, needs more work: Collections.sort(violations);
    done = true;
  }
  
}