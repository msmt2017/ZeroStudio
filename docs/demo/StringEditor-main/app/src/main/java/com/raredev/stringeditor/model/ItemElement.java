package com.raredev.stringeditor.model;

public class ItemElement extends BaseElement {
  private String value;

  public ItemElement(String name, String value) {
    super("item", name);
    this.value = value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public String toXml() {
    return "";
  }
}