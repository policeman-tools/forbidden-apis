# Bundled Signatures Files #

The JAR file contains the following signatures and can be used in Maven or Ant using `<bundledSignatures>`. All signatures are versioned against the specified JDK version:

  * **`jdk-unsafe-*`:** Signatures of "unsafe" methods that use default charset, default locale, or default timezone. For server applications it is very stupid to call those methods, as the results will definitely not what the user wants (for Java `*` = 1.5, 1.6, 1.7, 1.8; Maven automatically adds the compile Java version)
  * **`jdk-deprecated-*`:** This disallows all deprecated methods from the JDK (for Java `*` = 1.5, 1.6, 1.7, 1.8; Maven automatically adds the compile Java version)
  * **`jdk-system-out`:** On server-side applications or libraries used by other programs, printing to `System.out` or `System.err` is discouraged and should be avoided (any java version, no specific version)
  * **`commons-io-unsafe-*`:** If your application uses the famous _Apache Common-IO_ library, this adds signatures of all methods that depend on default charset (for versions `*` = 1.0, 1.1, 1.2, 1.3, 1.4, 2.0, 2.1, 2.2, 2.3, 2.4)