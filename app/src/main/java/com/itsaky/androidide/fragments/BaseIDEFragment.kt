package com.itsaky.androidide.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.itsaky.androidide.common.R
import com.itsaky.androidide.tasks.cancelIfActive
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.utils.resolveAttr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.greenrobot.eventbus.EventBus
import android.view.ContextMenu
import android.view.Menu

abstract class BaseIDEFragment : Fragment() {
    
    open val subscribeToEvents: Boolean = false
    
    open val navigationBarColor: Int
        get() = requireContext().resolveAttr(R.attr.colorSurface)
    
    open val statusBarColor: Int
        get() = requireContext().resolveAttr(R.attr.colorSurface)
    
    /**
     * [CoroutineScope] for executing tasks with the [Default][Dispatchers.Default] dispatcher.
     */
    val fragmentScope = CoroutineScope(Dispatchers.Default)
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        
        // Apply theme when fragment attaches to context
        // Note: Theme application is typically handled at the Activity level,
        // but including similar logic here for completeness
        activity?.window?.apply {
            navigationBarColor = this@BaseIDEFragment.navigationBarColor
            statusBarColor = this@BaseIDEFragment.statusBarColor
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        preCreateView()
        return bindLayout(inflater, container)
    }
    
    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this) && subscribeToEvents) {
            EventBus.getDefault().register(this)
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fragmentScope.cancelIfActive("Fragment is being destroyed")
    }
    
    /**
     * Loads a child fragment into the specified container view ID
     */
    fun loadChildFragment(fragment: Fragment, containerId: Int) {
        childFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .commit()
    }


    /**
     * Called before the layout is created. Override to perform any setup needed.
     */
    protected open fun preCreateView() {}
    
    /**
     * Bind the layout for this fragment. Override to provide the fragment's view.
     */
    protected abstract fun bindLayout(inflater: LayoutInflater, container: ViewGroup?): View
    
    companion object {
        const val REQCODE_STORAGE = 1009
        protected var LOG = ILogger.newInstance("BaseIDEFragment")
    }
}