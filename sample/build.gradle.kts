import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sp.kx.gradlex.camelCase

repositories {
    google()
    mavenCentral()
}

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "sp.sample.blescanner"
    compileSdk = Version.Android.compileSdk

    defaultConfig {
        applicationId = namespace
        minSdk = Version.Android.minSdk
        targetSdk = Version.Android.targetSdk
        versionCode = 1
        versionName = "0.0.$versionCode"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".$name"
            versionNameSuffix = "-$name"
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

androidComponents.onVariants { variant ->
    val output = variant.outputs.single()
    check(output is com.android.build.api.variant.impl.VariantOutputImpl)
    output.outputFileName = listOf(
        rootProject.name,
        android.defaultConfig.versionName!!,
        variant.name,
        android.defaultConfig.versionCode!!.toString(),
    ).joinToString(separator = "-", postfix = ".apk")
    afterEvaluate {
        tasks.getByName<JavaCompile>(camelCase("compile", variant.name, "JavaWithJavac")) {
            targetCompatibility = Version.jvmTarget
        }
        tasks.getByName<KotlinCompile>(camelCase("compile", variant.name, "Kotlin")) {
            kotlinOptions.jvmTarget = Version.jvmTarget
        }
    }
}

dependencies {
    implementation(project(":lib"))
    implementation("androidx.activity:activity:1.10.1")
}
