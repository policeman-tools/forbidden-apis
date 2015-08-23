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

import de.thetaphi.forbiddenapis.DeprecatedGen;

import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

new DeprecatedGen<URI>(properties['build.java.runtime'], URI.create("jrt:/modules/"), properties['deprecated.output.file'] as File) {
  @Override
  protected void collectClasses(URI uri) throws IOException {
    Path modules = Paths.get(uri);
    PathMatcher fileMatcher = modules.getFileSystem().getPathMatcher("glob:java.**/*.class");
    Files.walkFileTree(modules, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (fileMatcher.matches(modules.relativize(file))) {
          // System.out.println(file);
          Files.newInputStream(file).withStream { parseClass(it) };
        }
        return FileVisitResult.CONTINUE;
      }
   });
  }
}.run();
