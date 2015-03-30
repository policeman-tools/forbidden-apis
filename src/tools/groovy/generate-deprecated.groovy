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

import org.apache.tools.ant.BuildException;

def objectClassURL = ClassLoader.getSystemClassLoader().getResource("java/lang/Object.class");
def isJava9 = objectClassURL != null && "jrt".equalsIgnoreCase(objectClassURL.getProtocol());

def hasRTJar = new File(properties['java.home'], "lib/rt.jar").isFile();

def vendor = properties['java.vendor'];
def isOracle = vendor.contains("Oracle") || vendor.contains("Sun Microsystems");
def isDetectedJavaVersion = properties['java.version'].startsWith(properties['build.java.runtime']);

if (isOracle && isDetectedJavaVersion && (isJava9 || hasRTJar)) {
  def script = isJava9 ? "generate-deprecated-java9.groovy" : "generate-deprecated-java5.groovy";
  evaluate(new File(properties['groovy-tools.dir'], script));
} else {
  throw new BuildException("Regenerating the deprecated signatures files need stock Oracle/Sun JDK, "+
    "but your Java version or operating system is unsupported: " + properties['build.java.info']);
}