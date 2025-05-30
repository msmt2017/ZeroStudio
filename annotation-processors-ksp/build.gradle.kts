import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
}

group = "${BuildConfig.packageName}.annotations"

dependencies {
  implementation(kotlin("stdlib"))
  
  implementation(projects.annotations)
  
  implementation(libs.androidx.annotation)
  implementation(libs.common.javapoet)
  implementation(libs.common.ksp)
}

sourceSets.main {
  java.srcDirs("src/main/kotlin")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "17"
}