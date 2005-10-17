/**
 * 
 * Copyright 2005 LogicBlaze, Inc. http://www.logicblaze.com
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
 * 
 **/
package org.xbean.spring.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jam.JAnnotatedElement;
import org.codehaus.jam.JAnnotation;
import org.codehaus.jam.JAnnotationValue;
import org.codehaus.jam.JClass;
import org.codehaus.jam.JComment;
import org.codehaus.jam.JConstructor;
import org.codehaus.jam.JMethod;
import org.codehaus.jam.JParameter;
import org.codehaus.jam.JProperty;
import org.xbean.spring.context.impl.NamespaceHelper;
import org.xbean.spring.context.impl.PropertyEditorHelper;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 
 * @version $Revision: 1.1 $
 */
public class SchemaGenerator {
    public static final String XBEAN_ANNOTATION = "org.xbean.XBean";
    public static final String PROPERTY_ANNOTATION = "org.xbean.Property";

    private static final Log log = LogFactory.getLog(SchemaGenerator.class);

    private final JClass[] classes;
    private final File destFile;
    private final String defaultNamespace;
    private String metaInfDir = "target/classes/";

    private Map namespaces = new HashMap();
    private List allElements = new ArrayList();
    private SchemaElement rootElement;
    private Map xsdTypeMap;

    public SchemaGenerator(JClass[] classes, File destFile, String defaultNamespace, String metaInfDir) {
        this.classes = classes;
        this.destFile = destFile;
        this.defaultNamespace = defaultNamespace;
        this.metaInfDir = metaInfDir;

        PropertyEditorHelper.registerCustomEditors();
    }

    public void generate() throws IOException {
        loadModel();

        for (Iterator iter = namespaces.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            String namespace = (String) entry.getKey();
            List elements = (List) entry.getValue();
            Collections.sort(elements);
            if (namespace != null) {
                generatePropertiesFile(namespace, elements);
            }
            generateDocumentation(namespace, elements);
            generateSchema(namespace, elements);
        }

        if (namespaces.isEmpty()) {
            System.out.println("Warning: no namespaces found!");
        }
    }

    // Documentation generation
    // -------------------------------------------------------------------------
    protected void generatePropertiesFile(String namespace, List elements) throws IOException {
        File file = new File(metaInfDir + NamespaceHelper.createDiscoveryPathName(namespace));
        file.getParentFile().mkdirs();
        System.out.println("Generating META-INF properties file: " + file + " for namespace: " + namespace);
        PrintWriter out = new PrintWriter(new FileWriter(file));
        try {
            generatePropertiesFile(out, namespace, elements);
        }
        finally {
            out.close();
        }
    }

    protected void generatePropertiesFile(PrintWriter out, String namespace, List elements) {
        out.println("# NOTE: this file is autogenerated by XBeans");
        out.println();
        out.println("# beans");

        for (Iterator iter = elements.iterator(); iter.hasNext();) {
            SchemaElement element = (SchemaElement) iter.next();
            out.println(element.getLocalName() + " = " + element.getType().getQualifiedName());
            
            generatePropertiesFileContent(out, namespace, element);
            generatePropertiesFileConstructors(out, namespace, element);
            generatePropertiesFilePropertyAliases(out, namespace, element);
        }
    }

    protected void generatePropertiesFileContent(PrintWriter out, String namespace, SchemaElement element) {
        JAnnotation annotation = element.getType().getAnnotation(XBEAN_ANNOTATION);
        if (annotation != null) {
            String value = getStringValue(annotation, "contentProperty");
            if (value != null) {
                out.println(element.getLocalName() + ".contentProperty = " + value);
            }
        }
    }

    protected void generatePropertiesFileConstructors(PrintWriter out, String namespace, SchemaElement element) {
        JClass type = element.getType();
        JConstructor[] constructors = type.getConstructors();
        for (int i = 0; i < constructors.length; i++) {
            JConstructor constructor = constructors[i];
            generatePropertiesFileConstructor(out, namespace, element, constructor);
        }
    }
    
    protected void generatePropertiesFileConstructor(PrintWriter out, String namespace, SchemaElement element, JConstructor constructor) {
        JParameter[] parameters = constructor.getParameters();
        if (parameters.length == 0) {
            return;
        }
        out.print(element.getType().getQualifiedName());
        out.print("(");
        for (int i = 0; i < parameters.length; i++) {
            JParameter parameter = parameters[i];
            if (i > 0) {
                out.print(",");
            }
            out.print(parameter.getType().getQualifiedName());
        }
        out.print(").parameterNames =");
        for (int i = 0; i < parameters.length; i++) {
            JParameter parameter = parameters[i];
            out.print(" ");
            out.print(parameter.getSimpleName());
        }
        out.println();
    }
    
    protected void generatePropertiesFilePropertyAliases(PrintWriter out, String namespace, SchemaElement element) {
        JClass type = element.getType();
        JProperty[] properties = type.getProperties();
        for (int i = 0; i < properties.length; i++) {
            generatePropertiesFilePropertyAlias(out, namespace, element, properties[i]);
        }
    }

    protected void generatePropertiesFilePropertyAlias(PrintWriter out, String namespace, SchemaElement element, JProperty property) {
        JAnnotation annotation = property.getAnnotation(PROPERTY_ANNOTATION);
        if (annotation != null) {
            String text = getStringValue(annotation, "alias");
            if (text != null) {
                String name = decapitalise(property.getSimpleName());
                out.println(element.getLocalName() + ".alias." + text + " = " +name);
            }
        }
    }

    // Documentation generation
    // -------------------------------------------------------------------------
    protected void generateDocumentation(String namespace, List elements) throws IOException {
        // TODO can only handle 1 schema document so far...
        File file = new File(destFile.getParentFile(), destFile.getName() + ".html");
        System.out.println("Generating HTML documentation file: " + file + " for namespace: " + namespace);
        PrintWriter out = new PrintWriter(new FileWriter(file));
        try {
            generateDocumentation(out, namespace, elements);
        }
        finally {
            out.close();
        }
    }

    private void generateDocumentation(PrintWriter out, String namespace, List elements) {
        out.println("<!-- NOTE: this file is autogenerated by XBeans -->");
        out.println("<html>");
        out.println("<head>");
        out.println("<title>Schema for namespace: " + namespace + "</title>");
        out.println("<link rel='stylesheet' href='style.css' type='text/css'>");
        out.println("<link rel='stylesheet' href='http://activemq.org/style.css' type='text/css'>");
        out.println("<link rel='stylesheet' href='http://activemq.org/style-xb.css' type='text/css'>");
        out.println("</head>");
        out.println();
        out.println("<body>");
        out.println();

        if (rootElement != null) {
            out.println("<h1>Root Element</h1>");
            out.println("<table>");
            out.println("  <tr><th>Element</th><th>Description</th><th>Class</th>");
            generateHtmlElementSummary(out, rootElement);
            out.println("</table>");
            out.println();
        }

        out.println("<h1>Element Summary</h1>");
        out.println("<table>");
        out.println("  <tr><th>Element</th><th>Description</th><th>Class</th>");
        for (Iterator iter = elements.iterator(); iter.hasNext();) {
            SchemaElement element = (SchemaElement) iter.next();
            generateHtmlElementSummary(out, element);
        }
        out.println("</table>");
        out.println();
        out.println();

        out.println("<h1>Element Detail</h1>");
        for (Iterator iter = elements.iterator(); iter.hasNext();) {
            SchemaElement element = (SchemaElement) iter.next();
            generateHtmlElementDetail(out, element);
        }

        out.println();
        out.println("</body>");
        out.println("</html>");
    }

    protected void generateHtmlElementSummary(PrintWriter out, SchemaElement element) {
        String localName = element.getLocalName();
        JClass type = element.getType();
        out.println("  <tr><td><a href='#" + localName + "'>" + localName + "</a></td><td>" + getDescription(type)
                + "</td><td>" + type.getQualifiedName() + "</td></tr>");
    }

    protected void generateHtmlElementDetail(PrintWriter out, SchemaElement element) {
        String localName = element.getLocalName();
        out.println("<h2>Element: <a name='" + localName + "'>" + localName + "</a></h2>");

        out.println("<table>");
        out.println("  <tr><th>Attribute</th><th>Type</th><th>Description</th>");
        JClass type = element.getType();
        JProperty[] properties = type.getProperties();
        for (int i = 0; i < properties.length; i++) {
            JProperty property = properties[i];
            if (!isValidProperty(property)) {
                continue;
            }
            if (isSimpleType(property)) {
                out.println("  <tr><td>" + getPropertyXmlName(property) + "</td><td>" + getXSDType(property)
                        + "</td><td>" + getDescription(property) + "</td></tr>");
            }
        }
        out.println("</table>");

        out.println("<table>");
        out.println("  <tr><th>Element</th><th>Type</th><th>Description</th>");
        for (int i = 0; i < properties.length; i++) {
            JProperty property = properties[i];
            if (!isValidProperty(property)) {
                continue;
            }
            if (!isSimpleType(property)) {
                out.print("  <tr><td>" + getPropertyXmlName(property) + "</td><td>");
                printComplexPropertyTypeDocumentation(out, property);
                out.println("</td><td>" + getDescription(property) + "</td></tr>");
            }
        }
        out.println("</table>");
    }

    protected void printComplexPropertyTypeDocumentation(PrintWriter out, JProperty property) {
        JClass type = property.getType();
        String typeName = type.getQualifiedName();
        if (isCollection(type)) {
            out.print("<list/>");
        }
        else {
            int counter = 0;
            // lets find all the implementations of the type
            List types = findImplementationsOf(type);
            for (Iterator iter = types.iterator(); iter.hasNext();) {
                SchemaElement element = (SchemaElement) iter.next();
                if (counter++ > 0) {
                    out.print(" | ");
                }
                out.print("<" + element.getLocalName() + "/>");
            }
            if (counter > 0) {
                out.print(" | ");
            }
            out.print("<spring:bean/>");
        }
    }

    protected List findImplementationsOf(JClass type) {
        List answer = new ArrayList();
        for (Iterator iter = answer.iterator(); iter.hasNext();) {
            SchemaElement element = (SchemaElement) iter.next();
            if (isImplementationOf(type, element.getType())) {
                answer.add(element);
            }
        }
        return answer;
    }

    // XSD generation
    // -------------------------------------------------------------------------
    protected void generateSchema(String namespace, List elements) throws IOException {
        // TODO can only handle 1 schema document so far...
        File file = destFile;
        System.out.println("Generating XSD file: " + file + " for namespace: " + namespace);
        PrintWriter out = new PrintWriter(new FileWriter(file));
        try {
            generateSchema(out, namespace, elements);
        }
        finally {
            out.close();
        }
    }

    protected void generateSchema(PrintWriter out, String namespace, List elements) {
        out.println("<?xml version='1.0'?>");
        out.println("<!-- NOTE: this file is autogenerated by XBeans -->");
        out.println();
        out.println("<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'");
        out.println("  xmlns:xsd='http://www.w3.org/2001/XMLSchema'");
        out.println("  xmlns:tns='" + namespace + "' targetNamespace='" + namespace + "'>");

        for (Iterator iter = elements.iterator(); iter.hasNext();) {
            SchemaElement element = (SchemaElement) iter.next();
            generateSchemaElement(out, element);
        }

        out.println();
        out.println("</xs:schema");
    }

    protected void generateSchemaElement(PrintWriter out, SchemaElement element) {
        out.println();
        JClass type = element.getType();
        out.println("  <!-- element for type: " + type.getQualifiedName() + " -->");

        String localName = element.getLocalName();

        out.println("  <xs:element name='" + localName + "' type='tns:" + localName + "'/>");
        out.println("  <xs:complexType name='" + localName + "'>");

        JProperty[] properties = type.getProperties();
        int complexCount = 0;
        for (int i = 0; i < properties.length; i++) {
            JProperty property = properties[i];
            if (!isValidProperty(property)) {
                continue;
            }
            if (isSimpleType(property)) {
                generateSchemaElementSimpleProperty(out, element, property);
            }
            else {
                complexCount++;
            }
        }
        if (complexCount > 0) {
            out.println("    <xs:sequence>");
            for (int i = 0; i < properties.length; i++) {
                JProperty property = properties[i];
                if (!isValidProperty(property)) {
                    continue;
                }
                if (!isSimpleType(property)) {
                    generateSchemaElementComplexProperty(out, element, property);
                }
            }
        }
        out.println("    </xs:sequence>");
        out.println("  </xs:complexType>");
        out.println();
    }

    protected void generateSchemaElementSimpleProperty(PrintWriter out, SchemaElement element, JProperty property) {
        out.println("    <xs:attribute name='" + getPropertyXmlName(property) + "' type='" + getXSDType(property)
                + "'/>");
    }

    protected void generateSchemaElementComplexProperty(PrintWriter out, SchemaElement element, JProperty property) {
        out.println("      <xs:element name='" + getPropertyXmlName(property) + "' minOccurs='0' maxOccurs='1''/>");

        // TODO expand the element declaration to list possible implementations
        // and allow extension via xsd:any
    }

    protected boolean isSimpleType(JProperty property) {
        JClass type = property.getType();
        String name = type.getQualifiedName();
        if (type.isPrimitiveType()) {
            return true;
        }
        if (name.equals("javax.xml.namespace.QName")) {
            return true;
        }
        if (name.endsWith("]")) {
            return false;
        }
        Class theClass;
        try {
            theClass = loadClass(name);
        }
        catch (ClassNotFoundException e) {
            System.out.println("Warning, could not load class: " + name);
            return false;
        }
        // lets see if we can find a property editor for this type
        PropertyEditor editor = PropertyEditorManager.findEditor(theClass);
        return editor != null;
    }

    protected boolean isValidProperty(JProperty property) {
        if ( !property.getSimpleName().equals("Class")) {
            JMethod setter = property.getSetter();
            if (setter == null) {
                return false;
            }
            JAnnotation annotation = setter.getAnnotation(XBEAN_ANNOTATION);
            if (annotation != null) {
                JAnnotationValue value = annotation.getValue("hide");
                if (value != null) {
                    return value.asBoolean();
                }
            }
            return true;
        }
        return false;
    }

    protected String getPropertyXmlName(JAnnotatedElement element) {
        String answer = element.getSimpleName();
        if (answer.length() > 0) {
            answer = decapitalise(answer);
        }

        // lets strip off the trailing Bean for *FactoryBean types by default
        if (element instanceof JClass && answer.endsWith("FactoryBean")) {
            answer = answer.substring(0, answer.length() - 4);
        }
        return answer;
    }

    protected String decapitalise(String answer) {
        return answer.substring(0, 1).toLowerCase() + answer.substring(1);
    }

    protected String getDescription(JClass type) {
        return getCommentText(type);
    }

    protected String getDescription(JProperty property) {
        JMethod setter = property.getSetter();
        if (setter != null) {
            return getCommentText(setter);
        }
        return "";
    }

    protected String getCommentText(JAnnotatedElement element) {
        JAnnotation annotation = element.getAnnotation(XBEAN_ANNOTATION);
        if (annotation != null) {
            JAnnotationValue value = annotation.getValue("description");
            if (value != null) {
                return value.asString();
            }
        }
        JComment comment = element.getComment();
        if (comment != null) {
            return comment.getText();
        }
        return "";
    }

    protected String getXSDType(JProperty property) {
        if (xsdTypeMap == null) {
            xsdTypeMap = new HashMap();
            loadXsdTypeMap(xsdTypeMap);
        }
        String typeName = property.getType().getQualifiedName();
        String answer = (String) xsdTypeMap.get(typeName);
        if (answer == null) {
            answer = "xsd:string";
        }
        return answer;
    }

    protected void loadXsdTypeMap(Map map) {
        // TODO check these XSD types are right...
        map.put(String.class.getName(), "xsd:string");
        map.put(Boolean.class.getName(), "xsd:boolean");
        map.put(boolean.class.getName(), "xsd:boolean");
        map.put(Byte.class.getName(), "xsd:byte");
        map.put(byte.class.getName(), "xsd:byte");
        map.put(Short.class.getName(), "xsd:short");
        map.put(short.class.getName(), "xsd:short");
        map.put(Integer.class.getName(), "xsd:integer");
        map.put(int.class.getName(), "xsd:integer");
        map.put(Long.class.getName(), "xsd:long");
        map.put(long.class.getName(), "xsd:long");
        map.put(Float.class.getName(), "xsd:float");
        map.put(float.class.getName(), "xsd:float");
        map.put(Double.class.getName(), "xsd:double");
        map.put(double.class.getName(), "xsd:double");
        map.put(java.util.Date.class.getName(), "xsd:date");
        map.put(java.sql.Date.class.getName(), "xsd:date");
        map.put("javax.xml.namespace.QName", "xsd:QName");
    }

    /**
     * Attempts to load the class on the current thread context class loader or
     * the class loader which loaded us
     */
    protected Class loadClass(String name) throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return contextClassLoader.loadClass(name);
            }
            catch (ClassNotFoundException e) {
            }
        }
        return getClass().getClassLoader().loadClass(name);
    }

    protected void loadModel() {
        for (int i = 0; i < classes.length; i++) {
            JClass type = classes[i];
            JAnnotation annotation = type.getAnnotation(XBEAN_ANNOTATION);
            if (annotation != null) {
                String localName = getStringValue(annotation, "element");
                if (localName == null) {
                    localName = getPropertyXmlName(type);
                }
                String namespace = getStringValue(annotation, "namespace");
                if (namespace == null) {
                    namespace = defaultNamespace;
                }
                boolean root = getBooleanValue(annotation, "rootElement");
                addXmlType(type, localName, namespace, root);
            }
            else {
                log.debug("No XML annotation found for type: " + type.getQualifiedName());
            }
        }
    }

    protected void addXmlType(JClass type, String localName, String namespace, boolean root) {
        List list = (List) namespaces.get(namespace);
        if (list == null) {
            list = new ArrayList();
            namespaces.put(namespace, list);
        }
        SchemaElement element = new SchemaElement(type, localName, namespace);
        list.add(element);
        allElements.add(element);
        if (root) {
            rootElement = element;
        }
    }

    protected boolean isCollection(JClass type) {
        return type.isArrayType() || implementsInterface(type, Collection.class.getName());
    }

    protected boolean implementsInterface(JClass type, String interfaceClass) {
        JClass[] interfaces = type.getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            JClass anInterface = interfaces[i];
            if (anInterface.getQualifiedName().equals(interfaceClass)) {
                return true;
            }
        }
        JClass superclass = type.getSuperclass();
        if (superclass == null || superclass == type) {
            return false;
        }
        else {
            return implementsInterface(superclass, interfaceClass);
        }
    }

    protected boolean isImplementationOf(JClass type, JClass interfaceOrBaseClass) {
        if (interfaceOrBaseClass.isInterface()) {
            return implementsInterface(type, interfaceOrBaseClass.getQualifiedName());
        }
        while (type != null) {
            type = type.getSuperclass();
            if (type != null && type.getQualifiedName().equals(interfaceOrBaseClass.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    protected String getStringValue(JAnnotation annotation, String name) {
        JAnnotationValue value = annotation.getValue(name);
        if (value != null) {
            return value.asString();
        }
        return null;
    }

    protected boolean getBooleanValue(JAnnotation annotation, String name) {
        JAnnotationValue value = annotation.getValue(name);
        if (value != null) {
            return value.asBoolean();
        }
        return false;
    }
}
