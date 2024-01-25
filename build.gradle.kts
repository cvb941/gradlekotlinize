plugins {
    kotlin("multiplatform") version "1.9.22"
}

group = "net.kusik"
version = "1.6"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
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

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.11.0")

//    implementation(npm("monaco-editor", "0.34.1", generateExternals = true))
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

task<Exec>("deployFirebase") {
    dependsOn("browserProductionWebpack")
    commandLine("cmd", "/c", "firebase", "deploy")
}
