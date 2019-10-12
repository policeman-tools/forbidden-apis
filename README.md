# Policeman's Forbidden API Checker #

Allows to parse Java byte code to find invocations of method/class/field
signatures and fail build (Apache Ant, Apache Maven, or Gradle).

[![Maven Central](https://img.shields.io/maven-central/v/de.thetaphi/forbiddenapis.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22de.thetaphi%22%20AND%20a%3A%22forbiddenapis%22)
[![Build Status](https://jenkins.thetaphi.de/job/Forbidden-APIs/badge/icon)](https://jenkins.thetaphi.de/job/Forbidden-APIs/)

## Documentation ##

Please refer to the Github
[Wiki & Documentation](https://github.com/policeman-tools/forbidden-apis/wiki).

The checker is available as Apache Ant Task, Apache Maven Mojo, and Gradle plugin.
In addition there is a command line tool (CLI):

  * [Apache Ant](https://github.com/policeman-tools/forbidden-apis/wiki/AntUsage)
  * [Apache Maven](https://github.com/policeman-tools/forbidden-apis/wiki/MavenUsage)
  * [Gradle](https://github.com/policeman-tools/forbidden-apis/wiki/GradleUsage)
  * [Command Line](https://github.com/policeman-tools/forbidden-apis/wiki/CliUsage)

This project uses Apache Ant (and Apache Ivy) to build. The minimum
Ant version is 1.8.0 and it is recommended to not have Apache Ivy in
the Ant lib folder, because the build script will download the correct
version of Ivy automatically.

## Project Resources ##

  * [Github Homepage](https://github.com/policeman-tools/forbidden-apis)
  * [Blog Post](https://blog.thetaphi.de/2012/07/default-locales-default-charsets-and.html)
  * [Jenkins CI](https://jenkins.thetaphi.de/job/Forbidden-APIs/)
  * [Open HUB](https://www.openhub.net/p/forbidden-apis)
