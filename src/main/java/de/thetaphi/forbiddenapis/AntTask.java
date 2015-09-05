package de.thetaphi.forbiddenapis;

/*
 * (C) Copyright Uwe Schindler (Generics Policeman) and others.
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

import java.util.Locale;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

/**
 * {@inheritDoc}
 * @deprecated Use {@link de.thetaphi.forbiddenapis.ant.AntTask} instead.
 */
@Deprecated
public final class AntTask extends de.thetaphi.forbiddenapis.ant.AntTask {
  
  @Override
  public void execute() throws BuildException {
    log(String.format(Locale.ENGLISH, "DEPRECATED-WARNING: Please change your build.xml to use new task class '%s'",
        getClass().getSuperclass().getName()), Project.MSG_WARN);
    super.execute();
  }
  
}
