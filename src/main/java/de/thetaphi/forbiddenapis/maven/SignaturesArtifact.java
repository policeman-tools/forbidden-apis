package de.thetaphi.forbiddenapis.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;

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

/**
 * TODO
 * @since 2.0
 */
public final class SignaturesArtifact {
  public String groupId;
  public String artifactId;
  public String version;
  public String classifier;
  public String type;
  
  /** Path to resource inside JAR artifacts. */
  public String path;
  
  public Artifact createArtifact(ArtifactFactory artifactFactory) {
    if (groupId == null || artifactId == null || version == null || type == null) {
      throw new NullPointerException("signaturesArtifact is missing some properties. Required are: groupId, artifactId, version, type");
    }
    return (classifier != null) ? 
        artifactFactory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier) :
        artifactFactory.createArtifact(groupId, artifactId, version, null/*scope*/, type);
  }
}
