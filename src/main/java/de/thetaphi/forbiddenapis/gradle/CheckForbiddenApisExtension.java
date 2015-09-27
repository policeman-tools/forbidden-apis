package de.thetaphi.forbiddenapis.gradle;

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

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;

import org.gradle.api.file.FileCollection;

/**
 * Extension for the ForbiddenApis Gradle Task to store defaults.
 * For description of the properties refer to the {@link CheckForbiddenApis}
 * task documentation.
 * @since 2.0
 */
public class CheckForbiddenApisExtension {
  
  /** Fields used for the convention mapping, keep up-to-date with class members! */
  static final List<String> PROPS = Arrays.asList(
    "signaturesFiles",
    "signaturesURLs",
    "signatures",
    "bundledSignatures",
    "suppressAnnotations",
    "internalRuntimeForbidden",
    "failOnUnsupportedJava",
    "failOnMissingClasses",
    "failOnUnresolvableSignatures",
    "ignoreFailures",
    "disableClassloadingCache"
  );
  
  public FileCollection signaturesFiles;
  public Set<URL> signaturesURLs = new LinkedHashSet<URL>();
  public List<String> signatures = new ArrayList<String>();
  public Set<String> bundledSignatures = new LinkedHashSet<String>(),
    suppressAnnotations = new LinkedHashSet<String>();
  public boolean internalRuntimeForbidden = false,
    failOnUnsupportedJava = false,
    failOnMissingClasses = true,
    failOnUnresolvableSignatures = true,
    ignoreFailures = false,
    disableClassloadingCache = false;
  
}
