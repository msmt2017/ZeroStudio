package com.raredev.stringeditor.model;

public abstract class BaseElement {
  private String tagName;
  private String name;

  public BaseElement(String tagName, String name) {
    this.tagName = tagName;
    this.name = name;
  }

  public String getTagOpenName() {
    return "<" + tagName;
  }
  
  public String getTagCloseName() {
    return "</" + tagName + ">";
  }

  public void setTagName(String name) {
    this.tagName = name;
  }

  public String getName() {
    return name;
  }
  
  public String getAttributeName() {
    return " name=\"" + getName() + "\"";
  }

  public void setName(String name) {
    this.name = name;
  }
  
  public abstract Object getValue();
  public abstract String toXml();
  
  public static StringElement newString(String name, String value) {
    return new StringElement(name, value);
  }
}