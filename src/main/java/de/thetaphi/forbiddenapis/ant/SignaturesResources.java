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

package de.thetaphi.forbiddenapis.ant;

import org.apache.tools.ant.types.resources.Resources;

/** Custom implementation of {@link Resources} to allow adding bundled signatures. */
public final class SignaturesResources extends Resources {
  private final AntTask task;
  
  SignaturesResources(AntTask task) {
    this.task = task;
  }

  // this is a hack to allow <bundled name="..."/> to be added. This just delegates back to task.
  public BundledSignaturesType createBundled() {
    return task.createBundledSignatures();
  }
  
}
