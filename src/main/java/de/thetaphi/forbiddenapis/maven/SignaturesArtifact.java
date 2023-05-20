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

package de.thetaphi.forbiddenapis.maven;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Defines coordinates of a Maven artifact that provides signatures files.
 * It may be a plain text file ({@link #path} is not given) or alternatively
 * refer to a text file inside a JAR file. For that, define the resource path
 * using {@link #path}.
 * @since 2.0
 */
public final class SignaturesArtifact {
  
  /** Artifact's group ID (required) */
  public String groupId;
  
  /** Artifact's ID (required) */
  public String artifactId;

  /** Version (required) */
  public String version;
  
  /** Classifier (optional) */
  public String classifier;
  
  /**
   * Type (required; {@code txt} or {@code jar}).
   * If the artifact refers to a JAR file, the {@link #path} should be
   * given, that identifies the signatures file inside the JAR.
   * */
  public String type;
  
  /** Path to resource inside JAR artifacts. If given, the {@link #type} must be {@code "jar"} or {@code "zip"}. */
  public String path;
  
  /** Used by the mojo to fetch the artifact */
  Artifact createArtifact() {
    if (groupId == null || artifactId == null || version == null || type == null) {
      throw new NullPointerException("signaturesArtifact is missing some properties. Required are: groupId, artifactId, version, type");
    }
    return new DefaultArtifact(groupId, artifactId, classifier, type, version);
  }
}
