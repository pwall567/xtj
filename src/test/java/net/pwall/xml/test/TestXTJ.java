/*
 * @(#) TestXTJ.java
 *
 * xtj XML Templating for Java
 * Copyright (c) 2015, 2016 Peter Wall
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

package net.pwall.xml.test;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import net.pwall.xml.TemplateProcessor;
import net.pwall.xml.XML;

public class TestXTJ {

    private Document templateDOM;

    @Before
    public void setUp() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/testtemp.xml")) {
            templateDOM = XML.getDocumentBuilderNS().parse(is);
        }
    }

    @Test
    public void test1() throws Exception {
        TemplateProcessor tp = new TemplateProcessor(templateDOM, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tp.process(baos);
        byte[] array = baos.toByteArray();
        System.out.println(new String(array));
        ByteArrayInputStream bais = new ByteArrayInputStream(array);
        Document resultDOM = XML.parse(bais);
        Element element = resultDOM.getDocumentElement();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element childElement = (Element)node;
                if ("test-3".equals(childElement.getAttribute("id"))) {
                    node = childElement.getFirstChild();
                    assertTrue(node instanceof Text);
                    assertEquals("a = 3; a*a = 9", ((Text)node).getData());
                    break;
                }
            }
        }
    }

    @Test
    public void test2() throws Exception {
        TemplateProcessor tp = TemplateProcessor.from("src/test/resources/testxhtml.xml");
        tp.setVariable("content", "Hello, World!");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tp.process(baos);
        byte[] array = baos.toByteArray();
        System.out.println(new String(array));
    }

}
