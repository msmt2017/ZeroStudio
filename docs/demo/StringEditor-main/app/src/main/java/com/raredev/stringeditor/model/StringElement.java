package com.raredev.stringeditor.model;

import org.apache.commons.lang3.StringEscapeUtils;

public class StringElement extends BaseElement {
  private String value;

  public StringElement(String name, String value) {
    super("string", name);
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public String toXml() {
    return getTagOpenName() + getAttributeName() + ">" +StringEscapeUtils.escapeXml11(getValue()) + getTagCloseName() ;
  }
}