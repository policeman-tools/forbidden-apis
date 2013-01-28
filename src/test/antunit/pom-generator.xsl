<?xml version="1.0" encoding="UTF-8"?>
<!--
 * (C) Copyright 2013 Uwe Schindler (Generics Policeman) and others.
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
-->
<xsl:stylesheet version="1.0" 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:str="http://exslt.org/strings"
  extension-element-prefixes="str"
>
  <xsl:param name="jarfiles"/>
  
  <!--
    NOTE: This template matches the root element of any given input XML document!
    The XSL input file is ignored completely. The list of system dependencies is
    given via string parameter, that must be splitted at '|'.
  --> 
  <xsl:template match="/">
    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
      <modelVersion>4.0.0</modelVersion>
      <groupId>de.thetaphi</groupId>
      <artifactId>forbiddenapi-test</artifactId>
      <version>0.0-SNAPSHOT</version>
      <name>Dummy Project for Tests</name>
      
      <properties>
        <maven.compiler.target>${jdk.version}</maven.compiler.target>
      </properties>

      <build>
        <plugins>
          <plugin>
            <groupId>${groupId}</groupId>
            <artifactId>${artifactId}</artifactId>
            <version>${version}</version>
            <configuration>
              <internalRuntimeForbidden>true</internalRuntimeForbidden>
              <bundledSignatures>
                <bundledSignature>jdk-unsafe</bundledSignature>
                <bundledSignature>jdk-deprecated</bundledSignature>
              </bundledSignatures>
              <signatures>${antunit.signatures}</signatures>
            </configuration>
            <executions>
              <execution>
                <goals>
                  <goal>forbiddenapis</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
        <xsl:comment>The forbiddenapi checker uses the outputDirectory by default to locate the classes to check:</xsl:comment>
        <outputDirectory>${antunit.main.classes}</outputDirectory>
      </build>
      
      <xsl:comment>The following scope=system dependencies are dynamically generated by Apache IVY:</xsl:comment>
      <dependencies>
        <xsl:for-each select="str:split($jarfiles,'|')">
          <xsl:sort/>
          <dependency>
            <groupId>de.thetaphi</groupId>
            <artifactId><xsl:text>forbiddenapi-test-dep</xsl:text><xsl:value-of select="position()"/></artifactId>
            <version>1.0</version>
            <scope>system</scope>
            <systemPath><xsl:value-of select="."/></systemPath>
          </dependency>
        </xsl:for-each>
      </dependencies>
    </project>
  </xsl:template>
	
</xsl:stylesheet>
