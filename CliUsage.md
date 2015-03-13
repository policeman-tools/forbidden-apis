# Command Line Usage Instructions #

_(since version 1.1)_ You can call the forbidden API checker from the command line:

<pre>
$ java -jar forbiddenapis-1.7.jar --help<br>
usage: java -jar forbiddenapis-1.7.jar [options]<br>
Scans a set of class files for forbidden API usage.<br>
--allowmissingclasses           don't fail if a referenced class is<br>
missing on classpath<br>
--allowunresolvablesignatures   don't fail if a signature is not<br>
resolving<br>
-b,--bundledsignatures <name>      name of a bundled signatures<br>
definition (separated by commas or<br>
option can be given multiple times)<br>
-c,--classpath <path>              class search path of directories and<br>
zip/jar files<br>
-d,--dir <directory>               directory with class files to check<br>
for forbidden api usage; this<br>
directory is also added to classpath<br>
-e,--excludes <pattern>            ANT-style pattern to exclude some<br>
files from checks (separated by commas<br>
or option can be given multiple times)<br>
-f,--signaturesfile <file>         path to a file containing signatures<br>
(option can be given multiple times)<br>
-h,--help                          print this help<br>
-i,--includes <pattern>            ANT-style pattern to select class<br>
files (separated by commas or option<br>
can be given multiple times, defaults<br>
to '**/*.class')<br>
--internalruntimeforbidden      forbids calls to classes from the<br>
internal java runtime (like<br>
sun.misc.Unsafe)<br>
-V,--version                       print product version and exit<br>
Exit codes: 0 = SUCCESS, 1 = forbidden API detected, 2 = invalid command<br>
line, 3 = unsupported JDK version, 4 = other error (I/O,...)<br>
</pre>

The command line parameters match those of the [Ant Task](AntUsage.md).

The detailed documentation (based on nightly snapshots) can be found here: http://jenkins.thetaphi.de/job/Forbidden-APIs/javadoc/