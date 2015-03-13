# Intro #
This project implements the ANT task (+ Maven Mojo) announced in the [Generics Policeman Blog](http://blog.thetaphi.de/2012/07/default-locales-default-charsets-and.html). It checks Java byte code against a list of "forbidden" API signatures.

## A new Tool for the Policeman ##
I started to hack a tool as a custom [Apache Ant](http://ant.apache.org/) task using [ASM](http://asm.ow2.org/) (Lightweight Java Bytecode Manipulation Framework). The idea was to provide a list of methods signatures, field names and plain class names that should fail the build, once bytecode accesses it in any way. A first version of this task was published in as [Apache Lucene](http://lucene.apache.org/core/) issue [LUCENE-4199](https://issues.apache.org/jira/browse/LUCENE-4199), later improvements was to add support for fields ([LUCENE-4202](https://issues.apache.org/jira/browse/LUCENE-4202)) and a sophisticated signature expansion to also catch calls to subclasses of the given signatures ([LUCENE-4206](https://issues.apache.org/jira/browse/LUCENE-4206)).

## About the Google Code project ##
This project was started as a fork of the internal [Apache Ant](http://ant.apache.org/) Task. It additionally provides a [Apache Maven](http://maven.apache.org/) [Mojo](http://maven.apache.org/guides/introduction/introduction-to-plugins.html), that can check your application classes against forbidden signatures, too.

The Apache Ant and Apache Maven Mojo are available for download or use with Maven/Ivy through [Maven Central](http://repo1.maven.org/maven2/de/thetaphi/forbiddenapis/) and [Sonatype](http://oss.sonatype.org/content/repositories/releases/de/thetaphi/forbiddenapis/) repositories. Nightly snapshot builds are done by the [Policeman Jenkins Server](http://jenkins.thetaphi.de/job/Forbidden-APIs/) and can be downloaded from the [Sonatype Snapshot](https://oss.sonatype.org/content/repositories/snapshots/de/thetaphi/forbiddenapis/) repository.

## News ##
**The current version is 1.7, released on 2014-11-24**. Changes for each released version are listed on the following page: [Changes](Changes.md)

## Documentation ##
  * [Apache Ant Usage Instructions](AntUsage.md)
  * [Apache Maven Usage Instructions](MavenUsage.md)
  * [Command Line Usage Instructions](CliUsage.md) (version 1.1+)

  * [Nightly Snapshot Documentation](http://jenkins.thetaphi.de/job/Forbidden-APIs/javadoc/) (detailed)