/*
 * (C) Copyright 2014 Uwe Schindler (Generics Policeman) and others.
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

import java.util.*;

class Java5ClassReferences {
  static Integer[][] test() {
    Integer.class.getName();
    System.out.println(Integer[].class);
    Integer[] arr = new Integer[1];
    arr[0] = new Integer(0);
    return new Integer[1][1];
  }
  
  private Integer field1;
  private final Integer[] field2 = null;
  
  // we forbid the superclass or interface, but concrete instance is used:
  public List<?> list1;
  public ArrayList<?> list2;
  public HashSet<?> set1;
}
