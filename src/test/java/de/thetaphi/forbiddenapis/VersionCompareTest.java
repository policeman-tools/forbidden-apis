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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VersionCompareTest {

  @Test
  public void testVersionCompare() throws Exception {
    assertVersionEquals("1.0.0", "1.0.0");
    assertVersionEquals("1.0.0", "1.00.0");
    assertVersionEquals("1.0.0", "01.00.0");
    assertVersionEquals("1.0.0", "1.0");
    assertVersionEquals("1.0.0", "1");
    
    assertVersionLess("1.0.0", "2.0");
    assertVersionLess("1.0.0", "1.1");
    assertVersionLess("1", "1.1.0");
    assertVersionLess("1.0", "1.1.0");
    assertVersionLess("1.2", "1.10.0");
    
    assertVersionGreater("1.1.0", "1.0.0");
    assertVersionGreater("1.1.0", "1.0");
    assertVersionGreater("1.1.0", "1");
    assertVersionGreater("2", "1");
    assertVersionGreater("2.0", "1");
    assertVersionGreater("2.1.1", "2.1");
    assertVersionGreater("1.11", "1.2.0");
    assertVersionGreater("1.11", "1.02.0");
  }
  
  private void assertVersionEquals(String v1, String v2) {
    assertTrue(v1 + " <> " + v2, VersionCompare.compareVersion(v1, v2) == 0);
    assertTrue(v1 + " <> " + v2, VersionCompare.VERSION_COMPARATOR.compare(v1, v2) == 0);
  }
  
  private void assertVersionLess(String v1, String v2) {
    assertTrue(v1 + " <> " + v2, VersionCompare.compareVersion(v1, v2) < 0);
    assertTrue(v1 + " <> " + v2, VersionCompare.VERSION_COMPARATOR.compare(v1, v2) < 0);
  }
  
  private void assertVersionGreater(String v1, String v2) {
    assertTrue(v1 + " <> " + v2, VersionCompare.compareVersion(v1, v2) > 0);
    assertTrue(v1 + " <> " + v2, VersionCompare.VERSION_COMPARATOR.compare(v1, v2) > 0);
  }
  
  @Test
  public void testBundledSignaturesCompare() throws Exception {
    assertBsEquals("foo-1.0.0", "foo-1.00.0");
    assertBsEquals("foo-1.0.0", "foo-1.0");
    assertBsEquals("foo-1.0.0", "foo-01");
    
    assertBsLess("foo-1.0.0", "foo-2.0");
    assertBsLess("foo-1.2.0", "foo-10.1");
    assertBsLess("foo-1", "foo-1.2.0");
    assertBsLess("foo-1.2", "foo-1.010.0");
    assertBsLess("foo-2", "foo-10");
    
    assertBsLess("bar-1.0", "foo-1.0");
    assertBsLess("bar-1.0", "foo-1.1");
    assertBsLess("bar-1", "foo-0");
    assertBsLess("bar-1.0", "foo-0");
    assertBsLess("bar-1.0", "foo");
    assertBsLess("bar", "foo-1.0");
    
    assertBsGreater("foo-1.1.0", "foo-1.0.0");
    assertBsGreater("foo-1.1.0", "foo-1.0");
    assertBsGreater("foo-1.1.0", "foo-1");
    assertBsGreater("foo-2", "foo-1");
    assertBsGreater("foo-2.0", "foo-1");
    assertBsGreater("foo-2.1.1", "foo-2.1");
    assertBsGreater("foo-1.11", "foo-1.2.0");
    assertBsGreater("foo-1.11", "foo-1.02.0");

    assertBsGreater("foo-1", "bar-1");
    assertBsGreater("foo-0", "bar-1");
    assertBsGreater("foo", "bar");
    assertBsGreater("foo-2.1", "bar");
    assertBsGreater("foo", "bar-1.1");
  }
  
  private void assertBsEquals(String v1, String v2) {
    assertTrue(v1 + " <> " + v2, VersionCompare.compareBundledSignatures(v1, v2) == 0);
    assertTrue(v1 + " <> " + v2, VersionCompare.BUNDLED_SIGNATURES_COMPARATOR.compare(v1, v2) == 0);
  }
  
  private void assertBsLess(String v1, String v2) {
    assertTrue(v1 + " <> " + v2, VersionCompare.compareBundledSignatures(v1, v2) < 0);
    assertTrue(v1 + " <> " + v2, VersionCompare.BUNDLED_SIGNATURES_COMPARATOR.compare(v1, v2) < 0);
  }
  
  private void assertBsGreater(String v1, String v2) {
    assertTrue(v1 + " <> " + v2, VersionCompare.compareBundledSignatures(v1, v2) > 0);
    assertTrue(v1 + " <> " + v2, VersionCompare.BUNDLED_SIGNATURES_COMPARATOR.compare(v1, v2) > 0);
  }
  
}
