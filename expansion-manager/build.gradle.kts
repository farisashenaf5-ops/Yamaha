plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.faris.psrs975.expansion"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig { minSdk = 24 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":audio-engine"))
  implementation(project(":soundfont-engine"))
  implementation(project(":sample-manager"))
  implementation(project(":midi-engine"))
  implementation(project(":style-engine"))
  implementation(project(":data-storage"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit)
}
