/*
 * @(#) TemplateContext.java
 *
 * xtj XML Templating for Java
 * Copyright (c) 2015, 2016, 2019, 2020 Peter Wall
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

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import net.pwall.el.Constant;
import net.pwall.el.Expression;
import net.pwall.el.ExtendedResolver;
import net.pwall.el.Resolver;
import net.pwall.el.SimpleVariable;

/**
 * Template Context - includes Name Resolver for Expression Language.
 */
public class TemplateContext implements ExtendedResolver {

    private static final String nameAttrName = "name";

    private final TemplateContext parent;
    private final Element element;
    private final Map<String, Expression> map;
    private final Map<String, Element> macros;
    private final Map<String, Object> namespaces;
    private URL url;

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
        url = parent == null ? null : parent.getURL();
    }

    /**
     * Get the parent context.
     *
     * @return  the parent context
     */
    public TemplateContext getParent() {
        return parent;
    }

    public URL getURL() {
        return url;
    }

    public void setURL(URL url) {
        this.url = url;
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
        map.put(identifier, new Constant(object));
    }

    /**
     * Resolve an identifier to an expression.
     *
     * @param identifier  the identifier to be resolved
     * @return            a variable, or null if the name can not be resolved
     * @see     Resolver#resolve(String)
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
        if (!Expression.isValidIdentifier(name))
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

    public void addNamespace(String uri, Object impl) {
        namespaces.put(uri, impl);
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
    public Object resolveNamespace(String uri) {
        for (TemplateContext context = this; context != null; context = context.parent) {
            Object impl = context.namespaces.get(uri);
            if (impl != null)
                return impl;
        }
        return null;
    }

    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

}
