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

import sun.misc.BASE64Encoder;

/* Needs Sun/Oracle JDK to compile!
 * The binary class file is packaged together with the source distribution,
 * because it cannot be regenerated on every Java installation!
 */

class Demo1 {
  static {
    new BASE64Encoder().encode(new byte[0]);
  }
}
