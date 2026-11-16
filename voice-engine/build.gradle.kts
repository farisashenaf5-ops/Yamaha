plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.faris.psrs975.voice"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig { minSdk = 24 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":audio-engine"))
  implementation(project(":sample-manager"))
  implementation(project(":mixer-engine"))
  implementation(project(":effects-engine"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.junit)
}
