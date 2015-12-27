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

import static de.thetaphi.forbiddenapis.AsmUtils.glob2Pattern;
import static de.thetaphi.forbiddenapis.AsmUtils.isGlob;
import static de.thetaphi.forbiddenapis.AsmUtils.isPortableRuntimeClass;
import static de.thetaphi.forbiddenapis.AsmUtils.isRuntimeModule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
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
  public void testCrazyPatterns() {
    // those should not cause havoc:
    assertEquals("java\\.\\{.*\\}\\.Array", glob2Pattern("java.{**}.Array").pattern());
    assertEquals("java\\./.*<>\\.Array\\$1", glob2Pattern("java./**<>.Array$1").pattern());
    assertEquals("\\+\\^\\$", glob2Pattern("+^$").pattern());
  }
  
  @Test
  public void testPortableRuntime() {
    assertFalse(isPortableRuntimeClass("sun.misc.Unsafe"));
    assertFalse(isPortableRuntimeClass("jdk.internal.Asm"));
    assertFalse(isPortableRuntimeClass("sun.misc.Unsafe$1"));
    assertTrue(isPortableRuntimeClass(Object.class.getName()));
    assertTrue(isPortableRuntimeClass(ArrayList.class.getName()));
    assertTrue(isPortableRuntimeClass("org.w3c.dom.Document"));
    assertFalse(isPortableRuntimeClass(getClass().getName()));
  }
  
  @Test
  public void testRuntimeModule() {
    assertTrue(isRuntimeModule("java.base"));
    assertTrue(isRuntimeModule("java.sql"));
    assertTrue(isRuntimeModule("java.xml.ws"));
    assertTrue(isRuntimeModule("jdk.nashorn"));
    assertFalse(isRuntimeModule(null));
    assertFalse(isRuntimeModule("foo"));
    assertFalse(isRuntimeModule("foo.bar"));
  }
  
}
