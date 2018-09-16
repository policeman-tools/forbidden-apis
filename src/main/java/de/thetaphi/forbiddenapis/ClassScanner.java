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

public final class ClassScanner extends ClassVisitor implements Constants {
  private final boolean forbidNonPortableRuntime;
  final RelatedClassLookup lookup;
  final List<ForbiddenViolation> violations = new ArrayList<ForbiddenViolation>();
  
  final Signatures forbiddenSignatures;
  
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
  
  @SuppressWarnings("deprecation")
  public ClassScanner(RelatedClassLookup lookup, Signatures forbiddenSignatures, final Pattern suppressAnnotations) {
    super(Opcodes.ASM7_EXPERIMENTAL);
    this.lookup = lookup;
    this.forbiddenSignatures = forbiddenSignatures;
    this.suppressAnnotations = suppressAnnotations;
    this.forbidNonPortableRuntime = forbiddenSignatures.isNonPortableRuntimeForbidden();
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
    return source;
  }
  
  String checkClassUse(Type type, String what, boolean deep, String origInternalName) {
    while (type.getSort() == Type.ARRAY) {
      type = type.getElementType();
    }
    final String printout = forbiddenSignatures.checkType(type);
    if (printout != null) {
      return String.format(Locale.ENGLISH, "Forbidden %s use: %s", what, printout);
    }
    if (deep && forbidNonPortableRuntime) {
      final String binaryClassName = type.getClassName();
      final ClassSignature c = lookup.lookupRelatedClass(type.getInternalName(), origInternalName);
      if (c != null && c.isRuntimeClass && !AsmUtils.isPortableRuntimeClass(binaryClassName)) {
        return String.format(Locale.ENGLISH,
          "Forbidden %s use: %s [non-portable or internal runtime class]",
          what, binaryClassName
        );
      }
    }
    return null;
  }
  
  String checkClassUse(String internalName, String what, String origInternalName) {
    return checkClassUse(Type.getObjectType(internalName), what, true, origInternalName);
  }
  
  private String checkClassDefinition(String origName, String superName, String[] interfaces) {
    if (superName != null) {
      String violation = checkClassUse(superName, "class", origName);
      if (violation != null) {
        return violation;
      }
      final ClassSignature c = lookup.lookupRelatedClass(superName, origName);
      if (c != null && (violation = checkClassDefinition(origName, c.superName, c.interfaces)) != null) {
        return violation;
      }
    }
    if (interfaces != null) {
      for (String intf : interfaces) {
        String violation = checkClassUse(intf, "interface", origName);
        if (violation != null) {
          return violation;
        }
        final ClassSignature c = lookup.lookupRelatedClass(intf, origName);
        if (c != null && (violation = checkClassDefinition(origName, c.superName, c.interfaces)) != null) {
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
          final String internalName = type.getInternalName();
          violation = checkClassUse(type, "class/interface", true, internalName);
          if (violation != null) {
            return violation;
          }
          final ClassSignature c = lookup.lookupRelatedClass(internalName, internalName);
          if (c == null) return null;
          return checkClassDefinition(internalName, c.superName, c.interfaces);
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
    return checkClassUse(type, "annotation", visible, type.getInternalName());
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
    reportClassViolation(checkClassDefinition(name, superName, interfaces), "class declaration");
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
  
  @SuppressWarnings("deprecation")
  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, String signature, Object value) {
    currentGroupId++;
    if (classSuppressed) {
      return null;
    }
    return new FieldVisitor(Opcodes.ASM7_EXPERIMENTAL) {
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
  
  @SuppressWarnings("deprecation")
  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, String signature, String[] exceptions) {
    currentGroupId++;
    if (classSuppressed) {
      return null;
    }
    return new MethodVisitor(Opcodes.ASM7_EXPERIMENTAL) {
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
        String violation = checkClassUse(owner, "class/interface", owner);
        if (violation != null) {
          return violation;
        }
        if  (CLASS_CONSTRUCTOR_METHOD_NAME.equals(method.getName())) {
          // we don't check for violations on class constructors
          return null;
        }
        return checkMethodAccessRecursion(owner, method, true, owner);
      }
      
      private String checkMethodAccessRecursion(String owner, Method method, boolean checkClassUse, String origOwner) {
        String printout = forbiddenSignatures.checkMethod(owner, method);
        if (printout != null) {
          return "Forbidden method invocation: " + printout;
        }
        final ClassSignature c = lookup.lookupRelatedClass(owner, origOwner);
        if (c != null) {
          if (c.signaturePolymorphicMethods.contains(method.getName())) {
            // convert the invoked descriptor to a signature polymorphic one for the lookup
            final Method lookupMethod = new Method(method.getName(), SIGNATURE_POLYMORPHIC_DESCRIPTOR);
            printout = forbiddenSignatures.checkMethod(owner, lookupMethod);
            if (printout != null) {
              return "Forbidden method invocation: " + printout;
            }
          }
          String violation;
          if (checkClassUse && c.methods.contains(method)) {
            violation = checkClassUse(owner, "class/interface", origOwner);
            if (violation != null) {
              return violation;
            }
          }
          if (CONSTRUCTOR_METHOD_NAME.equals(method.getName())) {
            return null; // don't look into superclasses or interfaces to find constructors!
          }
          if (c.superName != null && (violation = checkMethodAccessRecursion(c.superName, method, true, origOwner)) != null) {
            return violation;
          }
          // JVM spec says: interfaces after superclasses
          if (c.interfaces != null) {
            for (String intf : c.interfaces) {
              // for interfaces we don't check the class use (it is too strict, if just the interface is implemented, but nothing more!):
              if (intf != null && (violation = checkMethodAccessRecursion(intf, method, false, origOwner)) != null) {
                return violation;
              }
            }
          }
        }
        return null;
      }
      
      private String checkFieldAccess(String owner, String field) {
        return checkFieldAccessRecursion(owner, field, owner);
      }
      
      private String checkFieldAccessRecursion(String owner, String field, String origOwner) {
        String violation = checkClassUse(owner, "class/interface", origOwner);
        if (violation != null) {
          return violation;
        }
        final String printout = forbiddenSignatures.checkField(owner, field);
        if (printout != null) {
          return "Forbidden field access: " + printout;
        }
        final ClassSignature c = lookup.lookupRelatedClass(owner, origOwner);
        // if we have seen the field already, no need to look into superclasses (fields cannot override)
        if (c != null && !c.fields.contains(field)) {
          if (c.interfaces != null) {
            for (String intf : c.interfaces) {
              if (intf != null && (violation = checkFieldAccessRecursion(intf, field, origOwner)) != null) {
                return violation;
              }
            }
          }
          // JVM spec says: superclasses after interfaces
          if (c.superName != null && (violation = checkFieldAccessRecursion(c.superName, field, origOwner)) != null) {
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