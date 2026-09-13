plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vigia"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.vigia"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Dos aplicaciones desde el mismo codigo fuente.
    // El alumno no puede entrar al panel del docente porque ese camino no existe
    // en su APK. Como el applicationId es distinto, las dos conviven en un equipo.
    flavorDimensions += "rol"

    productFlavors {
        create("alumno") {
            dimension = "rol"
            applicationIdSuffix = ".alumno"
            resValue("string", "app_name", "VIGÍA Alumno")
            buildConfigField("boolean", "ES_DOCENTE", "false")
        }
        create("docente") {
            dimension = "rol"
            applicationIdSuffix = ".docente"
            resValue("string", "app_name", "VIGÍA Docente")
            buildConfigField("boolean", "ES_DOCENTE", "true")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true      // sin esto no se genera BuildConfig.ES_DOCENTE
        resValues = true        // sin esto los flavors no pueden definir app_name
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}