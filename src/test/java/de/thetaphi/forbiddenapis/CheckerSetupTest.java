package de.thetaphi.forbiddenapis;

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

import static de.thetaphi.forbiddenapis.Checker.Option.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.util.Collections;
import java.util.EnumSet;

import org.junit.Before;
import org.junit.Test;

public final class CheckerSetupTest {
  
  @SuppressForbidden
  static final class MyChecker extends Checker {
    public MyChecker() {
      super(ClassLoader.getSystemClassLoader(), INTERNAL_RUNTIME_FORBIDDEN, FAIL_ON_MISSING_CLASSES, FAIL_ON_VIOLATION, FAIL_ON_UNRESOLVABLE_SIGNATURES);
    }

    @Override
    protected void logError(String msg) {
      System.err.println("ERROR: " + msg);
    }

    @Override
    protected void logWarn(String msg) {
      System.err.println("WARN: " + msg);
    }

    @Override
    protected void logInfo(String msg) {
      System.err.println(msg);
    }
  }
  
  protected Checker checker;
  
  @Before
  public void setUp() {
    checker = new MyChecker();
    assumeTrue("This test only works with a supported JDK (see docs)", checker.isSupportedJDK);
    assertEquals(EnumSet.of(INTERNAL_RUNTIME_FORBIDDEN, FAIL_ON_MISSING_CLASSES, FAIL_ON_VIOLATION, FAIL_ON_UNRESOLVABLE_SIGNATURES), checker.options);
  }

  @Test
  public void testEmpty() {
    assertEquals(Collections.emptyMap(), checker.forbiddenClasses);
    assertEquals(Collections.emptySet(), checker.forbiddenClassPatterns);
    assertEquals(Collections.emptyMap(), checker.forbiddenFields);
    assertEquals(Collections.emptyMap(), checker.forbiddenMethods);
  }

  @Test
  public void testClassSignature() throws Exception {
    checker.parseSignaturesString("java.lang.Object @ Foobar");
    assertEquals(Collections.singletonMap("java/lang/Object", "java.lang.Object [Foobar]"), checker.forbiddenClasses);
    assertEquals(Collections.emptySet(), checker.forbiddenClassPatterns);
    assertEquals(Collections.emptyMap(), checker.forbiddenFields);
    assertEquals(Collections.emptyMap(), checker.forbiddenMethods);
  }
  
  @Test
  public void testClassPatternSignature() throws Exception {
    checker.parseSignaturesString("java.lang.** @ Foobar");
    assertEquals(Collections.emptyMap(), checker.forbiddenClasses);
    assertEquals(Collections.singleton(new ClassPatternRule("java.lang.**", "java.lang.** [Foobar]")), checker.forbiddenClassPatterns);
    assertEquals(Collections.emptyMap(), checker.forbiddenFields);
    assertEquals(Collections.emptyMap(), checker.forbiddenMethods);
  }
  
  @Test
  public void testFieldSignature() throws Exception {
    checker.parseSignaturesString("java.lang.String#CASE_INSENSITIVE_ORDER @ Foobar");
    assertEquals(Collections.emptyMap(), checker.forbiddenClasses);
    assertEquals(Collections.emptySet(), checker.forbiddenClassPatterns);
    assertEquals(Collections.singletonMap("java/lang/String\000CASE_INSENSITIVE_ORDER", "java.lang.String#CASE_INSENSITIVE_ORDER [Foobar]"), checker.forbiddenFields);
    assertEquals(Collections.emptyMap(), checker.forbiddenMethods);
  }

  @Test
  public void testMethodSignature() throws Exception {
    checker.parseSignaturesString("java.lang.Object#toString() @ Foobar");
    assertEquals(Collections.emptyMap(), checker.forbiddenClasses);
    assertEquals(Collections.emptySet(), checker.forbiddenClassPatterns);
    assertEquals(Collections.emptyMap(), checker.forbiddenFields);
    assertEquals(Collections.singletonMap("java/lang/Object\000toString()Ljava/lang/String;", "java.lang.Object#toString() [Foobar]"), checker.forbiddenMethods);
  }
  
  @Test
  public void testEmptyCtor() throws Exception {
    Checker chk = new Checker(ClassLoader.getSystemClassLoader()) {
      @Override protected void logError(String msg) { }
      @Override protected void logWarn(String msg) { }
      @Override protected void logInfo(String msg) { }
    };
    assertEquals(EnumSet.noneOf(Checker.Option.class), chk.options);
  }
  
}
