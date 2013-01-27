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
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Utility class that is used to get an overview of all fields and implemented
 * methods of a class. It make the signatures available as Sets. */
final class ClassSignatureLookup {
  public final ClassReader reader;
  public final boolean isRuntimeClass;
  public final Set<Method> methods;
  public final Set<String> fields;
  
  public ClassSignatureLookup(final ClassReader reader, boolean isRuntimeClass) {
    this.reader = reader;
    this.isRuntimeClass = isRuntimeClass;
    final Set<Method> methods = new HashSet<Method>();
    final Set<String> fields = new HashSet<String>();
    reader.accept(new ClassVisitor(Opcodes.ASM4) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final Method m = new Method(name, desc);
        methods.add(m);
        return null;
      }
      
      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        fields.add(name);
        return null;
      }
    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    this.methods = Collections.unmodifiableSet(methods);
    this.fields = Collections.unmodifiableSet(fields);
  }
}
