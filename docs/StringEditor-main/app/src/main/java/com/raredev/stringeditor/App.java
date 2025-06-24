package com.raredev.stringeditor;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import org.eclipse.tm4e.core.registry.IThemeSource;

public class App extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    //DynamicColors.applyToActivitiesIfAvailable(this);
    startup();
  }

  private void startup() {
    try {
      FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(getAssets()));

      String[] themes = {"quietlight", "darcula"};
      var themeRegistry = ThemeRegistry.getInstance();

      for (int j = 0; j < themes.length; j++) {
        String name = themes[j];
        var path = "textmate/" + name + ".json";
        themeRegistry.loadTheme(
            new ThemeModel(
                IThemeSource.fromInputStream(
                    FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                name));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
  }
}
