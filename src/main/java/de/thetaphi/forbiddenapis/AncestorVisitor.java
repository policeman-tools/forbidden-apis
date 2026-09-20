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

public abstract class AncestorVisitor {
  
  protected final RelatedClassLookup lookup;
  protected final Reporter reporter;
  
  public static enum AncestorVisitorResult { VIOLATION, PROCEED, STOP }
  
  public AncestorVisitor(Reporter reporter, RelatedClassLookup lookup) {
    this.reporter = reporter;
    this.lookup = lookup;
  }

  protected abstract AncestorVisitorResult visit(ClassMetadata c, String origName, boolean isInterfaceOfAncestor, boolean previousInRuntime);
  
  public boolean visitAncestors(ClassMetadata cls, boolean visitSelf, boolean visitInterfacesFirst) {
    if (visitSelf) {
      switch (visit(cls, cls.className, cls.isInterface, cls.isRuntimeClass)) {
        case STOP:
          return false;
        case VIOLATION:
          return true;
        case PROCEED:
          // fallthrough
      }
    }
    return visitAncestorsRecursive(cls, cls.className, cls.isRuntimeClass, visitInterfacesFirst) == AncestorVisitorResult.VIOLATION;
  }
  
  private AncestorVisitorResult visitSuperclassRecursive(ClassMetadata cls, String origName, boolean previousInRuntime, boolean visitInterfacesFirst) {
    if (cls.superName == null) {
      return AncestorVisitorResult.PROCEED;
    }
    final ClassMetadata c = lookup.lookupRelatedClass(cls.superName, origName);
    if (c == null) {
      return AncestorVisitorResult.PROCEED;      
    }
    AncestorVisitorResult result = visit(c, origName, false, previousInRuntime);
    if (result == AncestorVisitorResult.VIOLATION) {
      return result;
    } else if (result == AncestorVisitorResult.PROCEED) {
      result = visitAncestorsRecursive(c, origName, cls.isRuntimeClass, visitInterfacesFirst);
      if (result != AncestorVisitorResult.PROCEED) {
        return result;
      }
    }
    return AncestorVisitorResult.PROCEED;
  }
  
  private AncestorVisitorResult visitInterfacesRecursive(ClassMetadata cls, String origName, boolean previousInRuntime, boolean visitInterfacesFirst) {
    if (cls.interfaces == null) {
      return AncestorVisitorResult.PROCEED;
    }
    for (String intf : cls.interfaces) {
      final ClassMetadata c = lookup.lookupRelatedClass(intf, origName);
      if (c == null) continue;
      AncestorVisitorResult result = visit(c, origName, true, previousInRuntime);
      if (result == AncestorVisitorResult.VIOLATION) {
        return result;
      } else if (result == AncestorVisitorResult.PROCEED) {
        result = visitAncestorsRecursive(c, origName, cls.isRuntimeClass, visitInterfacesFirst);
        if (result != AncestorVisitorResult.PROCEED) {
          return result;
        }
      }
    }
    return AncestorVisitorResult.PROCEED;
  }
  
  private AncestorVisitorResult visitAncestorsRecursive(ClassMetadata cls, String origName, boolean previousInRuntime, boolean visitInterfacesFirst) {
    AncestorVisitorResult result;
    if (visitInterfacesFirst) {
      result = visitInterfacesRecursive(cls, origName, previousInRuntime, visitInterfacesFirst);
      if (result != AncestorVisitorResult.PROCEED) {
        return result;
      }
    }
    result = visitSuperclassRecursive(cls, origName, previousInRuntime, visitInterfacesFirst);
    if (result != AncestorVisitorResult.PROCEED) {
      return result;
    }
    if (!visitInterfacesFirst) {
      result = visitInterfacesRecursive(cls, origName, previousInRuntime, visitInterfacesFirst);
      if (result != AncestorVisitorResult.PROCEED) {
        return result;
      }
    }
    return AncestorVisitorResult.PROCEED;
  }
  
}
