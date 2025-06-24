plugins {
  id("com.android.library")
  id("kotlin-android")
  id("kotlin-parcelize")
}

android {
  namespace = "${BuildConfig.packageName}.uidesigner"
}

dependencies {
  
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.nav.fragment)
  implementation(libs.androidx.nav.ui)
  implementation(libs.common.editor)
  implementation(libs.common.kotlin)
  implementation(libs.common.utilcode)
  implementation(libs.google.material)

  implementation(projects.core.actions)
  implementation(projects.core.annotations)
  implementation(projects.core.common)
  implementation(projects.editors.editor)
  implementation(projects.modules.logger)
  implementation(projects.modules.lookup)
  implementation(projects.lsp.api)
  implementation(projects.lsp.xml)
  implementation(projects.core.resources)
  implementation(projects.editors.xmlInflater)

  testImplementation(libs.tests.junit)
  testImplementation(libs.tests.google.truth)
  testImplementation(libs.tests.robolectric)
  testImplementation(libs.tests.mockito.kotlin)
}
