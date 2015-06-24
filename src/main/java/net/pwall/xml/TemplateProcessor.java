/*
 * @(#) TemplateProcessor.java
 */

package net.pwall.xml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.ParserConfigurationException;

import net.pwall.el.Expression;
import net.pwall.el.Functions;
import net.pwall.el.SimpleVariable;
import net.pwall.html.HTMLFormatter;
import net.pwall.util.UserError;

import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.AttributesImpl;

/**
 * XML Template Processor for Java
 */
public class TemplateProcessor {

    public static final String defaultNamespace = "http://pwall.net/xml/xt/0.1";

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
    private static final String idAttrName = "id";
    private static final String ifAttrName = "if";

    private static final String prefixYes = "yes";
    private static final String prefixNo = "no";
    private static final String whitespaceNone = "none";
    private static final String whitespaceAll = "all";
    private static final String whitespaceIndent = "indent";
    private static final String optionInclude = "include";

    private Document dom;
    private Expression.Parser parser;
    private TemplateContext context;
    private String namespace;
    private String whitespace;
    private boolean prefixXML;

    public TemplateProcessor(Document dom) {
        this.dom = Objects.requireNonNull(dom);
        parser = new Expression.Parser();
        parser.setConditionalAllowed(true);
        context = new TemplateContext(null, dom.getDocumentElement());
        namespace = defaultNamespace;
        whitespace = null;
        prefixXML = false;
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
        if (!(prefixYes.equalsIgnoreCase(prefixXML) || prefixNo.equalsIgnoreCase(prefixXML)))
            throw new TemplateException("Illegal prefix option - " + prefixXML);
        setPrefixXML(prefixYes.equalsIgnoreCase(prefixXML));
    }

    public void setVariable(String identifier, Object object) {
        context.setVariable(identifier, object);
    }

    public void addNamespace(String uri, String namespace) {
        context.addNamespace(uri, namespace);
    }

    public void process(OutputStream os) throws Expression.ExpressionException {
        Element documentElement = dom.getDocumentElement();
        if (XML.matchNS(documentElement, templateElementName, namespace)) {
            String whitespaceOption = substAttr(documentElement, whitespaceAttrName);
            if (!isEmpty(whitespaceOption))
                setWhitespace(whitespaceOption);
            String outputAttr = substAttr(documentElement, outputAttrName);
            if (!isEmpty(outputAttr)) {
                if (outputAttr.equalsIgnoreCase(outputXML)) {
                    String prefixAttr = substAttr(documentElement, prefixAttrName);
                    if (!isEmpty(prefixAttr))
                        setPrefixXML(prefixAttr);
                    processXML(os);
                }
                else if (outputAttr.equalsIgnoreCase(outputHTML))
                    processHTML(os);
                else
                    throw new TemplateException(documentElement,
                            "Illegal " + outputAttrName + ": " + outputAttr);
            }
            else
                processXML(os);
        }
        else
            processXML(os);
    }

    public void processXML(OutputStream os) throws Expression.ExpressionException {
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

    public void processHTML(OutputStream os) throws Expression.ExpressionException {
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

    private void processElement(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        String test = element.getAttributeNS(namespace, ifAttrName);
        String substTest = subst(test);
        if (!isEmpty(substTest)) {
            try {
                if (!parser.parseExpression(substTest, context).asBoolean())
                    return;
            }
            catch (Exception e) {
                throw new TemplateException("Error in \"if\" attribute - " + test + '\n' +
                        e.getMessage());
            }
        }
        if (XML.matchNS(element, errorElementName, namespace))
            processError(element, formatter);
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

    private void processElementContents(Element element, DefaultHandler2 formatter,
            boolean trim) throws Expression.ExpressionException {
        NodeList childNodes = element.getChildNodes();
        int start = 0;
        int end = childNodes.getLength();
        if (trim) {
            while (start < end && isCommentOrEmpty(childNodes.item(start)))
                start++;
            while (start < end && isCommentOrEmpty(childNodes.item(end - 1)))
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
                String data = ((Text)childNode).getData();
                if (trim) {
                    if (i == start)
                        data = trimLeading(data);
                    if (i == n - 1)
                        data = trimTrailing(data);
                }
                outputText(data, formatter);
            }
        }
    }

    private void processElementContentsNewContext(Element element, DefaultHandler2 formatter,
            boolean trim) throws Expression.ExpressionException {
        context = new TemplateContext(context, element);
        processElementContents(element, formatter, trim);
        context = context.getParent();
    }

    private void processError(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        // TODO check - is this how we want to handle this?
        String text = substAttr(element, textAttrName);
        throw new TemplateException(element, !isEmpty(text) ? text : "Error directive");
    }

    private void processDoctype(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        String name = substAttr(element, nameAttrName);
        if (isEmpty(name))
            throw new TemplateException(element, "Name missing");
        String systemAttr = substAttr(element, systemAttrName);
        String publicAttr = substAttr(element, publicAttrName);
        try {
            formatter.startDTD(name, isEmpty(publicAttr) ? null : publicAttr,
                    isEmpty(systemAttr) ? null : systemAttr);
            // TODO implement doctype internal subset? Otherwise check element empty?
            formatter.endDTD();
        }
        catch (SAXException e) {
            throw new TemplateException(element, "Error processing document type");
        }
    }

    private void processInclude(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        // TODO implement <include>
        throw new TemplateException(element, "Can't handle <include>");
    }

    private void processSet(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        String name = substAttr(element, nameAttrName);
        if (!isValidName(name))
            throw new TemplateException(element, "Name missing or invalid");
        if (!isEmpty(element.getAttribute(documentAttrName)))
            throw new TemplateException(element, "Can't handle <set document= >");
        // TODO should be able to do this in Java
        String value = substAttr(element, valueAttrName);
        try {
            Expression exp = parser.parseExpression(value, context);
            context.setVariable(name, exp.evaluate());
        }
        catch (Expression.ExpressionException e) {
            throw new TemplateException(element, "Error in value - " + value + '\n' +
                    e.getMessage());
        }
        if (!isElementEmpty(element))
            throw new TemplateException(element, "Illegal content");
        // TODO allow content - if it contains elements, store as an ElementWrapper;
        // otherwise parse as JSON
    }

    private void processIf(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        String test = substAttr(element, testAttrName);
        if (isEmpty(test))
            throw new TemplateException(element, "Test must be specified");
        boolean testResult;
        try {
            testResult = parser.parseExpression(test, context).asBoolean();
        }
        catch (Expression.ExpressionException e) {
            throw new TemplateException(element,
                    "Error in test - " + test + '\n' + e.getMessage());
        }
        if (testResult)
            processElementContentsNewContext(element, formatter, true);
    }

    private void processSwitch(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                if (XML.matchNS(childElement, caseElementName, namespace)) {
                    String test = substAttr(childElement, testAttrName);
                    boolean testResult = true;
                    if (!isEmpty(test)) {
                        try {
                            testResult = parser.parseExpression(test, context).asBoolean();
                        }
                        catch (Expression.ExpressionException e) {
                            throw new TemplateException(childElement,
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
                    throw new TemplateException(childElement,
                            "Illegal element within <switch>");
            }
            else if (!isCommentOrEmpty(node))
                throw new TemplateException(element, "Illegal content within <switch>");
        }
    }

    private void processFor(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        // TODO document not yet handled
        String name = substAttr(element, nameAttrName);
        String coll = substAttr(element, collectionAttrName);
        String from = substAttr(element, fromAttrName);
        String to = substAttr(element, toAttrName);
        String by = substAttr(element, byAttrName);
        if (!isEmpty(coll)) {
            if (!isEmpty(from) || !isEmpty(to) || !isEmpty(by))
                throw new TemplateException(element,
                        "<for> has illegal combination of attributes");
            processForCollection(element, formatter, name, coll);
        }
        else if (!isEmpty(from) || !isEmpty(to) || !isEmpty(by)) {
            Object fromObject = isEmpty(from) ? null :
                    parser.parseExpression(from, context).evaluate();
            Object toObject = isEmpty(to) ? null :
                    parser.parseExpression(to, context).evaluate();
            Object byObject = isEmpty(by) ? null :
                    parser.parseExpression(by, context).evaluate();
            if (isFloating(fromObject) || isFloating(toObject) || isFloating(byObject))
                processForSequenceFloat(element, formatter, name, fromObject, toObject,
                        byObject);
            else
                processForSequenceInt(element, formatter, name, fromObject, toObject, byObject);
        }
        else
            throw new TemplateException(element, "<for> must specify iteration type");
    }

    private void processForSequenceInt(Element element, DefaultHandler2 formatter, String name,
            Object from, Object to, Object by) throws Expression.ExpressionException {
        // note - "to" value is exclusive; from="0" to="4" will perform 0,1,2,3
        int fromValue = from == null ? 0 : intValue(from, element, "<for> from value invalid");
        int toValue = to == null ? 0 : intValue(to, element, "<for> to value invalid");
        int byValue = by == null ? 1 : intValue(by, element, "<for> by value invalid");
        if (byValue <= 0)
            throw new TemplateException(element, "<for> by value invalid");
        if (fromValue != toValue) {
            context = new TemplateContext(context, element);
            if (fromValue < toValue) {
                do {
                    if (!isEmpty(name))
                        context.setVariable(name, new Integer(fromValue));
                    processElementContents(element, formatter, true);
                    fromValue += byValue;
                } while (fromValue < toValue);
            }
            else {
                do {
                    if (!isEmpty(name))
                        context.setVariable(name, new Integer(fromValue));
                    processElementContents(element, formatter, true);
                    fromValue -= byValue;
                } while (fromValue > toValue);
            }
            context = context.getParent();
        }
    }

    private int intValue(Object obj, Element element, String msg)
            throws TemplateException {
        try {
            return Expression.asInt(obj);
        }
        catch (Expression.IntCoercionException e) {
            throw new TemplateException(element, msg);
        }
    }

    private void processForSequenceFloat(Element element, DefaultHandler2 formatter,
            String name, Object from, Object to, Object by)
            throws Expression.ExpressionException {
        // note - "to" value is exclusive; from="0" to="4" will perform 0,1,2,3
        double fromValue = from == null ? 0.0 : doubleValue(from, element,
                "<for> from value invalid");
        double toValue = to == null ? 0.0 : doubleValue(to, element, "<for> to value invalid");
        double byValue = by == null ? 1.0 : doubleValue(by, element, "<for> by value invalid");
        if (byValue <= 0.0)
            throw new TemplateException(element, "<for> by value invalid");
        if (fromValue != toValue) {
            context = new TemplateContext(context, element);
            if (fromValue < toValue) {
                do {
                    if (!isEmpty(name))
                        context.setVariable(name, new Double(fromValue));
                    processElementContents(element, formatter, true);
                    fromValue += byValue;
                } while (fromValue < toValue);
            }
            else {
                do {
                    if (!isEmpty(name))
                        context.setVariable(name, new Double(fromValue));
                    processElementContents(element, formatter, true);
                    fromValue -= byValue;
                } while (fromValue > toValue);
            }
            context = context.getParent();
        }
    }

    private double doubleValue(Object obj, Element element, String msg)
            throws TemplateException {
        try {
            return Expression.asDouble(obj);
        }
        catch (Expression.DoubleCoercionException e) {
            throw new TemplateException(element, msg);
        }
    }

    private void processForCollection(Element element, DefaultHandler2 formatter, String name,
            String coll) throws Expression.ExpressionException {
        Object collObject = parser.parseExpression(coll, context).evaluate();
        if (collObject != null) {
            context = new TemplateContext(context, element);
            if (collObject instanceof Map<?, ?>) {
                for (Object obj : ((Map<?, ?>)collObject).values()) {
                    if (!isEmpty(name))
                        context.setVariable(name, obj);
                    processElementContents(element, formatter, true);
                }
            }
            else if (collObject instanceof Iterable<?>) {
                for (Object obj : (Iterable<?>)collObject) {
                    if (!isEmpty(name))
                        context.setVariable(name, obj);
                    processElementContents(element, formatter, true);
                }
            }
            else if (collObject instanceof Object[]) {
                Object[] array = (Object[])collObject;
                for (int i = 0, n = array.length; i < n; i++) {
                    Object obj = array[i];
                    if (!isEmpty(name))
                        context.setVariable(name, obj);
                    processElementContents(element, formatter, true);
                }
            }
            else if (collObject.getClass().isArray()) {
                for (int i = 0, n = Array.getLength(collObject); i < n; i++) {
                    Object obj = Array.get(collObject, i);
                    if (!isEmpty(name))
                        context.setVariable(name, obj);
                    processElementContents(element, formatter, true);
                }
            }
            else
                throw new TemplateException(element,
                        "<for> collection must be capable of iteration");
            context = context.getParent();
        }
    }

    private void processCall(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
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
                if (XML.matchNS(childElement, paramElementName, namespace)) {
                    name = substAttr(childElement, nameAttrName);
                    if (!isValidName(name))
                        throw new TemplateException(childElement, "Name missing or invalid");
                    String value = substAttr(childElement, valueAttrName);
                    if (isEmpty(value))
                        throw new TemplateException(childElement, "Value missing");
                    try {
                        context.setVariable(name, // must be outer context
                                parser.parseExpression(value, context).evaluate());
                    }
                    catch (Expression.ExpressionException e) {
                        throw new TemplateException(childElement, "Error in value - " + value +
                                '\n' + e.getMessage());
                    }
                }
                else
                    throw new TemplateException(childElement, "Illegal element within <call>");
            }
            else if (!isCommentOrEmpty(childNode))
                throw new TemplateException(element, "Illegal content within <call>");
        }
        processElementContents(macro, formatter, true);
        context = context.getParent();
    }

    private void processComment(Element element, DefaultHandler2 formatter) {
        // TODO complete this
    }

    private void processCopy(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        String elementName = substAttr(element, elementAttrName);
        if (isEmpty(elementName))
            throw new TemplateException(element, "<copy> element missing");
        // TODO if element not specified, process contents of <copy> (skipping <intercept>s)??
        context = new TemplateContext(context, element);
        Element elementToCopy = null;
        try {
            Object obj = parser.parseExpression(elementName, context).evaluate();
            if (!(obj instanceof ElementWrapper))
                throw new TemplateException(element, "<copy> must specify element");
            elementToCopy = ((ElementWrapper)obj).getElement();
        }
        catch (TemplateException te) {
            throw te;
        }
        catch (Expression.ExpressionException ee) {
            throw new TemplateException(element, "Error in element specification");
        }
        boolean include = false;
        String opt = substAttr(element, optionAttrName);
        if (!isEmpty(opt)) {
            if (optionInclude.equals(opt))
                include = true;
            else
                throw new TemplateException(element, "<copy> option not recognised - " + opt);
        }
        List<Intercept> intercepts = new ArrayList<>();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element) {
                Element childElement = (Element)childNode;
                context = new TemplateContext(context, element);
                if (XML.matchNS(childElement, interceptElementName, namespace)) {
                    elementName = substAttr(childElement, elementAttrName);
                    if (isEmpty(elementName))
                        throw new TemplateException(element, "<intercept> element missing");
                    String name = substAttr(childElement, nameAttrName);
                    intercepts.add(new Intercept(elementName, childElement, name));
                }
                else
                    throw new TemplateException(element, "Illegal element within <copy>");
                context = context.getParent();
            }
            else if (!isCommentOrEmpty(childNode))
                throw new TemplateException(element, "Illegal content within <copy>");
        }
        if (include)
            copyElement(elementToCopy, intercepts, formatter);
        else
            copyElementContents(elementToCopy, intercepts, formatter);
        context = context.getParent();
    }

    private void copyElement(Element element, List<Intercept> intercepts,
            DefaultHandler2 formatter) throws Expression.ExpressionException {
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
            attrs.addAttribute(attr.getNamespaceURI(), attr.getLocalName(), attr.getNodeName(),
                    "CDATA", attr.getValue());
        }
        try {
            formatter.startElement(element.getNamespaceURI(), element.getLocalName(),
                    element.getNodeName(), attrs);
            copyElementContents(element, intercepts, formatter);
            formatter.endElement(element.getNamespaceURI(), element.getLocalName(),
                    element.getNodeName());
        }
        catch (SAXException e) {
            throw new TemplateException(element, "Error writing element");
        }
    }

    private void copyElementContents(Element element, List<Intercept> intercepts,
            DefaultHandler2 formatter) throws Expression.ExpressionException {
        context = new TemplateContext(context, element);
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element)
                copyElement((Element)childNode, intercepts, formatter);
            else if (childNode instanceof Text)
                outputText(((Text)childNode).getData(), formatter);
            else if (childNode instanceof CDATASection) {
                try {
                    formatter.startCDATA();
                    outputText(((Text)childNode).getData(), formatter);
                    formatter.endCDATA();
                }
                catch (SAXException e) {
                    throw new RuntimeException("Unexpected SAXException", e);
                }
            }
        }
        context = context.getParent();
    }

    private void outputElement(Element element, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        AttributesImpl attrs = new AttributesImpl();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr)attributes.item(i);
            if (!namespace.equals(attr.getNamespaceURI())) {
                String value = subst(attr.getValue());
                if (!isEmpty(value))
                    attrs.addAttribute(attr.getNamespaceURI(), attr.getLocalName(),
                            attr.getNodeName(), "CDATA", value);
            }
        }
        try {
            formatter.startElement(element.getNamespaceURI(), element.getLocalName(),
                    element.getNodeName(), attrs);
            processElementContents(element, formatter, false);
            formatter.endElement(element.getNamespaceURI(), element.getLocalName(),
                    element.getNodeName());
        }
        catch (SAXException e) {
            throw new TemplateException(element, "Error writing element");
        }
    }

    private void outputText(String data, DefaultHandler2 formatter)
            throws Expression.ExpressionException {
        try {
            String data1 = subst(data);
            formatter.characters(data1.toCharArray(), 0, data1.length());
        }
        catch (SAXException e) {
            throw new TemplateException("Error writing text node");
        }
    }

    private String substAttr(Element element, String attrName)
            throws Expression.ExpressionException {
        return subst(element.getAttribute(attrName));
    }

    private String subst(String str) throws Expression.ExpressionException {
        return str == null ? null : parser.substitute(str, context);
    }

    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    private static boolean isElementEmpty(Element element) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (!isCommentOrEmpty(childNodes.item(i)))
                return false;
        }
        return true;
    }

    private static boolean isCommentOrEmpty(Node node) {
        return node.getNodeType() == Node.COMMENT_NODE ||
                node.getNodeType() == Node.TEXT_NODE &&
                        XML.isAllWhiteSpace(((Text)node).getData());
    }

    public static boolean isValidName(String name) {
        if (isEmpty(name) || !isValidNameStart(name.charAt(0)))
            return false;
        for (int i = 1; i < name.length(); i++)
            if (!isValidNameContinuation(name.charAt(i)))
                return false;
        return true;
    }

    public static boolean isValidNameStart(char ch) {
        return ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_' || ch == '$';
    }

    public static boolean isValidNameContinuation(char ch) {
        return isValidNameStart(ch) || ch >= '0' && ch <= '9';
    }

    public static String trimLeading(String str) {
        int i = 0;
        int n = str.length();
        while (i < n && XML.isWhiteSpace(str.charAt(i)))
            i++;
        return i > 0 ? str.substring(i) : str;
    }

    public static String trimTrailing(String str) {
        int i = 0;
        int n = str.length();
        while (i < n && XML.isWhiteSpace(str.charAt(n - 1)))
            n--;
        return n < str.length() ? str.substring(0, n) : str;
    }

//    public static boolean isFloat(String num) {
//        return num != null &&
//                (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0);
//    }

    public static boolean isFloating(Object obj) {
        return obj instanceof Double || obj instanceof Float;
    }

    public static String getXPath(Element element) {
        StringBuilder sb = new StringBuilder();
        for  (;;) {
            sb.insert(0, getXPathElement(element));
            sb.insert(0, '/');
            Node parent = element.getParentNode();
            if (parent == null || !(parent instanceof Element))
                break;
            element = (Element)parent;
        }
        return sb.toString();
    }

    public static String getXPathElement(Element element) {
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
                    else if (sibling instanceof Element &&
                            ((Element)sibling).getTagName().equals(tagName))
                        count++;
                }
                if (count > 0)
                    sb.append('[').append(thisIndex + 1).append(']');
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        try {
            File template = null;
            File in = null;
            File out = null;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-template")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-template with no pathname");
                    if (template != null)
                        throw new UserError("Duplicate -template");
                    template = new File(args[i]);
                    if (!template.exists() || !template.isFile())
                        throw new UserError("-template file does not exist - " + args[i]);
                }
                else if (arg.equals("-in")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-in with no pathname");
                    if (in != null)
                        throw new UserError("Duplicate -in");
                    in = new File(args[i]);
                    if (!in.exists() || !in.isFile())
                        throw new UserError("-in file does not exist - " + args[i]);
                }
                else if (arg.equals("-out")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-out with no pathname");
                    if (out != null)
                        throw new UserError("Duplicate -out");
                    out = new File(args[i]);
                }
                else
                    throw new UserError("Unrecognised argument - " + arg);
            }
            if (out != null) {
                try (OutputStream os = new FileOutputStream(out)) {
                    run(os, in, template);
                }
                catch (IOException ioe) {
                    throw new RuntimeException("Error writing output file", ioe);
                }
            }
            else
                run(System.out, in, template);
        }
        catch (TemplateException te) {
            System.err.println();
            Element element = te.getElement();
            if (element != null)
                System.err.println("XPath: " + getXPath(element));
            System.err.println(te.getMessage());
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(OutputStream os, File in, File template)
            throws ParserConfigurationException, SAXException, IOException,
            Expression.ExpressionException {
        Document templateDOM =
                XML.getDocumentBuilderNS().parse(Objects.requireNonNull(template));
        TemplateProcessor processor = new TemplateProcessor(templateDOM);
        processor.addNamespace("http://java.sun.com/jsp/jstl/functions",
                Functions.class.getName());
        // should the above be variable? command-line args?
        if (in != null) {
            Document inputDOM = XML.parse(in);
            processor.setVariable("page", new ElementWrapper(inputDOM.getDocumentElement()));
        }
        processor.process(os);
    }

    public static class TemplateException extends Expression.ExpressionException {

        private static final long serialVersionUID = 7462874004583859973L;

        private Element element;

        public TemplateException(Element element, String message) {
            super(message);
            this.element = element;
        }

        public TemplateException(String message) {
            this(null, message);
        }

        public Element getElement() {
            return element;
        }

    }

    /**
     * Template Context - includes Name Resolver for Expression Language.
     */
    public static class TemplateContext implements Expression.ExtendedResolver {
    
        private TemplateContext parent;
        private Element element;
        private Map<String, Expression> map;
        private Map<String, Element> macros;
        private Map<String, String> namespaces;

        /**
         * Construct the <code>TemplateResolver</code>
         *
         * @param   parent  the parent context
         */
        public TemplateContext(TemplateContext parent, Element element) {
            this.parent = parent;
            this.element = element;
            map = new HashMap<>();
            macros = new HashMap<>();
            namespaces = new HashMap<>();
        }

        /**
         * Get the parent context.
         *
         * @return  the parent context
         */
        public TemplateContext getParent() {
            return parent;
        }

        /**
         * Create a variable, or modify an existing one.
         *
         * @param identifier  the identifier of the variable
         * @param object      the value of the variable
         */
        public void setVariable(String identifier, Object object) {
            map.put(identifier, new SimpleVariable(identifier, object));
        }

        public void setConstant(String identifier, Object object) {
            map.put(identifier, new Expression.Constant(object));
        }

        /**
         * Resolve an identifier to an expression.
         *
         * @param identifier  the identifier to be resolved
         * @return            a variable, or null if the name can not be resolved
         * @see     Expression.Resolver#resolve(String)
         */
        @Override
        public Expression resolve(String identifier) {
            for (TemplateContext context = this; context != null; context = context.parent) {
                Expression e = context.map.get(identifier);
                if (e != null)
                    return e;
            }
            return null;
        }

        /**
         * Add a macro to the current context.
         *
         * @param   element the macro element
         * @throws  TemplateException if the macro does not have a valid name, or is a duplicate
         */
        public void addMacro(Element element) throws TemplateException {
            String name = element.getAttribute(nameAttrName);
            if (!isValidName(name))
                throw new TemplateException(element, "Macro name missing or invalid");
            if (macros.containsKey(name))
                throw new TemplateException(element, "Duplicate macro - " + name);
            macros.put(name, element);
        }

        /**
         * Get a macro.
         *
         * @param   name    the macro name
         * @return  the macro element
         */
        public Element getMacro(String name) {
            for (TemplateContext context = this; context != null; context = context.parent) {
                Element macro = context.macros.get(name);
                if (macro != null)
                    return macro;
            }
            return null;
        }

        public void addNamespace(String uri, String namespace) {
            namespaces.put(uri, namespace);
        }

        @Override
        public String resolvePrefix(String prefix) {
            String xmlnsAttrName = "xmlns:" + Objects.requireNonNull(prefix);
            Element element = this.element;
            for (;;) {
                String uri = element.getAttribute(xmlnsAttrName);
                if (!isEmpty(uri))
                    return uri;
                Node parent = element.getParentNode();
                if (!(parent instanceof Element))
                    break;
                element = (Element)parent;
            }
            return null;
        }

        @Override
        public String resolveNamespace(String uri) {
            for (TemplateContext context = this; context != null; context = context.parent) {
                String classname = context.namespaces.get(uri);
                if (classname != null)
                    return classname;
            }
            return null;
        }

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
