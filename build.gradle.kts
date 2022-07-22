plugins {
    kotlin("js") version "1.7.10"
}

group = "net.kusik"
version = "1.2-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    testImplementation(kotlin("test-js"))
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.8.0")
}

kotlin {
    js(IR) {
        browser {
            binaries.executable()
            webpackTask {
                cssSupport.enabled = true
            }
            runTask {
                cssSupport.enabled = true
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
    }
}

task<Exec>("deployFirebase") {
    dependsOn("browserProductionWebpack")
    commandLine("cmd", "/c", "firebase", "deploy")
}
