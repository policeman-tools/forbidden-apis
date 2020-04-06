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

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** Utility class that is used to get an overview of all fields and implemented
 * methods of a class. It make the signatures available as Sets. */
final class ClassSignature implements Constants {
  private ClassReader reader;
  
  public final boolean isRuntimeClass;
  public final Set<Method> methods;
  public final Set<String> fields, signaturePolymorphicMethods;
  public final String className, superName;
  public final String[] interfaces;
  
  /** Builds the information from an ASM ClassReader */
  public ClassSignature(final ClassReader classReader, boolean isRuntimeClass, boolean withReader) {
    this.reader = withReader ? classReader : null;
    this.isRuntimeClass = isRuntimeClass;
    this.className = classReader.getClassName();
    this.superName = classReader.getSuperName();
    this.interfaces = classReader.getInterfaces();
    final Set<Method> methods = new HashSet<Method>();
    final Set<String> fields = new HashSet<String>();
    final Set<String> signaturePolymorphicMethods = new HashSet<String>();
    classReader.accept(new ClassVisitor(Opcodes.ASM8) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final Method m = new Method(name, desc);
        methods.add(m);
        if (className.startsWith(SIGNATURE_POLYMORPHIC_PKG_INTERNALNAME) &&
            (access & Opcodes.ACC_VARARGS) != 0 &&
            (access & Opcodes.ACC_NATIVE) != 0 &&
            SIGNATURE_POLYMORPHIC_DESCRIPTOR.equals(desc)
        ) {
          signaturePolymorphicMethods.add(name);
        }
        return null;
      }
      
      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        fields.add(name);
        return null;
      }
    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    this.methods = createSet(methods);
    this.fields = createSet(fields);
    this.signaturePolymorphicMethods = createSet(signaturePolymorphicMethods);
  }

  /** Alternative ctor that can be used to build the information via reflection from an already loaded class. Useful for Java 9 Jigsaw. */
  public ClassSignature(final Class<?> clazz, boolean isRuntimeClass) {
    this.reader = null; // no reader available!
    this.isRuntimeClass = isRuntimeClass;
    this.className = Type.getType(clazz).getInternalName();
    final Class<?> superclazz = clazz.getSuperclass();
    this.superName = superclazz == null ? null : Type.getType(superclazz).getInternalName();
    final Class<?>[] interfClasses = clazz.getInterfaces();
    this.interfaces = new String[interfClasses.length];
    for (int i = 0; i < interfClasses.length; i++) {
      this.interfaces[i] = Type.getType(interfClasses[i]).getInternalName();
    }
    final Set<Method> methods = new HashSet<Method>();
    final Set<String> fields = new HashSet<String>();
    final Set<String> signaturePolymorphicMethods = new HashSet<String>();
    for (final java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
      methods.add(Method.getMethod(m));
      if (className.startsWith(SIGNATURE_POLYMORPHIC_PKG_INTERNALNAME) &&
          m.isVarArgs() &&
          (m.getModifiers() & Modifier.NATIVE) != 0 &&
          SIGNATURE_POLYMORPHIC_DESCRIPTOR.equals(Type.getMethodDescriptor(m))
      ) {
        signaturePolymorphicMethods.add(m.getName());
      }
    }
    for (final java.lang.reflect.Constructor<?> m : clazz.getDeclaredConstructors()) {
      methods.add(Method.getMethod(m));
    }
    for (final java.lang.reflect.Field f : clazz.getDeclaredFields()) {
      fields.add(f.getName());
    }
    this.methods = createSet(methods);
    this.fields = createSet(fields);
    this.signaturePolymorphicMethods = createSet(signaturePolymorphicMethods);
  }
  
  private static <T> Set<T> createSet(Set<? extends T> s) {
    return s.isEmpty() ? Collections.<T>emptySet() : Collections.<T>unmodifiableSet(s);
  }

  public ClassReader getReader() {
    if (reader == null)
      throw new IllegalStateException("'" + Type.getObjectType(className).getClassName() + "' has no ClassReader, because it was already checked or is only loaded as related class.");
    try {
      return reader;
    } finally {
      reader = null;
    }
  }
}
