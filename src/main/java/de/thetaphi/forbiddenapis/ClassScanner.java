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
import java.util.HashMap;
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
  String internalName = null;
  int currentGroupId = 0;
  
  // Mapping from a (possible) lambda Method to groupId of declaring method
  final Map<Method,Integer> lambdas = new HashMap<Method,Integer>();
  
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
  
  String checkClassUse(String internalName, String what) {
    final String printout = forbiddenClasses.get(internalName);
    if (printout != null) {
      return String.format(Locale.ENGLISH, "Forbidden %s use: %s", what, printout);
    }
    if (internalRuntimeForbidden) {
      final String referencedClassName = Type.getObjectType(internalName).getClassName();
      if (isInternalClass(referencedClassName)) {
        final ClassSignature c = lookup.lookupRelatedClass(internalName);
        if (c == null || c.isRuntimeClass) {
          return String.format(Locale.ENGLISH,
            "Forbidden %s use: %s [non-public internal runtime class]",
            what, referencedClassName
          );
        }
      }
    }
    return null;
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
          violation = checkClassUse(type.getInternalName(), "class/interface");
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
  
  String checkAnnotationDescriptor(String desc, boolean visible) {
    final Type type = Type.getType(desc);
    if (type.getSort() != Type.OBJECT) {
      // should never happen for annotations!
      throw new IllegalArgumentException("Annotation descriptor '" + desc + "' has wrong sort: " + type.getSort());
    }
    if (visible) {
      // for visible annotations, we don't need to look into super-classes, interfaces,...
      // -> we just check if its disallowed or internal runtime!
      return checkClassUse(type.getInternalName(), "annotation");
    } else {
      // if annotation is not visible at runtime, we don't do deep checks (not
      // even internal runtime checks), just lookup in forbidden classes list!
      // The reason for this is: They may not be available in classpath at all!!!
      final String printout = forbiddenClasses.get(type.getInternalName());
      if (printout != null) {
        return "Forbidden annotation use: " + printout;
      }
    }
    return null;
  }
  
  private void reportClassViolation(String violation, String where) {
    if (violation != null) {
      violations.add(new ForbiddenViolation(currentGroupId, violation, where, -1));
    }
  }
  
  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    this.internalName = name;
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
    reportClassViolation(checkAnnotationDescriptor(desc, visible), "annotation on class declaration");
    return null;
  }
  
  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
    reportClassViolation(checkAnnotationDescriptor(desc, visible), "type annotation on class declaration");
    return null;
  }
  
  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, String signature, Object value) {
    currentGroupId++;
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
        reportFieldViolation(checkAnnotationDescriptor(desc, visible), "annotation on field declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        reportFieldViolation(checkAnnotationDescriptor(desc, visible), "type annotation on field declaration");
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
          reportMethodViolation(checkType(DEPRECATED_TYPE), "deprecation on method declaration");
        }
      }
      
      private String checkMethodAccess(String owner, Method method) {
        String violation = checkClassUse(owner, "class/interface");
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
        String violation = checkClassUse(owner, "class/interface");
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
            final Method m = new Method(handle.getName(), handle.getDesc());
            if (handle.getOwner().equals(internalName) && handle.getName().startsWith("lambda$")) {
              // as described in <http://cr.openjdk.java.net/~briangoetz/lambda/lambda-translation.html>,
              // we will record this metafactory call as "lamda" invokedynamic,
              // so we can assign the called lambda with the same groupId like *this* method:
              lambdas.put(m, currentGroupId);
            }
            return checkMethodAccess(handle.getOwner(), m);
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
        reportMethodViolation(checkAnnotationDescriptor(desc, visible), "annotation on method declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(desc, visible), "parameter annotation on method declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(desc, visible), "type annotation on method declaration");
        return null;
      }

      @Override
      public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(desc, visible), "annotation in method body");
        return null;
      }

      @Override
      public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(desc, visible), "annotation in method body");
        return null;
      }
      
      @Override
      public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        reportMethodViolation(checkAnnotationDescriptor(desc, visible), "annotation in method body");
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
    // sort the violations by group id and later by line number:
    Collections.sort(violations);
    done = true;
  }
  
}