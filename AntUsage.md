# Ant Usage Instructions #

The Ant task can be downloaded as an Ant Task from this web page (the JAR file provides both the Maven Mojo and the Ant Task). Place the JAR file in the lib folder of your Ant project and include the taskdef like the following:

```
<project>

  <taskdef name="forbiddenapis" classname="de.thetaphi.forbiddenapis.AntTask" classpath="path/to/forbiddenapis.jar"/>

  <property name="src.dir" location="..."/>
  <property name="build.dir" location="..."/>
  <property name="jdk.version" value="1.6"/>

  <path id="build.classpath">
    <!--
     define your build classpath here, so all referenced JAR files can be found.
     This classpath should be used by javac and the forbidden API checker.
    -->
  </path>

  <target name="compile">
    <mkdir dir="${build.dir}"/>
    <javac classpathref="build.classpath"
      srcdir="${src.dir}" destdir="${build.dir}"
      source="${jdk.version}" target="${jdk.version}"/>
  </target>

  <target name="forbidden-checks" depends="compile">
    <forbiddenapis internalRuntimeForbidden="true" classpathref="build.classpath" dir="${build.dir}">
      <bundledsignatures name="jdk-unsafe-${jdk.version}"/>
      <bundledsignatures name="jdk-deprecated-${jdk.version}"/>
      <signaturesFileset file="path/to/signatures.txt"/>
    </forbiddenapis>
  </target>

</project>
```

The possible `<bundledsignatures>` can be found on a [separate page](BundledSignatures.md). You can also give your own signatures in separate files from your project directory. The Ant task also allows to place Java method signatures directly inside the task element.

Alternatively, if you have the `forbiddenapis.jar` in your `~/.ant/lib` folder, you can omit the taskdef and use it via an ANTLIB mechanism:

```
<project xmlns:fa="antlib:de.thetaphi.forbiddenapis">

  <!-- ... -->
  <fa:forbiddenapis .../>

</project>
```

If your project is using [Apache Ivy](http://ant.apache.org/ivy/) (recommended), you can define a cachepath for Ivy and download it automatically from Maven Central:

```
<project xmlns:ivy="antlib:org.apache.ivy.ant" xmlns:fa="antlib:de.thetaphi.forbiddenapis">

  <property name="src.dir" location="..."/>
  <property name="build.dir" location="..."/>
  <property name="jdk.version" value="1.6"/>

  <path id="build.classpath">
    <!--
     define your build classpath here, so all referenced JAR files can be found.
     This classpath should be used by javac and the forbidden API checker.
    -->
  </path>

  <target name="-init">
    <ivy:cachepath organisation="de.thetaphi" module="forbiddenapis" revision="1.7"
      inline="true" pathid="forbiddenapis.classpath"/>
    <taskdef uri="antlib:de.thetaphi.forbiddenapis" classpathref="forbiddenapis.classpath"/>
  </target>

  <target name="compile" depends="-init">
    <mkdir dir="${build.dir}"/>
    <javac classpathref="build.classpath"
      srcdir="${src.dir}" destdir="${build.dir}"
      source="${jdk.version}" target="${jdk.version}"/>
  </target>

  <target name="forbidden-checks" depends="compile">
    <fa:forbiddenapis internalRuntimeForbidden="true" classpathref="build.classpath" dir="${build.dir}">
      <bundledsignatures name="jdk-unsafe-${jdk.version}"/>
      <bundledsignatures name="jdk-deprecated-${jdk.version}"/>
      <signaturesFileset file="path/to/signatures.txt"/>
    </fa:forbiddenapis>
  </target>

</project>
```

If you don't like the additional XML-Namespaces, you can of course also define the task like in the first example, with Ivy's cachepath as reference.

The detailed documentation (based on nightly snapshots) can be found here: http://jenkins.thetaphi.de/job/Forbidden-APIs/javadoc/