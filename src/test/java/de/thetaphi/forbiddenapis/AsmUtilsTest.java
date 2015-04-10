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

import static de.thetaphi.forbiddenapis.AsmUtils.*;
import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

public final class AsmUtilsTest {

  @Test
  public void testIsGlob() {
    assertTrue(isGlob("a.b.c.*"));
    assertTrue(isGlob("sun.**"));
    assertTrue(isGlob("a?bc.x"));
    assertFalse(isGlob(Object.class.getName()));
    assertFalse(isGlob(getClass().getName()));
    assertFalse(isGlob("sun.misc.Unsafe$1"));
  }

  @Test
  public void testGlob() {
    Pattern pat = glob2Pattern("a.b.c.*");
    assertTrue(pat.matcher("a.b.c.d").matches());
    assertTrue(pat.matcher("a.b.c.def").matches());
    assertFalse(pat.matcher("a.b.c").matches());
    assertFalse(pat.matcher("a.b.c.d.e").matches());
    
    pat = glob2Pattern("a.b.c.**");
    assertTrue(pat.matcher("a.b.c.d").matches());
    assertTrue(pat.matcher("a.b.c.def").matches());
    assertTrue(pat.matcher("a.b.c.d.e").matches());
    assertTrue(pat.matcher("a.b.c.d.e.f").matches());
    
    pat = glob2Pattern("sun.*.*");
    assertTrue(pat.matcher("sun.misc.Unsafe").matches());
    assertTrue(pat.matcher("sun.misc.Unsafe$1").matches());
    assertFalse(pat.matcher("sun.misc.Unsafe.xy").matches());
    
    pat = glob2Pattern("java.**.Array?");
    assertTrue(pat.matcher("java.util.Arrays").matches());
    assertFalse(pat.matcher("java.util.ArrayList").matches());
    assertFalse(pat.matcher("java.util.Array").matches());
    assertTrue(pat.matcher("java.lang.reflect.Arrays").matches());
  }
  
  @Test
  public void testInternalRuntime() {
    assertTrue(isInternalClass("sun.misc.Unsafe"));
    assertTrue(isInternalClass("jdk.internal.Asm"));
    assertTrue(isInternalClass("sun.misc.Unsafe$1"));
    assertFalse(isInternalClass(Object.class.getName()));
    assertFalse(isInternalClass(getClass().getName()));
  }
  
}
