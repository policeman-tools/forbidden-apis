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

import java.io.IOException;

@SuppressWarnings("serial")
final class RelatedClassLoadingException extends RuntimeException {
  
  private final String className;

  public RelatedClassLoadingException(ClassNotFoundException e, String className) {
    super(e);
    this.className = className;
  }
  
  public RelatedClassLoadingException(IOException e, String className) {
    super(e);
    this.className = className;
  }
  
  public Exception getException() {
    return (Exception) getCause();
  }
  
  public String getClassName() {
    return className;
  }
  
}
