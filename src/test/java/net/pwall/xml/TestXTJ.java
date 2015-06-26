/*
 * @(#) TestXTJ.java
 */

package net.pwall.xml;

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

}
