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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Generates a signatures file listing all JRE-internal classes for the current runtime. */
public final class InternalApiGen {
  
  final static String NL = System.getProperty("line.separator", "\n");
  final String javaVersion, header;
  
  private final File output;
  
  InternalApiGen(String javaVersion, String outputFile) {
    this(javaVersion, new File(outputFile));
  }
  
  InternalApiGen(String javaVersion, File output) {
    this.javaVersion = javaVersion;
    this.output = output;
    this.header = new StringBuilder()
      .append("# This file contains API signatures that are marked as internal in Java.").append(NL)
      .append("# It is provided here for reference, but can easily regenerated by executing from the source folder of forbidden-apis:").append(NL)
      .append("# $ ant generate-internal").append(NL)
      .append(NL)
      .append("# This file contains all internal packages listed in Security.getProperty(\"package.access\") of Java version ").append(javaVersion)
        .append(" (extracted from build ").append(System.getProperty("java.version")).append(").").append(NL)
      .append(NL)
      .append("@defaultMessage non-public internal runtime class in Java ").append(javaVersion).append(NL)
      .append(NL)
      .toString();
    try {
      int v = Integer.parseInt(javaVersion);
      if (v >= 24) {
        throw new IllegalArgumentException("InternalApiGen only works till Java 23, for later versions use the generated file from Java 23.");
      }
    } catch (NumberFormatException nfe) {
      // pass
    }
  }
  
  private void parsePackages(String packagesStr, Set<String> packages) {
    if (packagesStr == null || packagesStr.isEmpty()) {
      return;
    }
    for (String pkg : packagesStr.split(",")) {
      pkg = pkg.trim();
      if (!pkg.endsWith(".")) {
        pkg = pkg.concat(".");
      }
      packages.add(pkg.concat("**"));
    }
  }
  
  @SuppressForbidden
  void run() throws IOException {
    System.err.println(String.format(Locale.ENGLISH, "Writing internal APIs to signatures file '%s'...", output));
    
    final Set<String> packages = new TreeSet<>();
    parsePackages(Security.getProperty("package.access"), packages);
    // TODO: add this, too??: parsePackages(Security.getProperty("package.definition"), packages);

    try (final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8))) {
      writer.write(header);
      for (final String s : packages) {
        writer.write(s);
        writer.newLine();
      }
    }
    
    System.err.println("Internal API signatures for Java version " + javaVersion + " written successfully.");
  }

  public static void main(String... args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Need 2 arguments: java version, output file");
    }
    new InternalApiGen(args[0], args[1]).run();
  }
}
