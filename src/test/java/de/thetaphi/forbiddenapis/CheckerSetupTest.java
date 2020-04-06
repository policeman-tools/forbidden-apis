/*
 * (C) Copyright Uwe Schindler (Generics Policeman) and others.
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

import static de.thetaphi.forbiddenapis.Checker.Option.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeNoException;

import java.util.Collections;
import java.util.EnumSet;

import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.commons.Method;

public final class CheckerSetupTest {
  
  protected Checker checker;
  protected Signatures forbiddenSignatures;
  
  @Before
  public void setUp() {
    checker = new Checker(StdIoLogger.INSTANCE, ClassLoader.getSystemClassLoader(), FAIL_ON_MISSING_CLASSES, FAIL_ON_VIOLATION, FAIL_ON_UNRESOLVABLE_SIGNATURES);
    assumeTrue("This test only works with a supported JDK (see docs)", checker.isSupportedJDK);
    assertEquals(EnumSet.of(FAIL_ON_MISSING_CLASSES, FAIL_ON_VIOLATION, FAIL_ON_UNRESOLVABLE_SIGNATURES), checker.options);
    forbiddenSignatures = checker.forbiddenSignatures;
  }

  @Test
  public void testEmpty() {
    assertEquals(Collections.emptyMap(), forbiddenSignatures.signatures);
    assertEquals(Collections.emptySet(), forbiddenSignatures.classPatterns);
    assertTrue(checker.hasNoSignatures());
  }

  @Test
  public void testClassSignature() throws Exception {
    checker.parseSignaturesString("java.lang.Object @ Foobar");
    assertEquals(Collections.singletonMap(Signatures.getKey("java/lang/Object"), "java.lang.Object [Foobar]"), forbiddenSignatures.signatures);
    assertEquals(Collections.emptySet(), forbiddenSignatures.classPatterns);
  }
  
  @Test
  public void testClassPatternSignature() throws Exception {
    checker.parseSignaturesString("java.lang.** @ Foobar");
    assertEquals(Collections.emptyMap(), forbiddenSignatures.signatures);
    assertEquals(Collections.singleton(new ClassPatternRule("java.lang.**", "Foobar")),
        forbiddenSignatures.classPatterns);
  }
  
  @Test
  public void testFieldSignature() throws Exception {
    checker.parseSignaturesString("java.lang.String#CASE_INSENSITIVE_ORDER @ Foobar");
    assertEquals(Collections.singletonMap(Signatures.getKey("java/lang/String", "CASE_INSENSITIVE_ORDER"), "java.lang.String#CASE_INSENSITIVE_ORDER [Foobar]"),
        forbiddenSignatures.signatures);
    assertEquals(Collections.emptySet(), forbiddenSignatures.classPatterns);
  }

  @Test
  public void testMethodSignature() throws Exception {
    checker.parseSignaturesString("java.lang.Object#toString() @ Foobar");
    assertEquals(Collections.singletonMap(Signatures.getKey("java/lang/Object", new Method("toString", "()Ljava/lang/String;")), "java.lang.Object#toString() [Foobar]"),
        forbiddenSignatures.signatures);
    assertEquals(Collections.emptySet(), forbiddenSignatures.classPatterns);
  }
  
  @Test
  public void testEmptyCtor() throws Exception {
    Checker chk = new Checker(StdIoLogger.INSTANCE, ClassLoader.getSystemClassLoader());
    assertEquals(EnumSet.noneOf(Checker.Option.class), chk.options);
  }
  
  @Test
  public void testRuntimeClassSignatures() throws Exception {
    String internalName = "java/lang/String";
    ClassSignature cs = checker.lookupRelatedClass(internalName, internalName);
    assertTrue(cs.isRuntimeClass);
    assertTrue(cs.signaturePolymorphicMethods.isEmpty());
  }
  
  @Test
  public void testSignaturePolymorphic() throws Exception {
    String internalName = "java/lang/invoke/MethodHandle";
    ClassSignature cs = checker.lookupRelatedClass(internalName, internalName);
    assertTrue(cs.signaturePolymorphicMethods.contains("invoke"));
    assertTrue(cs.signaturePolymorphicMethods.contains("invokeExact"));
    // System.out.println(cs.signaturePolymorphicMethods);
  }
  
  @Test
  public void testJava9ModuleSystemFallback() {
    final Class<?> moduleClass;
    try {
      moduleClass = Class.forName("java.lang.Module");
    } catch (ClassNotFoundException cfe) {
      assumeNoException("This test only works with Java 9+", cfe);
      return;
    }
    assertNotNull(checker.method_Class_getModule);
    assertSame(moduleClass, checker.method_Class_getModule.getReturnType());
    assertNotNull(checker.method_Module_getName);
    assertSame(moduleClass, checker.method_Module_getName.getDeclaringClass());
  }

}
