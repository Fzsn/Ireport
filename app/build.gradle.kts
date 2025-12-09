plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.iresponderapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.iresponderapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"https://agghqjkyzpkxvlvurjpj.supabase.co\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"sb_publishable_h9YlcvcoLrzNTJsvN_cXnQ_nOOAsKFu\""
        )
        buildConfigField(
            "String",
            "GOOGLE_MAPS_API_KEY",
            "\"AIzaSyBuylnOdkYntsIFYVDbsQFemeyqya1TaTc\""
        )
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = "AIzaSyBuylnOdkYntsIFYVDbsQFemeyqya1TaTc"
    }

    signingConfigs {
        create("release") {
            val storePath = project.findProperty("RELEASE_STORE_FILE") as String?
            storeFile = when {
                storePath.isNullOrBlank() -> file("$rootDir/release.keystore")
                file(storePath).exists() -> file(storePath)
                else -> file("$rootDir/$storePath")
            }
            storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "iResponder-${variant.versionName}-${variant.buildType.name}.apk"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.squareup.picasso:picasso:2.71828")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.android)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
}

