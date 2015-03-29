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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/* Needs JDK 8 to compile!
 * The binary class file is packaged together with the source distribution,
 * because it cannot be regenerated on every Java installation!
 */

@Java8Annotations.FooBar
@Deprecated
class Java8Annotations<@Java8Annotations.FooBar X> {
  
  @Deprecated
  static void test(@FooBar int param1, @FooBar long param2) {
    try {
      new @FooBar Java8Annotations();
      test(param1, param2);
    } catch (@FooBar StackOverflowError e) {
      System.out.println(FooBar.class.getName());
    }
  }
  
  final class InnerClassWithCtorParam {
    public InnerClassWithCtorParam(@FooBar X param) {
      System.out.println(Java8Annotations.this);
      System.out.println(param);
    }
  }
  
  @Deprecated
  public int testField1;
  
  @FooBar
  public int testField2;
  
  @ClassFileOnly
  public StringBuilder testField3;
  
  @Retention(value=RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
  static @interface FooBar {}
  
  @Retention(value=RetentionPolicy.CLASS)
  @Target({ElementType.FIELD, ElementType.METHOD})
  static @interface ClassFileOnly {}
}
