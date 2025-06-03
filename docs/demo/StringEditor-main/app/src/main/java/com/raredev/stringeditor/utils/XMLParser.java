package com.raredev.stringeditor.utils;

import java.io.ByteArrayInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import java.io.File;
import java.util.ArrayList;

public class XMLParser {
  private Document doc;

  public XMLParser(String xmlText) {
    try {
      ByteArrayInputStream input = new ByteArrayInputStream(xmlText.getBytes());
        
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      doc = dBuilder.parse(input);
      doc.getDocumentElement().normalize();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public String getTagName(String tagName) {
    NodeList nList = doc.getElementsByTagName(tagName);
    Node nNode = nList.item(0);
    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
      Element eElement = (Element) nNode;
      return eElement.getAttribute("name");
    }
    return null;
  }

  public String getResourcesTag() {
    Node resourcesNode = doc.getElementsByTagName("resources").item(0);
    if (resourcesNode == null) {
        resourcesNode = doc.createElement("resources");
        doc.appendChild(resourcesNode);
      }
    NamedNodeMap attributes = resourcesNode.getAttributes();
    
    StringBuilder sbAttributes = new StringBuilder();
    for (int i = 0; i < attributes.getLength(); i++) {
        Node attribute = attributes.item(i);
        sbAttributes.append(" " + attribute.getNodeName() + "=\"" + attribute.getNodeValue() + "\"");
    }
    return "<resources" + sbAttributes.toString() + ">";
  }

  public String getValue(String tagName) {
    NodeList nList = doc.getElementsByTagName(tagName);
    Node nNode = nList.item(0);
    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
      Element eElement = (Element) nNode;
      return eElement.getTextContent();
    }
    return null;
  }

  public ArrayList<Element> getElement(String tag) {
    ArrayList<Element> resources = new ArrayList<>();
    NodeList nList = doc.getElementsByTagName(tag);
    for (int i = 0; i < nList.getLength(); i++) {
      Node nNode = nList.item(i);
      if (nNode.getNodeType() == Node.ELEMENT_NODE) {
        resources.add((Element) nNode);
      }
    }
    return resources;
  }
  
  public ArrayList<Element> getAllElements() {
    ArrayList<Element> elements = new ArrayList<>();
    NodeList nList = doc.getChildNodes();
    for (int i = 0; i < nList.getLength(); i++) {
      Node nNode = nList.item(i);
      if (nNode.getNodeType() == Node.ELEMENT_NODE) {
        elements.add((Element) nNode);
      }
    }
    return elements;
  }
}