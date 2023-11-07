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

/* The binary class file is packaged together with the source distribution.
 */

import java.util.BitSet;

public class Java7StaticVsVirtual {
  public static final long[] data = new long[] {1, 2, 3, 4};
  
  public static void main(String[] args) {
    X.valueOf(data).toString();  // Line 26 -- the only violation for static BitSet#valueOf(**)
    Y.valueOf(data).toString();  // Line 27 -- should pass
    (new Z()).go();
  }

  public static class X extends BitSet { }

  public static class Y extends X {
    public static BitSet valueOf(long[] longs) {
      return new BitSet();
    }
  }

  public static class Z extends Y {
    public String go() {
      return valueOf(data).toString();  // Line 41 -- should pass
    }
  }
}
