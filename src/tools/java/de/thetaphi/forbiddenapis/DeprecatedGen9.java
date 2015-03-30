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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.util.Locale;

/** Variant of {@link DeprecatedGen}, suitable for scanning Java 9+ runtime modules. */
public class DeprecatedGen9 extends DeprecatedGen {

  protected DeprecatedGen9(String javaVersion) {
    super(javaVersion);
  }

  private void parseFS(URI uri) throws IOException {
    final Path modules = Paths.get(uri);
    final PathMatcher fileMatcher = modules.getFileSystem().getPathMatcher("glob:*.class"),
      prefixMatcher = modules.getFileSystem().getPathMatcher("glob:/java.**");
    Files.walkFileTree(modules, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (fileMatcher.matches(file.getFileName())) {
          // System.out.println(file);
          try (final InputStream in = Files.newInputStream(file)) {
            parseClass(in);
          }
        }
        return FileVisitResult.CONTINUE;
      }
      
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return (dir.getNameCount() == 0 || prefixMatcher.matches(dir)) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
      }
   });
  }
  
  @SuppressForbidden
  public static void main(String... args) throws Exception {
    if (args.length != 2) {
      System.err.println("Invalid parameters; must be: java_version /path/to/outputfile.txt");
      System.exit(1);
    }
    final URI uri = URI.create("jrt:/");
    System.err.println(String.format(Locale.ENGLISH, "Reading '%s' and extracting deprecated APIs to signatures file '%s'...", uri, args[1]));
    final DeprecatedGen9 parser = new DeprecatedGen9(args[0]);
    parser.parseFS(uri);
    try (final OutputStream out = Files.newOutputStream(Paths.get(args[1]))) {
      parser.writeOutput(out);
    }
  }
}
