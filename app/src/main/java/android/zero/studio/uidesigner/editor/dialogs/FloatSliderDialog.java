package android.zero.studio.uidesigner.editor.dialogs;

import android.content.Context;
import android.widget.SeekBar;
import android.widget.TextView;
import com.google.android.material.slider.Slider;

import android.view.LayoutInflater;
import android.view.View;
import android.zero.studio.uidesigner.databinding.LayoutSliderDialogBinding;
import androidx.annotation.NonNull;

public class FloatSliderDialog extends AttributeDialog {

    private final LayoutSliderDialogBinding binding;
    private float minValue;
    private float maxValue;
    private String unit;

    public FloatSliderDialog(Context context, String savedValue, float minValue, float maxValue, String unit) {
        super(context);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = unit;

        binding = LayoutSliderDialogBinding.inflate(getDialog().getLayoutInflater());
        setView(binding.getRoot());

        binding.slider.setValueFrom(minValue);
        binding.slider.setValueTo(maxValue);

        float currentValue = 0f;
        try {
            if (!savedValue.isEmpty()) {
                 String numericValue = savedValue.replaceAll("[^\\d.]", "");
                 if(!numericValue.isEmpty()) {
                    currentValue = Float.parseFloat(numericValue);
                 }
            }
        } catch (NumberFormatException e) {
            currentValue = minValue;
        }

        binding.slider.setValue(currentValue);
        binding.valueText.setText(String.format("%.2f%s", currentValue, unit));

        binding.slider.addOnChangeListener((slider, value, fromUser) -> {
            binding.valueText.setText(String.format("%.2f%s", value, unit));
        });
    }

    @Override
    protected void onClickSave() {
        super.onClickSave();
        String valueToSave = String.format("%.2f%s", binding.slider.getValue(), unit);
        if (listener != null) {
            listener.onSave(valueToSave);
        }
    }
}