plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "me.stellarsand.android.fastscroll.compose"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "me.StellarSand"
            artifactId = "AndroidFastScroll-kt-compose"
            version = "v1.0.9"
            afterEvaluate { from(components["release"]) }
        }
    }
}
