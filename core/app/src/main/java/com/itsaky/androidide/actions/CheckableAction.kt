package com.itsaky.androidide.actions

/**
 * An interface for actions that can be checked, like a checkbox or a toggle switch.
 */
interface CheckableAction : ActionItem {
    /**
     * Represents the checked state of the action.
     * `true` if checked, `false` otherwise.
     */
    var checked: Boolean
}