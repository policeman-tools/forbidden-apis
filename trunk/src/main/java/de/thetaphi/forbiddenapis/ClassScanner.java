package de.thetaphi.forbiddenapis;

import java.util.Formatter;
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

  final boolean internalRuntimeForbidden;
  final Checker checker;
  final int[] violations;
  final String className;
  
  // key is the internal name (slashed), followed by \000 and the field name:
  final Map<String,String> forbiddenFields;
  // key is the internal name (slashed), followed by \000 and the method signature:
  final Map<String,String> forbiddenMethods;
  // key is the internal name (slashed):
  final Map<String,String> forbiddenClasses;
  
  String source = null;
  boolean isDeprecated = false;
  
  public ClassScanner(Checker checker, String className,
      final Map<String,String> forbiddenClasses, Map<String,String> forbiddenMethods, Map<String,String> forbiddenFields,
      boolean internalRuntimeForbidden, int[] violations) {
    super(Opcodes.ASM5);
    this.checker = checker;
    this.violations = violations;
    this.className = className;
    this.forbiddenClasses = forbiddenClasses;
    this.forbiddenMethods = forbiddenMethods;
    this.forbiddenFields = forbiddenFields;
    this.internalRuntimeForbidden = internalRuntimeForbidden;
  }
  
  private boolean isInternalClass(String className) {
    return className.startsWith("sun.") || className.startsWith("com.sun.") || className.startsWith("com.oracle.") || className.startsWith("jdk.")  || className.startsWith("sunw.");
  }
  
  boolean checkClassUse(String internalName) {
    final String printout = forbiddenClasses.get(internalName);
    if (printout != null) {
      checker.logError("Forbidden class/interface/annotation use: " + printout);
      return true;
    }
    if (internalRuntimeForbidden) {
      final String referencedClassName = Type.getObjectType(internalName).getClassName();
      if (isInternalClass(referencedClassName)) {
        final ClassSignatureLookup c = checker.lookupRelatedClass(internalName);
        if (c == null || c.isRuntimeClass) {
          checker.logError(String.format(Locale.ENGLISH,
            "Forbidden class/interface/annotation use: %s [non-public internal runtime class]",
            referencedClassName
          ));
          return true;
        }
      }
    }
    return false;
  }
  
  private boolean checkClassDefinition(String superName, String[] interfaces) {
    if (superName != null) {
      if (checkClassUse(superName)) {
        return true;
      }
      final ClassSignatureLookup c = checker.lookupRelatedClass(superName);
      if (c != null && checkClassDefinition(c.superName, c.interfaces)) {
        return true;
      }
    }
    if (interfaces != null) {
      for (String intf : interfaces) {
        if (checkClassUse(intf)) {
          return true;
        }
        final ClassSignatureLookup c = checker.lookupRelatedClass(intf);
        if (c != null && checkClassDefinition(c.superName, c.interfaces)) {
          return true;
        }
      }
    }
    return false;
  }
  
  boolean checkType(Type type) {
    while (type != null) {
      switch (type.getSort()) {
        case Type.OBJECT:
          if (checkClassUse(type.getInternalName())) {
            return true;
          }
          final ClassSignatureLookup c = checker.lookupRelatedClass(type.getInternalName());
          return (c != null && checkClassDefinition(c.superName, c.interfaces));
        case Type.ARRAY:
          type = type.getElementType();
          break;
        case Type.METHOD:
          boolean violation = checkType(type.getReturnType());
          for (final Type t : type.getArgumentTypes()) {
            violation |= checkType(t);
          }
          return violation;
        default:
          return false;
      }
    }
    return false;
  }
  
  boolean checkDescriptor(String desc) {
    return checkType(Type.getType(desc));
  }
  
  private void reportClassViolation(boolean violation, String where) {
    if (violation) {
      violations[0]++;
      final StringBuilder sb = new StringBuilder("  in ").append(className);
      if (source != null) {
        new Formatter(sb, Locale.ENGLISH).format(" (%s, %s)", source, where).flush();
      } else {
        new Formatter(sb, Locale.ENGLISH).format(" (%s)", where).flush();
      }
      checker.logError(sb.toString());
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
      
      private void reportFieldViolation(boolean violation, String where) {
        if (violation) {
          violations[0]++;
          final StringBuilder sb = new StringBuilder("  in ").append(className);
          if (source != null) {
            new Formatter(sb, Locale.ENGLISH).format(" (%s, %s of '%s')", source, where, name).flush();
          } else {
            new Formatter(sb, Locale.ENGLISH).format(" (%s of '%s')", where, name).flush();
          }
          ClassScanner.this.checker.logError(sb.toString());
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
      
      private boolean checkMethodAccess(String owner, Method method) {
        if (checkClassUse(owner)) {
          return true;
        }
        final String printout = forbiddenMethods.get(owner + '\000' + method);
        if (printout != null) {
          ClassScanner.this.checker.logError("Forbidden method invocation: " + printout);
          return true;
        }
        final ClassSignatureLookup c = checker.lookupRelatedClass(owner);
        if (c != null && !c.methods.contains(method)) {
          if (c.superName != null && checkMethodAccess(c.superName, method)) {
            return true;
          }
          // JVM spec says: interfaces after superclasses
          if (c.interfaces != null) {
            for (String intf : c.interfaces) {
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
          ClassScanner.this.checker.logError("Forbidden field access: " + printout);
          return true;
        }
        final ClassSignatureLookup c = checker.lookupRelatedClass(owner);
        if (c != null && !c.fields.contains(field)) {
          if (c.interfaces != null) {
            for (String intf : c.interfaces) {
              if (intf != null && checkFieldAccess(intf, field)) {
                return true;
              }
            }
          }
          // JVM spec says: superclasses after interfaces
          if (c.superName != null && checkFieldAccess(c.superName, field)) {
            return true;
          }
        }
        return false;
      }

      private boolean checkHandle(Handle handle) {
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
        return false;
      }
      
      private boolean checkConstant(Object cst) {
        if (cst instanceof Type) {
          if (checkType((Type) cst)) {
            return true;
          }
        } else if (cst instanceof Handle) {
          if (checkHandle((Handle) cst)) {
            return true;
          }
        }
        return false;
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

      private void reportMethodViolation(boolean violation, String where) {
        if (violation) {
          violations[0]++;
          final StringBuilder sb = new StringBuilder("  in ").append(className);
          if (source != null) {
            if (lineNo >= 0) {
              new Formatter(sb, Locale.ENGLISH).format(" (%s:%d)", source, lineNo).flush();
            } else {
              new Formatter(sb, Locale.ENGLISH).format(" (%s, %s of '%s')", source, where, getHumanReadableMethodSignature()).flush();
            }
          } else {
            new Formatter(sb, Locale.ENGLISH).format(" (%s of '%s')", where, getHumanReadableMethodSignature()).flush();
          }
          ClassScanner.this.checker.logError(sb.toString());
        }
      }
      
      @Override
      public void visitLineNumber(int lineNo, Label start) {
        this.lineNo = lineNo;
      }
    };
  }
}