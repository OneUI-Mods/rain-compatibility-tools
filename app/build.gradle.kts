import java.util.Properties

plugins { id("com.android.application") }

val releaseKeys =
    Properties().apply {
        providers.environmentVariable("ONEUI_SIGNING_PROPERTIES").orNull?.let { path ->
            file(path).reader(Charsets.UTF_8).use { load(it) }
        }
    }

android {
    namespace = "dev.ryuya.raincompat"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "dev.ryuya.raincompat"
        minSdk = 37
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        if (!releaseKeys.isEmpty)
            create("distribution") {
                storeFile = file(releaseKeys.getProperty("storeFile"))
                storePassword = releaseKeys.getProperty("storePassword")
                keyAlias = releaseKeys.getProperty("keyAlias")
                keyPassword = releaseKeys.getProperty("keyPassword")
            }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("distribution")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}

dependencies {
    // Rain embeds LSPatch API 93. The injected payload uses its legacy bridge,
    // not a second modern Vector runtime. Never package the API implementation.
    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.google.android.material:material:1.14.0")
    testImplementation("junit:junit:4.13.2")
}

val exportRuntimeComponents =
    tasks.register("exportRuntimeComponents") {
        doLast {
            val components =
                configurations
                    .getByName("debugRuntimeClasspath")
                    .resolvedConfiguration
                    .resolvedArtifacts
                    .map { it.moduleVersion.id.toString() }
                    .toSortedSet()
            val file = layout.buildDirectory.file("runtime-components.txt").get().asFile
            file.parentFile.mkdirs()
            file.writeText(components.joinToString("\n") + "\n")
        }
    }
val generatedLicenses = layout.buildDirectory.dir("generated/licenseAssets")

android.sourceSets
    .getByName("main")
    .assets
    .directories
    .add(generatedLicenses.get().asFile.absolutePath)

val dependencyLicenses =
    tasks.register("generateDependencyLicenses") {
        dependsOn(exportRuntimeComponents)
        inputs.file(rootProject.file("third_party/components.tsv"))
        inputs.dir(rootProject.file("third_party"))
        inputs.file(rootProject.file("LICENSE"))
        inputs.file(layout.buildDirectory.file("runtime-components.txt"))
        outputs.dir(generatedLicenses)
        doLast {
            val rows =
                rootProject
                    .file("third_party/components.tsv")
                    .readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { it.split('\t') }
            val licensed = rows.map { it[0] }.toSet()
            val actual =
                layout.buildDirectory
                    .file("runtime-components.txt")
                    .get()
                    .asFile
                    .readLines()
                    .filter { it.isNotBlank() }
                    .toSet() + "de.robv.android.xposed:api:82"
            check(actual == licensed) {
                "Review dependency licenses: missing=${actual-licensed}; obsolete=${licensed-actual}"
            }
            val out = generatedLicenses.get().asFile
            out.mkdirs()
            rootProject.file("LICENSE").copyTo(out.resolve("APP_LICENSE.txt"), true)
            rootProject
                .file("third_party/Apache-2.0.txt")
                .copyTo(out.resolve("Apache-2.0.txt"), true)
            rootProject
                .file("third_party/KOTLIN-2.4.21.txt")
                .copyTo(out.resolve("KOTLIN-2.4.21.txt"), true)
            val notices = buildString {
                append(
                    "Bundled runtime dependencies (including transitive dependencies), plus compile-only Xposed API.\n\n",
                )
                append(
                    "AndroidX / Material: The Android Open Source Project and respective contributors.\nKotlin / coroutines / annotations: JetBrains and respective contributors.\nGuava / Error Prone / J2ObjC / JSpecify: Google and respective contributors.\nLegacy Xposed API: rovo89 and respective contributors.\n\n",
                )
                for (row in rows) {
                    append(row[0] + "\n" + row[1] + "\n" + row.getOrElse(3) { "" } + "\n\n")
                }
                for (name in rows.map { it[2] }.distinct()) {
                    append("\n===== $name =====\n\n")
                    append(rootProject.file("third_party/$name").readText())
                }
            }
            out.resolve("THIRD_PARTY_NOTICES.txt").writeText(notices)
        }
    }

tasks.named("preBuild").configure { dependsOn(dependencyLicenses) }

// Package only our hook classes. Rain supplies the Xposed bridge and Kotlin runtime.
val payloadJar =
    tasks.register<Jar>("payloadJar") {
        dependsOn("compileReleaseKotlin")
        from(
            layout.buildDirectory.dir(
                "intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes",
            ),
        ) {
            include("dev/ryuya/raincompat/payload/**")
        }
        archiveFileName.set("rain-resource-payload.jar")
        destinationDirectory.set(layout.buildDirectory.dir("payload"))
    }

abstract class DexPayloadTask : JavaExec() {
    @get:org.gradle.api.tasks.OutputDirectory abstract val outputDirectory: DirectoryProperty
}

val dexPayload =
    tasks.register<DexPayloadTask>("dexPayload") {
        dependsOn(payloadJar)
        inputs.file(payloadJar.flatMap { it.archiveFile })
        outputDirectory.set(layout.buildDirectory.dir("generated/payloadAssets"))
        mainClass.set("com.android.tools.r8.D8")
        doFirst {
            val sdk = androidComponents.sdkComponents.sdkDirectory.get().asFile
            classpath = files(sdk.resolve("build-tools/37.0.0/lib/d8.jar"))
            val output = outputDirectory.get().asFile
            output.mkdirs()
            args(
                "--release",
                "--min-api",
                "36",
                "--lib",
                sdk.resolve("platforms/android-37.2/android.jar"),
                "--output",
                output,
                payloadJar.get().archiveFile.get().asFile,
            )
        }
    }

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(dexPayload, DexPayloadTask::outputDirectory)
}
