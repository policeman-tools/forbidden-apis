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

import java.util.regex.Pattern;

import org.objectweb.asm.Type;

public interface Constants {

  final String BS_JDK_NONPORTABLE = "jdk-non-portable";
  
  final Pattern JDK_SIG_PATTERN = Pattern.compile("(jdk\\-.*?\\-)(\\d+)(\\.\\d+)?(\\.\\d+)*");
  
  final Type DEPRECATED_TYPE = Type.getType(Deprecated.class);
  final String DEPRECATED_DESCRIPTOR = DEPRECATED_TYPE.getDescriptor();

  final String LAMBDA_META_FACTORY_INTERNALNAME = "java/lang/invoke/LambdaMetafactory";
  final String LAMBDA_METHOD_NAME_PREFIX = "lambda$";

  final String SIGNATURE_POLYMORPHIC_PKG_INTERNALNAME = "java/lang/invoke/";
  final String SIGNATURE_POLYMORPHIC_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Object[].class));

  final String CLASS_CONSTRUCTOR_METHOD_NAME = "<clinit>";
  final String CONSTRUCTOR_METHOD_NAME = "<init>";
  
}
