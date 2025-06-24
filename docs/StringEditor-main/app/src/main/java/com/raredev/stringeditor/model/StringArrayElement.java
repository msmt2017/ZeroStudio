package com.raredev.stringeditor.model;

import java.util.List;

public class StringArrayElement extends BaseElement {
  private List<String> values;

  public StringArrayElement(String name, List<String> values) {
    super("string-array", name);
    this.values = values;
  }

  public void setValues(List<String> values) {
    this.values = values;
  }

  @Override
  public Object getValue() {
    return values;
  }

  @Override
  public String toXml() {
    return "";
  }
}