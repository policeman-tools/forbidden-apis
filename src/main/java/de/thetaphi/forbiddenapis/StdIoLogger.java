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

@SuppressForbidden
public final class StdIoLogger implements Logger {
  
  public static final Logger INSTANCE = new StdIoLogger();
  
  private StdIoLogger() {}
  
  public void error(String msg) {
    System.err.println("ERROR: " + msg);
  }
  
  public void warn(String msg) {
    System.err.println("WARNING: " + msg);
  }
  
  public void info(String msg) {
    System.out.println(msg);
  }
  
}
