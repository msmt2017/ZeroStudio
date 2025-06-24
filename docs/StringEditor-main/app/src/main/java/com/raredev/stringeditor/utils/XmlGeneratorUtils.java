package com.raredev.stringeditor.utils;

import com.raredev.stringeditor.model.BaseElement;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

public class XmlGeneratorUtils {
  private static XmlGeneratorUtils instance;
  private String code;
  
  public static XmlGeneratorUtils getInstance() {
    if (instance == null) {
      instance = new XmlGeneratorUtils();
    }
    return instance;
  }
  
  public void setCode(String code) {
    this.code = code;
  }
  
  public String getGeneratedCode() {
    return code;
  }

  public void generateXml(List<BaseElement> list) {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
    sb.append(getResourceTag() + "\n");
    
    for (BaseElement elelemt : list) {
      sb.append("\t" + elelemt.toXml() + "\n");
    }
    sb.append("</resources>");
    
    code = sb.toString();
  }
  
  public List<BaseElement> xmlToList() {
    List<BaseElement> list = new ArrayList<>();
    XMLParser parser = new XMLParser(code);
    List<Element> elements = parser.getElement("string");
    for (Element element : elements) {
      list.add(BaseElement.newString(element.getAttribute("name"), element.getTextContent()));
    }
    return list;
  }

  private String getResourceTag() {
    XMLParser parser = new XMLParser(code);
    return parser.getResourcesTag();
  }
}