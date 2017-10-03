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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class CheckerStaticTest {

  @Test
  public void testTargetVersionFix() throws Exception {
    assertEquals("jdk-dummy-1.7", Checker.fixTargetVersion("jdk-dummy-1.7"));
    assertEquals("jdk-dummy-1.7", Checker.fixTargetVersion("jdk-dummy-7"));
    assertEquals("jdk-dummy-1.7", Checker.fixTargetVersion("jdk-dummy-7.0"));
    
    assertEquals("jdk-dummy-1.1", Checker.fixTargetVersion("jdk-dummy-1.1"));
    
    assertEquals("jdk-dummy-9", Checker.fixTargetVersion("jdk-dummy-9"));
    assertEquals("jdk-dummy-9", Checker.fixTargetVersion("jdk-dummy-9.0"));
    
    assertEquals("jdk-dummy-18", Checker.fixTargetVersion("jdk-dummy-18"));
    assertEquals("jdk-dummy-18.3", Checker.fixTargetVersion("jdk-dummy-18.3"));
    assertEquals("jdk-dummy-18.3", Checker.fixTargetVersion("jdk-dummy-18.03"));
    
    assertFails("jdk-dummy-0");
    assertFails("jdk-dummy-1");

    assertFails("jdk-dummy-1.7.1");
    assertFails("jdk-dummy-1.7.1.1");
    assertFails("jdk-dummy-1.7.0.1");

    assertFails("jdk-dummy-7.1");
    assertFails("jdk-dummy-7.1.1");
    assertFails("jdk-dummy-7.0.1");
    
    assertFails("jdk-dummy-1.9");
    assertFails("jdk-dummy-9.0.1");
    assertFails("jdk-dummy-9.0.0.1");
  }
  
  private void assertFails(String name) {
    try {
      Checker.fixTargetVersion(name);
      fail("Should fail for: " + name);
    } catch (ParseException pe) {
      // pass
    }
  }
  
}
