package de.thetaphi.forbiddenapis;

/*
 * (C) Copyright 2013 Uwe Schindler (Generics Policeman) and others.
 * Parts of this work are licensed to the Apache Software Foundation (ASF)
 * under one or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileResource;
import org.apache.tools.ant.types.resources.Resources;
import org.apache.tools.ant.types.resources.StringResource;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Task to check if a set of class files contains calls to forbidden APIs
 * from a given classpath and list of API signatures (either inline or as pointer to files).
 * In contrast to other ANT tasks, this tool does only visit the given classpath
 * and the system classloader, not ANT's class loader.
 */
public final class AntTask extends Task {

  private final Resources classFiles = new Resources();
  private final Resources apiSignatures = new Resources();
  private final List<BundledSignaturesType> bundledSignatures = new ArrayList<BundledSignaturesType>();
  private Path classpath = null;
  
  private boolean failOnUnsupportedJava = false;
  private boolean internalRuntimeForbidden = false;
  private boolean restrictClassFilename = true;
  private boolean failOnMissingClasses = true;
    
  @Override
  public void execute() throws BuildException {
    AntClassLoader antLoader = null;
    try {
      final ClassLoader loader;
      if (classpath != null) {
        classpath.setProject(getProject());
        loader = antLoader = getProject().createClassLoader(ClassLoader.getSystemClassLoader(), classpath);
        antLoader.setParentFirst(true); // use default classloader delegation
      } else {
        loader = ClassLoader.getSystemClassLoader();
      }
      classFiles.setProject(getProject());
      apiSignatures.setProject(getProject());
      
      final Checker checker = new Checker(loader, internalRuntimeForbidden, failOnMissingClasses) {
        @Override
        protected void logError(String msg) {
          log(msg, Project.MSG_ERR);
        }
        
        @Override
        protected void logWarn(String msg) {
          // ANT has no real log levels printed, so prefix with "WARNING":
          log("WARNING: " + msg, Project.MSG_WARN);
        }
        
        @Override
        protected void logInfo(String msg) {
          log(msg, Project.MSG_INFO);
        }
      };
      
      if (!checker.isSupportedJDK) {
        final String msg = String.format(Locale.ENGLISH, 
          "Your Java runtime (%s %s) is not supported by <%s/>. Please run the checks with a supported JDK!",
          System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version"), getTaskName());
        if (failOnUnsupportedJava) {
          throw new BuildException(msg);
        } else {
          log("WARNING: " + msg, Project.MSG_WARN);
          return;
        }
      }
      
      try {
        for (BundledSignaturesType bs : bundledSignatures) {
          final String name = bs.getName();
          if (name == null) {
            throw new BuildException("<bundledSignatures/> must have the mandatory attribute 'name' referring to a bundled signatures file.");
          }
          log("Reading bundled API signatures: " + name, Project.MSG_INFO);
          checker.parseBundledSignatures(name, null);
        }
        
        @SuppressWarnings("unchecked")
        Iterator<Resource> iter = (Iterator<Resource>) apiSignatures.iterator();
        while (iter.hasNext()) {
          final Resource r = iter.next();
          if (!r.isExists()) { 
            throw new BuildException("Signatures file does not exist: " + r);
          }
          if (r instanceof StringResource) {
            final String s = ((StringResource) r).getValue();
            if (s != null && s.trim().length() > 0) {
              log("Reading inline API signatures...", Project.MSG_INFO);
              checker.parseSignaturesString(s);
            }
          } else {
            log("Reading API signatures: " + r, Project.MSG_INFO);
            checker.parseSignaturesFile(r.getInputStream());
          }
        }
      } catch (IOException ioe) {
        throw new BuildException("IO problem while reading files with API signatures: " + ioe);
      } catch (ParseException pe) {
        throw new BuildException("Parsing signatures failed: " + pe.getMessage());
      }
        
      if (checker.hasNoSignatures()) {
        throw new BuildException("No API signatures found; use signaturesFile=, <signaturesFileSet/>, <bundledSignatures/> or inner text to define those!");
      }

      log("Loading classes to check...", Project.MSG_INFO);
      try {
        @SuppressWarnings("unchecked")
        Iterator<Resource> iter = (Iterator<Resource>) classFiles.iterator();
        if (!iter.hasNext()) {
          throw new BuildException("There is no <fileset/> given or the fileset does not contain any class files to check.");
        }
        while (iter.hasNext()) {
          final Resource r = iter.next();
          final String name = r.getName();
          if (restrictClassFilename && name != null && !name.endsWith(".class")) {
            continue;
          }
          if (!r.isExists()) { 
            throw new BuildException("Class file does not exist: " + r);
          }
          checker.addClassToCheck(r.getInputStream());
        }
      } catch (IOException ioe) {
        throw new BuildException("Failed to load one of the given class files:" + ioe);
      }

      try {
        log("Scanning for API signatures and dependencies...", Project.MSG_INFO);
        checker.run();
      } catch (ForbiddenApiException fae) {
        throw new BuildException(fae.getMessage());
      }
    } finally {
      if (antLoader != null) antLoader.cleanup();
    }
  }
  
  /** Set of class files to check */
  public void add(ResourceCollection rc) {
    classFiles.add(rc);
  }
  
  /** Sets a directory as base for class files. The implicit pattern '**&#47;*.class' is used to only scan class files. */
  public void setDir(File dir) {
    final FileSet fs = new FileSet();
    fs.setProject(getProject());
    fs.setDir(dir);
    // needed if somebody sets restrictClassFilename=false:
    fs.setIncludes("**/*.class");
    classFiles.add(fs);
  }
  
  /** A file with API signatures signaturesFile= attribute */
  public void setSignaturesFile(File file) {
    final Resource res = new FileResource(file);
    res.setProject(getProject());
    apiSignatures.add(res);
  }
  
  /** Set of files with API signatures as <signaturesFileSet/> nested element */
  public FileSet createSignaturesFileSet() {
    final FileSet fs = new FileSet();
    fs.setProject(getProject());
    apiSignatures.add(fs);
    return fs;
  }

  public BundledSignaturesType createBundledSignatures() {
    final BundledSignaturesType s = new BundledSignaturesType();
    s.setProject(getProject());
    bundledSignatures.add(s);
    return s;
  }

  /** A bundled signatures name */
  public void setBundledSignatures(String name) {
    final BundledSignaturesType s = new BundledSignaturesType();
    s.setProject(getProject());
    s.setName(name);
    bundledSignatures.add(s);
  }
  
  /** Support for API signatures list as nested text */
  public void addText(String text) {
    final Resource res = new StringResource(text);
    res.setProject(getProject());
    apiSignatures.add(res);
  }

  /** Classpath as classpath= attribute */
  public void setClasspath(Path classpath) {
    createClasspath().append(classpath);
  }

  /** Classpath as classpathRef= attribute */
  public void setClasspathRef(Reference r) {
    createClasspath().setRefid(r);
  }

  /** Classpath as <classpath/> nested element */
  public Path createClasspath() {
    if (this.classpath == null) {
        this.classpath = new Path(getProject());
    }
    return this.classpath.createPath();
  }
  
  /**
   * Fail the build, if the bundled ASM library cannot read the class file format
   * of the runtime library or the runtime library cannot be discovered.
   * Defaults to {@code false}. 
   */
  public void setFailOnUnsupportedJava(boolean failOnUnsupportedJava) {
    this.failOnUnsupportedJava = failOnUnsupportedJava;
  }

  /**
   * Fail the build, if a referenced class is missing. This requires
   * that you pass the whole classpath including all dependencies.
   * If you don't have all classes in the filesets, the application classes
   * must be reachable through this classpath, too.
   * Defaults to {@code true}. 
   */
  public void setFailOnMissingClasses(boolean failOnMissingClasses) {
    this.failOnMissingClasses = failOnMissingClasses;
  }

  /**
   * Forbids calls to classes from the internal java runtime (like sun.misc.Unsafe)
   * Defaults to {@code false}. 
   */
  public void setInternalRuntimeForbidden(boolean internalRuntimeForbidden) {
    this.internalRuntimeForbidden = internalRuntimeForbidden;
  }

  /** Automatically restrict resource names included to files with a name ending in '.class'.
   * This makes filesets easier, as the includes="**&#47;*.class" is not needed.
   * Defaults to {@code true}.
   */
  public void setRestrictClassFilename(boolean restrictClassFilename) {
    this.restrictClassFilename = restrictClassFilename;
  }

}
