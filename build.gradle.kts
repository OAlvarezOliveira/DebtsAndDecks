plugins {
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}