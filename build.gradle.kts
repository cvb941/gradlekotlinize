plugins {
    kotlin("js") version "1.8.0"
}

group = "net.kusik"
version = "1.5"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    testImplementation(kotlin("test-js"))
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.8.0")

//    implementation(npm("monaco-editor", "0.34.1", generateExternals = true))
}

kotlin {
    js(IR) {
        browser {
            binaries.executable()
            webpackTask {
            }
            runTask {
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
}

task<Exec>("deployFirebase") {
    dependsOn("browserProductionWebpack")
    commandLine("cmd", "/c", "firebase", "deploy")
}
