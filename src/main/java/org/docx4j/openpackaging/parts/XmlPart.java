/*
 *  Copyright 2007-2008, Plutext Pty Ltd.
 *   
 *  This file is part of docx4j.

    docx4j is licensed under the Apache License, Version 2.0 (the "License"); 
    you may not use this file except in compliance with the License. 

    You may obtain a copy of the License at 

        http://www.apache.org/licenses/LICENSE-2.0 

    Unless required by applicable law or agreed to in writing, software 
    distributed under the License is distributed on an "AS IS" BASIS, 
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
    See the License for the specific language governing permissions and 
    limitations under the License.

 */

package org.docx4j.openpackaging.parts;


import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import org.apache.xpath.CachedXPathAPI;
import org.apache.xpath.objects.XObject;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.NamespacePrefixMappings;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.utils.XPathFactoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.traversal.NodeIterator;

/** OPC Parts are either XML, or binary (or text) documents.
 * 
 *  Most are XML documents.
 *  
 *  docx4j aims to represent XML parts using JAXB.  However, 
 *  at present there are some parts for which we don't have
 *  JAXB representations. 
 *  
 *  Until such time as a JAXB representation for an XML Part exists,
 *  the Part should extend this class.   
 *  
 * */
public abstract class XmlPart extends Part {
	
	protected static Logger log = LoggerFactory.getLogger(XmlPart.class);	
	
	public XmlPart(PartName partName) throws InvalidFormatException {
		super(partName);
	}

	public XmlPart() throws InvalidFormatException {
		super();
	}

	/**
	 * This part's XML contents.  Not guaranteed to be up to date.
	 * Whether it is or not will depend on how the class which extends
	 * Part chooses to treat it.  It may be that the class uses some
	 * other internal representation for its data. 
	 */
	protected Document doc;
	
	private static XPath xPath = XPathFactoryUtil.newXPath();
	
	
	private NamespacePrefixMappings nsContext;
	private NamespacePrefixMappings getNamespaceContext() {
		if (nsContext==null) {
			nsContext = new NamespacePrefixMappings();
			xPath.setNamespaceContext(nsContext);
		}
		return nsContext;
	}

	public void setDocument(InputStream is) throws Docx4JException {
		try {
            DocumentBuilder documentBuilder = XmlUtils.getNewDocumentBuilder();  
            doc = documentBuilder.parse(is);
		} catch (Exception e) {
			throw new Docx4JException("Problems parsing InputStream for part " + this.getPartName().getName(), e);
		} 
	}	

	public void setDocument( org.w3c.dom.Document doc ) {
		this.doc = doc;
	}
	
	public abstract Document getDocument() throws Docx4JException;
	
	/**
	 * Get the XML as a String.
	 * @throws Docx4JException 
	 * 
	 * @since 3.0.1
	 */
	public String getXML() throws Docx4JException {
		return XmlUtils.w3CDomNodeToString(getDocument());
	}
	
	/**
	 * Note: If the result is an empty node-set, it will be converted to an
	 * empty string, rather than null.
	 * 
	 * @param xpathString
	 * @param prefixMappings
	 * @return
	 * @throws Docx4JException
	 */
	public String xpathGetString(String xpathString, String prefixMappings)  throws Docx4JException {
		try {
			
			String result;
			synchronized(xPath) {
				getNamespaceContext().registerPrefixMappings(prefixMappings);
				result = xPath.evaluate(xpathString, doc );
			}
			if (result.equals("") && log.isWarnEnabled()) {
				// Provide diagnostics as to cause of '' result 
				NodeList nl = (NodeList) xPath.evaluate(xpathString, doc, XPathConstants.NODESET );
				if (nl.getLength()==0) {
					// empty node-set is converted to empty string
					log.warn("No match for " + xpathString + " so result is empty string");
				} else {
					log.debug(xpathString + " ---> '" + result + "'");
				}
			} else {
				log.debug(xpathString + " ---> '" + result + "'");
			}
			return result;
		} catch (Exception e) {
			throw new Docx4JException("Problems evaluating xpath '" + xpathString + "'", e);
		}
	}
	
	private CachedXPathAPI cachedXPathAPI = null;
	private String cachedPrefixMappings = null;
	
	public String cachedXPathGetString(String xpath, String prefixMappings) throws Docx4JException {

		if (cachedXPathAPI==null) {
			cachedXPathAPI = new CachedXPathAPI();

			// Ignored by CachedXPathAPI in Xalan 2.7.2! 
			//cachedXPathAPI.getXPathContext().setNamespaceContext(
			//		getNamespaceContext());
		}
		
		// Init namespace prefix mappings
		if (cachedPrefixMappings == null
				&& prefixMappings!=null ) {
			cachedPrefixMappings = prefixMappings;
			getNamespaceContext().registerPrefixMappings(prefixMappings);
						
		}
		
		// Register any new prefixes; simple-minded
		if (prefixMappings!=null 
				&& !prefixMappings.equals(cachedPrefixMappings)) {
			getNamespaceContext().registerPrefixMappings(prefixMappings);
			
		}
		
		
		try {
			
			XObject xo = cachedXPathAPI.eval(doc, xpath, getNamespaceContext());

			/* Note: we also execute booleans here!
			 * 
			 * For example, from org.opendope.conditions.Xpathref.evaluate(Xpathref.java:87)
			 * 
			 * We need to handle this explicitly, since otherwise
			 * 
			 * string(/rating__type[1])='Critical' results in org.apache.xpath.XPathException: 
			 * Can not convert #BOOLEAN to a NodeList!
			 * 
				at org.apache.xpath.objects.XObject.error(XObject.java:709)
				at org.apache.xpath.objects.XObject.nodeset(XObject.java:439)
				at org.apache.xpath.CachedXPathAPI.selectNodeIterator(CachedXPathAPI.java:186)
				at org.apache.xpath.CachedXPathAPI.selectSingleNode(CachedXPathAPI.java:144)
				at org.apache.xpath.CachedXPathAPI.selectSingleNode(CachedXPathAPI.java:124)

			 * Quick n dirty heuristic to try to avoid falling through to the catch block.
			 * 
			 */
 
			if (xpath.contains("=")
				|| xpath.trim().startsWith("boolean")) {
					
				if (xpath.contains("count(")
						) {
					
					// eg: count(*[@foo='1']) is not boolean!
					log.debug("complex path detected: " + xpath);
					// fall through to catch handling
						
				} else {
				
					try {
						if (xo.bool(cachedXPathAPI.getXPathContext())) {
							return "true";
						} else {
							return "false";					
						}
					} catch (org.apache.xpath.XPathException e) {
						log.debug(e.getMessage());
						// handle below
					}
				}
					
			} else if (xpath.trim().startsWith("count(")) {

				/*
				 * Consider:
				 * 
				 *     count(path1) > 0 or count(path2 > 0)  which is boolean
				 * 
				 * versus
				 * 
				 *     count(path1 or path2) which is number
				 * 
				 * Let's not try to tell which we want here.
				 */
				if (xpath.contains("=")
						|| xpath.contains(">")
						|| xpath.contains("<")
						) {
					
					log.debug("complex path detected: " + xpath);
					// fall through to catch handling
						
				} else {
					// Reasonably confident this is a number
					try {
						double d = xo.num(cachedXPathAPI.getXPathContext());
						if (xpath.trim().startsWith("count(")
								&& /* it looks like it should be an integer */ d == Math.rint(d)) {
							return "" + Math.round(d); // convert eg 2.0
							
						} else {
							return "" + d;
						}
					} catch (org.apache.xpath.XPathException e) {
						log.debug(e.getMessage());
						// handle below
					}
				}
			}
			
			try {
			
				// get the first node or null, from NodeSetDTM
				Node result;
				if (log.isDebugEnabled() /* verify just a single result */) {
					NodeIterator ni = xo.nodeset();
					result = ni.nextNode();
					Node nextNode = ni.nextNode();
					if (nextNode!=null) {
						log.debug( xpath + " returned multiple results. Did you intend that?");
						//log.debug(nextNode.getTextContent());
					}
				} else {
					result = xo.nodeset().nextNode();
				}
			    
				if (result==null) {
					log.debug(xpath + " returned null; returning empty string");
					return "";
				}				
				
				return result.getTextContent();
				
			} catch (org.apache.xpath.XPathException e) {
								
				/*
				    error(XPATHErrorResources.ER_CANT_CONVERT_TO_NODELIST,
				          new Object[]{ getTypeString() });  //"Can not convert "+getTypeString()+" to a NodeList!");
				          
				    which uses eg org.apache.xpath.res XPATHErrorResources_de
				    
				  { ER_CANT_CONVERT_TO_NODELIST,
				      "{0} kann nicht in NodeList konvertiert werden!"},
				 */
				
				if (e.getMessage().contains("#BOOLEAN") ) {
										
					log.debug("Fallback handling XPath of form: " + xpath + " in case of " + e.getMessage() );
					if (xo.bool(cachedXPathAPI.getXPathContext())) {
						return "true";
					} else {
						return "false";					
					}
				} else if (e.getMessage().contains("#NUMBER") ) { 

					log.debug("Fallback handling XPath of form: " + xpath  + " in case of " + e.getMessage() );
					double d = xo.num(cachedXPathAPI.getXPathContext());
					if (xpath.trim().startsWith("count(")
							&& /* it looks like it should be an integer */ d == Math.rint(d)) {
						return "" + Math.round(d); // convert eg 2.0
						
					} else {
						return "" + d;
					}
				} else {
					log.error(e.getMessage());
					log.error("Handle XPath of form: " + xpath);
					throw e;
				}
				
			}
			
			
		} catch (TransformerException e) {
			log.error(System.getProperty("java.vendor"));
			log.error(System.getProperty("java.version"));
			log.error(Locale.getDefault().toString());				
			throw new Docx4JException("Exception executing " + xpath, e);
		}
	}	
	
	public void discardCacheXPathObject() {
		
		cachedXPathAPI = null;
		cachedPrefixMappings = null;		
	}
	
	public List<Node> xpathGetNodes(String xpathString, String prefixMappings) {
		
		synchronized(xPath) {
			getNamespaceContext().registerPrefixMappings(prefixMappings);
			return XmlUtils.xpath(doc, xpathString, 
					getNamespaceContext() );
		}		
	}
	
	
	/**
	 * Set the value of the node referenced in the xpath expression.
	 * 
	 * @param xpath
	 * @param value
	 * @param prefixMappings a string such as "xmlns:ns0='http://schemas.medchart'"
	 * @return
	 * @throws Docx4JException
	 */
	public boolean setNodeValueAtXPath(String xpath, String value, String prefixMappings) throws Docx4JException {

		try {
			Node n;
			synchronized(xPath) {
				getNamespaceContext().registerPrefixMappings(prefixMappings);
				n = (Node)xPath.evaluate(xpath, doc, XPathConstants.NODE );
			}
			if (n==null) {
				log.debug("xpath returned null");
				return false;
			}
			log.debug(n.getClass().getName());
			
			// Method 1: Crimson throws error
			// Could avoid with System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
			// 		"com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
			//n.setTextContent(value);
			
			// Method 2: crimson ignores
			// n.setNodeValue(value);

			// Method 3: createTextNode, then append it
			// First, need to delete/replace existing text node 
			if (n.getChildNodes() !=null
					&& n.getChildNodes().getLength() > 0) {
				NodeList nodes = n.getChildNodes();
				for (int i = nodes.getLength(); i>0; i--) {
					n.removeChild( nodes.item(i-1));
				}
			}
			Text t = n.getOwnerDocument().createTextNode(value);
			n.appendChild(t);			
			
			// cache is now invalid
			return true;
		} catch (Exception e) {
			throw new Docx4JException("Problem setting value at xpath " + xpath);
		} 
		
	}
	
	
    public boolean isContentEqual(Part other) throws Docx4JException {

    	if (!(other instanceof XmlPart))
    		return false;
    	
    	Document doc1 = getDocument();
    	Document doc2 = ((XmlPart)other).getDocument();
    	
    	return doc1.isEqualNode(doc2);

    }
		
}
