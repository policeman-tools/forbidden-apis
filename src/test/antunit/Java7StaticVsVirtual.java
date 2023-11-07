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
    Z.valueOf(data).toString();  // Line 28 -- should pass
    Integer.toString(Y.a); // Line 29 -- violation (static field access)
    Integer.toString(Z.a); // Line 30 -- should pass (hidden)
    new X().get(0);  // Line 31 -- violation (virtual methods detected regardles how they are called)
    new Y().get(0);  // Line 32 -- violation (virtual methods detected regardles how they are called)
    new Z().get(0);  // Line 33 -- violation (virtual methods detected regardles how they are called)
    Integer.toString(new Y().b);  // Line 34 -- violation
    Integer.toString(new Z().b);  // Line 35 -- should pass (hidden)
  }

  public static class X extends BitSet { }

  public static class Y extends X {
    public static int a;
    public int b;
    
    public static BitSet valueOf(long[] longs) {
      return new BitSet();
    }
    
    @Override
    public boolean get(int bit) {
      return false;
    }
  }

  public static class Z extends Y {
    public static int a; // hides field in superclass
    public int b; // hides field in superclass

    public String goStatic() {
      return valueOf(data).toString();  // Line 41 -- should pass
    }
    public boolean goVirtual() {
      return get(0);  // Line 59 -- violation (virtual methods detected regardles how they are called)
    }
  }
}
