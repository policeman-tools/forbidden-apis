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
import java.util.Objects;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.Method;

public final class ClassScanner extends ClassVisitor implements Constants, Reporter {
  private final boolean forbidNonPortableRuntime;
  final ClassMetadata metadata;
  final RelatedClassLookup lookup;
  final List<ForbiddenViolation> violations = new ArrayList<>();
  
  final Signatures forbiddenSignatures;
  
  // pattern that matches binary (dotted) class name of all annotations that suppress:
  final Pattern suppressAnnotations;
  
  private String source = null;
  private boolean isDeprecated = false;
  private boolean done = false;
  int currentGroupId = 0;
  private String currentLocation;
  
  // Mapping from a (possible) lambda Method to groupId of declaring method
  final Map<Method,Integer> lambdas = new HashMap<>();
  
  // all groups that were disabled due to suppressing annotation
  final BitSet suppressedGroups = new BitSet();
  boolean classSuppressed = false;
  private final boolean failOnViolation;
  
  public ClassScanner(ClassMetadata metadata, RelatedClassLookup lookup, Signatures forbiddenSignatures, final Pattern suppressAnnotations, boolean failOnViolation) {
    super(Opcodes.ASM9);
    this.metadata = metadata;
    this.lookup = lookup;
    this.forbiddenSignatures = forbiddenSignatures;
    this.suppressAnnotations = suppressAnnotations;
    this.forbidNonPortableRuntime = forbiddenSignatures.isNonPortableRuntimeForbidden();
    this.failOnViolation = failOnViolation;
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
  
  AncestorVisitor newClassRelationAncestorVisitor(Reporter reporter) {
    return new AncestorVisitor(reporter, lookup) {
      @Override
      protected AncestorVisitorResult visit(ClassMetadata c, String origName, boolean isInterfaceOfAncestor, boolean previousInRuntime) {
        if (previousInRuntime && c.isNonPortableRuntime) {
          return AncestorVisitorResult.PROCEED; // something inside the JVM is extending internal class/interface
        }
        return checkClassUse(reporter, c.className, isInterfaceOfAncestor ? "interface" : "class", origName) ?
            AncestorVisitorResult.VIOLATION : AncestorVisitorResult.PROCEED; 
      }
    };
  }
  
  boolean checkClassUse(Reporter reporter, Type type, String what, boolean isAnnotation, String origInternalName) {
    while (type.getSort() == Type.ARRAY) {
      type = type.getElementType(); // unwrap array
    }
    if (type.getSort() != Type.OBJECT) {
      return false; // we don't know this type, just pass!
    }
    if (forbiddenSignatures.checkType(reporter, type, what)) return true;
    // try best to check for non portable runtime
    if (forbidNonPortableRuntime) try {
      final String binaryClassName = type.getClassName();
      final ClassMetadata c = lookup.lookupRelatedClass(type.getInternalName(), origInternalName);
      if (c != null && c.isNonPortableRuntime) {
        reporter.report(String.format(Locale.ENGLISH,
          "Forbidden %s use: %s [non-portable or internal runtime class]",
          what, binaryClassName), failOnViolation ? ViolationSeverity.ERROR : ViolationSeverity.WARNING);
        return true;
      }
    } catch (RelatedClassLoadingException e) {
      // only throw exception if it is not an annotation
      if (false == isAnnotation) throw e;
    }
    return false;
  }
  
  boolean checkClassUse(Reporter reporter, String internalName, String what, String origInternalName) {
    return checkClassUse(reporter, Type.getObjectType(internalName), what, false, origInternalName);
  }
  
  boolean checkType(Reporter reporter, Type type, boolean inspectMethodTypes) {
    while (type != null) {
      switch (type.getSort()) {
        case Type.OBJECT:
          final String internalName = type.getInternalName();
          if (checkClassUse(reporter, type, "class/interface", false, internalName)) return true;
          final ClassMetadata c = lookup.lookupRelatedClass(internalName, internalName);
          return (c == null) ? false : newClassRelationAncestorVisitor(reporter).visitAncestors(c, false, false);
        case Type.ARRAY:
          type = type.getElementType();
          break;
        case Type.METHOD:
          boolean result = false;
          if (inspectMethodTypes) {
            result |= checkType(reporter, type.getReturnType(), inspectMethodTypes);
            for (final Type t : type.getArgumentTypes()) {
              result |= checkType(reporter, t, inspectMethodTypes);
            }
          }
          return result;
        default:
          return false;
      }
    }
    return false;
  }
  
  boolean checkDescriptor(Reporter reporter, String desc, boolean inspectMethodTypes) {
    return checkType(reporter, Type.getType(desc), inspectMethodTypes);
  }
  
  boolean checkAnnotationDescriptor(Reporter reporter, Type type, boolean visible) {
    // for annotations, we don't need to look into super-classes, interfaces,...
    return checkClassUse(reporter, type, "annotation", true, type.getInternalName());
  }
  
  void maybeSuppressCurrentGroup(Type annotation) {
    if (suppressAnnotations.matcher(annotation.getClassName()).matches()) {
      suppressedGroups.set(currentGroupId);
    }
  }
  
  @Override
  public void location(String location) {
    this.currentLocation = location;
  }
  
  @Override
  public void report(String message, ViolationSeverity severity) {
    violations.add(new ForbiddenViolation(currentGroupId, message, currentLocation, -1, severity));
  }
  
  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    if (!Objects.equals(name, metadata.className)) {
      throw new AssertionError("Wrong class parsed: " + name);
    }
    this.isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
    location("class declaration");
    newClassRelationAncestorVisitor(this).visitAncestors(metadata, false, false);
    if (this.isDeprecated) {
      classSuppressed |= suppressAnnotations.matcher(DEPRECATED_TYPE.getClassName()).matches();
      location("deprecation on class declaration");
      checkType(this, DEPRECATED_TYPE, false);
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
    location("annotation on class declaration");
    checkAnnotationDescriptor(this, type, visible);
    return null;
  }
  
  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
    location("type annotation on class declaration");
    checkAnnotationDescriptor(this, Type.getType(desc), visible);
    return null;
  }
  
  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, String signature, Object value) {
    currentGroupId++;
    if (classSuppressed) {
      return null;
    }
    return new FieldScanner(access, name, desc);
  }
  
  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, String signature, String[] exceptions) {
    currentGroupId++;
    if (classSuppressed) {
      return null;
    }
    return new MethodScanner(access, name, desc);
  }

  @Override
  public RecordComponentVisitor visitRecordComponent(final String name, final String desc, final String signature) {
    currentGroupId++;
    if (classSuppressed) {
      return null;
    }
    return new RecordComponentScanner(name, desc);
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
  
  private final class RecordComponentScanner extends RecordComponentVisitor implements Reporter {
    private final String name;
    private String currentLocation;
    
    RecordComponentScanner(String name, String desc) {
      super(Opcodes.ASM9);
      this.name = name;
      location("record component declaration");
      checkDescriptor(this, desc, false);
    }
    
    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      final Type type = Type.getType(desc);
      maybeSuppressCurrentGroup(type);
      location("annotation on record component declaration");
      checkAnnotationDescriptor(this, type, visible);
      return null;
    }
    
    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      location("type annotation on record component declaration");
      checkAnnotationDescriptor(this, Type.getType(desc), visible);
      return null;
    }
    
    @Override
    public void location(String location) {
      this.currentLocation = location;
    }
    
    @Override
    public void report(String message, ViolationSeverity severity) {
      violations.add(new ForbiddenViolation(currentGroupId, message, String.format(Locale.ENGLISH, "%s of '%s'", currentLocation, name), -1, severity));
    }
  }

  private final class FieldScanner extends FieldVisitor implements Reporter {
    private final String name;
    private final boolean isDeprecated;
    private String currentLocation;
    
    FieldScanner(int access, String name, String desc) {
      super(Opcodes.ASM9);
      this.name = name;

      this.isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
      // only check signature, if field is not synthetic
      if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
        location("field declaration");
        checkDescriptor(this, desc, false);
      }
      if (this.isDeprecated) {
        maybeSuppressCurrentGroup(DEPRECATED_TYPE);
        location("deprecation on field declaration");
        checkType(this, DEPRECATED_TYPE, false);
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
      location("annotation on field declaration");
      checkAnnotationDescriptor(this, type, visible);
      return null;
    }
    
    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      location("type annotation on field declaration");
      checkAnnotationDescriptor(this, Type.getType(desc), visible);
      return null;
    }
    
    @Override
    public void location(String location) {
      this.currentLocation = location;
    }
    
    @Override
    public void report(String message, ViolationSeverity severity) {
      violations.add(new ForbiddenViolation(currentGroupId, message, String.format(Locale.ENGLISH, "%s of '%s'", currentLocation, name), -1, severity));
    }
  }

  private final class MethodScanner extends MethodVisitor implements Reporter {
    private final Method myself;
    private final boolean isDeprecated;
    private int lineNo = -1;
    private String currentLocation;

    MethodScanner(int access, String name, String desc) {
      super(Opcodes.ASM9);
      this.myself = new Method(name, desc);
      this.isDeprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
      
      // only check signature, if method is not synthetic
      if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
        location("method declaration");
        checkDescriptor(this, desc, true);
      }
      if (this.isDeprecated) {
        maybeSuppressCurrentGroup(DEPRECATED_TYPE);
        location("deprecation on method declaration");
        checkType(this, DEPRECATED_TYPE, false);
      }
    }
    
    private boolean checkMethodAccess(String owner, final Method method, final boolean callIsVirtual) {
      if  (CLASS_CONSTRUCTOR_METHOD_NAME.equals(method.getName())) {
        // we don't check for violations on class constructors
        return false;
      }
      if (checkClassUse(this, owner, "class/interface", owner)) {
        return true;
      }
      // do a quick check that works without a ClassSignature (a more thorough check is done later):
      if (forbiddenSignatures.checkMethod(this, owner, method)) {
        return true;
      }
      if (CONSTRUCTOR_METHOD_NAME.equals(method.getName())) {
        return false; // don't look into superclasses or interfaces to find constructors!
      }
      final ClassMetadata c = lookup.lookupRelatedClass(owner, owner);
      if (c == null) {
        return false;
      }
      return new AncestorVisitor(this, lookup) {
        @Override
        protected AncestorVisitorResult visit(ClassMetadata c, String origName, boolean isInterfaceOfAncestor, boolean previousInRuntime) {
          final Method lookupMethod;
          if (c.signaturePolymorphicMethods.contains(method.getName())) {
            // convert the invoked descriptor to a signature polymorphic one for the lookup
            lookupMethod = new Method(method.getName(), SIGNATURE_POLYMORPHIC_DESCRIPTOR);
          } else {
            lookupMethod = method;
          }
          if (!c.methods.contains(lookupMethod)) {
            return AncestorVisitorResult.PROCEED;
          }
          // is we have a virtual call, also look into superclasses, otherwise stop once we found a method match (previous statement):
          final AncestorVisitorResult notFoundRet = callIsVirtual ? AncestorVisitorResult.PROCEED : AncestorVisitorResult.STOP;
          if (previousInRuntime && c.isNonPortableRuntime) {
            return notFoundRet; // something inside the JVM is extending internal class/interface
          }
          if (forbiddenSignatures.checkMethod(reporter, c.className, lookupMethod)) {
            return AncestorVisitorResult.VIOLATION;
          }
          // for interfaces we don't check the class use (it is too strict, if just the interface is implemented, but nothing more!):
          if (!isInterfaceOfAncestor) {
            if (checkClassUse(reporter, c.className, "class", origName)) {
              return AncestorVisitorResult.VIOLATION;
            }
          }
          return notFoundRet;
        }
      }.visitAncestors(c, true, false /* JVM spec says: interfaces after superclasses */);
    }
    
    private boolean checkFieldAccess(String owner, final String field) {
      if (checkClassUse(this, owner, "class/interface", owner)) {
        return true;
      }
      // do a quick check that works without a ClassSignature (a more thorough check is done later):
      if (forbiddenSignatures.checkField(this, owner, field)) {
        return true;
      }
      final ClassMetadata c = lookup.lookupRelatedClass(owner, owner);
      if (c == null) {
        return false;
      }
      return new AncestorVisitor(this, lookup) {
        @Override
        protected AncestorVisitorResult visit(ClassMetadata c, String origName, boolean isInterfaceOfAncestor, boolean previousInRuntime) {
          if (!c.fields.contains(field)) {
            return AncestorVisitorResult.PROCEED;
          }
          // fields are never virtual, so always stop once we found the field (previous statement):
          final AncestorVisitorResult notFoundRet = AncestorVisitorResult.STOP;
          if (previousInRuntime && c.isNonPortableRuntime) {
            return notFoundRet; // something inside the JVM is extending internal class/interface
          }
          if (forbiddenSignatures.checkField(reporter, c.className, field)) {
            return AncestorVisitorResult.VIOLATION;
          }
          // for interfaces we don't check the class use (it is too strict, if just the interface is implemented, but nothing more!):
          if (!isInterfaceOfAncestor) {
            if (checkClassUse(reporter, c.className, "class", origName)) {
              return AncestorVisitorResult.VIOLATION;
            }
          }
          // we found the field and as those are not virtual, there is no need to go up in class hierarchy:
          return notFoundRet;
        }
      }.visitAncestors(c, true, true /* JVM spec says: superclasses after interfaces */);
    }
    
    @SuppressWarnings("fallthrough")
    private boolean checkHandle(Handle handle, boolean checkLambdaHandle) {
      boolean result = false;
      switch (handle.getTag()) {
        case Opcodes.H_GETFIELD:
        case Opcodes.H_PUTFIELD:
        case Opcodes.H_GETSTATIC:
        case Opcodes.H_PUTSTATIC:
          result |= checkFieldAccess(handle.getOwner(), handle.getName());
          break;
        case Opcodes.H_NEWINVOKESPECIAL:
          // newInvokeSpecial is a combination of NEW opcode followed by invokespecial on ctor, so we need to ADDITIONALLY check NEW opcode:
          result |= forbiddenSignatures.checkNew(this, handle.getOwner());
          /* FALLTHROUGH */
        case Opcodes.H_INVOKEVIRTUAL:
        case Opcodes.H_INVOKESTATIC:
        case Opcodes.H_INVOKESPECIAL:
        case Opcodes.H_INVOKEINTERFACE:
          final Method m = new Method(handle.getName(), handle.getDesc());
          if (checkLambdaHandle && handle.getOwner().equals(metadata.className) && handle.getName().startsWith(LAMBDA_METHOD_NAME_PREFIX)) {
            // as described in <http://cr.openjdk.java.net/~briangoetz/lambda/lambda-translation.html>,
            // we will record this metafactory call as "lambda" invokedynamic,
            // so we can assign the called lambda with the same groupId like *this* method:
            lambdas.put(m, currentGroupId);
          }
          final boolean callIsVirtual = (handle.getTag() == Opcodes.H_INVOKEVIRTUAL) || (handle.getTag() == Opcodes.H_INVOKEINTERFACE);
          result |= checkMethodAccess(handle.getOwner(), m, callIsVirtual);
          break;
      }
      return result;
    }
    
    private boolean checkConstant(Object cst, boolean checkLambdaHandle) {
      if (cst instanceof Type) {
        return checkType(this, (Type) cst, false);
      } else if (cst instanceof Handle) {
        return checkHandle((Handle) cst, checkLambdaHandle);
      }
      return false;
    }
    
    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      if (this.isDeprecated && DEPRECATED_DESCRIPTOR.equals(desc)) {
        // don't report 2 times!
        return null;
      }
      final Type type = Type.getType(desc);
      maybeSuppressCurrentGroup(type);
      location("annotation on method declaration");
      checkAnnotationDescriptor(this, type, visible);
      return null;
    }
    
    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      location("parameter annotation on method declaration");
      checkAnnotationDescriptor(this, Type.getType(desc), visible);
      return null;
    }
    
    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      location("type annotation on method declaration");
      checkAnnotationDescriptor(this, Type.getType(desc), visible);
      return null;
    }
    
    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      location("annotation in method body");
      checkAnnotationDescriptor(this, Type.getType(desc), visible);
      return null;
    }
    
    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
      location("annotation in method body");
      checkAnnotationDescriptor(this, Type.getType(desc), visible);
      return null;
    }
    
    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      location("annotation in method body");
      checkAnnotationDescriptor(this, Type.getType(desc), visible);
      return null;
    }
    
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      final boolean callIsVirtual = (opcode == Opcodes.INVOKEVIRTUAL) || (opcode == Opcodes.INVOKEINTERFACE);
      location("method body");
      checkMethodAccess(owner, new Method(name, desc), callIsVirtual);
    }
    
    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      location("method body");
      checkFieldAccess(owner, name);
    }
    
    @Override
    public void visitTypeInsn(int opcode, String type) {
      location("method body");
      switch (opcode) {
        case Opcodes.ANEWARRAY:
          checkType(this, Type.getObjectType(type), false);
          break;
        case Opcodes.NEW:
          // for new operator, we don't check class use, because if the constructor
          // is invoked later it will catched by <init> method. This solely tries to
          // match "::new" signatures for explicit NEW operators (not when superclass
          // ctor is called).
          forbiddenSignatures.checkNew(this, type);
          break;
      }
    }
    
    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
      location("method body");
      checkDescriptor(this, desc, false);
    }
    
    @Override
    public void visitLdcInsn(Object cst) {
      location("method body");
      checkConstant(cst, false);
    }
    
    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
      final boolean isLambdaMetaFactory = LAMBDA_META_FACTORY_INTERNALNAME.equals(bsm.getOwner());
      location("method body");
      checkHandle(bsm, false);
      for (final Object cst : bsmArgs) {
        checkConstant(cst, isLambdaMetaFactory);
      }
    }
    
    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      if (type != null) {
        location("catch in method body");
        // don't look into ancestors, only exact matches
        checkClassUse(this, type, "exception", type);
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
    
    @Override
    public void visitLineNumber(int lineNo, Label start) {
      this.lineNo = lineNo;
    }
    
    @Override
    public void location(String location) {
      this.currentLocation = location;
    }
    
    @Override
    public void report(String message, ViolationSeverity severity) {
      violations.add(new ForbiddenViolation(currentGroupId, myself, message, String.format(Locale.ENGLISH, "%s of '%s'", currentLocation, getHumanReadableMethodSignature()), lineNo, severity));
    }
  }

}
