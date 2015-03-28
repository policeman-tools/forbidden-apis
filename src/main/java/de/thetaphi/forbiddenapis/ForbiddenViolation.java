package de.thetaphi.forbiddenapis;

/*
 * (C) Copyright Uwe Schindler (Generics Policeman) and others.
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

import java.util.Formatter;
import java.util.Locale;

import org.objectweb.asm.commons.Method;

public final class ForbiddenViolation implements Comparable<ForbiddenViolation> {
  
  private int groupId;
  public final Method targetMethod;
  public final String description;
  public final String locationInfo;
  public final int lineNo;
  
  ForbiddenViolation(int counter, String description, String locationInfo, int lineNo) {
    this(counter, null, description, locationInfo, lineNo);
  }

  ForbiddenViolation(int counter, Method targetMethod, String description, String locationInfo, int lineNo) {
    this.groupId = counter;
    this.targetMethod = targetMethod;
    this.description = description;
    this.locationInfo = locationInfo;
    this.lineNo = lineNo;
  }
  
  public void setGroupId(int groupId) {
    this.groupId = groupId;
  }
  
  public int getGroupId() {
    return groupId;
  }
  
  @SuppressWarnings("resource")
  public String format(String className, String source) {
    final StringBuilder sb = new StringBuilder(description);
    sb.append("\n  in ").append(className);
    if (source != null) {
      if (lineNo >= 0) {
        new Formatter(sb, Locale.ENGLISH).format(" (%s:%d)", source, lineNo).flush();
      } else {
        new Formatter(sb, Locale.ENGLISH).format(" (%s, %s)", source, locationInfo).flush();
      }
    } else {
      new Formatter(sb, Locale.ENGLISH).format(" (%s)", locationInfo).flush();
    }
    return sb.toString();
  }

  // not before Java 6: @Override
  public int compareTo(ForbiddenViolation other) {
    if (this.groupId == other.groupId) {
      return Long.signum((long) this.lineNo - (long) other.lineNo);
    } else {
      return Long.signum((long) this.groupId - (long) other.groupId);
    }
  }
  
}
