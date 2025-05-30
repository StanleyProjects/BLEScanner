import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sp.gx.core.buildDir
import sp.gx.core.camelCase
import sp.gx.core.create
import sp.gx.core.map
import sp.gx.core.qn
import sp.gx.core.string
import sp.gx.core.xml

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.compose") version "1.7.3"
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions.kotlinCompilerExtensionVersion = "1.5.15"
}

androidComponents.onVariants { variant ->
    val output = variant.outputs.single()
    check(output is com.android.build.api.variant.impl.VariantOutputImpl)
    output.outputFileName = listOf(
        rootProject.name,
        "sample",
        android.defaultConfig.versionName!!,
        variant.name,
        android.defaultConfig.versionCode!!.toString(),
    ).joinToString(separator = "-", postfix = ".apk")
    afterEvaluate {
        tasks.getByName<JavaCompile>("compile${variant.name.capitalize()}JavaWithJavac") {
            targetCompatibility = Version.jvmTarget
        }
        tasks.getByName<KotlinCompile>("compile${variant.name.capitalize()}Kotlin") {
            kotlinOptions.jvmTarget = Version.jvmTarget
        }
        val checkManifestTask = tasks.create("checkManifest", variant.name) {
            dependsOn(camelCase("compile", variant.name, "Sources"))
            doLast {
                val actual = layout.buildDir()
                    .dir("intermediates/merged_manifests/${variant.name}")
                    .dir(camelCase("process", variant.name, "Manifest"))
                    .xml("AndroidManifest.xml")
                    .map("uses-permission".qn()) {
                        it.string("{http://schemas.android.com/apk/res/android}name".qn())
                    }
                val expected = setOf(
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.BLUETOOTH",
                    "android.permission.BLUETOOTH_ADMIN",
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_LOCATION",
                    "android.permission.POST_NOTIFICATIONS",
                    "${variant.applicationId.get()}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                )
                check(actual.sorted() == expected.sorted()) {
                    "Actual is:\n$actual\nbut expected is:\n$expected"
                }
            }
        }
        tasks.getByName(camelCase("assemble", variant.name)) {
            dependsOn(checkManifestTask)
        }
    }
}

dependencies {
    implementation(project(":lib"))
//    implementation("androidx.appcompat:appcompat:1.7.0")
//    implementation("androidx.lifecycle:lifecycle-runtime-android:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(compose.foundation)
}
