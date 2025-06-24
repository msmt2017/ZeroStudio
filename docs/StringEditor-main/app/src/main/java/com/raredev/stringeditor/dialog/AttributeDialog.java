package com.raredev.stringeditor.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.raredev.stringeditor.callback.DialogCallback;
import com.raredev.stringeditor.databinding.DialogAttributeBinding;
import com.raredev.stringeditor.utils.Validator;

public class AttributeDialog {
  private DialogAttributeBinding binding;
  private DialogCallback callback;
  private AlertDialog alertDialog;

  public AttributeDialog(Context context, DialogCallback callback) {
    this.callback = callback;
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
    binding = DialogAttributeBinding.inflate(((Activity) context).getLayoutInflater());
    builder.setView(binding.getRoot());

    builder.setPositiveButton(
        "Save",
        (dlg, i) ->
            callback.onPositiveButtonClicked(
                binding.textinputName.getText().toString(),
                binding.textinputValue.getText().toString()));
    builder.setNegativeButton("Cancel", (d, w) -> d.dismiss());

    alertDialog = builder.create();

    /*TextWatcher textWatcher =
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence p1, int p2, int p3, int p4) {}

          @Override
          public void onTextChanged(CharSequence p1, int p2, int p3, int p4) {
            checkNameAndValueErrors(
                binding.textInputLayoutName,
                binding.textInputLayoutValue,
                binding.textinputName.getText().toString(),
                binding.textinputValue.getText().toString(),
                alertDialog);
          }

          @Override
          public void afterTextChanged(Editable p1) {}
        };

    binding.textinputName.addTextChangedListener(textWatcher);
    binding.textinputValue.addTextChangedListener(textWatcher);*/
  }

  public void setTitle(String title) {
    alertDialog.setTitle(title);
  }

  public TextInputEditText getEditTextName() {
    return binding.textinputName;
  }

  public TextInputEditText getEditTextValue() {
    return binding.textinputValue;
  }

  public void show() {
    alertDialog.show();
  }
  
  public void setTextWatcher() {
    getEditTextName().addTextChangedListener(getTextWatcher());
    getEditTextValue().addTextChangedListener(getTextWatcher());
  }

  private TextWatcher getTextWatcher() {
    return new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence p1, int p2, int p3, int p4) {}

      @Override
      public void onTextChanged(CharSequence p1, int p2, int p3, int p4) {
        checkNameAndValueErrors(
            binding.textInputLayoutName,
            binding.textInputLayoutValue,
            binding.textinputName.getText().toString(),
            binding.textinputValue.getText().toString(),
            alertDialog);
      }

      @Override
      public void afterTextChanged(Editable p1) {}
    };
  }

  private void checkNameAndValueErrors(TextInputLayout nameLayout, TextInputLayout valueLayout, String name, String value, AlertDialog dialog) {
    if (checkNameErrors(nameLayout, name, dialog) && checkValueErrors(valueLayout, value, dialog)) {
      dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
      return;
    }
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
  }

  private boolean checkNameErrors(TextInputLayout nameLayout, String name, AlertDialog dialog) {
    if (!Validator.isValidStringName(name)) {
      nameLayout.setErrorEnabled(true);
      nameLayout.setError("Invalid name!");
      return false;
    }
    nameLayout.setErrorEnabled(false);
    nameLayout.setError("");
    return true;
  }

  private boolean checkValueErrors(TextInputLayout valueLayout, String value, AlertDialog dialog) {
    if (!Validator.isValidStringValue(value)) {
      valueLayout.setErrorEnabled(true);
      valueLayout.setError("Invalid value!");
      return false;
    }
    valueLayout.setErrorEnabled(false);
    valueLayout.setError("");
    return true;
  }
}