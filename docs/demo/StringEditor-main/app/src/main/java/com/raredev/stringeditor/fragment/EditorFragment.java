package com.raredev.stringeditor.fragment;

import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import com.blankj.utilcode.util.ClipboardUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.google.android.material.elevation.SurfaceColors;
import com.raredev.stringeditor.R;
import com.raredev.stringeditor.databinding.FragmentEditorBinding;
import com.raredev.stringeditor.utils.XmlGeneratorUtils;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;

public class EditorFragment extends Fragment {
  private FragmentEditorBinding binding;
  
  private String preset_xml =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<resources>\n"
          + "\t<string name=\"hello_world\">Hello World!</string>\n"
          + "</resources>";

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentEditorBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    init();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    binding = null;
  }

  @Override
  public void onStart() {
    super.onStart();
    getActivity().invalidateOptionsMenu();
    String code = XmlGeneratorUtils.getInstance().getGeneratedCode();
    if (code != null && !code.isEmpty()) {
      binding.editor.setText(code);
    }
  }

  @Override
  public void onPause() {
    XmlGeneratorUtils.getInstance().setCode(
      binding.editor.getText().toString()
    );
    super.onPause();
  }

  private void init() {
    binding.editor.setText(preset_xml);
    setupSymbolInput();
    setupEditor();

    binding.fab.setOnClickListener(
        (v) -> {
          ClipboardUtils.copyText(binding.editor.getText().toString());
          ToastUtils.showShort("Copied");
        });
    binding.editor.setOnScrollChangeListener(
        (v, x, y, oldX, oldY) -> {
          if (y > oldY + 20 && binding.fab.isExtended()) {
            binding.fab.shrink();
          }
          if (y < oldY - 20 && !binding.fab.isExtended()) {
            binding.fab.extend();
          }
          if (y == 0) {
            binding.fab.extend();
          }
        });
  }

  private void setupEditor() {
    binding.editor.setTypefaceText(jetBrainsMono());
    binding.editor.setTypefaceLineNumber(jetBrainsMono());
    updateEditorScheme();
    updateEditorLanguage();
  }
  
  private void setupSymbolInput() {
    binding.symbolInput.setBackgroundColor(SurfaceColors.SURFACE_0.getColor(getContext()));
    binding.symbolInput.bindEditor(binding.editor);
    binding.symbolInput.addSymbols(
      new String[] {"→", "<", ">", "/", "=", "\"", ":", "!", "?"},
      new String[] {"\t", "<>", ">", "/", "=", "\"\"", ":", "!", "?"});
    binding.symbolInput.forEachButton((b) -> b.setTypeface(jetBrainsMono()));
  }

  private void updateEditorScheme() {
    try {
      var registry = ThemeRegistry.getInstance();
      if (isDarkMode()) {
        registry.setTheme("darcula");
      } else {
        registry.setTheme("quietlight");
      }

      binding.editor.setColorScheme(TextMateColorScheme.create(registry));
    } catch (Exception e) {
      e.printStackTrace();
    }
    binding.editor.invalidate();
  }

  private void updateEditorLanguage() {
    binding.editor.setEditorLanguage(TextMateLanguage.create("text.xml", false));
    binding.editor.invalidate();
  }

  private Typeface jetBrainsMono() {
    return ResourcesCompat.getFont(getContext(), R.font.jetbrains_mono_regular);
  }

  private boolean isDarkMode() {
    int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return uiMode == Configuration.UI_MODE_NIGHT_YES;
  }
  
  public static EditorFragment newInstance() {
    EditorFragment fragment = new EditorFragment();
    fragment.setRetainInstance(true);
    return fragment;
  }
}