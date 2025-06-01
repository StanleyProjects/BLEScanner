import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sp.gx.core.Badge
import sp.gx.core.GitHub
import sp.gx.core.Markdown
import sp.gx.core.Maven
import sp.gx.core.asFile
import sp.gx.core.assemble
import sp.gx.core.buildDir
import sp.gx.core.camelCase
import sp.gx.core.check
import sp.gx.core.create
import sp.gx.core.existing
import sp.gx.core.file
import sp.gx.core.filled
import sp.gx.core.getByName
import sp.gx.core.kebabCase
import sp.gx.core.task

version = "0.1.1"

val maven = Maven.Artifact(
    group = "com.github.kepocnhh",
    id = rootProject.name,
)

val gh = GitHub.Repository(
    owner = "StanleyProjects",
    name = rootProject.name,
)

repositories {
    google()
    mavenCentral()
}

plugins {
    id("com.android.library")
    id("kotlin-android")
}

fun BaseVariant.getVersion(): String {
    return when (flavorName) {
        "unstable" -> {
            when (buildType.name) {
                "debug" -> kebabCase("${version}u", "SNAPSHOT")
                else -> error("Build type \"${buildType.name}\" is not supported for flavor \"$flavorName\"!")
            }
        }
        else -> error("Flavor name \"$flavorName\" is not supported!")
    }
}

fun BaseVariant.getOutputFileName(extension: String): String {
    check(extension.isNotEmpty())
    return "${kebabCase(rootProject.name, getVersion())}.$extension"
}

fun checkReadme(variant: BaseVariant) {
    tasks.create("check", variant.name, "Readme") {
        doLast {
            when (variant.name) {
                "unstableDebug" -> {
                    val badge = Markdown.image(
                        text = "version",
                        url = Badge.url(
                            label = "version",
                            message = variant.getVersion(),
                            color = "2962ff",
                        ),
                    )
                    val expected = setOf(
                        badge,
                        Markdown.link("Maven", Maven.Snapshot.url(maven, variant.getVersion())),
                        "implementation(\"${maven.moduleName(variant.getVersion())}\")",
                    )
                    val report = buildDir()
                        .dir("reports/analysis/readme")
                        .dir(variant.name)
                        .asFile("index.html")
                    rootDir.resolve("README.md").check(
                        expected = expected,
                        report = report,
                    )
                }
                else -> error("Variant \"${variant.name}\" is not supported!")
            }
        }
    }
}

fun assemblePom(variant: BaseVariant) {
    tasks.create("assemble", variant.name, "Pom") {
        doLast {
            val file = buildDir()
                .dir("xml")
                .dir(variant.name)
                .file("maven.pom.xml")
                .assemble(
                    maven.pom(
                        version = variant.getVersion(),
                        packaging = "aar",
                    ),
                )
            println("POM: ${file.absolutePath}")
        }
    }
}

fun assembleSource(variant: BaseVariant) {
    task<Jar>("assemble", variant.name, "Source") {
        val sourceSets = variant.sourceSets.flatMap { it.kotlinDirectories }.distinctBy { it.absolutePath }
        from(sourceSets)
        val dir = buildDir()
            .dir("sources")
            .asFile(variant.name)
        val file = File(dir, "${maven.name(variant.getVersion())}-sources.jar")
        outputs.upToDateWhen {
            file.exists()
        }
        doLast {
            dir.mkdirs()
            val renamed = archiveFile.get().asFile.existing().file().filled().renameTo(file)
            check(renamed)
            println("Archive: ${file.absolutePath}")
        }
    }
}

fun assembleMetadata(variant: BaseVariant) {
    task(camelCase("assemble", variant.name, "Metadata")) {
        doLast {
            val file = layout.buildDirectory.get()
                .dir("yml")
                .dir(variant.name)
                .file("metadata.yml")
                .assemble(
                    """
                        repository:
                         owner: '${gh.owner}'
                         name: '${gh.name}'
                        version: '${variant.getVersion()}'
                    """.trimIndent(),
                )
            println("Metadata: ${file.absolutePath}")
        }
    }
}

android {
    namespace = "sp.ax.blescanner"
    compileSdk = Version.Android.compileSdk

    defaultConfig {
        minSdk = Version.Android.minSdk
    }

    productFlavors {
        mapOf(
            "stability" to setOf(
                "unstable",
            ),
        ).forEach { (dimension, flavors) ->
            flavorDimensions += dimension
            flavors.forEach { flavor ->
                create(flavor) {
                    this.dimension = dimension
                }
            }
        }
    }

    fun onVariant(variant: LibraryVariant) {
        val supported = setOf(
            "unstableDebug",
        )
        if (!supported.contains(variant.name)) {
            tasks.getByName(camelCase("pre", variant.name, "Build")) {
                doFirst {
                    error("Variant \"${variant.name}\" is not supported!")
                }
            }
            return
        }
        val output = variant.outputs.single()
        check(output is com.android.build.gradle.internal.api.LibraryVariantOutputImpl)
        output.outputFileName = variant.getOutputFileName("aar")
        checkReadme(variant)
        assemblePom(variant)
        assembleSource(variant)
        assembleMetadata(variant)
        afterEvaluate {
            tasks.getByName<JavaCompile>("compile", variant.name, "JavaWithJavac") {
                targetCompatibility = Version.jvmTarget
            }
            tasks.getByName<KotlinCompile>("compile", variant.name, "Kotlin") {
                kotlinOptions {
                    jvmTarget = Version.jvmTarget
                    freeCompilerArgs = freeCompilerArgs + setOf("-module-name", maven.moduleName())
                }
            }
        }
    }

    libraryVariants.all {
        onVariant(this)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}
