# Policeman's Forbidden API Checker #

Allows to parse Java byte code to find invocations of method/class/field
signatures and fail build (Apache Ant or Apache Maven).

See also:

  * [Blog Post](http://blog.thetaphi.de/2012/07/default-locales-default-charsets-and.html)
  * [Project Homepage](https://github.com/policeman-tools/forbidden-apis)

The checker is available as Apache Ant Task or as Apache Maven Mojo. For documentation
refer to the [Wiki & Documentation](https://github.com/policeman-tools/forbidden-apis/wiki):

  * [Apache Ant](https://github.com/policeman-tools/forbidden-apis/wiki/AntUsage)
  * [Apache Maven](https://github.com/policeman-tools/forbidden-apis/wiki/MavenUsage)
  * [Command Line](https://github.com/policeman-tools/forbidden-apis/wiki/CliUsage)

This project uses Apache Ant (and Apache Ivy) to build. The minimum
Ant version is 1.8.0 and it is recommended to not have Apache Ivy in
the Ant lib folder, because the build script will download the correct
version of Ant automatically.
