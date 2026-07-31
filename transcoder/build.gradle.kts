plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.skyd.transcoder"
        minSdk = 24
        compileSdk {
            version = release(37) { minorApiLevel = 0 }
        }
        buildToolsVersion = "37.0.0"
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

    compilerOptions {
        optIn.addAll(
            "kotlinx.coroutines.DelicateCoroutinesApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}