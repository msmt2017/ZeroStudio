package com.raredev.stringeditor;

import android.os.*;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.raredev.stringeditor.databinding.ActivityMainBinding;
import com.raredev.stringeditor.fragment.*;

public class MainActivity extends AppCompatActivity {
  private ActivityMainBinding binding;
  private Menu menu;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    init();
  }

  private void init() {
    setSupportActionBar(binding.toolbar);

    getSupportFragmentManager()
        .beginTransaction()
        .add(R.id.container, EditorFragment.newInstance(), "editorFragment")
        .commit();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, 0, 0, "Visual Editor");
    this.menu = menu;
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    var title = item.getTitle();
    if (title.equals("Visual Editor")) {
      replaceFragment(ElementListFragment.newInstance(), "elementsListFragment");
      item.setTitle("Show XML");
    } else if (title.equals("Show XML")) {
      replaceFragment(EditorFragment.newInstance(), "editorFragment");
      item.setTitle("Visual Editor");
    }
    return super.onOptionsItemSelected(item);
  }

  private void replaceFragment(Fragment newFragment, String tag) {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.container, newFragment, tag)
        .addToBackStack(null)
        .commit();
  }

  @Override
  public void onBackPressed() {
    for (Fragment fragment : getSupportFragmentManager().getFragments()) {
      if (fragment.getTag().equals("editorFragment")) finishAffinity();
      else if (fragment.getTag().equals("elementsListFragment")) {
        replaceFragment(EditorFragment.newInstance(), "editorFragment");
        if (menu != null) menu.getItem(0).setTitle("Visual Editor");
      }
    }
  }
}