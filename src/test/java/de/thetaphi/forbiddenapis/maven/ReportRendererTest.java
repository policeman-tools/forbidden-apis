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

package de.thetaphi.forbiddenapis.maven;

import static de.thetaphi.forbiddenapis.Checker.Option.FAIL_ON_MISSING_CLASSES;
import static de.thetaphi.forbiddenapis.Checker.Option.FAIL_ON_VIOLATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.apache.maven.doxia.sink.Sink;
import org.junit.Before;
import org.junit.Test;

import de.thetaphi.forbiddenapis.Checker;
import de.thetaphi.forbiddenapis.CheckerResult;
import de.thetaphi.forbiddenapis.StdIoLogger;
import de.thetaphi.forbiddenapis.ViolationSeverity;

public final class ReportRendererTest {

  /** Class with calls to APIs, which are forbidden by the signatures in {@link #setUp()}. */
  static final class Fixture {
    List<String> createList() {
      return new ArrayList<>();
    }

    Integer boxedNumber() {
      return Integer.valueOf(42);
    }

    String suppressed(String s) {
      return s.trim();
    }
  }

  private static final String FIXTURE_CLASS = Fixture.class.getName();

  private List<CheckerResult> results;

  @Before
  public void setUp() throws Exception {
    final Checker checker = new Checker(StdIoLogger.INSTANCE, ReportRendererTest.class.getClassLoader(), FAIL_ON_MISSING_CLASSES, FAIL_ON_VIOLATION);
    assumeTrue("This test only works with a supported JDK (see docs)", checker.isSupportedJDK);
    checker.parseSignaturesString(
        "java.util.ArrayList @ Use <LinkedList> & friends\n" +
        "java.lang.Integer#valueOf(int) @ No boxing\n" +
        "java.lang.String#trim() @ Suppressed");
    checker.setSignaturesSeverity(Collections.singleton("java.lang.Integer#valueOf(int)"), ViolationSeverity.WARNING);
    checker.setSignaturesSeverity(Collections.singleton("java.lang.String#trim()"), ViolationSeverity.SUPPRESS);
    final String resource = "/" + FIXTURE_CLASS.replace('.', '/') + ".class";
    try (InputStream in = Fixture.class.getResourceAsStream(resource)) {
      assertTrue("Fixture class file must be found: " + resource, in != null);
      checker.streamReadClassToCheck(in, resource);
    }
    results = checker.runWithResults();
  }

  private static String render(ReportRenderer renderer) {
    final StringBuilder html = new StringBuilder();
    renderer.render(StandaloneHtmlSink.create(html));
    return html.toString();
  }

  @Test
  public void testReportWithViolations() {
    final ReportRenderer renderer = new ReportRenderer("Forbidden APIs", "main classes", results, Collections.<String>emptySet());
    assertTrue(renderer.hasResults());
    assertEquals(1, renderer.getViolationCount(ViolationSeverity.WARNING));
    assertTrue(renderer.getViolationCount(ViolationSeverity.ERROR) > 0);
    assertEquals(renderer.getViolationCount(ViolationSeverity.ERROR) + 1, renderer.getViolationCount());

    final String html = render(renderer);
    assertTrue(html, html.startsWith("<!DOCTYPE html>"));
    assertTrue(html, html.contains("<title>Forbidden APIs</title>"));
    assertTrue(html, html.contains(" detected in the main classes of this project."));
    // class is listed with a link to its details:
    final String anchor = ReportRenderer.createAnchor(FIXTURE_CLASS, new TreeSet<String>());
    assertTrue(html, html.contains("<a href=\"#" + anchor + "\">" + FIXTURE_CLASS + "</a>"));
    assertTrue(html, html.contains("<a id=\"" + anchor + "\">"));
    assertTrue(html, html.contains("<h3>" + FIXTURE_CLASS + "</h3>"));
    assertTrue(html, html.contains("<code>ReportRendererTest.java</code>"));
    // the violations with message (escaped), severity and location:
    assertTrue(html, html.contains("java.util.ArrayList [Use &lt;LinkedList&gt; &amp; friends]"));
    assertTrue(html, html.contains("<td>Forbidden method invocation: java.lang.Integer#valueOf(int) [No boxing]</td>"));
    assertTrue(html, html.contains("<td>Warning</td>"));
    assertTrue(html, html.contains("<td>Error</td>"));
    assertTrue(html, html.contains("<td>method body of &#39;boxedNumber()&#39;</td>"));
    // suppressed signatures are not listed:
    assertFalse(html, html.contains("Suppressed"));
    assertFalse(html, html.contains("Missing Classes"));
    assertFalse(html, html.contains("No forbidden API invocations were found."));
  }

  @Test
  public void testReportWithoutViolations() {
    final List<CheckerResult> noViolations = new ArrayList<>();
    for (final CheckerResult r : results) {
      if (!r.getClassName().equals(FIXTURE_CLASS)) {
        noViolations.add(r);
      }
    }
    final ReportRenderer renderer = new ReportRenderer("Forbidden APIs (Tests)", "test classes", noViolations, Collections.<String>emptySet());
    assertEquals(0, renderer.getViolationCount());
    final String html = render(renderer);
    assertTrue(html, html.contains(" detected in the test classes of this project."));
    assertTrue(html, html.contains("No forbidden API invocations were found."));
    assertFalse(html, html.contains("<h2>Details</h2>"));
  }

  @Test
  public void testReportWithMissingClasses() {
    final ReportRenderer renderer = new ReportRenderer("Forbidden APIs", "main classes", results,
        new TreeSet<>(Arrays.asList("foo.bar.Missing1", "foo.bar.Missing2")));
    final String html = render(renderer);
    assertTrue(html, html.contains("<h2>Missing Classes</h2>"));
    assertTrue(html, html.contains("<li><code>foo.bar.Missing1</code></li>"));
    assertTrue(html, html.contains("<li><code>foo.bar.Missing2</code></li>"));
  }

  @Test
  public void testSkippedReport() {
    final ReportRenderer renderer = new ReportRenderer("Forbidden APIs", "main classes", null, Collections.<String>emptySet());
    assertFalse(renderer.hasResults());
    assertEquals(0, renderer.getViolationCount());
    final String html = render(renderer);
    assertTrue(html, html.contains("The check was skipped, so there are no results."));
    assertFalse(html, html.contains("<h2>Summary</h2>"));
  }

  @Test
  public void testAnchors() {
    final TreeSet<String> anchors = new TreeSet<>();
    assertEquals("class.foo.Bar_Inner", ReportRenderer.createAnchor("foo.Bar$Inner", anchors));
    // same anchor name for another class must be unique:
    assertEquals("class.foo.Bar_Inner-2", ReportRenderer.createAnchor("foo.Bar_Inner", anchors));
    assertEquals("class.foo.Bar_Inner-3", ReportRenderer.createAnchor("foo.Bar\u00E4Inner", anchors));
  }

  @Test
  public void testUnsupportedSinkEvent() {
    final Sink sink = StandaloneHtmlSink.create(new StringBuilder());
    try {
      sink.horizontalRule();
      fail("StandaloneHtmlSink should only support the events used by ReportRenderer");
    } catch (UnsupportedOperationException e) {
      // pass
    }
  }

}
