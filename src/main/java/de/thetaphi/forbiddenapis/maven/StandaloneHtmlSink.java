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

package de.thetaphi.forbiddenapis.maven;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.maven.doxia.sink.Sink;

/**
 * Minimal {@link Sink} that renders a standalone HTML page into a {@link StringBuilder}.
 * It is used if a report Mojo is executed directly and not as part of the Maven site.
 * <p>
 * The sink is implemented as a dynamic proxy, so it works with every version of the Doxia Sink API.
 * It only supports the events used by {@link ReportRenderer}, which exist with identical
 * signatures in Doxia 1.x and Doxia 2.x. All other events throw {@link UnsupportedOperationException},
 * so the tests detect if the renderer uses an event that may not be available in all Doxia versions.
 */
final class StandaloneHtmlSink implements InvocationHandler {

  private static final String STYLE = "<style>\n"
      + "body { font-family: sans-serif; margin: 2em; }\n"
      + "table { border-collapse: collapse; margin-bottom: 1em; }\n"
      + "th, td { border: 1px solid #ccc; padding: 0.25em 0.5em; text-align: left; vertical-align: top; }\n"
      + "th { background-color: #eee; }\n"
      + "</style>\n";

  private final StringBuilder out;

  private StandaloneHtmlSink(StringBuilder out) {
    this.out = out;
  }

  /** Creates a new sink that appends the HTML page to the given {@link StringBuilder}. */
  static Sink create(StringBuilder out) {
    return (Sink) Proxy.newProxyInstance(Sink.class.getClassLoader(), new Class<?>[] { Sink.class }, new StandaloneHtmlSink(out));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    if (method.getDeclaringClass() == Object.class) {
      switch (method.getName()) {
        case "equals":
          return proxy == args[0];
        case "hashCode":
          return System.identityHashCode(proxy);
        case "toString":
          return getClass().getSimpleName();
        default:
          throw new UnsupportedOperationException(method.toString());
      }
    }
    final StringBuilder signature = new StringBuilder(method.getName()).append('(');
    boolean comma = false;
    for (final Class<?> type : method.getParameterTypes()) {
      if (comma) signature.append(',');
      signature.append(type.getSimpleName());
      comma = true;
    }
    signature.append(')');
    switch (signature.toString()) {
      case "head()":
        out.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n");
        break;
      case "head_()":
        out.append(STYLE).append("</head>\n");
        break;
      case "title()":
        out.append("<title>");
        break;
      case "title_()":
        out.append("</title>\n");
        break;
      case "body()":
        out.append("<body>\n");
        break;
      case "body_()":
        out.append("</body>\n</html>\n");
        break;
      case "section1()":
      case "section2()":
      case "section3()":
        out.append("<section>\n");
        break;
      case "section1_()":
      case "section2_()":
      case "section3_()":
        out.append("</section>\n");
        break;
      case "sectionTitle1()":
        out.append("<h1>");
        break;
      case "sectionTitle1_()":
        out.append("</h1>\n");
        break;
      case "sectionTitle2()":
        out.append("<h2>");
        break;
      case "sectionTitle2_()":
        out.append("</h2>\n");
        break;
      case "sectionTitle3()":
        out.append("<h3>");
        break;
      case "sectionTitle3_()":
        out.append("</h3>\n");
        break;
      case "paragraph()":
        out.append("<p>");
        break;
      case "paragraph_()":
        out.append("</p>\n");
        break;
      case "text(String)":
        escape((String) args[0]);
        break;
      case "monospaced()":
        out.append("<code>");
        break;
      case "monospaced_()":
        out.append("</code>");
        break;
      case "link(String)":
        out.append("<a href=\"");
        escape((String) args[0]);
        out.append("\">");
        break;
      case "link_()":
        out.append("</a>");
        break;
      case "anchor(String)":
        out.append("<a id=\"");
        escape((String) args[0]);
        out.append("\">");
        break;
      case "anchor_()":
        out.append("</a>\n");
        break;
      case "list()":
        out.append("<ul>\n");
        break;
      case "list_()":
        out.append("</ul>\n");
        break;
      case "listItem()":
        out.append("<li>");
        break;
      case "listItem_()":
        out.append("</li>\n");
        break;
      case "table()":
        out.append("<table>\n");
        break;
      case "table_()":
        out.append("</table>\n");
        break;
      case "tableRows(int[],boolean)":
      case "tableRows_()":
        break;
      case "tableRow()":
        out.append("<tr>");
        break;
      case "tableRow_()":
        out.append("</tr>\n");
        break;
      case "tableHeaderCell()":
        out.append("<th>");
        break;
      case "tableHeaderCell_()":
        out.append("</th>");
        break;
      case "tableCell()":
        out.append("<td>");
        break;
      case "tableCell_()":
        out.append("</td>");
        break;
      case "flush()":
      case "close()":
        break;
      default:
        throw new UnsupportedOperationException("Sink event is not supported (it may not be available in all Doxia versions): " + signature);
    }
    return null;
  }

  private void escape(String text) {
    for (int i = 0, c = text.length(); i < c; i++) {
      final char ch = text.charAt(i);
      switch (ch) {
        case '&':
          out.append("&amp;");
          break;
        case '<':
          out.append("&lt;");
          break;
        case '>':
          out.append("&gt;");
          break;
        case '"':
          out.append("&quot;");
          break;
        case '\'':
          out.append("&#39;");
          break;
        default:
          out.append(ch);
      }
    }
  }

}
