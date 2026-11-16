plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.faris.psrs975.ui"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig { minSdk = 24 }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":audio-engine"))
  implementation(project(":voice-engine"))
  implementation(project(":style-engine"))
  implementation(project(":chord-engine"))
  implementation(project(":effects-engine"))
  implementation(project(":mixer-engine"))
  implementation(project(":midi-engine"))
  implementation(project(":recording-engine"))
  implementation(project(":sample-manager"))
  implementation(project(":expansion-manager"))
  implementation(project(":data-storage"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.kotlinx.coroutines.core)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
