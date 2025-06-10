

package com.itsaky.androidide.preferences

import android.content.Intent
import com.itsaky.androidide.activities.AboutActivity
import com.itsaky.androidide.app.IDEApplication
import com.itsaky.androidide.resources.R

private const val KEY_CHANGELOG = "idepref_changelog"
private const val KEY_ABOUT = "idepref_about"

val changelog =
  SimpleClickablePreference(
    key = KEY_CHANGELOG,
    title = R.string.pref_changelog,
    summary = R.string.idepref_changelog_summary
  ) {
    IDEApplication.instance.showChangelog()
    true
  }
val about =
  SimpleClickablePreference(
    key = KEY_ABOUT,
    title = R.string.idepref_about_title,
    summary = R.string.idepref_about_summary
  ) {
    it.context.startActivity(Intent(it.context, AboutActivity::class.java))
    true
  }