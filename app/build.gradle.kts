plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.debtsdecks"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.debtsdecks"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-mvp"

        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xjsr305=strict"
        )
    }

    buildFeatures {
        viewBinding = false
        compose = false
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/*.kotlin_module"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
}

val gdxVersion = "1.12.1"
val kotlinVersion = "1.9.22"
val koinVersion = "3.5.3"
val kotlinxSerializationVersion = "1.6.3"
val kotlinxCoroutinesVersion = "1.7.3"
val junitVersion = "5.10.2"
val mockkVersion = "1.13.13"

val natives by configurations.creating

dependencies {
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    implementation("io.insert-koin:koin-android:$koinVersion")
    implementation("io.insert-koin:koin-core:$koinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinxCoroutinesVersion")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val copyAndroidNatives by tasks.registering {
    val abiDirs = mapOf(
        "natives-armeabi-v7a.jar" to "armeabi-v7a",
        "natives-arm64-v8a.jar" to "arm64-v8a",
        "natives-x86.jar" to "x86",
        "natives-x86_64.jar" to "x86_64"
    )

    doFirst {
        abiDirs.values.forEach { abi -> file("libs/$abi").mkdirs() }

        natives.files.forEach { jar ->
            val abi = abiDirs.entries.firstOrNull { jar.name.endsWith(it.key) }?.value
            if (abi != null) {
                copy {
                    from(zipTree(jar))
                    into(file("libs/$abi"))
                    include("*.so")
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(copyAndroidNatives)
}