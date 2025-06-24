package com.itsaky.androidide.inflater.internal.adapters

import android.R.layout
import android.content.Context
import android.widget.Adapter
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
        

        // 在新版 Kotlin 中，对泛型 `*` (星号投影) 的类型检查变得更加严格。
        // `(view.view as AdapterView<*>)` 的 `adapter` 属性类型是未知的，
        // 因此直接赋值一个 `ArrayAdapter<String>` 会导致类型不匹配错误。
        //
        // 解决方案:
        // 我们将 `view.view` 强制转换为 `AdapterView<Adapter>`。这会明确告诉编译器，
        // 我们要操作的这个视图可以接受任何实现了 `Adapter` 接口的对象。
        // 因为 `ArrayAdapter<String>` 实现了 `Adapter` 接口，所以赋值操作现在是类型安全的。
        // `@Suppress("UNCHECKED_CAST")` 用于抑制这是一个未经检查的转换的警告，
        // 因为我们在此上下文中确信它是安全的。
        @Suppress("UNCHECKED_CAST")
        (view.view as AdapterView<Adapter>).adapter = newSimpleAdapter(view.view.context)


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
