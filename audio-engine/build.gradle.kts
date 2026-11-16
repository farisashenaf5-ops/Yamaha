plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.faris.psrs975.audio"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  ndkVersion = "27.3.13750724"

  defaultConfig {
    minSdk = 24
    externalNativeBuild {
      cmake {
        arguments("-DANDROID_STL=c++_shared")
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  buildFeatures {
    prefab = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":effects-engine"))
  implementation(libs.google.oboe)
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit)
}
