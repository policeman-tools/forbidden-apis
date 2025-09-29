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

package de.thetaphi.forbiddenapis;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Simple version comparator (to support downgrading versions in bundled-signatures names). */
final class VersionCompare {
  
  private static final Pattern DOT_SPLITTER_PATTERN = Pattern.compile("\\.");

  private VersionCompare() {}
  
  public static int compareVersions(String version1, String version2) {
    final String[] version1Splits = DOT_SPLITTER_PATTERN.split(version1),
        version2Splits = DOT_SPLITTER_PATTERN.split(version2);
    final int maxLengthOfVersionSplits = Math.max(version1Splits.length, version2Splits.length);
    
    for (int i = 0; i < maxLengthOfVersionSplits; i++) {
      final int v1 = i < version1Splits.length ? Integer.parseInt(version1Splits[i]) : 0;
      final int v2 = i < version2Splits.length ? Integer.parseInt(version2Splits[i]) : 0;
      final int compare = Integer.compare(v1, v2);
      if (compare != 0) {
        return compare;
      }
    }
    return 0;
  }
  
  public static Comparator<String> VERSION_COMPARATOR = new Comparator<String>() {
    @Override
    public int compare(String version1, String version2) {
      return compareVersions(version1, version2);
    }
  };

  public static Comparator<String> BUNDLED_SIGNATURES_COMPARATOR = new Comparator<String>() {
    @Override
    public int compare(String bs1, String bs2) {
      final Matcher m1 = Constants.ENDS_WITH_VERSION_PATTERN.matcher(bs1),
          m2 = Constants.ENDS_WITH_VERSION_PATTERN.matcher(bs2);
      if (m1.matches() && m2.matches()) {
        final int prefixCmp = m1.group(1).compareTo(m2.group(1));
        if (prefixCmp != 0) {
          return prefixCmp;
        }
        return compareVersions(m1.group(2), m2.group(2));
      } else {
        return bs1.compareTo(bs2);
      }
    }
  };

}
