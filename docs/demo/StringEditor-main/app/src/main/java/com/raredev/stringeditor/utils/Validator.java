package com.raredev.stringeditor.utils;

public class Validator {

  public static boolean isValidStringName(String nameInput) {
    String pattern = "^[a-zA-Z]+[a-zA-Z0-9_]*$";

    if (!nameInput.matches(pattern) || nameInput.isEmpty()) {
      return false;
    }
    return true;
  }

  public static boolean isValidStringValue(String value) {
    if (value.isEmpty()) {
      return false;
    }
    return true;
  }
}
