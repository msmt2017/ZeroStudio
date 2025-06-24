// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(chatai.plugins.android.application) apply false
  alias(chatai.plugins.kotlin.android) apply false
  alias(chatai.plugins.kotlin.compose) apply false
  alias(chatai.plugins.android.library) apply false
  alias(chatai.plugins.ksp) apply false
  // alias(chatai.plugins.google.services) apply false
  // alias(chatai.plugins.firebase.crashlytics) apply false
  alias(chatai.plugins.objectbox) apply false
}