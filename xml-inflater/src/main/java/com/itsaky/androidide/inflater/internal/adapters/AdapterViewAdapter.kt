package com.itsaky.androidide.inflater.internal.adapters

import android.R.layout
import android.content.Context
import android.widget.Adapter // Import Adapter interface
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.itsaky.androidide.inflater.IView
import com.itsaky.androidide.inflater.internal.ViewGroupImpl

/**
 * Attribute adapter for [AdapterView][android.widget.AdapterView]
 *
 * @author Akash Yadav
 */
abstract class AdapterViewAdapter<T : AdapterView<*>> : ViewGroupAdapter<T>() {

  companion object {
    const val ADAPTER_DEFAULT_ITEM_COUNT = 3
  }

  override fun applyBasic(view: IView) {
    super.applyBasic(view)
    // Explicitly cast the AdapterView to AdapterView<Adapter?> to resolve the type mismatch.
    // This tells the compiler that the 'adapter' property expects an object that implements the Adapter interface.
    (view.view as AdapterView<Adapter?>).adapter = newSimpleAdapter(view.view.context)
    if (view is ViewGroupImpl) {
      view.childrenModifiable = false
    }
  }

  protected open fun newSimpleAdapter(ctx: Context): ArrayAdapter<String> {
    return newSimpleAdapter(ctx, newAdapterItems(ADAPTER_DEFAULT_ITEM_COUNT))
  }

  protected open fun newSimpleAdapter(ctx: Context, items: Array<String>): ArrayAdapter<String> {
    return ArrayAdapter<String>(ctx, layout.simple_list_item_1, items)
  }

  protected open fun newAdapterItems(size: Int): Array<String> {
    return Array(size) { "Item $it" }
  }
}
