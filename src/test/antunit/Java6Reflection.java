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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

class Java6Reflection {
  static Field test() throws Exception {
    Class c = Java6Reflection.class;
    Field f = c.getDeclaredField("field1");
    f.setAccessible(true);
    
    Method m = c.getDeclaredMethod("testMethod");
    m.setAccessible(true);
    m.invoke(new Java6Reflection());
    
    return f;
  }
  
  private Integer field1;

  private void testMethod() {
    // nothing to do here
  }
}
