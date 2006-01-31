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

package test.interop.whitemesa.round1.util;
import org.apache.axis2.om.OMAbstractFactory;
import org.apache.axis2.om.OMElement;
import org.apache.axis2.om.OMNamespace;
import org.apache.axis2.soap.SOAPEnvelope;
import org.apache.axis2.soap.SOAPFactory;
import test.interop.whitemesa.SunClientUtil;

public class Round1StructUtil implements SunClientUtil {

    public SOAPEnvelope getEchoSoapEnvelope() {

        SOAPFactory omfactory = OMAbstractFactory.getSOAP11Factory();
        SOAPEnvelope reqEnv = omfactory.getDefaultEnvelope();

        OMNamespace envNs = reqEnv.declareNamespace("http://schemas.xmlsoap.org/soap/envelope/", "soapenv");
        OMNamespace typeNs = reqEnv.declareNamespace("http://www.w3.org/2001/XMLSchema-instance", "xsi");
        reqEnv.declareNamespace("http://www.w3.org/2001/XMLSchema", "xsd");
        reqEnv.declareNamespace("http://schemas.xmlsoap.org/soap/encoding/", "SOAP-ENC");
        reqEnv.declareNamespace("http://soapinterop.org/", "tns");
        reqEnv.declareNamespace("http://soapinterop.org/xsd", "s");

        OMElement operation = omfactory.createOMElement("echoStruct", "http://soapinterop.org/", null);
        reqEnv.getBody().addChild(operation);
        operation.declareNamespace(envNs);
        operation.addAttribute("encodingStyle", "http://schemas.xmlsoap.org/soap/encoding/", envNs);
        OMElement part = omfactory.createOMElement("inputStruct", null);
        part.declareNamespace(typeNs);
        part.addAttribute("type", "xsd:SOAPStruct", typeNs);

        OMElement value0 = omfactory.createOMElement("varString", null);
        value0.declareNamespace(typeNs);
        value0.addAttribute("type", "xsd:string", typeNs);
        value0.addChild(omfactory.createText("strss fdfing1"));
        OMElement value1 = omfactory.createOMElement("varInt", null);
        value1.declareNamespace(typeNs);
        value1.addAttribute("type", "xsd:int", typeNs);
        value1.addChild(omfactory.createText("25"));
        OMElement value2 = omfactory.createOMElement("varFloat", null);
        value2.declareNamespace(typeNs);
        value2.addAttribute("type", "xsd:float", typeNs);
        value2.addChild(omfactory.createText("25.23"));

        part.addChild(value0);
        part.addChild(value1);
        part.addChild(value2);

        operation.addChild(part);

        return reqEnv;
    }
}
