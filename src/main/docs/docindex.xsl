<?xml version="1.0" encoding="UTF-8"?>
<!--
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
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="html" encoding="UTF-8" media-type="text/html"/>
  
  <xsl:param name="clihelp"/>
  
  <xsl:template match="/plugin">
    <html>
      <head>
        <title><xsl:value-of select="name"/></title>
      </head>
      <body>
        <h1>
          <xsl:value-of select="name"/>
          <xsl:text> </xsl:text>
          <em>
            <xsl:text>(</xsl:text>
            <xsl:value-of select="version"/>
            <xsl:text>)</xsl:text>
          </em>
        </h1>
        <p><xsl:value-of select="description"/></p>
        <p>
          <strong>Full name:</strong>
          <xsl:text> </xsl:text>
          <code>
            <xsl:value-of select="groupId"/>
            <xsl:text>:</xsl:text>
            <xsl:value-of select="artifactId"/>
            <xsl:text>:</xsl:text>
            <xsl:value-of select="version"/>
          </code>
        </p>
        <h2>Apache Ant</h2>
        <p><a href="ant-task.html">Task Documentation</a></p>
        <h2>Apache Maven: Mojo Goals</h2>
        <ul>
          <xsl:apply-templates select="mojos/mojo"/>
        </ul>
        <h2>Command Line Interface</h2>
        <p>
          <xsl:text>The JAR file can be called as command line tool using </xsl:text>
          <code>java -jar</code>
          <xsl:text>. This is the documentation of the CLI executable as printed by itsself:</xsl:text>
        </p>
        <pre>
          <strong><xsl:text>$ java -jar </xsl:text><xsl:value-of select="artifactId"/><xsl:text>-</xsl:text><xsl:value-of select="version"/><xsl:text>.jar --help</xsl:text></strong>
          <xsl:text>&#10;</xsl:text>
          <xsl:value-of select="$clihelp"/>
        </pre>
      </body>
    </html>
  </xsl:template>
	
  <xsl:template match="mojo">
    <li>
      <a href="{goal}-mojo.html">
        <xsl:value-of select="/plugin/goalPrefix"/>
        <xsl:text>:</xsl:text>
        <xsl:value-of select="goal"/>
      </a>
      <br/>
      <xsl:value-of select="description"/>
    </li>
  </xsl:template>
    
</xsl:stylesheet>
