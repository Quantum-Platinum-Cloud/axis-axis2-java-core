package org.apache.axis2.databinding.utils;

import org.apache.axis2.databinding.ADBBean;
import org.apache.axis2.om.OMAttribute;
import org.apache.axis2.om.OMElement;
import org.apache.axis2.util.BeanSerializerUtil;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

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
 *
 */

public class ADBPullParser implements XMLStreamReader {

    private Object[] properties;
    private OMAttribute[] attributes;
    private QName elementQName;

    private boolean isHandlingDocumentElement;
    private boolean isStartDocumentThrown = false;
    private boolean isEndDocumentThrown = false;


    // Every parser can contain a reference to a pull parser of one of its children
    private ADBPullParser childPullParser;

    // a flag for this class to know, we are currently accessing one of the children's parsers
    private boolean accessingChildPullParser = false;

    // ===== To be used this with ADBBean =============
    private boolean isEndElementFinished = false;

    // ===== To be used with Simple Name Value pair ====
    private boolean processingADBNameValuePair = false;
    private boolean nameValuePairStartElementProcessed = false;
    private boolean nameValuePairTextProcessed = false;
    private boolean nameValuePairEndElementProcessed = false;
    private ParserInformation tempParserInfo;
    // ==============================================

    private ParserInformation parserInformation;

    // a pointer to the children list of current location
    private int currentIndex = 0;


    private ADBPullParser(QName adbBeansQName, Object[] properties, OMAttribute[] attributes) {
        this.properties = properties;
        this.elementQName = adbBeansQName;
        this.attributes = attributes;
        isHandlingDocumentElement = false;
    }

    private ADBPullParser(QName adbBeansQName, Object[] properties, OMAttribute[] attributes, boolean isHandlingDocumentElement) {
        this.properties = properties;
        this.elementQName = adbBeansQName;
        this.attributes = attributes;
        this.isHandlingDocumentElement = isHandlingDocumentElement;
    }

    /**
     * @param adbBeansQName
     * @param properties    - this should contain all the stuff that stax events should be generated.
     *                      Lets take an example of a bean.
     *                      <pre> <Person>
     *                                                                                                                                                                                                                                                                                                                                <Name>FooOne</Name>
     *                                                                                                                                                                                                                                                                                                                                <DependentOne>
     *                                                                                                                                                                                                                                                                                                                                    <Name>FooTwo</Name>
     *                                                                                                                                                                                                                                                                                                                                    <Age>25</Age>
     *                                                                                                                                                                                                                                                                                                                                    <Sex>Male</Sex>
     *                                                                                                                                                                                                                                                                                                                                </DependentOne>
     *                                                                                                                                                                                                                                                                                                                            </Person>
     *                      <p/>
     *                                                                                                                                                                                                                                                                                                                            so the mapping bean for this is
     *                                                                                                                                                                                                                                                                                                                            class Person {
     *                                                                                                                                                                                                                                                                                                                                String Name;
     *                                                                                                                                                                                                                                                                                                                                Dependent dependentOne;
     *                                                                                                                                                                                                                                                                                                                            }
     *                      <p/>
     *                                                                                                                                                                                                                                                                                                                            class Dependent {
     *                                                                                                                                                                                                                                                                                                                                String name;
     *                                                                                                                                                                                                                                                                                                                                int age;
     *                                                                                                                                                                                                                                                                                                                                String sex;
     *                                                                                                                                                                                                                                                                                                                            }
     *                      <p/>
     *                                                                                                                                                                                                                                                                                                                            So if one needs to generate pull events out of a Person bean, the array he needs
     *                                                                                                                                                                                                                                                                                                                            to pass is like this.
     *                                                                                                                                                                                                                                                                                                                            ---------------------------------------------------------------
     *                                                                                                                                                                                                                                                                                                                            | "Name" | "FooOne" | QName("DependentOne") | Dependent object|
     *                                                                                                                                                                                                                                                                                                                            ---------------------------------------------------------------
     *                                                                                                                                                                                                                                                                                                                            Remember "Name" and "FooOne" MUST be strings and DependentOne SHOULD be
     *                                                                                                                                                                                                                                                                                                                            QName.
     *                                                                                                                                                                                                                                                                                                                            This DependentObject can either be an ADBBean or a POJO. If its an ADBBean
     *                                                                                                                                                                                                                                                                                                                            We directly get the pull parser from that. If not we create a reflection based
     *                                                                                                                                                                                                                                                                                                                            pull parser for that java bean.
     *                                                                                                                                                                                                                                                                                                        </pre>
     * @param attributes    - this will contain an array of OMAttributes
     * @return XMLStreamReader
     */
    public static XMLStreamReader createPullParser(QName adbBeansQName, Object[] properties, OMAttribute[] attributes) {
        return new ADBPullParser(adbBeansQName, properties, attributes);
    }

    public static XMLStreamReader createPullParser(QName adbBeansQName, Object[] properties, OMAttribute[] attributes, boolean isDocumentElement) {
        return new ADBPullParser(adbBeansQName, properties, attributes, isDocumentElement);
    }

    public boolean isCompleted() {
        return isEndElementFinished;
    }

    // ----------- XMLStreamReader Methods -------------------------------------------//
    public Object getProperty(String string) throws IllegalArgumentException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int next() throws XMLStreamException {
        int event = 0;

        // First check whether the parser has already has completed. currentIndex starts with 0. But
        // but the first emulated event is the start element of the given element. The same index pointer
        // is used to point to the elements in the Array. Since we allocate index 0 to the current element,
        // currentIndex is always ahead one step from the location of the array which is currently being processed.
        // so when you check for completeness you have to check for currentIndex >= array length + 2

        if (isHandlingDocumentElement && !isStartDocumentThrown) {
            isStartDocumentThrown = true;
            return XMLStreamConstants.START_DOCUMENT;
        }

        // terminate condition.
        // if properties are set check we have traversed all of them. If there are no properties, then
        // check whether we have already thrown the END Element.
        if ( ( properties != null &&  currentIndex >= properties.length + 2) || (properties == null && isEndElementFinished)) {
            if (isHandlingDocumentElement && isEndDocumentThrown) {
                isEndDocumentThrown = true;
                return XMLStreamConstants.END_DOCUMENT;
            }
            throw new XMLStreamException("End of elements has already been reached. Can not go beyond that");
        }

        if (accessingChildPullParser) {
            if (!childPullParser.isCompleted()) {
                return childPullParser.next();
            } else {
                accessingChildPullParser = false;
                currentIndex += 2;
            }
        }

        if (processingADBNameValuePair && nameValuePairEndElementProcessed) {
            processingADBNameValuePair = false;
            currentIndex = currentIndex + 2;
            parserInformation = tempParserInfo;
        }

        if (currentIndex == 0) {
            // then this is just the start element
            currentIndex++;
            parserInformation = new ParserInformation(this.elementQName);
            return XMLStreamConstants.START_ELEMENT;
        } else if ( properties == null || properties.length + 1 == currentIndex) {
            // this is the end of this element
            currentIndex++;
            isEndElementFinished = true;
            return XMLStreamConstants.END_ELEMENT;
        } else {
            if (processingADBNameValuePair) {
                return processADBNameValuePair(null, null);
            }
            Object o = properties[currentIndex - 1];
            if (o instanceof QName) {

                Object object = properties[currentIndex];
                if (object instanceof ADBBean) {
                    ADBBean adbBean = (ADBBean) object;
                    childPullParser = (ADBPullParser) adbBean.getPullParser((QName) o);
                } else if (object instanceof OMElement) {
//                   childPullParser = (OMElement) ;
                } else {
                    childPullParser = (ADBPullParser) BeanSerializerUtil.getPullParser(object, (QName) o);
                }
                accessingChildPullParser = true;
                return this.next();
            } else if (o instanceof String) {
                String simplePropertyName = (String) o;
                String simplePropertyValue = (String) properties[currentIndex];
                processingADBNameValuePair = true;
                return processADBNameValuePair(simplePropertyName, simplePropertyValue);
            } else {
                throw new XMLStreamException("Sorry !! We only support QNames and Strings as the keys of the properties list");
            }
        }

    }


    public boolean hasNext() throws XMLStreamException {
        return !isEndElementFinished;
    }

    public String getElementText() throws XMLStreamException {
        ParserInformation parserInfo = getCorrectParserInformation();
        return parserInfo != null ? parserInfo.getText() : "";
    }


    public int getAttributeCount() {
        if (accessingChildPullParser) return childPullParser.getAttributeCount();
        if (attributes != null) return attributes.length;
        return 0;
    }

    public int getNamespaceCount() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getText() {
        ParserInformation parserInfo = getCorrectParserInformation();
        return parserInfo != null ? parserInfo.getText() : "";
    }

    public boolean hasText() {
        ParserInformation parserInfo = getCorrectParserInformation();
        return parserInfo != null && parserInfo.getText() != null && !"".equals(parserInformation.getText());
    }

    public QName getName() {
        ParserInformation parserInfo = getCorrectParserInformation();
        return parserInfo != null ? parserInfo.getName() : null;
    }

    public String getLocalName() {
        ParserInformation parserInfo = getCorrectParserInformation();
        return parserInfo != null ? parserInfo.getName().getLocalPart() : null;
    }

    public boolean hasName() {
        ParserInformation parserInfo = getCorrectParserInformation();
        return parserInfo != null && parserInfo.getName() != null;
    }

    public String getNamespaceURI() {
        ParserInformation parserInfo = getCorrectParserInformation();
        return parserInfo != null && parserInfo.getName() != null ? parserInfo.getName().getNamespaceURI() : "";
    }

    public String getPrefix() {
        ParserInformation parserInfo = getCorrectParserInformation();
        return parserInfo != null ? parserInfo.getName().getPrefix() : null;
    }

    public String getAttributeValue(String namespaceURI, String localName) {
        boolean testNSUri = false;
        boolean testLocalName = false;
        if (accessingChildPullParser) return childPullParser.getAttributeValue(namespaceURI, localName);

        // Do I need to handle the no attributes case. It will automatically gets passed from here, returning null.
        if (namespaceURI == null || "".equals(namespaceURI)) testNSUri = true;
        if (localName == null || "".equals(localName)) testLocalName = true;

        for (int i = 0; i < attributes.length; i++) {
            QName attrQName = attributes[i].getQName();
            boolean isNSUriMatch = !testNSUri;
            boolean isLocalNameMatch = !testLocalName;
            if (testNSUri && namespaceURI.equals(attrQName.getNamespaceURI())) {
                isNSUriMatch = true;
            }
            if (testLocalName && localName.equals(attrQName.getLocalPart())) {
                isLocalNameMatch = true;
            }
            if (isLocalNameMatch && isNSUriMatch) return attributes[i].getValue();
        }
        return null;
    }

    public QName getAttributeName(int i) {
        if (accessingChildPullParser) {
            return childPullParser.getAttributeName(i);
        } else if (attributes != null && attributes.length >= i) {
            return attributes[i].getQName();
        }
        return null;
    }

    public String getAttributeNamespace(int i) {
        if (accessingChildPullParser) {
            return childPullParser.getAttributeNamespace(i);
        } else if (attributes != null && attributes.length >= i && attributes[i].getNamespace() != null) {
            return attributes[i].getNamespace().getName();
        }
        return null;
    }

    public String getAttributeLocalName(int i) {
        if (accessingChildPullParser) {
            return childPullParser.getAttributeLocalName(i);
        } else if (attributes != null && attributes.length >= i) {
            return attributes[i].getLocalName();
        }
        return null;
    }

    public String getAttributePrefix(int i) {
        if (accessingChildPullParser) {
            return childPullParser.getAttributePrefix(i);
        } else if (attributes != null && attributes.length >= i) {
            return attributes[i].getQName().getPrefix();
        }
        return null;
    }

    public String getAttributeType(int i) {
        // see http://www.w3.org/TR/REC-xml/#NT-StringType.
        // since all our attrinutes are Strings, I'm returning CDATA here. But not 100% sure what to do
        return "CDATA";
    }

    public String getAttributeValue(int i) {
        if (accessingChildPullParser) {
            return childPullParser.getAttributeValue(i);
        } else if (attributes != null && attributes.length >= i) {
            return attributes[i].getValue();
        }
        return null;
    }

    public boolean isAttributeSpecified(int i) {
        if (accessingChildPullParser) {
            return childPullParser.isAttributeSpecified(i);
        }
        return (attributes != null && attributes.length >= i);
    }

// -------- un-implemented methods ----------

    public void require(int i, String string, String string1) throws XMLStreamException {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public int nextTag() throws XMLStreamException {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public void close() throws XMLStreamException {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public String getNamespaceURI(String string) {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public boolean isStartElement() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public boolean isEndElement() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public boolean isCharacters() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public boolean isWhiteSpace() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }


    public String getNamespacePrefix(int i) {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public String getNamespaceURI(int i) {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public NamespaceContext getNamespaceContext() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public int getEventType() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public char[] getTextCharacters() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public int getTextCharacters(int i, char[] chars, int i1, int i2) throws XMLStreamException {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public int getTextStart() {
        throw new UnsupportedOperationException("Yet to be implemented !!");

    }

    public int getTextLength() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public String getEncoding() {
        // https://stax-utils.dev.java.net/nonav/javadoc/api/javax/xml/stream/XMLStreamReader.html#getEncoding()
        return null;
    }

    public Location getLocation() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public String getVersion() {
        // https://stax-utils.dev.java.net/nonav/javadoc/api/javax/xml/stream/XMLStreamReader.html#getVersion()
        return null;
    }

    public boolean isStandalone() {
        // https://stax-utils.dev.java.net/nonav/javadoc/api/javax/xml/stream/XMLStreamReader.html#isStandalone()
        return false;
    }

    public boolean standaloneSet() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public String getCharacterEncodingScheme() {
        // https://stax-utils.dev.java.net/nonav/javadoc/api/javax/xml/stream/XMLStreamReader.html#getCharacterEncodingScheme()
        return null;
    }

    public String getPITarget() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

    public String getPIData() {
        throw new UnsupportedOperationException("Yet to be implemented !!");
    }

// =============================================================================
// Utill methods inside this class
// =============================================================================

    private int processADBNameValuePair(String simplePropertyName, String simplePropertyValue) {
        int event = 0;
        if (!nameValuePairStartElementProcessed) {
            event = XMLStreamConstants.START_ELEMENT;
            tempParserInfo = parserInformation;
            parserInformation = new ParserInformation(new QName(simplePropertyName), simplePropertyValue);
            nameValuePairStartElementProcessed = true;
            nameValuePairEndElementProcessed = false;
        } else if (nameValuePairStartElementProcessed && !nameValuePairTextProcessed) {
            event = XMLStreamConstants.CHARACTERS;
            nameValuePairTextProcessed = true;
        } else if (nameValuePairTextProcessed) {
            event = XMLStreamConstants.END_ELEMENT;
            nameValuePairEndElementProcessed = true;
            nameValuePairStartElementProcessed = false;
            nameValuePairTextProcessed = false;
        }
        return event;
    }

    /**
     * This will returns the parser information
     */
    public ParserInformation getParserInformation() {
        return accessingChildPullParser ? childPullParser.getParserInformation() : this.parserInformation;
    }

    private ADBPullParser.ParserInformation getCorrectParserInformation() {
        return accessingChildPullParser ? childPullParser.getParserInformation() : this.parserInformation;
    }

// --------------------------------------------------------------------------------------------------//

    /**
     * Inner class which holds stuff for the parser to pick data
     * This hold the information the parser will hold when user request for data. Every ADBPullParser
     * hold this kind of object inside it and within the methods of ADBPullParser, they refer to the
     * fields inside this class. So if user needs to change what parser returns, he just need to
     * change parser information object.
     */
    public class ParserInformation {
        String text;
        QName name;


        public ParserInformation(QName name, String text) {
            this.text = text;
            this.name = name;
        }

        public ParserInformation(QName name) {
            this.name = name;
        }


        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public QName getName() {
            return name;
        }

        public void setName(QName name) {
            this.name = name;
        }
    }
}
