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

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

public final class CheckerSetupTest {
  
  static final class MyChecker extends Checker {
    public MyChecker() {
      super(ClassLoader.getSystemClassLoader(), true, true, true);
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
  }

  @Test
  public void testEmpty() {
    assertEquals(Collections.emptyMap(), checker.forbiddenClasses);
    assertEquals(Collections.emptyMap(), checker.forbiddenClassPatterns);
    assertEquals(Collections.emptyMap(), checker.forbiddenFields);
    assertEquals(Collections.emptyMap(), checker.forbiddenMethods);
  }

  @Test
  public void testClassSignature() throws Exception {
    checker.parseSignaturesString("java.lang.Object @ Foobar");
    assertEquals(Collections.singletonMap("java/lang/Object", "java.lang.Object [Foobar]"), checker.forbiddenClasses);
    assertEquals(Collections.emptyMap(), checker.forbiddenClassPatterns);
    assertEquals(Collections.emptyMap(), checker.forbiddenFields);
    assertEquals(Collections.emptyMap(), checker.forbiddenMethods);
  }
  
  @Test
  public void testClassPatternSignature() throws Exception {
    checker.parseSignaturesString("java.lang.** @ Foobar");
    assertEquals(Collections.emptyMap(), checker.forbiddenClasses);
    assertEquals(AsmUtils.glob2Pattern("java.lang.**").pattern(), checker.forbiddenClassPatterns.keySet().iterator().next().pattern());
    assertEquals(Collections.emptyMap(), checker.forbiddenFields);
    assertEquals(Collections.emptyMap(), checker.forbiddenMethods);
  }
  
}
