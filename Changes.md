This page lists all changes since the first released version.

# Version 1.7 (released 2014-11-24) #

**New features:**
  * Auto-generate HTML documentation for Ant Task, Maven Mojo, and CLI ([issue #32](https://code.google.com/p/forbidden-apis/issues/detail?id=#32), [issue #37](https://code.google.com/p/forbidden-apis/issues/detail?id=#37)).
  * Add a new documentation ZIP file to release ([issue #32](https://code.google.com/p/forbidden-apis/issues/detail?id=#32), [issue #37](https://code.google.com/p/forbidden-apis/issues/detail?id=#37)).
  * Add help-mojo ([issue #32](https://code.google.com/p/forbidden-apis/issues/detail?id=#32), [issue #37](https://code.google.com/p/forbidden-apis/issues/detail?id=#37)).
  * Add support for signaturesFilelist and signaturesFile elements ([issue #36](https://code.google.com/p/forbidden-apis/issues/detail?id=#36)).
  * Add option to also ignore unresolvable signatures in Ant and CLI ([issue #42](https://code.google.com/p/forbidden-apis/issues/detail?id=#42)).
  * Allow option to only print warning if Ant fileset of classes to scan is empty (like Maven) ([issue #35](https://code.google.com/p/forbidden-apis/issues/detail?id=#35)).

**Bug fixes:**
  * Fix bug that deprecated signatures of Java 8 fail to load on Java 9 ([issue #41](https://code.google.com/p/forbidden-apis/issues/detail?id=#41)).

**Backwards compatibility:**
  * Remove deprecated Mojo of version 1.0 ([issue #33](https://code.google.com/p/forbidden-apis/issues/detail?id=#33)).
  * Rename some CLI options to be consistent with others ([issue #42](https://code.google.com/p/forbidden-apis/issues/detail?id=#42)).

# Version 1.6.1 (released 2014-08-05) #

**Bug fixes:**
  * Fix wrong plugin descriptor in Maven artifact. No code change, just new binary artifact.

# Version 1.6 (released 2014-08-04) #

**New features:**
  * Option to skip the execution of the plugin (pass `mvn -Dforbiddenapis.skip=true`) ([issue #29](https://code.google.com/p/forbidden-apis/issues/detail?id=#29)).
  * Maven plugin should log warning if target version is not set ([issue #28](https://code.google.com/p/forbidden-apis/issues/detail?id=#28)).

**Other changes:**
  * Upgrade ASM to bugfix release 5.0.3.

# Version 1.5.1 (released 2014-04-17) #

**Bug fixes:**
  * Fix regression caused by [issue #8](https://code.google.com/p/forbidden-apis/issues/detail?id=#8) with non-runtime visible annotations (e.g. `java.lang.Synthetic`) which are not in classpath ([issue #27](https://code.google.com/p/forbidden-apis/issues/detail?id=#27)). This hotfix disables detection of class-file only annotations. Annotations that need to be detected must have `RetentionPolicy.RUNTIME` to be visible to forbidden-apis.
  * Improve logging when no line numbers are available.

# Version 1.5 (released 2014-04-16) #

**New features:**
  * Make it possible to ban annotations ([issue #8](https://code.google.com/p/forbidden-apis/issues/detail?id=#8)).

**Bug fixes:**
  * Upgrade ASM to bugfix release 5.0.1.
  * Forbidden class use does not work in field declarations and method declarations ([issue #25](https://code.google.com/p/forbidden-apis/issues/detail?id=#25)).
  * Fix lookup of class references in `checkType()` / `checkDescriptor()` to also inspect superclasses and interfaces ([issue #26](https://code.google.com/p/forbidden-apis/issues/detail?id=#26)).

# Version 1.4.1 (released 2014-03-19) #

**New features:**
  * Upgrade to ASM 5.0 ([issue #24](https://code.google.com/p/forbidden-apis/issues/detail?id=#24)).
  * Full support for Java 8: Update deprecated signatures with final version of JDK 1.8.0; recompile and verify test classes.

**Bug fixes:**
  * Add some missing unsafe signatures ([issue #22](https://code.google.com/p/forbidden-apis/issues/detail?id=#22)).

# Version 1.4 (released 2013-11-21) #

**New features:**
  * Upgrade to ASM 5.0 BETA	([issue #18](https://code.google.com/p/forbidden-apis/issues/detail?id=#18)).
  * Add Java 8 deprecated + unsafe signatures ([issue #16](https://code.google.com/p/forbidden-apis/issues/detail?id=#16)).
  * Detect references to invokeDynamic using method handles to forbidden methods ([issue #11](https://code.google.com/p/forbidden-apis/issues/detail?id=#11)).
  * Skip execution for Maven projects with packaging "pom" (Maven only, [issue #10](https://code.google.com/p/forbidden-apis/issues/detail?id=#10)).
  * Add an option to ignore unresolvable signatures (Maven only, [issue #14](https://code.google.com/p/forbidden-apis/issues/detail?id=#14)).
  * Enhance the target parameter to also support testTarget like maven-compiler-plugin (Maven only, [issue #15](https://code.google.com/p/forbidden-apis/issues/detail?id=#15)).

**Bug fixes:**
  * Fix missing methods in commons-io ([issue #9](https://code.google.com/p/forbidden-apis/issues/detail?id=#9)).

**Optimizations:**
  * Improve memory usage ([issue #20](https://code.google.com/p/forbidden-apis/issues/detail?id=#20)).

# Version 1.3 (released 2013-04-28) #

**New features:**
  * Preliminary support for Java 8 ([issue #7](https://code.google.com/p/forbidden-apis/issues/detail?id=#7), tested with preview build 86 of the Oracle Java 8 JDK): The tool can now read Java 8 class files and detects usage of forbidden APIs in default interface methods and closures. It does not yet ship with signature files for Java 8, as the API is not yet official.

**Optimizations:**
  * Reduced binary JAR size by using non-debug ASM version.

# Version 1.2 (released 2013-02-16) #

**New features:**
  * Validating test classes is now supported by the Maven Mojo. The goals were renamed to "check" and "testCheck" ([issue #4](https://code.google.com/p/forbidden-apis/issues/detail?id=#4)).

**Bug fixes:**
  * fixed [issue #5](https://code.google.com/p/forbidden-apis/issues/detail?id=#5) (Apple-provided JDK 1.6 on MacOSX was detected as "unsupported"). The algorithm to get the bootclasspath was improved to support those JDK versions.

# Version 1.1 (released 2013-02-11) #

**New features:**
  * added a Command Line Interface (CLI, [issue #3](https://code.google.com/p/forbidden-apis/issues/detail?id=#3)).

**Bug fixes:**
  * fixed [issue #1](https://code.google.com/p/forbidden-apis/issues/detail?id=#1) (the bundled signature `jdk-system-out` was not working in Maven).
  * fixed [issue #2](https://code.google.com/p/forbidden-apis/issues/detail?id=#2) (the Ant task was incorrectly failing to execute on empty task tag `<forbiddenapis internalRuntimeForbidden="true" dir="..."/>`, although the checks for internal api calls are enabled).

# Version 1.0 (released 2013-02-04) #

Initial release, including support for Apache Ant, Apache Maven.