/*
 * @(#) TemplateProcessor.java
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

package net.pwall.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import net.pwall.el.Expression;
import net.pwall.el.Functions;
import net.pwall.html.HTMLFormatter;
import net.pwall.json.JSON;
import net.pwall.json.JSONException;
import net.pwall.util.Strings;
import net.pwall.util.UserError;

import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.AttributesImpl;

/**
 * XML Template Processor for Java
 *
 * @author Peter Wall
 */
public class TemplateProcessor {

    // TODO list: (* means completed)
    //*1. Reconsider use of exceptions - make TemplateException a subclass of UserError?
    //*2. Implement <include>
    // 3. Consider <set> with contents - allocate element itself to a variable to allow access
    //    to elements contained within it? (Less important since 4 below completed)
    //*4. In Expression - allow JavaScript object syntax?
    //    e.g. <xt:set name="array" value="['a','b','c']">
    // 5. Consider passing context object on each call - avoids potential error in unwinding
    // 6. Update namespace version number to 1.0 (or greater?)
    // 7. Consider a "for" attribute (or "while") to go with "if"
    // 8. Block use of <set> to modify an existing variable?
    // 9. Is <macro> the best name for this functionality?
    //10. Add logging
    //*11. <for collection="xxx" index="aa"> (access index within collection)
    //*12. xt:if attribute must be applied to inner elements (case, param, ...)
    // 13. xt:element - create element with dynamic name (xt:attribute, xt:content/xt:data)
    // 14. xt:expect (with default=) in macros - declare parameters expected
    // 15. Manage namespaces on xml output
    // 16. Change <for> name= to variable= ?

    public static final String applicationName;
    public static final String applicationVersion;
    public static final String applicationHeading;
    static {
        Package pkg = TemplateProcessor.class.getPackage();
        String implementationTitle = pkg.getImplementationTitle();
        applicationName = implementationTitle != null ? implementationTitle : "xtj";
        applicationVersion = pkg.getImplementationVersion();
        applicationHeading = applicationVersion != null ? applicationName + " V" + applicationVersion : applicationName;
    }

    public static final String defaultNamespace = "http://pwall.net/xml/xt/1.0";
    public static final String jstlFunctionsURL = "http://java.sun.com/jsp/jstl/functions";

    private static final String templateElementName = "template";
    private static final String macroElementName = "macro";
    private static final String errorElementName = "error";
    private static final String doctypeElementName = "doctype";
    private static final String includeElementName = "include";
    private static final String setElementName = "set";
    private static final String ifElementName = "if";
    private static final String switchElementName = "switch";
    private static final String caseElementName = "case";
    private static final String forElementName = "for";
    private static final String callElementName = "call";
    private static final String paramElementName = "param";
    private static final String commentElementName = "comment";
    private static final String copyElementName = "copy";
    private static final String interceptElementName = "intercept";

    private static final String whitespaceAttrName = "whitespace";
    private static final String outputAttrName = "output";
    private static final String outputXML = "xml";
    private static final String outputHTML = "html";
    private static final String prefixAttrName = "prefix";
    private static final String textAttrName = "text";
    private static final String systemAttrName = "system";
    private static final String publicAttrName = "public";
    private static final String nameAttrName = "name";
    private static final String documentAttrName = "document";
    private static final String valueAttrName = "value";
    private static final String testAttrName = "test";
    private static final String collectionAttrName = "collection";
    private static final String fromAttrName = "from";
    private static final String toAttrName = "to";
    private static final String byAttrName = "by";
    private static final String elementAttrName = "element";
    private static final String optionAttrName = "option";
    private static final String ifAttrName = "if";
    private static final String hrefAttrName = "href";
    private static final String indexAttrName = "index";

    private static final String prefixTrue = "true";
    private static final String prefixFalse = "false";
    private static final String prefixYes = "yes";
    private static final String prefixNo = "no";
    private static final String whitespaceNone = "none";
    private static final String whitespaceAll = "all";
    private static final String whitespaceIndent = "indent";
    private static final String optionInclude = "include";

    private static final String versionSwitch = "-version";
    private static final String nojstlSwitch = "-nojstl";
    private static final String templateSwitch = "-template";
    private static final String xmlSwitch = "-xml";
    private static final String jsonSwitch = "-json";
    private static final String propSwitch = "-prop";
    private static final String outSwitch = "-out";
    private static final String dSwitch = "-D";

    private static Map<String, Document> documentMap = new HashMap<>();

    private Document dom;
    private Expression.Parser parser;
    private TemplateContext context;
    private String namespace;
    private String whitespace;
    private boolean prefixXML;

    public TemplateProcessor() {
        dom = null;
        parser = new Expression.Parser();
        parser.setConditionalAllowed(true);
        context = new TemplateContext(null, null);
        namespace = defaultNamespace;
        whitespace = null;
        prefixXML = false;
    }

    public TemplateProcessor(Document dom, URL url) {
        this();
        setTemplate(dom, url);
    }

    public TemplateProcessor(URL url) {
        this(getDocument(url), url);
    }

    public static TemplateProcessor from(File file) throws FileNotFoundException {
        if (!file.exists())
            throw new FileNotFoundException(file.getAbsolutePath());
        try {
            return new TemplateProcessor(new URL("file://" + file.getAbsoluteFile()));
        }
        catch (MalformedURLException e) {
            throw new RuntimeException("Unexpected MalformedURLException", e);
        }
    }

    public static TemplateProcessor from(String filename) throws FileNotFoundException {
        File file = new File(filename);
        return from(file);
    }

    public Document getDom() {
        return dom;
    }

    public void setTemplate(Document dom, URL url) {
        this.dom = Objects.requireNonNull(dom);
        context = new TemplateContext(context, dom.getDocumentElement());
        context.setURL(url);
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getWhitespace() {
        return whitespace;
    }

    public void setWhitespace(String whitespace) throws TemplateException {
        if (!(whitespaceNone.equalsIgnoreCase(whitespace) ||
                whitespaceAll.equalsIgnoreCase(whitespace) ||
                whitespaceIndent.equalsIgnoreCase(whitespace)))
            throw new TemplateException("Illegal whitespace option - " + whitespace);
        this.whitespace = whitespace;
    }

    public boolean isPrefixXML() {
        return prefixXML;
    }

    public void setPrefixXML(boolean prefixXML) {
        this.prefixXML = prefixXML;
    }

    public void setPrefixXML(String prefixXML) throws TemplateException {
        if (!(prefixTrue.equalsIgnoreCase(prefixXML) || prefixFalse.equalsIgnoreCase(prefixXML) ||
                prefixYes.equalsIgnoreCase(prefixXML) || prefixNo.equalsIgnoreCase(prefixXML)))
            throw new TemplateException("Illegal prefix option - " + prefixXML);
        setPrefixXML(prefixTrue.equalsIgnoreCase(prefixXML) || prefixYes.equalsIgnoreCase(prefixXML));
    }

    public void setVariable(String identifier, Object object) {
        context.setVariable(identifier, object);
    }

    public <V> void setVariables(Map<String, V> map) {
        for (Map.Entry<String, V> entry : map.entrySet())
            context.setVariable(entry.getKey(), entry.getValue());
    }

    public void addNamespace(String uri, Object impl) {
        context.addNamespace(uri, impl);
    }

    public void process(OutputStream os) throws TemplateException {
        if (context == null)
            throw new IllegalStateException("No template specified");
        Element documentElement = dom.getDocumentElement();
        if (XML.matchNS(documentElement, templateElementName, namespace)) {
            String whitespaceOption = substAttr(documentElement, whitespaceAttrName);
            if (!isEmpty(whitespaceOption))
                setWhitespace(whitespaceOption);
            String outputAttr = substAttr(documentElement, outputAttrName);
            if (!isEmpty(outputAttr)) {
                if (outputAttr.equalsIgnoreCase(outputXML)) {
                    applyPrefixAttr(documentElement, documentElement.getAttribute(prefixAttrName));
                    processXML(os);
                }
                else if (outputAttr.equalsIgnoreCase(outputHTML))
                    processHTML(os);
                else
                    throw new TemplateException(documentElement, outputAttrName,
                            "Illegal " + outputAttrName + ": " + outputAttr);
            }
            else
                processXML(os);
        }
        else
            processByOutputAttr(os, documentElement, documentElement.getAttributeNS(namespace, outputAttrName),
                    documentElement.getAttributeNS(namespace, prefixAttrName));
    }

    private void processByOutputAttr(OutputStream os, Element documentElement, String outputAttrValue,
            String prefixAttrValue) throws TemplateException {
        if (outputAttrValue != null) {
            try {
                String substValue = subst(outputAttrValue);
                if (!isEmpty(substValue)) {
                    if (substValue.equalsIgnoreCase(outputXML)) {
                        applyPrefixAttr(documentElement, prefixAttrValue);
                        processXML(os);
                    }
                    else if (substValue.equalsIgnoreCase(outputHTML)) {
                        processHTML(os);
                    }
                    else
                        throw new TemplateException(documentElement, outputAttrName,
                                "Illegal " + outputAttrName + ": " + substValue);
                }
            }
            catch (Expression.ExpressionException eee) {
                throw new TemplateException(documentElement, outputAttrName,
                        "Error in expression substitution" + '\n' + eee.getMessage());
            }
        }
        else
            processXML(os);
    }

    private void applyPrefixAttr(Element documentElement, String prefixAttrValue) {
        if (prefixAttrValue != null) {
            try {
                String substValue = subst(prefixAttrValue);
                if (!isEmpty(substValue))
                    setPrefixXML(substValue);
            }
            catch (Expression.ExpressionException eee) {
                throw new TemplateException(documentElement, prefixAttrName,
                        "Error in expression substitution" + '\n' + eee.getMessage());
            }
        }
    }

    public void processXML(OutputStream os) throws TemplateException {
        if (context == null)
            throw new IllegalStateException("No template specified");
        try (XMLFormatter formatter = new XMLFormatter(os)) {
            if (whitespaceNone.equalsIgnoreCase(whitespace))
                formatter.setWhitespace(XMLFormatter.Whitespace.NONE);
            else if (whitespaceAll.equalsIgnoreCase(whitespace))
                formatter.setWhitespace(XMLFormatter.Whitespace.ALL);
            else if (whitespaceIndent.equalsIgnoreCase(whitespace))
                formatter.setWhitespace(XMLFormatter.Whitespace.INDENT);
            if (prefixXML)
                formatter.prefix();
            formatter.startDocument();
            Element documentElement = dom.getDocumentElement();
            if (XML.matchNS(documentElement, templateElementName, namespace))
                processElementContents(documentElement, formatter, false);
            else
                processElement(documentElement, formatter);
            formatter.endDocument();
        }
        catch (IOException ioe) {
            throw new RuntimeException("Unexpected I/O exception", ioe);
        }
        catch (SAXException saxe) {
            throw new RuntimeException("Unexpected SAX exception", saxe);
        }
    }

    public void processHTML(OutputStream os) throws TemplateException {
        if (context == null)
            throw new IllegalStateException("No template specified");
        try (HTMLFormatter formatter = new HTMLFormatter(os)) {
            if (whitespaceNone.equalsIgnoreCase(whitespace))
                formatter.setWhitespace(HTMLFormatter.Whitespace.NONE);
            else if (whitespaceAll.equalsIgnoreCase(whitespace))
                formatter.setWhitespace(HTMLFormatter.Whitespace.ALL);
            else if (whitespaceIndent.equalsIgnoreCase(whitespace))
                formatter.setWhitespace(HTMLFormatter.Whitespace.INDENT);
            formatter.startDocument();
            Element documentElement = dom.getDocumentElement();
            if (XML.matchNS(documentElement, templateElementName, namespace))
                processElementContents(documentElement, formatter, false);
            else
                processElement(documentElement, formatter);
            formatter.endDocument();
        }
        catch (IOException ioe) {
            throw new RuntimeException("Unexpected I/O exception", ioe);
        }
        catch (SAXException saxe) {
            throw new RuntimeException("Unexpected SAX exception", saxe);
        }
    }

    private void processElement(Element element, DefaultHandler2 formatter) throws TemplateException {
        if (isIncluded(element)) {
            if (XML.matchNS(element, errorElementName, namespace))
                processError(element);
            else if (XML.matchNS(element, doctypeElementName, namespace))
                processDoctype(element, formatter);
            else if (XML.matchNS(element, includeElementName, namespace))
                processInclude(element, formatter);
            else if (XML.matchNS(element, setElementName, namespace))
                processSet(element, formatter);
            else if (XML.matchNS(element, ifElementName, namespace))
                processIf(element, formatter);
            else if (XML.matchNS(element, switchElementName, namespace))
                processSwitch(element, formatter);
            else if (XML.matchNS(element, forElementName, namespace))
                processFor(element, formatter);
            else if (XML.matchNS(element, callElementName, namespace))
                processCall(element, formatter);
            else if (XML.matchNS(element, commentElementName, namespace))
                processComment(element, formatter);
            else if (XML.matchNS(element, copyElementName, namespace))
                processCopy(element, formatter);
            else
                outputElement(element, formatter);
        }
    }

    private boolean isIncluded(Element element) throws TemplateException {
        // i.e. not excluded by xt:if=""
        Attr ifAttr = element.getAttributeNodeNS(namespace, ifAttrName);
        if (ifAttr != null) {
            String test = ifAttr.getValue();
            try {
                String substTest = subst(test);
                if (!isEmpty(substTest) && !parser.parseExpression(substTest, context).asBoolean())
                    return false;;
            }
            catch (Expression.ExpressionException eee) {
                throw new TemplateException(element, ifAttr.getName(),
                        "Error in \"if\" attribute - " + test + '\n' +eee.getMessage());
            }
        }
        return true;
    }

    private void processElementContents(Element element, DefaultHandler2 formatter, boolean trim)
            throws TemplateException {
        NodeList childNodes = element.getChildNodes();
        int start = 0;
        int end = childNodes.getLength();
        if (trim) {
            while (start < end && XML.isCommentOrEmpty(childNodes.item(start)))
                start++;
            while (start < end && XML.isCommentOrEmpty(childNodes.item(end - 1)))
                end--;
        }
        for (int i = start, n = end; i < n; i++) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE &&
                    XML.matchNS((Element)childNode, macroElementName, namespace))
                context.addMacro((Element)childNode);
        }
        for (int i = start, n = end; i < n; i++) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (!XML.matchNS((Element)childNode, macroElementName, namespace))
                    processElement((Element)childNode, formatter);
            }
            else if (childNode.getNodeType() == Node.TEXT_NODE) {
                Text text = (Text)childNode;
                String data = text.getData();
                if (trim) {
                    if (i == start)
                        data = XML.trimLeading(data);
                    if (i == n - 1)
                        data = XML.trimTrailing(data);
                }
                outputText(text, data, formatter);
            }
        }
    }

    private void processElementContentsNewContext(Element element, DefaultHandler2 formatter, boolean trim)
            throws TemplateException {
        context = new TemplateContext(context, element);
        processElementContents(element, formatter, trim);
        context = context.getParent();
    }

    private void processError(Element element) throws TemplateException {
        String text = substAttr(element, textAttrName);
        throw new TemplateException(element, !isEmpty(text) ? text : "Error element");
    }

    private void processDoctype(Element element, DefaultHandler2 formatter) throws TemplateException {
        String name = substAttr(element, nameAttrName);
        if (isEmpty(name))
            throw new TemplateException(element, "Name missing");
        String systemAttr = substAttr(element, systemAttrName);
        String publicAttr = substAttr(element, publicAttrName);
        try {
            formatter.startDTD(name, isEmpty(publicAttr) ? null : publicAttr, isEmpty(systemAttr) ? null : systemAttr);
            // TODO implement doctype internal subset? Otherwise check element empty?
            formatter.endDTD();
        }
        catch (SAXException saxe) {
            throw new RuntimeException("Unexpected SAX exception", saxe);
        }
    }

    private void processInclude(Element element, DefaultHandler2 formatter) throws TemplateException {
        String href = substAttr(element, hrefAttrName);
        if (isEmpty(href))
                throw new TemplateException(element, "HRef missing");
        URL url = context.getURL();
        URL includeURL = null;
        Document included = null;
        try {
            includeURL = url == null ? new URL(href) : new URL(url, href);
            included = getDocument(includeURL);
        }
        catch (Exception e) {
            throw new TemplateException(element, "Error on include - " + href);
        }
        // TODO check element is empty - or allow <param> elements for included code
        Element documentElement = included.getDocumentElement();
        context = new TemplateContext(context, documentElement);
        context.setURL(includeURL);
        if (XML.matchNS(documentElement, templateElementName, namespace)) {
            // TODO process attributes on included template?
            // TODO consider forcing specification of variables used in included template
            processElementContents(documentElement, formatter, false);
        }
        else
            processElement(documentElement, formatter);
        context = context.getParent();
    }

    private void processSet(Element element, @SuppressWarnings("unused") DefaultHandler2 formatter)
            throws TemplateException {
        String name = substAttr(element, nameAttrName);
        if (!Expression.isValidIdentifier(name))
            throw new TemplateException(element, "Name missing or invalid");
        if (!isEmpty(element.getAttribute(documentAttrName)))
            throw new TemplateException(element, "Can't handle <set document= >");
        // TODO should be able to do this in Java
        String value = substAttr(element, valueAttrName);
        context.setVariable(name, evaluate(value, element, valueAttrName));
        if (!XML.isElementEmpty(element))
            throw new TemplateException(element, "Illegal content");
        // TODO allow content - if it contains elements, store as an ElementWrapper;
        // otherwise parse as JSON
    }

    private void processIf(Element element, DefaultHandler2 formatter) throws TemplateException {
        String test = substAttr(element, testAttrName);
        if (isEmpty(test))
            throw new TemplateException(element, "Test must be specified");
        boolean testResult;
        try {
            testResult = parser.parseExpression(test, context).asBoolean();
        }
        catch (Expression.ExpressionException e) {
            throw new TemplateException(element, testAttrName, "Error in test - " + test + '\n' + e.getMessage());
        }
        if (testResult)
            processElementContentsNewContext(element, formatter, true);
    }

    private void processSwitch(Element element, DefaultHandler2 formatter) throws TemplateException {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                if (isIncluded(childElement)) {
                    if (XML.matchNS(childElement, caseElementName, namespace)) {
                        String test = substAttr(childElement, testAttrName);
                        boolean testResult = true;
                        if (!isEmpty(test)) {
                            try {
                                testResult = parser.parseExpression(test, context).asBoolean();
                            }
                            catch (Expression.ExpressionException e) {
                                throw new TemplateException(childElement, testAttrName,
                                        "Error in test - " + test + '\n' + e.getMessage());
                            }
                        }
                        if (testResult) {
                            processElementContentsNewContext(childElement, formatter, true);
                            break;
                            // note - doesn't check switch contents following successful case
                        }
                    }
                    else
                        throw new TemplateException(childElement, "Illegal element within <switch>");
                }
            }
            else if (!XML.isCommentOrEmpty(node))
                throw new TemplateException(element, "Illegal content within <switch>");
        }
    }

    private void processFor(Element element, DefaultHandler2 formatter)
            throws TemplateException {
        // TODO document not yet handled
        String name = substAttr(element, nameAttrName);
        if (!isEmpty(name) && !Expression.isValidIdentifier(name))
            throw new TemplateException(element, nameAttrName, "Illegal name in <for>");
        String coll = substAttr(element, collectionAttrName);
        String from = substAttr(element, fromAttrName);
        String to = substAttr(element, toAttrName);
        String by = substAttr(element, byAttrName);
        String index = substAttr(element, indexAttrName);
        if (!isEmpty(index) && !Expression.isValidIdentifier(index))
            throw new TemplateException(element, indexAttrName, "Illegal index in <for>");
        if (!isEmpty(coll)) {
            if (!isEmpty(from) || !isEmpty(to) || !isEmpty(by))
                throw new TemplateException(element, "<for> has illegal combination of attributes");
            processForCollection(element, formatter, name, coll, index);
        }
        else if (!isEmpty(from) || !isEmpty(to) || !isEmpty(by)) {
            if (!isEmpty(index))
                throw new TemplateException(element, "<for> has illegal combination of attributes");
            Object fromObject = isEmpty(from) ? null : evaluate(from, element, fromAttrName);
            Object toObject = isEmpty(to) ? null : evaluate(to, element, toAttrName);
            Object byObject = isEmpty(by) ? null : evaluate(by, element, byAttrName);
            if (isFloating(fromObject) || isFloating(toObject) || isFloating(byObject))
                processForSequenceFloat(element, formatter, name, fromObject, toObject, byObject);
            else
                processForSequenceInt(element, formatter, name, fromObject, toObject, byObject);
        }
        else
            throw new TemplateException(element, "<for> must specify iteration type");
    }

    private void processForSequenceInt(Element element, DefaultHandler2 formatter, String name, Object from, Object to,
            Object by) throws TemplateException {
        // note - "to" value is exclusive; from="0" to="4" will perform 0,1,2,3
        int fromValue = from == null ? 0 : intValue(from, element, fromAttrName, "<for> from value invalid");
        int toValue = to == null ? 0 : intValue(to, element, toAttrName, "<for> to value invalid");
        int byValue = by == null ? 1 : intValue(by, element, byAttrName, "<for> by value invalid");
        if (byValue <= 0)
            throw new TemplateException(element, byAttrName, "<for> by value invalid");
        if (fromValue != toValue) {
            context = new TemplateContext(context, element);
            if (fromValue < toValue) {
                do {
                    if (!isEmpty(name))
                        context.setVariable(name, fromValue);
                    processElementContents(element, formatter, true);
                    fromValue += byValue;
                } while (fromValue < toValue);
            }
            else {
                do {
                    if (!isEmpty(name))
                        context.setVariable(name, fromValue);
                    processElementContents(element, formatter, true);
                    fromValue -= byValue;
                } while (fromValue > toValue);
            }
            context = context.getParent();
        }
    }

    private int intValue(Object obj, Element elem, String attrName, String msg) throws TemplateException {
        try {
            return Expression.asInt(obj);
        }
        catch (Expression.IntCoercionException e) {
            throw new TemplateException(elem, attrName, msg);
        }
    }

    private void processForSequenceFloat(Element element, DefaultHandler2 formatter, String name, Object from,
            Object to, Object by) throws TemplateException {
        // note - "to" value is exclusive; from="0" to="4" will perform 0,1,2,3
        double fromValue = from == null ? 0.0 : doubleValue(from, element, fromAttrName, "<for> from value invalid");
        double toValue = to == null ? 0.0 : doubleValue(to, element, toAttrName, "<for> to value invalid");
        double byValue = by == null ? 1.0 : doubleValue(by, element, byAttrName, "<for> by value invalid");
        if (byValue <= 0.0)
            throw new TemplateException(element, byAttrName, "<for> by value invalid");
        if (fromValue != toValue) {
            context = new TemplateContext(context, element);
            if (fromValue < toValue) {
                do {
                    if (!isEmpty(name))
                        context.setVariable(name, fromValue);
                    processElementContents(element, formatter, true);
                    fromValue += byValue;
                } while (fromValue < toValue);
            }
            else {
                do {
                    if (!isEmpty(name))
                        context.setVariable(name, fromValue);
                    processElementContents(element, formatter, true);
                    fromValue -= byValue;
                } while (fromValue > toValue);
            }
            context = context.getParent();
        }
    }

    private double doubleValue(Object obj, Element elem, String attrName, String msg) throws TemplateException {
        try {
            return Expression.asDouble(obj);
        }
        catch (Expression.DoubleCoercionException e) {
            throw new TemplateException(elem, attrName, msg);
        }
    }

    private void processForCollection(Element element, DefaultHandler2 formatter, String name, String coll,
            String index) throws TemplateException {
        Object collObject = evaluate(coll, element, collectionAttrName);
        if (collObject != null) {
            context = new TemplateContext(context, element);
            if (collObject instanceof Map<?, ?>) {
                int i = 0;
                for (Object obj : ((Map<?, ?>)collObject).values()) { // should this be keys?
                    if (!isEmpty(name))
                        context.setVariable(name, obj);
                    if (!isEmpty(index))
                        context.setVariable(index, i);
                    processElementContents(element, formatter, true);
                    i++;
                }
            }
            else if (collObject instanceof Iterable<?>) {
                int i = 0;
                for (Object obj : (Iterable<?>)collObject) {
                    if (!isEmpty(name))
                        context.setVariable(name, obj);
                    if (!isEmpty(index))
                        context.setVariable(index, i);
                    processElementContents(element, formatter, true);
                    i++;
                }
            }
            else if (collObject instanceof Object[]) {
                Object[] array = (Object[])collObject;
                for (int i = 0, n = array.length; i < n; i++) {
                    Object obj = array[i];
                    if (!isEmpty(name))
                        context.setVariable(name, obj);
                    if (!isEmpty(index))
                        context.setVariable(index, i);
                    processElementContents(element, formatter, true);
                }
            }
            else
                throw new TemplateException(element, "<for> collection must be capable of iteration");
            context = context.getParent();
        }
    }

    private void processCall(Element element, DefaultHandler2 formatter) throws TemplateException {
        String name = substAttr(element, nameAttrName);
        Element macro = context.getMacro(name);
        if (macro == null)
            throw new TemplateException(element, "macro name incorrect - " + name);
        context = new TemplateContext(context, element);
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element)childNode;
                if (isIncluded(childElement)) {
                    if (XML.matchNS(childElement, paramElementName, namespace)) {
                        name = substAttr(childElement, nameAttrName);
                        if (!Expression.isValidIdentifier(name))
                            throw new TemplateException(childElement, "Name missing or invalid");
                        String value = substAttr(childElement, valueAttrName);
                        if (isEmpty(value))
                            throw new TemplateException(childElement, "Value missing");
                        try {
                            context.setVariable(name, // must be outer context
                                    parser.parseExpression(value, context).evaluate());
                        }
                        catch (Expression.ExpressionException e) {
                            throw new TemplateException(childElement, valueAttrName,
                                    "Error in value - " + value + '\n' + e.getMessage());
                        }
                    }
                    else
                        throw new TemplateException(childElement, "Illegal element within <call>");
                }
            }
            else if (!XML.isCommentOrEmpty(childNode))
                throw new TemplateException(element, "Illegal content within <call>");
        }
        processElementContents(macro, formatter, true);
        context = context.getParent();
    }

    private void processComment(@SuppressWarnings("unused") Element element,
            @SuppressWarnings("unused") DefaultHandler2 formatter) {
        // TODO complete this
    }

    private void processCopy(Element element, DefaultHandler2 formatter) throws TemplateException {
        String elementName = substAttr(element, elementAttrName);
        if (isEmpty(elementName))
            throw new TemplateException(element, "<copy> element missing");
        // TODO if element not specified, process contents of <copy> (skipping <intercept>s)??
        context = new TemplateContext(context, element);
        Object obj = evaluate(elementName, element, elementAttrName);
        if (!(obj instanceof ElementWrapper))
            throw new TemplateException(element, elementName, "<copy> must specify element");
        Element elementToCopy = ((ElementWrapper)obj).getElement();
        boolean include = false;
        String opt = substAttr(element, optionAttrName);
        if (!isEmpty(opt)) {
            if (optionInclude.equals(opt))
                include = true;
            else
                throw new TemplateException(element, optionAttrName, "<copy> option not recognised - " + opt);
        }
        List<Intercept> intercepts = new ArrayList<>();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element) {
                Element childElement = (Element)childNode;
                if (isIncluded(childElement)) {
                    context = new TemplateContext(context, element);
                    if (XML.matchNS(childElement, interceptElementName, namespace)) {
                        elementName = substAttr(childElement, elementAttrName);
                        if (isEmpty(elementName))
                            throw new TemplateException(element, "<intercept> element missing");
                        String name = substAttr(childElement, nameAttrName);
                        if (!isEmpty(name) && !Expression.isValidIdentifier(name))
                            throw new TemplateException(childElement, nameAttrName, "Invalid name on <intercept>");
                        intercepts.add(new Intercept(elementName, childElement, name));
                    }
                    else
                        throw new TemplateException(element, "Illegal element within <copy>");
                    context = context.getParent();
                }
            }
            else if (!XML.isCommentOrEmpty(childNode))
                throw new TemplateException(element, "Illegal content within <copy>");
        }
        if (include)
            copyElement(elementToCopy, intercepts, formatter);
        else
            copyElementContents(elementToCopy, intercepts, formatter);
        context = context.getParent();
    }

    private void copyElement(Element element, List<Intercept> intercepts, DefaultHandler2 formatter)
            throws TemplateException {
        for (Intercept intercept : intercepts) {
            if (element.getTagName().equals(intercept.getTagName())) {
                Element replacement = intercept.getReplacement();
                context = new TemplateContext(context, replacement);
                String name = intercept.getName();
                if (!isEmpty(name))
                    context.setVariable(name, new ElementWrapper(element));
                processElementContents(replacement, formatter, true);
                context = context.getParent();
                return;
            }
        }
        AttributesImpl attrs = new AttributesImpl();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr)attributes.item(i);
            attrs.addAttribute(attr.getNamespaceURI(), attr.getLocalName(), attr.getNodeName(), "CDATA",
                    attr.getValue());
        }
        try {
            formatter.startElement(element.getNamespaceURI(), element.getLocalName(), element.getNodeName(), attrs);
            copyElementContents(element, intercepts, formatter);
            formatter.endElement(element.getNamespaceURI(), element.getLocalName(), element.getNodeName());
        }
        catch (SAXException saxe) {
            throw new RuntimeException("Unexpected SAX exception", saxe);
        }
    }

    private void copyElementContents(Element element, List<Intercept> intercepts, DefaultHandler2 formatter)
            throws TemplateException {
        context = new TemplateContext(context, element);
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element)
                copyElement((Element)childNode, intercepts, formatter);
            else if (childNode instanceof CDATASection) {
                try {
                    formatter.startCDATA();
                    outputData(((Text)childNode).getData(), formatter);
                    formatter.endCDATA();
                }
                catch (SAXException saxe) {
                    throw new RuntimeException("Unexpected SAX exception", saxe);
                }
            }
            else if (childNode instanceof Text)
                outputData(((Text)childNode).getData(), formatter);
        }
        context = context.getParent();
    }

    private void outputElement(Element element, DefaultHandler2 formatter) throws TemplateException {
        AttributesImpl attrs = new AttributesImpl();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr)attributes.item(i);
            if (!namespace.equals(attr.getNamespaceURI())) {
                String value = attr.getValue();
                try {
                    String substValue = subst(value);
                    if (!isEmpty(substValue))
                        attrs.addAttribute(attr.getNamespaceURI(), attr.getLocalName(), attr.getNodeName(), "CDATA",
                                substValue);
                }
                catch (Expression.ExpressionException eee) {
                    throw new TemplateException(element, attr.getName(), "Error in expression substitution - " + value);
                }
            }
        }
        try {
            formatter.startElement(element.getNamespaceURI(), element.getLocalName(), element.getNodeName(), attrs);
            processElementContents(element, formatter, false);
            formatter.endElement(element.getNamespaceURI(), element.getLocalName(), element.getNodeName());
        }
        catch (SAXException saxe) {
            throw new RuntimeException("Unexpected SAX exception", saxe);
        }
    }

    private void outputText(Text text, String data, DefaultHandler2 formatter) throws TemplateException {
        try {
            String substData = subst(data);
            outputData(substData, formatter);
        }
        catch (Expression.ExpressionException eee) {
            throw new TemplateException(text, "Error in expression substitution" + '\n' + eee.getMessage());
        }
    }

    private void outputData(String data, DefaultHandler2 formatter) {
        try {
            formatter.characters(data.toCharArray(), 0, data.length());
        }
        catch (SAXException saxe) {
            throw new RuntimeException("Unexpected SAX exception", saxe);
        }
    }

    private Object evaluate(String str, Element element, String attrName) throws TemplateException {
        try {
            return parser.parseExpression(str, context).evaluate();
        }
        catch (Expression.ExpressionException eee) {
            throw new TemplateException(element, attrName, "Error in expression evaluation" + '\n' + eee.getMessage());
        }
    }

    private String substAttr(Element element, String attrName) throws TemplateException {
        try {
            return subst(element.getAttribute(attrName));
        }
        catch (Expression.ExpressionException eee) {
            throw new TemplateException(element, attrName,
                    "Error in expression substitution" + '\n' + eee.getMessage());
        }
    }

    private String subst(String str) throws Expression.ExpressionException {
        return str == null ? null : parser.substitute(str, context);
    }

    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    private static boolean isFloating(Object obj) {
        return obj instanceof Double || obj instanceof Float;
    }

    public static void main(String[] args) {
        try {
            TemplateProcessor processor = new TemplateProcessor();
            File currentDir = new File(".");
            URL baseURL = new URL("file://" + currentDir.getAbsoluteFile());
            File out = null;
            boolean jstlFunctions = true;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals(versionSwitch)) {
                    System.err.println(applicationHeading);
                    if (args.length == 1)
                        System.exit(0);
                }
                else if (arg.equals(nojstlSwitch))
                    jstlFunctions = false;
                else if (arg.equals(templateSwitch)) {
                    if (processor.getDom() != null)
                        throw new UserError("Duplicate " + templateSwitch);
                    URL template = getArgURL(args, ++i, baseURL, templateSwitch);
                    processor.setTemplate(getDocument(template), template);
                }
                else if (arg.equals(xmlSwitch)) {
                    String ident = getArgIdent(args, ++i, xmlSwitch);
                    URL xmlURL = getArgURL(args, ++i, baseURL, xmlSwitch);
                    try {
                        Element element = getDocument(xmlURL).getDocumentElement();
                        processor.setVariable(ident, new ElementWrapper(element));
                    }
                    catch (TemplateException te) {
                        throw new UserError(xmlSwitch + " content invalid - " + args[i]);
                    }
                    catch (Exception e) {
                        throw new UserError("Error reading xml - " + args[i]);
                    }
                }
                else if (arg.equals(jsonSwitch)) {
                    String ident = getArgIdent(args, ++i, jsonSwitch);
                    URL jsonURL = getArgURL(args, ++i, baseURL, jsonSwitch);
                    try {
                        processor.setVariable(ident, JSON.parse(getURLReader(jsonURL)));
                    }
                    catch (JSONException je) {
                        throw new UserError(jsonSwitch + " content invalid - " + args[i]);
                    }
                    catch (Exception e) {
                        throw new UserError("Error reading json - " + args[i]);
                    }
                }
                else if (arg.equals(propSwitch)) {
                    String ident = getArgIdent(args, ++i, propSwitch);
                    URL propURL = getArgURL(args, ++i, baseURL, propSwitch);
                    try {
                        Properties properties = new Properties();
                        properties.load(getURLReader(propURL));
                        processor.setVariable(ident, properties);
                    }
                    catch (Exception e) {
                        throw new UserError("Error reading properties - " + args[i]);
                    }
                }
                else if (arg.equals(outSwitch)) {
                    if (out != null)
                        throw new UserError("Duplicate " + outSwitch);
                    out = new File(getArg(args, ++i, outSwitch + " with no pathname"));
                }
                else if (arg.startsWith(dSwitch) && arg.length() > dSwitch.length()) {
                    int j = arg.indexOf('=');
                    String lhs = j < 0 ? arg.substring(dSwitch.length()) :
                        arg.substring(dSwitch.length(), j);
                    String rhs = j < 0 ? null : arg.substring(j + 1);
                    if (!Expression.isValidIdentifier(lhs))
                        throw new UserError(dSwitch + " identifier invalid - " + lhs);
                    processor.setVariable(lhs, rhs == null ? Boolean.TRUE : parseArg(rhs));
                }
                else
                    throw new UserError("Unrecognised argument - " + arg);
            }
            if (processor.getDom() == null)
                throw new UserError("No " + templateSwitch);
            if (jstlFunctions)
                processor.addNamespace(jstlFunctionsURL, new Functions());
            if (out != null) {
                try (OutputStream os = new FileOutputStream(out)) {
                    processor.process(os);
                }
                catch (IOException ioe) {
                    throw new RuntimeException("Error writing output file", ioe);
                }
            }
            else
                processor.process(System.out);
        }
        catch (TemplateException te) {
            System.err.println();
            String xpath = te.getXPath();
            if (xpath != null)
                System.err.println("XPath: " + xpath);
            System.err.println(te.getMessage());
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getArg(String[] args, int index, String msg) {
        if (index >= args.length)
            throw new UserError(msg);
        String result = args[index];
        if (result.startsWith("-"))
            throw new UserError(msg);
        return result;
    }

    private static String getArgIdent(String[] args, int index, String name) {
        String ident = getArg(args, index, name + " with no identifier");
        if (!Expression.isValidIdentifier(ident))
            throw new UserError(name + " identifier invalid - " + ident);
        return ident;
    }

    private static URL getArgURL(String[] args, int index, URL baseURL, String name) {
        String arg = getArg(args, index, name + " with no URL");
        try {
            return new URL(baseURL, arg);
        }
        catch (Exception e) {
            throw new UserError(name + " URL invalid - " + arg);
        }
    }

    private static Reader getURLReader(URL url) {
        try {
            if ("file".equals(url.getProtocol())) // workaround for Windows
                return new FileReader(url.getPath());
            URLConnection urlConnection = url.openConnection();
            InputStream stream = urlConnection.getInputStream();
            String contentTypeHeader = urlConnection.getContentType();
            if (contentTypeHeader != null) {
                String[] params = Strings.split(contentTypeHeader, ';');
                for (int i = 1; i < params.length; i++) {
                    String param = params[i];
                    int j = param.indexOf('=');
                    if (j > 0) {
                        String lhs = param.substring(0, j).trim();
                        String rhs = param.substring(j + 1).trim();
                        if (lhs.equalsIgnoreCase("charset"))
                            return new InputStreamReader(stream, rhs);
                    }
                }
            }
            return new InputStreamReader(stream);
        }
        catch (IOException e) {
            throw new UserError("Error reading " + url);
        }
    }

    private static Object parseArg(String arg) {
        if (arg.equalsIgnoreCase("null"))
            return null;
        if (arg.equalsIgnoreCase("true"))
            return Boolean.TRUE;
        if (arg.equalsIgnoreCase("false"))
            return Boolean.FALSE;
        try {
            return Integer.decode(arg);
        }
        catch (NumberFormatException e) {
            // ignore
        }
        try {
            return Long.decode(arg);
        }
        catch (NumberFormatException e) {
            // ignore
        }
        try {
            return Double.valueOf(arg);
        }
        catch (NumberFormatException e) {
            // ignore
        }
        return arg;
    }

    private static synchronized Document getDocument(URL url) throws TemplateException {
        String urlString = url.toString();
        Document document = documentMap.get(urlString);
        if (document == null) {
            try {
                InputSource is = new InputSource();
                if ("file".equals(url.getProtocol())) // workaround for Windows
                    is.setByteStream(new FileInputStream(url.getPath()));
                else
                    is.setSystemId(urlString);
                document = XML.getDocumentBuilderNS().parse(is);
            }
            catch (IOException e) {
                throw new TemplateException("I/O error reading URL - " + urlString);
            }
            catch (SAXException e) {
                throw new TemplateException("Parsing error reading URL - " + urlString);
            }
            catch (Exception e) {
                throw new TemplateException("Unexpected error reading URL - " + urlString);
            }
            documentMap.put(urlString, document);
        }
        return document;
    }

    public static class ElementWrapper {

        private Element element;
        private List<ElementWrapper> elems;
        private Map<String, Object> attrs;
        private String text;

        public ElementWrapper(Element element) {
            this.element = element;
            elems = null;
            attrs = null;
            text = null;
        }

        public Element getElement() {
            return element;
        }

        public String getTagName() {
            return element.getTagName();
        }

        public List<ElementWrapper> getElems() {
            if (elems == null) {
                elems = new ArrayList<>();
                NodeList children = element.getChildNodes();
                for (int i = 0, n = children.getLength(); i < n; i++) {
                    Node child = children.item(i);
                    if (child instanceof Element)
                        elems.add(new ElementWrapper((Element)child));
                }
            }
            return elems;
        }

        public Map<String, Object> getAttrs() {
            if (attrs == null) {
                attrs = new HashMap<>();
                NamedNodeMap attributes = element.getAttributes();
                for (int i = 0, n = attributes.getLength(); i < n; i++) {
                    Attr attr = (Attr)attributes.item(i);
                    attrs.put(attr.getName(), attr.getValue());
                }
            }
            return attrs;
        }

        public String getText() {
            if (text == null) {
                StringBuilder sb = new StringBuilder();
                appendData(sb, element);
                text = sb.toString();
            }
            return text;
        }

        private void appendData(StringBuilder sb, Node node) {
            if (node instanceof Text)
                sb.append(((Text)node).getData());
            else if (node instanceof Element) {
                NodeList children = ((Element)node).getChildNodes();
                for (int i = 0, n = children.getLength(); i < n; i++)
                    appendData(sb, children.item(i));
            }
        }

    }

    public static class Intercept {

        private String tagName;
        private Element replacement;
        private String name;

        public Intercept(String tagName, Element replacement, String name) {
            this.tagName = tagName;
            this.replacement = replacement;
            this.name = name;
        }

        public String getTagName() {
            return tagName;
        }

        public Element getReplacement() {
            return replacement;
        }

        public String getName() {
            return name;
        }

    }

}
