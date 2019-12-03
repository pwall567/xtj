/*
 * @(#) TemplateException.java
 *
 * xtj XML Templating for Java
 * Copyright (c) 2015, 2016, 2019 Peter Wall
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.pwall.xml;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import net.pwall.util.UserError;

public class TemplateException extends UserError {

    private static final long serialVersionUID = 6540965713285875008L;
    private static final String idAttrName = "id";

    private Node node;
    private String attrName;

    public TemplateException(Node node, String message) {
        super(message);
        this.node = node;
    }

    public TemplateException(Element element, String attrName, String message) {
        super(message);
        this.node = element;
        this.attrName = attrName;
    }

    public TemplateException(String message) {
        this(null, message);
    }

    public Node getNode() {
        return node;
    }

    public String getAttrName() {
        return attrName;
    }

    public String getXPath() {
        if (node == null)
            return null;
        StringBuilder sb = new StringBuilder();
        if (attrName != null)
            sb.append("/ @").append(attrName);
        else if (node instanceof Text) {
            sb.append("/ text()");
            Node parent = node.getParentNode();
            if (parent != null) {
                int count = 0;
                int thisIndex = 0;
                NodeList siblings = parent.getChildNodes();
                for (int i = 0, n = siblings.getLength(); i < n; i++) {
                    Node sibling = siblings.item(i);
                    if (sibling == node)
                        thisIndex = count;
                    else if (sibling instanceof Text)
                        count++;
                }
                if (count > 0)
                    sb.append('[').append(thisIndex + 1).append(']');
            }
            node = node.getParentNode();
        }
        while (node != null && node instanceof Element) {
            sb.insert(0, ' ');
            sb.insert(0, getXPathElement((Element)node));
            sb.insert(0, "/ ");
            node = node.getParentNode();
        }
        return sb.toString();
    }

    private static String getXPathElement(Element element) {
        StringBuilder sb = new StringBuilder(element.getTagName());
        String id = element.getAttribute(idAttrName);
        if (!isEmpty(id))
            sb.append('#').append(id);
        else {
            Node parent = element.getParentNode();
            if (parent != null) {
                String tagName = element.getTagName();
                int count = 0;
                int thisIndex = 0;
                NodeList siblings = parent.getChildNodes();
                for (int i = 0, n = siblings.getLength(); i < n; i++) {
                    Node sibling = siblings.item(i);
                    if (sibling == element)
                        thisIndex = count;
                    else if (sibling instanceof Element && ((Element)sibling).getTagName().equals(tagName))
                        count++;
                }
                if (count > 0)
                    sb.append('[').append(thisIndex + 1).append(']');
            }
        }
        return sb.toString();
    }

    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

}
