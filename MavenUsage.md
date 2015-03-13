# Maven Usage Instructions #

To use the forbidden API checker in Maven, use the following template to include the plugin:

```
<properties>
  <!-- 
   It is recommended to set the compiler version globally,
   as the compiler plugin and the forbidden API checker both
   use this version
  -->
  <maven.compiler.target>1.6</maven.compiler.target>
</properties>

<build>
  <plugins>
    <plugin>
      <groupId>de.thetaphi</groupId>
      <artifactId>forbiddenapis</artifactId>
      <version>1.7</version>
      <configuration>
        <!-- disallow undocumented classes like sun.misc.Unsafe: -->
        <internalRuntimeForbidden>true</internalRuntimeForbidden>
        <!--
          if the used Java version is too new,
          don't fail, just do nothing:
        -->
        <failOnUnsupportedJava>false</failOnUnsupportedJava>
        <bundledSignatures>
          <!--
            This will automatically choose the right
            signatures based on 'maven.compiler.target':
          -->
          <bundledSignature>jdk-unsafe</bundledSignature>
          <bundledSignature>jdk-deprecated</bundledSignature>
        </bundledSignatures>
        <signaturesFiles>
          <signaturesFile>./rel/path/to/signatures.txt</signaturesFile>
        </signaturesFiles>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
            <goal>testCheck</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
  <!-- more build settings here... -->
</build>
```

The possible `<bundledSignatures>` can be found on a [separate page](BundledSignatures.md). You can also give your own signatures in separate files from your project directory.

Since version 1.2 the goal was renamed to "check" and "testCheck" (to check the test classes) was added.

The detailed documentation (based on nightly snapshots) can be found here: http://jenkins.thetaphi.de/job/Forbidden-APIs/javadoc/