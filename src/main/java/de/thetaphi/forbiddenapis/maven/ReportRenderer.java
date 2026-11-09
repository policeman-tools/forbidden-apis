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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.maven.doxia.sink.Sink;

import de.thetaphi.forbiddenapis.CheckerResult;
import de.thetaphi.forbiddenapis.ForbiddenViolation;
import de.thetaphi.forbiddenapis.ViolationSeverity;

/**
 * Renders the results of a forbidden API check as a Doxia document. This is used by the report Mojos.
 * <p>
 * To stay compatible with all versions of {@code maven-site-plugin} (Doxia 1.x and Doxia 2.x), this class
 * must only use {@link Sink} methods which exist with identical signatures in all Doxia versions.
 * {@link StandaloneHtmlSink} only supports exactly those methods, so the tests fail if a new one is used.
 */
final class ReportRenderer {

  static final String PROJECT_URL = "https://github.com/policeman-tools/forbidden-apis";

  private static final ViolationSeverity[] REPORTED_SEVERITIES = {
      ViolationSeverity.ERROR, ViolationSeverity.WARNING, ViolationSeverity.INFO, ViolationSeverity.DEBUG
  };

  private final String title;
  private final String scope;
  private final int numberOfClasses;
  private final List<ClassViolations> classes;
  private final Map<ViolationSeverity,Integer> totals;
  private final Set<String> missingClasses;

  /** Violations of a single class (violations with severity {@link ViolationSeverity#SUPPRESS} are already removed). */
  private static final class ClassViolations {
    final String className, sourceFile, anchor;
    final List<ForbiddenViolation> violations;
    final Map<ViolationSeverity,Integer> counts;

    ClassViolations(String className, String sourceFile, String anchor, List<ForbiddenViolation> violations) {
      this.className = className;
      this.sourceFile = sourceFile;
      this.anchor = anchor;
      this.violations = violations;
      this.counts = countSeverities(violations);
    }
  }

  /**
   * Creates the renderer.
   * @param title title of the report (also used as page title)
   * @param scope human readable description of the checked classes, e.g. "main classes"
   * @param results the results of the check or {@code null}, if the check was skipped
   * @param missingClasses classes that were referenced by the checked classes, but were missing on classpath
   */
  ReportRenderer(String title, String scope, List<CheckerResult> results, Set<String> missingClasses) {
    this.title = title;
    this.scope = scope;
    this.missingClasses = missingClasses;
    if (results == null) {
      this.numberOfClasses = -1;
      this.classes = null;
      this.totals = null;
      return;
    }
    this.numberOfClasses = results.size();
    final List<CheckerResult> sortedResults = new ArrayList<>(results);
    Collections.sort(sortedResults, new Comparator<CheckerResult>() {
      @Override
      public int compare(CheckerResult r1, CheckerResult r2) {
        return r1.getClassName().compareTo(r2.getClassName());
      }
    });
    final List<ClassViolations> classes = new ArrayList<>();
    final List<ForbiddenViolation> allViolations = new ArrayList<>();
    final Set<String> anchors = new HashSet<>();
    for (final CheckerResult result : sortedResults) {
      final List<ForbiddenViolation> violations = new ArrayList<>();
      for (final ForbiddenViolation v : result.getViolations()) {
        if (v.severity != ViolationSeverity.SUPPRESS) {
          violations.add(v);
        }
      }
      if (!violations.isEmpty()) {
        classes.add(new ClassViolations(result.getClassName(), result.getSourceFile(), createAnchor(result.getClassName(), anchors), violations));
        allViolations.addAll(violations);
      }
    }
    this.classes = Collections.unmodifiableList(classes);
    this.totals = countSeverities(allViolations);
  }

  /** Returns whether the check was executed (and not skipped). */
  boolean hasResults() {
    return classes != null;
  }

  /** Returns the number of reported violations (all severities except {@link ViolationSeverity#SUPPRESS}). */
  int getViolationCount() {
    int sum = 0;
    if (totals != null) {
      for (final int count : totals.values()) {
        sum += count;
      }
    }
    return sum;
  }

  /** Returns the number of reported violations with the given severity. */
  int getViolationCount(ViolationSeverity severity) {
    return (totals == null) ? 0 : totals.get(severity);
  }

  static Map<ViolationSeverity,Integer> countSeverities(List<ForbiddenViolation> violations) {
    final Map<ViolationSeverity,Integer> counts = new EnumMap<>(ViolationSeverity.class);
    for (final ViolationSeverity severity : REPORTED_SEVERITIES) {
      counts.put(severity, 0);
    }
    for (final ForbiddenViolation v : violations) {
      counts.put(v.severity, counts.get(v.severity) + 1);
    }
    return counts;
  }

  /**
   * Creates an anchor name from the class name which is a valid HTML/XML ID in all Doxia versions
   * (they would otherwise encode it on their own, which may differ between anchors and links).
   */
  static String createAnchor(String className, Set<String> existing) {
    final StringBuilder sb = new StringBuilder("class.");
    for (int i = 0, c = className.length(); i < c; i++) {
      final char ch = className.charAt(i);
      if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '_') {
        sb.append(ch);
      } else {
        sb.append('_');
      }
    }
    final String base = sb.toString();
    String anchor = base;
    for (int i = 2; !existing.add(anchor); i++) {
      anchor = base + "-" + i;
    }
    return anchor;
  }

  static String getSeverityName(ViolationSeverity severity) {
    final String name = severity.name();
    return name.charAt(0) + name.substring(1).toLowerCase(Locale.ENGLISH);
  }

  /** Renders the report to the given sink and closes it. */
  void render(Sink sink) {
    sink.head();
    sink.title();
    sink.text(title);
    sink.title_();
    sink.head_();

    sink.body();
    sink.section1();
    sink.sectionTitle1();
    sink.text(title);
    sink.sectionTitle1_();

    sink.paragraph();
    sink.text("This report lists the forbidden API invocations that ");
    sink.link(PROJECT_URL);
    sink.text("Policeman's Forbidden API Checker");
    sink.link_();
    sink.text(" detected in the " + scope + " of this project.");
    sink.paragraph_();

    if (classes == null) {
      sink.paragraph();
      sink.text("The check was skipped, so there are no results. See the build log for details.");
      sink.paragraph_();
    } else {
      renderSummary(sink);
      if (!missingClasses.isEmpty()) {
        renderMissingClasses(sink);
      }
      if (!classes.isEmpty()) {
        renderClasses(sink);
        renderDetails(sink);
      }
    }

    sink.section1_();
    sink.body_();
    sink.flush();
    sink.close();
  }

  private void renderSummary(Sink sink) {
    sink.section2();
    sink.sectionTitle2();
    sink.text("Summary");
    sink.sectionTitle2_();
    sink.table();
    sink.tableRows(null, false);
    sink.tableRow();
    headerCell(sink, "Classes");
    headerCell(sink, "Classes with violations");
    headerCell(sink, "Violations");
    headerCell(sink, "Errors");
    headerCell(sink, "Warnings");
    sink.tableRow_();
    sink.tableRow();
    cell(sink, Integer.toString(numberOfClasses));
    cell(sink, Integer.toString(classes.size()));
    cell(sink, Integer.toString(getViolationCount()));
    cell(sink, Integer.toString(getViolationCount(ViolationSeverity.ERROR)));
    cell(sink, Integer.toString(getViolationCount(ViolationSeverity.WARNING)));
    sink.tableRow_();
    sink.tableRows_();
    sink.table_();
    if (classes.isEmpty()) {
      sink.paragraph();
      sink.text("No forbidden API invocations were found.");
      sink.paragraph_();
    }
    sink.section2_();
  }

  private void renderMissingClasses(Sink sink) {
    sink.section2();
    sink.sectionTitle2();
    sink.text("Missing Classes");
    sink.sectionTitle2_();
    sink.paragraph();
    sink.text("The following classes are referenced by the checked classes, but were not found on classpath, "
        + "so some violations may be missing:");
    sink.paragraph_();
    sink.list();
    for (final String className : missingClasses) {
      sink.listItem();
      sink.monospaced();
      sink.text(className);
      sink.monospaced_();
      sink.listItem_();
    }
    sink.list_();
    sink.section2_();
  }

  private void renderClasses(Sink sink) {
    sink.section2();
    sink.sectionTitle2();
    sink.text("Classes");
    sink.sectionTitle2_();
    sink.table();
    sink.tableRows(null, false);
    sink.tableRow();
    headerCell(sink, "Class");
    headerCell(sink, "Violations");
    headerCell(sink, "Errors");
    headerCell(sink, "Warnings");
    sink.tableRow_();
    for (final ClassViolations c : classes) {
      sink.tableRow();
      sink.tableCell();
      sink.link("#" + c.anchor);
      sink.text(c.className);
      sink.link_();
      sink.tableCell_();
      cell(sink, Integer.toString(c.violations.size()));
      cell(sink, Integer.toString(c.counts.get(ViolationSeverity.ERROR)));
      cell(sink, Integer.toString(c.counts.get(ViolationSeverity.WARNING)));
      sink.tableRow_();
    }
    sink.tableRows_();
    sink.table_();
    sink.section2_();
  }

  private void renderDetails(Sink sink) {
    sink.section2();
    sink.sectionTitle2();
    sink.text("Details");
    sink.sectionTitle2_();
    for (final ClassViolations c : classes) {
      sink.section3();
      sink.anchor(c.anchor);
      sink.anchor_();
      sink.sectionTitle3();
      sink.text(c.className);
      sink.sectionTitle3_();
      if (c.sourceFile != null) {
        sink.paragraph();
        sink.text("Source file: ");
        sink.monospaced();
        sink.text(c.sourceFile);
        sink.monospaced_();
        sink.paragraph_();
      }
      sink.table();
      sink.tableRows(null, false);
      sink.tableRow();
      headerCell(sink, "Severity");
      headerCell(sink, "Violation");
      headerCell(sink, "Location");
      headerCell(sink, "Line");
      sink.tableRow_();
      for (final ForbiddenViolation v : c.violations) {
        sink.tableRow();
        cell(sink, getSeverityName(v.severity));
        cell(sink, v.description);
        cell(sink, v.locationInfo);
        cell(sink, (v.lineNo >= 0) ? Integer.toString(v.lineNo) : "");
        sink.tableRow_();
      }
      sink.tableRows_();
      sink.table_();
      sink.section3_();
    }
    sink.section2_();
  }

  private static void headerCell(Sink sink, String text) {
    sink.tableHeaderCell();
    sink.text(text);
    sink.tableHeaderCell_();
  }

  private static void cell(Sink sink, String text) {
    sink.tableCell();
    sink.text(text);
    sink.tableCell_();
  }

}
