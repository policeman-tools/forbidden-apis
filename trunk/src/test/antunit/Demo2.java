/*
 * (C) Copyright 2013 Uwe Schindler (Generics Policeman) and others.
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

import java.io.Closeable;
import java.util.Arrays;
import java.util.ArrayList;

/* Needs JDK 8 to compile!
 * The binary class file is packaged together with the source distribution,
 * because it cannot be regenerated on every Java installation!
 */

interface Demo2 extends Closeable {
  default void close2() {
    new StringBuilder().append("police");
    Arrays.sort(new Integer[0], (Integer a, Integer b) -> a.compareTo(b));
  }
  
  static void test() {
    // use a static method as closure
    Arrays.sort(new Float[0], Float::compare);
    // use a closure which is implemented by (a,b) -> a.compareTo(b)
    Arrays.sort(new Integer[0], Integer::compareTo);
    // a thread which creates an ArrayList by its constructor:
    new Thread(ArrayList::new).run();
  }
}
