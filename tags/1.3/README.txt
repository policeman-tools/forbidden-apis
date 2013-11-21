"Policeman's Forbidden API Checker"
Allows to parse Java byte code to find invocations of method/class/field
signatures and fail build (Apache Ant or Apache Maven).

See also:
http://blog.thetaphi.de/2012/07/default-locales-default-charsets-and.html

Project homepage:
http://code.google.com/p/forbidden-apis/

The checker is available as Apache Ant Task or as Apache Maven Mojo. For
documentation refer to the folowing web pages:

* http://code.google.com/p/forbidden-apis/wiki/AntUsage (Apache Ant)
* http://code.google.com/p/forbidden-apis/wiki/MavenUsage (Apache Maven)
* http://code.google.com/p/forbidden-apis/wiki/CliUsage (Command Line)

This project uses Apache Ant (and Apache Ivy) to build. The minimum
Ant version is 1.8.0 and it is recommended to not have Apache Ivy in
the Ant lib folder, because the build script will download the correct
version of Ant automatically.
