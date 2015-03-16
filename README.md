# Policeman's Forbidden API Checker #

Allows to parse Java byte code to find invocations of method/class/field
signatures and fail build (Apache Ant or Apache Maven).

## Documentation ##

Please refer to the Github
[Wiki & Documentation](https://github.com/policeman-tools/forbidden-apis/wiki).

The checker is available as Apache Ant Task or as Apache Maven Mojo.
In addition there is a command line tool (CLI):

  * [Apache Ant](https://github.com/policeman-tools/forbidden-apis/wiki/AntUsage)
  * [Apache Maven](https://github.com/policeman-tools/forbidden-apis/wiki/MavenUsage)
  * [Command Line](https://github.com/policeman-tools/forbidden-apis/wiki/CliUsage)

This project uses Apache Ant (and Apache Ivy) to build. The minimum
Ant version is 1.8.0 and it is recommended to not have Apache Ivy in
the Ant lib folder, because the build script will download the correct
version of Ivy automatically.

## Project Resources ##

  * [Github Homepage](https://github.com/policeman-tools/forbidden-apis)
  * [Blog Post](http://blog.thetaphi.de/2012/07/default-locales-default-charsets-and.html)
  * [Jenkins CI](http://jenkins.thetaphi.de/job/Forbidden-APIs/)
  * [Open HUB](https://www.openhub.net/p/forbidden-apis)
