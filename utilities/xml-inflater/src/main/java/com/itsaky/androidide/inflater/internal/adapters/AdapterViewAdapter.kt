package com.itsaky.androidide.inflater.internal.adapters

import android.R.layout
import android.content.Context
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
        // 修正：将 AdapterView<*> 更改为 AdapterView<android.widget.Adapter>
        // 这是因为 AdapterView 的泛型参数表示它所接受的 Adapter 类型，
        // 使用 <*> 会导致在赋值时类型擦除或不确定性，从而编译器推断为 Nothing!
        // 明确指定接受 android.widget.Adapter 类型可以解决赋值不匹配的问题。
        (view.view as AdapterView<android.widget.Adapter>).adapter = newSimpleAdapter(view.view.context)
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
