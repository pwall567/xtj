/*
 * @(#) TestTemplateProcessor.java
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

package net.pwall.xml.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;

import org.junit.Test;
import org.w3c.dom.Document;
import static org.junit.Assert.*;

import net.pwall.xml.TemplateProcessor;

public class TestTemplateProcessor {

    @Test
    public void testNoArgConstructor() {
        TemplateProcessor templateProcessor = new TemplateProcessor();
        assertNull(templateProcessor.getDom());
        assertEquals(TemplateProcessor.defaultNamespace, templateProcessor.getNamespace());
        assertNull(templateProcessor.getWhitespace());
    }

    @Test
    public void testConstructorWithDOM() throws Exception {
        File currentDir = new File(".");
        URL baseURL = new URL("file://" + currentDir.getAbsoluteFile());
        URL templateURL = new URL(baseURL, "src/test/resources/testtemp.xml");
        TemplateProcessor templateProcessor = new TemplateProcessor(templateURL);
        Document document = templateProcessor.getDom();
        assertNotNull(document);
        assertEquals("xt:template", document.getDocumentElement().getTagName());
        assertEquals(TemplateProcessor.defaultNamespace, templateProcessor.getNamespace());
        assertNull(templateProcessor.getWhitespace());
    }

    @Test
    public void testFactoryFromFile() throws FileNotFoundException {
        TemplateProcessor templateProcessor = TemplateProcessor.from(new File("src/test/resources/testtemp.xml"));
        Document document = templateProcessor.getDom();
        assertNotNull(document);
        assertEquals("xt:template", document.getDocumentElement().getTagName());
        assertEquals(TemplateProcessor.defaultNamespace, templateProcessor.getNamespace());
        assertNull(templateProcessor.getWhitespace());
    }

    @Test
    public void testFactoryFromFilename() throws FileNotFoundException {
        TemplateProcessor templateProcessor = TemplateProcessor.from("src/test/resources/testtemp.xml");
        Document document = templateProcessor.getDom();
        assertNotNull(document);
        assertEquals("xt:template", document.getDocumentElement().getTagName());
        assertEquals(TemplateProcessor.defaultNamespace, templateProcessor.getNamespace());
        assertNull(templateProcessor.getWhitespace());
    }

}
