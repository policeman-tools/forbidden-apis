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

import groovy.lang.Closure;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

/**
 * Extension for the ForbiddenApis Gradle Task to store defaults.
 * For description of the properties refer to the {@link CheckForbiddenApis}
 * task documentation.
 */
public class CheckForbiddenApisExtension implements PatternFilterable {
  
  /** Fields used for the convention mapping, keep up-to-date with class members! */
  static final List<String> PROPS = Arrays.asList(
    "signaturesFiles",
    "signatures",
    "bundledSignatures",
    "suppressAnnotations",
    "internalRuntimeForbidden",
    "failOnUnsupportedJava",
    "failOnMissingClasses",
    "failOnUnresolvableSignatures",
    "ignoreFailures",
    "patternSet"
  );
  
  public PatternSet patternSet = new PatternSet().include("**/*.class");
  
  public FileCollection signaturesFiles;
  public List<String> signatures,
    bundledSignatures,
    suppressAnnotations;
  public boolean internalRuntimeForbidden = false,
    failOnUnsupportedJava = false,
    failOnMissingClasses = true,
    failOnUnresolvableSignatures = true,
    ignoreFailures = false;
  
  // PatternFilterable implementation:
  
  public Set<String> getIncludes() {
    return patternSet.getIncludes();
  }

  public CheckForbiddenApisExtension setIncludes(Iterable<String> includes) {
    patternSet.setIncludes(includes);
    return this;
  }

  public Set<String> getExcludes() {
    return patternSet.getExcludes();
  }

  public CheckForbiddenApisExtension setExcludes(Iterable<String> excludes) {
    patternSet.setExcludes(excludes);
    return this;
  }

  public CheckForbiddenApisExtension exclude(String... arg0) {
    patternSet.exclude(arg0);
    return this;
  }

  public CheckForbiddenApisExtension exclude(Iterable<String> arg0) {
    patternSet.exclude(arg0);
    return this;
  }

  public CheckForbiddenApisExtension exclude(Spec<FileTreeElement> arg0) {
    patternSet.exclude(arg0);
    return this;
  }

  public CheckForbiddenApisExtension exclude(@SuppressWarnings("rawtypes") Closure arg0) {
    patternSet.exclude(arg0);
    return this;
  }

  public CheckForbiddenApisExtension include(String... arg0) {
    patternSet.include(arg0);
    return this;
  }

  public CheckForbiddenApisExtension include(Iterable<String> arg0) {
    patternSet.include(arg0);
    return this;
  }

  public CheckForbiddenApisExtension include(Spec<FileTreeElement> arg0) {
    patternSet.include(arg0);
    return this;
  }

  public CheckForbiddenApisExtension include(@SuppressWarnings("rawtypes") Closure arg0) {
    patternSet.include(arg0);
    return this;
  }

}
