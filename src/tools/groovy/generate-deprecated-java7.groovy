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

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

new DeprecatedGen<File>(properties['build.java.runtime'], new File(properties['java.home'], "lib/rt.jar"), properties['deprecated.output.file'] as File) {
  @Override
  protected void collectClasses(File source) throws IOException {
    new ZipInputStream(new FileInputStream(source)).withStream {
      ZipEntry entry;
      while ((entry = it.getNextEntry()) != null) {
        try {
          if (entry.isDirectory()) continue;
          if (entry.getName().endsWith(".class")) {
            parseClass(it);
          }
        } finally {
          it.closeEntry();
        }
      }
    }
  }
}.run();
