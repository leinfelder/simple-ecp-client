/* ***************************************************************************
 * Copyright 2012 Carolina Lindqvist
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * ***************************************************************************/

package jettyClient.parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import javax.xml.namespace.QName;
import javax.xml.validation.Schema;

import jettyClient.simpleClient.ClientConfiguration;

import org.opensaml.xml.Configuration;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.Marshaller;
import org.opensaml.xml.io.MarshallerFactory;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.parse.XMLParserException;
import org.opensaml.xml.schema.SchemaBuilder;
import org.opensaml.xml.schema.SchemaBuilder.SchemaLanguage;
import org.opensaml.xml.util.XMLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class ParseHelper {
	
	// Get the servlet logger
	private final static Logger logger = LoggerFactory
			.getLogger(ClientConfiguration.logger);
	
	/**
	 * Unmarshall anything into an XMLObject. The invoker should cast the
	 * XMLObject.
	 * 
	 * @param defaultElementName
	 * @return
	 */
	public static XMLObject unmarshall(Element element) {

		if (element == null) return null;
		
		QName defaultElementName = getDefaultElementName(element);

		UnmarshallerFactory unmarshallerFactory = Configuration
				.getUnmarshallerFactory();

		Unmarshaller unmarshaller = unmarshallerFactory
				.getUnmarshaller(defaultElementName);

		XMLObject object = null;

		try {
			object = (XMLObject) unmarshaller.unmarshall(element);
		} catch (UnmarshallingException e) {
			e.printStackTrace();
		}
		return object;
	}

	/**
	 * Return the elements DEFAULT_ELEMENT_NAME.
	 * 
	 * @param node
	 * @return
	 */
	
	public static QName getDefaultElementName(Node node) {
		if (node == null) return null;
		
		QName qName = null;

		if (node.getPrefix() != null) {
			qName = new QName(node.getNamespaceURI(), node.getLocalName(),
					node.getPrefix());
		} else {
			qName = new QName(node.getNamespaceURI(), node.getLocalName());
		}
		return qName;
	}

	/**
	 * Turn any xml object into a text string.
	 * (Test/Debug/Printing)
	 * 
	 * @param xmlObject
	 * @return
	 */

	public static String anythingToXMLString(XMLObject object) {
		Element element = marshall(object);
		return XMLHelper.prettyPrintXML(element);
	}

	/**
	 * Marshall anything into an element.
	 * 
	 * @param object
	 * @return
	 */
	public static Element marshall(XMLObject object) {

		MarshallerFactory MarshallerFactory = Configuration
				.getMarshallerFactory();

		Marshaller marshaller = MarshallerFactory.getMarshaller(object
				.getElementQName());

		Element element = null;

		try {
			element = marshaller.marshall(object);
		} catch (MarshallingException e) {
			e.printStackTrace();
		}
		return element;
	}

	/**
	 * Create, configure and initialize a parser for a given schema.
	 * 
	 * @param schemaFilePath
	 * @return
	 */
	private static BasicParserPool createBasicParserPool(String schemaFilePath) {

		BasicParserPool pool = null;
		Schema schema = null;
		InputStream schemaStream = BasicParserPool.class.getResourceAsStream(schemaFilePath);
		
		if (schemaStream != null) {

			try {
				schema = SchemaBuilder.buildSchema(SchemaLanguage.XML,
						schemaStream);
			} catch (SAXException e) {
				logger.debug("SAXException when parsing file " + schemaFilePath);
			}

			// Configure pool and set schema as given in parameter.
			pool = new BasicParserPool();
			pool.setIgnoreElementContentWhitespace(true);
			pool.setNamespaceAware(true);
			pool.setSchema(schema);

//			try {
//				pool.initialize(); // initialize
//			} catch (ComponentInitializationException e) {
//				logger.debug("Could not initialize parserpool using schema " +schema +".");
//			}

		} else {
			logger.debug("File " + schemaFilePath + " not found.");	
		}
		return pool;
	}

	/**
	 * Attempt to parse an element from text stored in a byte array, using a
	 * schema from schemafilepath.
	 * 
	 * @param bytes
	 * @param schemaFilePath
	 * @return
	 */
	public static Element extractElement(ByteArrayInputStream inputStream, String schemaFilePath) {
		// Create parser pool for the schema. (e.g. SOAP Envelope,
		// EntityDescriptor)
		BasicParserPool pool = createBasicParserPool(schemaFilePath);
		
		if (pool == null) return null; // :(

		// Create a XML document from the stream/response.
		Document document = null;

		try {
			document = pool.parse(inputStream);
		}catch (XMLParserException e) {
			logger.debug("Unable to parse XML.");
			return null;
		}

		// Get the element from the document.
		return document.getDocumentElement();
	}

	/**
	 * Build any XMLObject. The caller will cast the returned XMLObject.
	 * 
	 * Seems unsafe somehow? - Can fail if builder not available => catch
	 * 
	 * @param defaultElementName
	 * @return
	 */
	public static XMLObject buildObject(QName defaultElementName) {
		return Configuration.getBuilderFactory()
				.getBuilder(defaultElementName).buildObject(defaultElementName);
	}

}
