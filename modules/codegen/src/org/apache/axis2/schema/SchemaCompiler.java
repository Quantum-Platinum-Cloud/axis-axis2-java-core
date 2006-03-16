package org.apache.axis2.schema;

import org.apache.axis2.schema.i18n.SchemaCompilerMessages;
import org.apache.axis2.schema.util.SchemaPropertyLoader;
import org.apache.axis2.schema.writer.BeanWriter;
import org.apache.ws.commons.om.OMElement;
import org.apache.ws.commons.om.OMAttribute;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaAll;
import org.apache.ws.commons.schema.XmlSchemaAny;
import org.apache.ws.commons.schema.XmlSchemaAnyAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaComplexContent;
import org.apache.ws.commons.schema.XmlSchemaComplexContentExtension;
import org.apache.ws.commons.schema.XmlSchemaComplexContentRestriction;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaContent;
import org.apache.ws.commons.schema.XmlSchemaContentModel;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaImport;
import org.apache.ws.commons.schema.XmlSchemaObject;
import org.apache.ws.commons.schema.XmlSchemaObjectCollection;
import org.apache.ws.commons.schema.XmlSchemaObjectTable;
import org.apache.ws.commons.schema.XmlSchemaParticle;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaSimpleContent;
import org.apache.ws.commons.schema.XmlSchemaSimpleContentExtension;
import org.apache.ws.commons.schema.XmlSchemaSimpleContentRestriction;
import org.apache.ws.commons.schema.XmlSchemaSimpleType;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeContent;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeList;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeRestriction;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeUnion;
import org.apache.ws.commons.schema.XmlSchemaType;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Schema compiler for ADB. Based on WS-Commons schema object model.
 */
public class SchemaCompiler {


    private CompilerOptions options;
    private HashMap processedTypemap;

    //the list of processedElements for the outer elements
    private HashMap processedElementMap;

    private HashMap processedAnonymousComplexTypesMap;

    //we need this map to keep the referenced elements. these elements need to be kept seperate
    //to avoid conflicts
    private HashMap processedElementRefMap;
    private HashMap simpleTypesMap;
    private HashMap changedTypeMap;

    // this map is necessary to retain the metainformation of types. The reason why these
    // meta info 'bags' would be useful later is to cater for the extensions and restrictions
    // of types
    private HashMap processedTypeMetaInfoMap;

    //
    private ArrayList processedElementList;

    //a list of nillable elements - used to generate code
    //for nillable elements
    private List nillableElementList;
    private BeanWriter writer = null;
    private Map baseSchemaTypeMap = null;

    // a list of externally identified QNames to be processed. This becomes
    // useful when  only a list of external elements need to be processed

    public static final String ANY_ELEMENT_FIELD_NAME = "extraElement";
    public static final String EXTRA_ATTRIBUTE_FIELD_NAME = "extraAttributes";

    public static final String DEFAULT_CLASS_NAME = OMElement.class.getName();
    public static final String DEFAULT_CLASS_ARRAY_NAME = "org.apache.ws.commons.om.OMElement[]";

    public static final String DEFAULT_ATTRIB_CLASS_NAME = OMAttribute.class.getName();
    public static final String DEFAULT_ATTRIB_ARRAY_CLASS_NAME = "org.apache.ws.commons.om.OMAttribute[]";


    private static int typeCounter = 0;




    /**
     * @return the processes element map
     * includes the Qname of the element as the key and a
     * String representing the fully qualified class name
     */
    public HashMap getProcessedElementMap() {
        return processedElementMap;
    }


    /**
     * @return a map of Qname vs models. A model can be anything,
     * ranging from a DOM document to a stream. This is taken from the
     * writer and the schema compiler has no control over it
     */
    public Map getProcessedModelMap() {
        return writer.getModelMap();
    }

    /**
     * Constructor - Accepts a options bean
     *
     * @param options
     */
    public SchemaCompiler(CompilerOptions options) throws SchemaCompilationException {

        if (options == null) {
            //create an empty options object
            this.options = new CompilerOptions();
        } else {
            this.options = options;
        }

        //instantiate the maps
        processedTypemap = new HashMap();
        processedElementMap = new HashMap();
        simpleTypesMap = new HashMap();
        processedElementList = new ArrayList();
        processedAnonymousComplexTypesMap = new HashMap();
        changedTypeMap = new HashMap();
        processedTypeMetaInfoMap = new HashMap();
        processedElementRefMap = new HashMap();
        nillableElementList = new ArrayList();

        //load the writer and initiliaze the base types
        writer = SchemaPropertyLoader.getBeanWriterInstance();
        writer.init(this.options);

        //load the base types
        baseSchemaTypeMap = SchemaPropertyLoader.getTypeMapperInstance().getTypeMap();


    }

    /**
     * Compile a list of schemas
     * This actually calls the compile (XmlSchema s) method repeatedly
     *
     * @param schemalist
     * @throws SchemaCompilationException
     * @see #compile(org.apache.ws.commons.schema.XmlSchema)
     */
    public void compile(List schemalist) throws SchemaCompilationException {
        XmlSchema schema;
        try {
            for (int i = 0; i < schemalist.size(); i++) {
                schema = (XmlSchema) schemalist.get(i);
                compile(schema);
            }
        } catch (SchemaCompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaCompilationException(e);
        }
    }

    /**
     * Compile (rather codegen) a single schema element
     * @param schema
     * @throws SchemaCompilationException
     */
    public void compile(XmlSchema schema) throws SchemaCompilationException {

        //First look for the schemas that are imported and process them
        //Note that these are processed recursively!

        XmlSchemaObjectCollection includes = schema.getIncludes();
        if (includes != null) {
            Iterator tempIterator = includes.getIterator();
            while (tempIterator.hasNext()) {
                Object o = tempIterator.next();
                if (o instanceof XmlSchemaImport) {
                    XmlSchema schema1 = ((XmlSchemaImport) o).getSchema();
                    if (schema1 != null) compile(schema1);
                }
            }
        }


        //select all the elements. We generate the code for types
        //only if the elements refer them!!! regardless of the fact that
        //we have a list of elementnames, we'll need to process all the elements
        XmlSchemaObjectTable elements = schema.getElements();
        Iterator xmlSchemaElement1Iterator = elements.getValues();
        while (xmlSchemaElement1Iterator.hasNext()) {
            //this is the set of outer elements so we need to generate classes
            //The outermost elements do not contain occurence counts (!) so we do not need
            //to check for arraytypes
            processElement((XmlSchemaElement) xmlSchemaElement1Iterator.next(), schema);
        }


        Iterator xmlSchemaElement2Iterator = elements.getValues();

        // re-iterate through the elements and write them one by one
        // if the mode is unwrap this process will not really write the
        // classes but will accumilate the models for a final single shot
        // write
        while (xmlSchemaElement2Iterator.hasNext()) {
            //this is the set of outer elements so we need to generate classes
            writeElement((XmlSchemaElement) xmlSchemaElement2Iterator.next());
        }


        if (options.isWrapClasses()) {
            writer.writeBatch();
        }
    }

    /**
     * @return the property map of the schemacompiler.
     * In this case it would be the property map loaded from
     * the configuration file
     */
    public Properties getCompilerProperties(){
        return SchemaPropertyLoader.getPropertyMap() ;
    }


    /**
     * Writes the element
     *
     * @param xsElt
     * @throws SchemaCompilationException
     */
    private void writeElement(XmlSchemaElement xsElt) throws SchemaCompilationException {

        if (this.processedElementMap.containsKey(xsElt.getQName())) {
            return;
        }

        XmlSchemaType schemaType = xsElt.getSchemaType();


        BeanWriterMetaInfoHolder metainf = new BeanWriterMetaInfoHolder();
        if (schemaType != null && schemaType.getName() != null) {
            //this is a named type
            QName qName = schemaType.getQName();
            //find the class name
            String className = findClassName(qName, isArray(xsElt));

            //this means the schema type actually returns a different QName
            if (changedTypeMap.containsKey(qName)) {
                metainf.registerMapping(xsElt.getQName(),
                        (QName) changedTypeMap.get(qName),
                        className);
            } else {
                metainf.registerMapping(xsElt.getQName(),
                        qName,
                        className);
            }


        }else if (xsElt.getRefName()!= null){
            // Since top level elements would not have references
            // and we only write toplevel elements, this should
            // not be a problem , atleast should not occur in a legal schema
        }else if (xsElt.getSchemaTypeName()!= null) {
            QName qName = xsElt.getSchemaTypeName();
            String className = findClassName(qName, isArray(xsElt));
            metainf.registerMapping(xsElt.getQName(),
                    qName,
                    className);


        }else if (schemaType != null){  //the named type should have been handled already

            //we are going to special case the anonymous complex type. Our algorithm for dealing
            //with it is to generate a single object that has the complex content inside. Really the
            //intent of the user when he declares the complexType anonymously is to use it privately
            //First copy the schema types content into the metainf holder
            metainf = (BeanWriterMetaInfoHolder) this.processedAnonymousComplexTypesMap.get(xsElt);
            metainf.setAnonymous(true);
        }else{
            //this means we did not find any schema type associated with the particular element. we
            //should be throwing an exception here
            throw new SchemaCompilationException("no type!!");//todo i18n this
        }

        if (nillableElementList.contains(xsElt.getQName())){
            metainf.registerNillableQName(xsElt.getQName());
        }


        String writtenClassName = writer.write(xsElt, processedTypemap, metainf);
        processedElementMap.put(xsElt.getQName(), writtenClassName);
    }

    /**
     * For inner elements
     * @param xsElt
     * @param innerElementMap
     * @param parentSchema
     * @throws SchemaCompilationException
     */
    private void processElement(XmlSchemaElement xsElt,Map innerElementMap,List localNillableList,XmlSchema parentSchema) throws SchemaCompilationException {
        processElement(xsElt,false,innerElementMap,localNillableList,parentSchema);
    }

    /**
     * For outer elements
     * @param xsElt
     * @param parentSchema
     * @throws SchemaCompilationException
     */
    private void processElement(XmlSchemaElement xsElt,XmlSchema parentSchema) throws SchemaCompilationException {
        processElement(xsElt,true,null,null,parentSchema);
    }
    /**
     * Process and Element
     *
     * @param xsElt
     * @param isOuter-  We need to know this since the treatment of outer elements is different that
     *                     inner elements
     * @param isArray-  flag saying whether the elements represents an array
     * @throws SchemaCompilationException
     */
    private void processElement(XmlSchemaElement xsElt, boolean isOuter,Map innerElementMap,List localNillableList, XmlSchema parentSchema) throws SchemaCompilationException {

        //The processing element logic seems to be quite simple. Look at the relevant schema type
        //for each and every element and process that accordingly.
        //this means that any unused type definitions would not be generated!
        if (isOuter && processedElementList.contains(xsElt.getQName())) {
            return;
        }

        XmlSchemaType schemaType = xsElt.getSchemaType();
        if (schemaType != null) {
            processSchema(xsElt, schemaType,parentSchema);
            //at this time it is not wise to directly write the class for the element
            //so we push the complete element to an arraylist and let the process
            //pass through. We'll be iterating through the elements writing them
            //later

            if (!isOuter) {

                if (schemaType.getName()!=null){
                    // this element already has a name. Which means we can directly
                    // register it
                    innerElementMap.put(xsElt.getQName(), findClassName(schemaType.getQName(), isArray(xsElt)));
                    //since this is a inner element we should add it to the inner element map
                }else{
                    //this is an anon type. This should have been already processed and registered at
                    //the anon map. we've to write it just like we treat a referenced type(giving due
                    //care that this is meant to be an attribute in some class)

                    QName generatedTypeName = generateTypeQName(xsElt.getQName(), parentSchema);

                    if (schemaType instanceof XmlSchemaComplexType){
                        //set a name
                        schemaType.setName(generatedTypeName.getLocalPart());
                        writeComplexType((XmlSchemaComplexType)schemaType,
                                (BeanWriterMetaInfoHolder)processedAnonymousComplexTypesMap.get(xsElt));
                        //remove the reference from the anon list since we named the type
                        processedAnonymousComplexTypesMap.remove(xsElt);
                        innerElementMap.put(
                                xsElt.getQName(),
                                findClassName(schemaType.getQName(), isArray(xsElt)));
                    }
                }
            }else{
                this.processedElementList.add(xsElt.getQName());
            }

        }else if (xsElt.getRefName()!=null){

            //process the referenced type. It could be thought that the referenced element replaces this
            //element

            XmlSchemaElement referencedElement = parentSchema.getElementByName(xsElt.getRefName());
            //if the element is referenced, then it should be one of the outer (global) ones
            processElement(referencedElement, parentSchema);

            //no outer check required here. If the element is having a ref, then it is definitely
            //not an outer element since the top level elements are not supposed to have refs
            //Also we are sure that it should have a type reference
            QName referenceEltQName = referencedElement.getQName();
            if (referencedElement.getSchemaTypeName()!=null){
                String className = findClassName(referencedElement.getSchemaTypeName(), isArray(xsElt));
                //if this element is referenced, there's no QName for this element
                this.processedElementRefMap.put(referenceEltQName, className);
            }else{
                //this referenced element has an anon type and that anon type has been already
                //processed. But in this case we need it to be a seperate class since this
                // complextype has to be added as an attribute in a class.
                //generate a name for this type
                QName generatedTypeName = generateTypeQName(referenceEltQName, parentSchema);
                XmlSchemaType referenceSchemaType = referencedElement.getSchemaType();

                if (referenceSchemaType instanceof XmlSchemaComplexType){
                    //set a name
                    referenceSchemaType.setName(generatedTypeName.getLocalPart());

                    writeComplexType((XmlSchemaComplexType)referenceSchemaType,
                            (BeanWriterMetaInfoHolder)processedAnonymousComplexTypesMap.get(referencedElement));
                    //remove the reference from the anon list since we named the type
                    processedAnonymousComplexTypesMap.remove(referencedElement);

                    //add this to the processed ref type map
                    this.processedElementRefMap.put(referenceEltQName,findClassName(
                            referenceSchemaType.getQName(), isArray(referencedElement)
                    ));
                }
            }

        }else if (xsElt.getSchemaTypeName()!=null){
            //There can be instances where the SchemaType is null but the schemaTypeName is not!
            //this specifically happens with xsd:anyType.
            QName schemaTypeName = xsElt.getSchemaTypeName();
            XmlSchemaType typeByName = parentSchema.getTypeByName(schemaTypeName);
            if (typeByName!=null){
                //this type is found in the schema so we can process it
                processSchema(xsElt, typeByName,parentSchema);
                if (!isOuter) {
                    String className = findClassName(schemaTypeName, isArray(xsElt));
                    //since this is a inner element we should add it to the inner element map
                    innerElementMap.put(xsElt.getQName(), className);
                }else{
                    this.processedElementList.add(xsElt.getQName());
                }
            }else{
                //this type is not found at all. we'll just register it with whatever the class name we can comeup with
                if (!isOuter) {
                    String className = findClassName(schemaTypeName, isArray(xsElt));
                    innerElementMap.put(xsElt.getQName(), className);
                }else{
                    this.processedElementList.add(xsElt.getQName());
                }
            }
        }

        //add this elements QName to the nillable group if it has the  nillable attribute
        if (xsElt.isNillable()){
            if (isOuter){
                this.nillableElementList.add(xsElt.getQName());
            }else{
                localNillableList.add(xsElt.getQName());
            }
        }

    }

    /**
     * Generate a unique type Qname using an element name
     * @param referenceEltQName
     * @param parentSchema
     * @return
     */
    private QName generateTypeQName(QName referenceEltQName, XmlSchema parentSchema) {
        QName generatedTypeName = new QName(referenceEltQName.getNamespaceURI(),
                referenceEltQName.getLocalPart() + getNextTypeSuffix());
        while (parentSchema.getTypeByName(generatedTypeName)!= null){
            generatedTypeName = new QName(referenceEltQName.getNamespaceURI(),
                    referenceEltQName.getLocalPart() + getNextTypeSuffix());
        }
        return generatedTypeName;
    }

    /**
     * Finds whether a given class is already made
     * @param qName
     * @return
     */
    private boolean isAlreadyProcessed(QName qName){
        return processedTypemap.containsKey(qName)||
                simpleTypesMap.containsKey(qName) ||
                baseSchemaTypeMap.containsKey(qName);
    }


    /**
     * A method to pick the ref class name
     * @param name
     * @param isArray
     * @return
     */
    private String findRefClassName(QName name,boolean isArray){
        String className = null;
        if (processedElementRefMap.get(name)!=null){
            className =(String)processedElementRefMap.get(name);

            if (isArray) {
                //append the square braces that say this is an array
                //hope this works for all cases!!!!!!!
                //todo this however is a thing that needs to be
                //todo fixed to get complete language support
                className = className + "[]";
            }
        }
        return className;

    }
    /**
     * Finds a class name from the given Qname
     *
     * @param schemaType
     * @return
     */
    private String findClassName(QName qName, boolean isArray) {

        //find the class name
        String className;
        if (processedTypemap.containsKey(qName)) {
            className = (String) processedTypemap.get(qName);
        } else if (simpleTypesMap.containsKey(qName)) {
            className = (String) simpleTypesMap.get(qName);
        } else if (baseSchemaTypeMap.containsKey(qName)) {
            className = (String) baseSchemaTypeMap.get(qName);
        } else {
            // We seem to have failed in finding a class name for the
            //contained schema type. We better set the default then
            //however it's better if the default can be set through the
            //property file
            className = DEFAULT_CLASS_NAME;
        }

        if (isArray) {
            //append the square braces that say this is an array
            //hope this works for all cases!!!!!!!
            //todo this however is a thing that needs to be
            //todo fixed to get complete language support
            className = className + "[]";
        }
        return className;
    }

    /**
     * Process a schema element
     *
     * @param schemaType
     * @throws SchemaCompilationException
     */
    private void processSchema(XmlSchemaElement xsElt, XmlSchemaType schemaType,XmlSchema parentSchema) throws SchemaCompilationException {
        if (schemaType instanceof XmlSchemaComplexType) {
            //write classes for complex types
            XmlSchemaComplexType complexType = (XmlSchemaComplexType) schemaType;
            if (complexType.getName() != null) {
                processNamedComplexSchemaType(complexType,parentSchema);
            } else {
                processAnonymousComplexSchemaType(xsElt, complexType,parentSchema);
            }
        } else if (schemaType instanceof XmlSchemaSimpleType) {
            //process simple type
            processSimpleSchemaType((XmlSchemaSimpleType) schemaType);
        }
    }


    /**
     * @param complexType
     * @throws SchemaCompilationException
     */
    private void processAnonymousComplexSchemaType(XmlSchemaElement elt, XmlSchemaComplexType complexType,XmlSchema parentSchema)
            throws SchemaCompilationException {
        BeanWriterMetaInfoHolder metaInfHolder = processComplexType(complexType,parentSchema);

        //since this is a special case (an unnamed complex type) we'll put the already processed
        //metainf holder in a special map to be used later
        this.processedAnonymousComplexTypesMap.put(elt, metaInfHolder);
    }

    /**
     * handle the complex types which are named
     * @param complexType
     */
    private void processNamedComplexSchemaType(XmlSchemaComplexType complexType,XmlSchema parentSchema) throws SchemaCompilationException {

        if (processedTypemap.containsKey(complexType.getQName())
                || baseSchemaTypeMap.containsKey(complexType.getQName())) {
            return;
        }

        BeanWriterMetaInfoHolder metaInfHolder = processComplexType(complexType,parentSchema);

        //write the class. This type mapping would have been populated right now
        //Note - We always write classes for named complex types
        writeComplexType(complexType, metaInfHolder);


    }

    /**
     * Writes a complex type
     * @param complexType
     * @param metaInfHolder
     * @throws SchemaCompilationException
     */
    private void writeComplexType(XmlSchemaComplexType complexType, BeanWriterMetaInfoHolder metaInfHolder) throws SchemaCompilationException {
        String fullyQualifiedClassName = writer.write(complexType, processedTypemap, metaInfHolder);
        //populate the type map with the type QName
        processedTypemap.put(complexType.getQName(), fullyQualifiedClassName);
        processedTypeMetaInfoMap.put(complexType.getQName(),metaInfHolder);
    }

    private BeanWriterMetaInfoHolder processComplexType(XmlSchemaComplexType complexType,XmlSchema parentSchema) throws SchemaCompilationException {
        XmlSchemaParticle particle = complexType.getParticle();
        BeanWriterMetaInfoHolder metaInfHolder = new BeanWriterMetaInfoHolder();
        if (particle != null) {
            //Process the particle
            processParticle(particle, metaInfHolder,parentSchema);
        }

        //process attributes - first look for the explicit attributes
        XmlSchemaObjectCollection attribs = complexType.getAttributes();
        Iterator attribIterator = attribs.getIterator();
        while (attribIterator.hasNext()) {
            Object o = attribIterator.next();
            if (o instanceof XmlSchemaAttribute) {
                processAttribute((XmlSchemaAttribute) o, metaInfHolder);

            }
        }

        //process any attribute
        //somehow the xml schema parser does not seem to pickup the any attribute!!
        XmlSchemaAnyAttribute anyAtt = complexType.getAnyAttribute();
        if (anyAtt != null) {
            processAnyAttribute(metaInfHolder,anyAtt);
        }

        //process content ,either  complex or simple
        if (complexType.getContentModel()!=null){
            processContentModel(complexType.getContentModel(),
                    metaInfHolder,
                    parentSchema);
        }
        return metaInfHolder;
    }

    /**
     * Process the content models. A content model is either simple type or a complex type
     * and included inside a complex content
     */
    private void processContentModel(XmlSchemaContentModel content,
                                     BeanWriterMetaInfoHolder metaInfHolder,
                                     XmlSchema parentSchema)
            throws SchemaCompilationException{
        if (content instanceof XmlSchemaComplexContent){
            processComplexContent((XmlSchemaComplexContent)content,metaInfHolder,parentSchema);
        }else if (content instanceof XmlSchemaSimpleContent){
            processSimpleContent((XmlSchemaSimpleContent)content,metaInfHolder);
        }
    }

    /**
     * Prcess the complex content
     */
    private void processComplexContent(XmlSchemaComplexContent complexContent,
                                       BeanWriterMetaInfoHolder metaInfHolder,
                                       XmlSchema parentSchema)
            throws SchemaCompilationException{
        XmlSchemaContent content = complexContent.getContent();

        if (content instanceof XmlSchemaComplexContentExtension ){

            // to handle extension we need to attach the extended items to the base type
            // and create a new type
            XmlSchemaComplexContentExtension extension = (XmlSchemaComplexContentExtension)
                    content;

            //process the base type if it has not been processed yet
            if (!isAlreadyProcessed(extension.getBaseTypeName())){
                //pick the relevant basetype from the schema and process it
                XmlSchemaType type=  parentSchema.getTypeByName(extension.getBaseTypeName());
                if (type instanceof XmlSchemaComplexType) {
                    XmlSchemaComplexType complexType = (XmlSchemaComplexType) type;
                    if (complexType.getName() != null) {
                        processNamedComplexSchemaType(complexType,parentSchema);
                    } else {
                        //this is not possible. The extension should always
                        //have a name
                        throw new SchemaCompilationException("Unnamed complex type used in extension");//Internationlize this
                    }
                } else if (type instanceof XmlSchemaSimpleType) {
                    //process simple type
                    processSimpleSchemaType((XmlSchemaSimpleType)type);
                }
            }

            // before actually processing this node, we need to recurse through the base types and add their
            // children (sometimes even preserving the order) to the metainfo holder of this type
            // the reason is that for extensions, the prefered way is to have the sequences of the base class
            //* before * the sequence of the child element.
            copyMetaInfoHierarchy(metaInfHolder,extension.getBaseTypeName(),parentSchema);


            //process the particle of this node
            processParticle(extension.getParticle(),metaInfHolder,parentSchema);

            //the particle has been processed, However since this is an extension we need to
            //add the basetype as an extension to the complex type class.
            // The basetype has been processed already
            metaInfHolder.setExtension(true);
            metaInfHolder.setExtensionClassName(findClassName(extension.getBaseTypeName(),false));
            //Note  - this is no array! so the array boolean is false

        }else if (content instanceof XmlSchemaComplexContentRestriction){
            //todo handle complex restriction here
        }
    }

    /**
     * Recursive method to populate the metainfo holders with info from the base types
     * @param metaInfHolder
     * @param baseTypeName
     * @param parentSchema
     */
    private void copyMetaInfoHierarchy(BeanWriterMetaInfoHolder metaInfHolder,
                                       QName baseTypeName,
                                       XmlSchema parentSchema)
            throws SchemaCompilationException {
        XmlSchemaType type = parentSchema.getTypeByName(baseTypeName);
        BeanWriterMetaInfoHolder baseMetaInfoHolder = (BeanWriterMetaInfoHolder)
                processedTypeMetaInfoMap.get(baseTypeName);

        if (baseMetaInfoHolder!= null){

            // see whether this type is also extended from some other type first
            // if so proceed to set their parents as well.
            if (type instanceof XmlSchemaComplexType){
                XmlSchemaComplexType complexType = (XmlSchemaComplexType)type;
                if (complexType.getContentModel()!= null){
                    XmlSchemaContentModel content = complexType.getContentModel();
                    if (content instanceof XmlSchemaComplexContent){
                        XmlSchemaComplexContent complexContent =
                                (XmlSchemaComplexContent)content;
                        if (complexContent.getContent() instanceof XmlSchemaComplexContentExtension){
                            XmlSchemaComplexContentExtension extension =
                                    (XmlSchemaComplexContentExtension)complexContent.getContent();
                            //recursively call the copyMetaInfoHierarchy method
                            copyMetaInfoHierarchy(baseMetaInfoHolder,
                                    extension.getBaseTypeName(),
                                    parentSchema);

                        }else  if (complexContent.getContent() instanceof XmlSchemaComplexContentRestriction){

                            XmlSchemaComplexContentRestriction restriction =
                                    (XmlSchemaComplexContentRestriction)complexContent.getContent();
                            //recursively call the copyMetaInfoHierarchy method
                            copyMetaInfoHierarchy(baseMetaInfoHolder,
                                    restriction.getBaseTypeName(),
                                    parentSchema);

                        }else{
                            throw new SchemaCompilationException(
                                    SchemaCompilerMessages.getMessage("schema.unknowncontenterror"));
                        }

                    }else if (content instanceof XmlSchemaSimpleContent){
                        //todo
                    }else{
                        throw new SchemaCompilationException(
                                SchemaCompilerMessages.getMessage("schema.unknowncontenterror"));
                    }
                }

                //Do the actual parent setting
                metaInfHolder.setAsParent(baseMetaInfoHolder);
            }
        }
    }

    private void processSimpleContent(XmlSchemaSimpleContent simpleContent,BeanWriterMetaInfoHolder metaInfHolder){
        XmlSchemaContent content;
        content = simpleContent.getContent();
        if (content instanceof XmlSchemaSimpleContentExtension){
            //todo - handle simple type extension here
        }else if (content instanceof XmlSchemaSimpleContentRestriction){
            //todo - Handle simple type restriction here
        }
    }

    /**
     * Handle any attribute
     * @param metainf
     */
    private void processAnyAttribute(BeanWriterMetaInfoHolder metainf,XmlSchemaAnyAttribute anyAtt) {

        //The best thing we can do here is to add a set of OMAttributes
        //since attributes do not have the notion of minoccurs/maxoccurs the
        //safest option here is to have an OMAttribute array
        metainf.registerMapping(new QName(EXTRA_ATTRIBUTE_FIELD_NAME),
                null,
                DEFAULT_ATTRIB_ARRAY_CLASS_NAME,
                SchemaConstants.ANY_ATTRIBUTE_TYPE);

    }



    /**
     * Process the attribute
     *
     * @param att
     * @param metainf
     */
    public void processAttribute(XmlSchemaAttribute att, BeanWriterMetaInfoHolder metainf) {
        //for now we assume (!!!) that attributes refer to standard types only
        QName schemaTypeName = att.getSchemaTypeName();
        if (baseSchemaTypeMap.containsKey(schemaTypeName)) {
            metainf.registerMapping(att.getQName(),
                    schemaTypeName,
                    baseSchemaTypeMap.get(schemaTypeName).toString(), SchemaConstants.ATTRIBUTE_TYPE);
        } else {
            //todo his attribute refers to a custom type, probably one of the extended simple types.
            //todo handle it here
        }
    }

    /**
     * Process a particle- A particle may be a sequence,all or a choice
     *
     * @param particle
     * @param metainfHolder
     * @throws SchemaCompilationException
     */
    private void processParticle(XmlSchemaParticle particle, //particle being processed
                                 BeanWriterMetaInfoHolder metainfHolder // metainf holder
            ,XmlSchema parentSchema) throws SchemaCompilationException {
        if (particle instanceof XmlSchemaSequence) {
            XmlSchemaObjectCollection items = ((XmlSchemaSequence) particle).getItems();
            process(items, metainfHolder, true,parentSchema);
        } else if (particle instanceof XmlSchemaAll) {
            XmlSchemaObjectCollection items = ((XmlSchemaAll) particle).getItems();
            process(items, metainfHolder, false,parentSchema);
        } else if (particle instanceof XmlSchemaChoice) {
            XmlSchemaObjectCollection items = ((XmlSchemaChoice) particle).getItems();
            metainfHolder.setChoice(true);
            process(items, metainfHolder, false,parentSchema);

        }
    }

    /**
     * @param items
     * @param metainfHolder
     * @param order
     * @throws SchemaCompilationException
     */
    private void process(XmlSchemaObjectCollection items,
                         BeanWriterMetaInfoHolder metainfHolder,
                         boolean order,
                         XmlSchema parentSchema) throws SchemaCompilationException {
        int count = items.getCount();
        Map processedElementArrayStatusMap = new HashMap();
        Map processedElementTypeMap = new HashMap();
        List localNillableList = new ArrayList();

        Map elementOrderMap = new HashMap();

        for (int i = 0; i < count; i++) {
            XmlSchemaObject item = items.getItem(i);

            if (item instanceof XmlSchemaElement) {
                //recursively process the element
                XmlSchemaElement xsElt = (XmlSchemaElement) item;

                boolean isArray = isArray(xsElt);
                processElement(xsElt, processedElementTypeMap,localNillableList,parentSchema); //we know for sure this is not an outer type
                processedElementArrayStatusMap.put(xsElt, (isArray) ? Boolean.TRUE : Boolean.FALSE);
                if (order) {
                    //we need to keep the order of the elements. So push the elements to another
                    //hashmap with the order number
                    elementOrderMap.put(xsElt, new Integer(i));
                }

                //handle xsd:any ! We place an OMElement (or an array of OMElements) in the generated class
            } else if (item instanceof XmlSchemaAny) {
                XmlSchemaAny any = (XmlSchemaAny) item;
                processedElementTypeMap.put(new QName(ANY_ELEMENT_FIELD_NAME),any);
                //any can also be inside a sequence
                if (order) {
                    elementOrderMap.put(any, new Integer(i));
                }
                //we do not register the array status for the any type
                processedElementArrayStatusMap.put(any,isArray(any) ? Boolean.TRUE : Boolean.FALSE);
            } else {
                //there may be other types to be handled here. Add them
                //when we are ready
            }


        }

        // loop through the processed items and add them to the matainf object
        Iterator processedElementsIterator = processedElementArrayStatusMap.keySet().iterator();
        int startingItemNumberOrder = metainfHolder.getOrderStartPoint();
        while (processedElementsIterator.hasNext()) {
            Object child = processedElementsIterator.next();

            // process the XmlSchemaElement
            if (child instanceof XmlSchemaElement){
                XmlSchemaElement elt = (XmlSchemaElement) child;
                String clazzName;
                QName referencedQName;

                if (elt.getQName()!=null){
                    clazzName = (String) processedElementTypeMap.get(elt.getQName());
                    referencedQName = elt.getQName();                                
                    metainfHolder.registerMapping(referencedQName,
                            elt.getSchemaType()!=null?elt.getSchemaType().getQName():elt.getSchemaTypeName(), //always get the schema type name from the schema it-self
                            clazzName,
                            ((Boolean) processedElementArrayStatusMap.get(elt)).booleanValue() ?
                                    SchemaConstants.ARRAY_TYPE :
                                    SchemaConstants.ELEMENT_TYPE);
                }else{ //probably this is referenced
                    referencedQName = elt.getRefName();
                    boolean arrayStatus = ((Boolean) processedElementArrayStatusMap.get(elt)).booleanValue();
                    clazzName = findRefClassName(referencedQName,arrayStatus);
                    metainfHolder.registerMapping(referencedQName,
                            parentSchema.getElementByName(referencedQName).getSchemaTypeName()
                            , clazzName,
                            arrayStatus ?
                                    SchemaConstants.ARRAY_TYPE :
                                    SchemaConstants.ELEMENT_TYPE);
                }



                //register the occurence counts
                metainfHolder.addMaxOccurs(referencedQName, elt.getMaxOccurs());
                metainfHolder.addMinOccurs(referencedQName, elt.getMinOccurs());
                //we need the order to be preserved. So record the order also
                if (order) {
                    //record the order in the metainf holder
                    Integer integer = (Integer) elementOrderMap.get(elt);
                    metainfHolder.registerQNameIndex(referencedQName,
                            startingItemNumberOrder + integer.intValue());
                }

                //get the nillable state and register that on the metainf holder
                if (localNillableList.contains(elt.getQName())){
                    metainfHolder.registerNillableQName(elt.getQName());
                }

                // process the XMLSchemaAny
            }else if (child instanceof XmlSchemaAny){
                XmlSchemaAny any = (XmlSchemaAny)child;

                //since there is only one element here it does not matter
                //for the constant. However the problem occurs if the users
                //uses the same name for an element decalration
                QName anyElementFieldName = new QName(ANY_ELEMENT_FIELD_NAME);

                //this can be an array or a single element
                boolean isArray =  ((Boolean) processedElementArrayStatusMap.get(any)).booleanValue();
                metainfHolder.registerMapping(anyElementFieldName,
                        null,
                        isArray?DEFAULT_CLASS_ARRAY_NAME:DEFAULT_CLASS_NAME,
                        isArray?SchemaConstants.ANY_ARRAY_TYPE:SchemaConstants.ANY);

                metainfHolder.addMaxOccurs(anyElementFieldName,any.getMaxOccurs());
                metainfHolder.addMinOccurs(anyElementFieldName,any.getMinOccurs());

                if (order) {
                    //record the order in the metainf holder for the any
                    Integer integer = (Integer) elementOrderMap.get(any);
                    metainfHolder.registerQNameIndex(anyElementFieldName,
                            startingItemNumberOrder + integer.intValue());
                }


            }


        }

        //set the ordered flag in the metainf holder
        metainfHolder.setOrdered(order);
    }

    /**
     * Handle the simple content
     *
     * @param simpleType
     */
    private void processSimpleSchemaType(XmlSchemaSimpleType simpleType) {
        // handle the restriction
        XmlSchemaSimpleTypeContent content = simpleType.getContent();
        if (content != null) {
            if (content instanceof XmlSchemaSimpleTypeRestriction) {
                XmlSchemaSimpleTypeRestriction restriction = (XmlSchemaSimpleTypeRestriction) content;
                QName baseTypeName = restriction.getBaseTypeName();
                //check whether the base type is one of the base schema types
                if (baseSchemaTypeMap.containsKey(baseTypeName)) {
                    //this is a basic xsd datatype. Populate the map and populate
                    //the mappings map
                    String className = (String) baseSchemaTypeMap.get(baseTypeName);
                    this.simpleTypesMap.put(simpleType.getQName(), className);
                    //set the old schema type QName and the new schema type QName
                    this.changedTypeMap.put(simpleType.getQName(), baseTypeName);
                } else {
                    //recurse
                    if (restriction.getBaseType() != null) {
                        processSimpleSchemaType(restriction.getBaseType());
                    }
                }
            }else if (content instanceof XmlSchemaSimpleTypeUnion) {
                //Todo - Handle unions here

            }else if (content instanceof XmlSchemaSimpleTypeList){
                //todo - Handle lists here
            }

        }


    }


    /**
     * Find whether a given particle is an array. The logic for deciding
     * whether a given particle is an array is depending on their minOccurs
     * and maxOccurs counts. If Maxoccurs is greater than one (1) then the
     * content is an array.
     * Also no higher level element will have the maxOccurs greater than one
     *
     * @param particle
     * @return
     * @throws SchemaCompilationException
     */
    private boolean isArray(XmlSchemaParticle particle) throws SchemaCompilationException {
        long minOccurs = particle.getMinOccurs();
        long maxOccurs = particle.getMaxOccurs();

        if (maxOccurs < minOccurs) {
            throw new SchemaCompilationException();
        } else {
            return (maxOccurs > 1);
        }

    }

    private String getNextTypeSuffix(){
        if (typeCounter==Integer.MAX_VALUE){
            typeCounter = 0;
        }
        return ("_type" +typeCounter++);
    }

}
