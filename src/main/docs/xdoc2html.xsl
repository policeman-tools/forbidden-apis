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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xdoc="http://maven.apache.org/XDOC/2.0" exclude-result-prefixes="xdoc">
  <xsl:output method="html" encoding="UTF-8" media-type="text/html"/>
  
  <xsl:template match="/xdoc:document">
    <html>
      <head>
        <title><xsl:value-of select="xdoc:properties/xdoc:title"/></title>
        <style type="text/css"><![CDATA[
          table {
            border-collapse: collapse;
          }
          table, tr, th, td {
            border: 1px solid dimgray;
            vertical-align: top;
            text-align: left;
          }
          th, td {
            padding: .2em;
          }
        ]]></style>
      </head>
      <body>
        <xsl:apply-templates select="xdoc:body/node()"/>
      </body>
    </html>
  </xsl:template>
	
  <xsl:template match="xdoc:section">
    <h1><xsl:value-of select="@name"/></h1>
    <xsl:apply-templates/>
  </xsl:template>
	
  <xsl:template match="xdoc:subsection">
    <h2><xsl:value-of select="@name"/></h2>
    <xsl:apply-templates/>
  </xsl:template>
	
  <!-- template to copy elements -->
  <xsl:template match="*">
    <xsl:element name="{local-name()}">
      <xsl:apply-templates select="@* | node()"/>
    </xsl:element>
  </xsl:template>

  <!-- template to copy attributes -->
  <xsl:template match="@*">
    <xsl:attribute name="{local-name()}">
      <xsl:value-of select="."/>
    </xsl:attribute>
  </xsl:template>

  <!-- template to copy the rest of the nodes -->
  <xsl:template match="comment() | text() | processing-instruction()">
    <xsl:copy/>
  </xsl:template>

</xsl:stylesheet>
