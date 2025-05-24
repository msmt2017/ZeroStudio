package com.itsaky.androidide.preferences

import android.content.Intent
import com.itsaky.androidide.activities.AboutActivity
import com.itsaky.androidide.app.IDEApplication
import com.itsaky.androidide.resources.R
import java.io.Serializable

private const val KEY_CHANGELOG = "idepref_changelog"
private const val KEY_ABOUT = "idepref_about"
private const val KEY_CHATAI = "idepref_chatai"

// val changelog =
    // SimpleClickablePreference(
        // key = KEY_CHANGELOG,
        // title = R.string.pref_changelog,
        // summary = R.string.idepref_changelog_summary,
        // onClick = { preference ->
            // // 显示 Changelog
            // IDEApplication.instance.showChangelog()
            // true
        // }
    // )

val about =
    SimpleClickablePreference(
        key = KEY_ABOUT,
title = R.string.idepref_about_title,
        summary = R.string.idepref_about_summary,
        icon = R.drawable.ic_autot,
        onClick = { preference ->
            // 跳转到 AboutActivity
            preference.context.startActivity(Intent(preference.context, AboutActivity::class.java))
        }
    )

val chatai =
    SimpleClickablePreference(
        key = KEY_CHATAI,
        title = R.string.zero_ai_chat,
        summary = R.string.ai_set_info,
        icon = R.drawable.ic_ai_modle,
        onClick = { preference ->
            // 跳转到 GPTMobileSettingActivity
            preference.context.startActivity(Intent(preference.context, GPTMobileSettingActivity::class.java))
        }
    )