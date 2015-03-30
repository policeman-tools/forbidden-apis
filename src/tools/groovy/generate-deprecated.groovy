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

URL objectClassURL = getClass().getClassLoader().getResource("java/lang/Object.class");
if (objectClassURL != null && "jrt".equalsIgnoreCase(objectClassURL.getProtocol())) {
  return evaluate(new File("src/tools/groovy/generate-deprecated-java9.groovy"))
} else {
  return evaluate(new File("src/tools/groovy/generate-deprecated-java5.groovy"))
}
