@OptIn(ExperimentalJsExport::class)
@JsExport
object Sample {
    //language=Groovy
    const val GROOVY = """plugins {
    id 'com.android.application'
}

android {
    namespace 'com.example.myapp'
    compileSdkVersion 28
    defaultConfig {
        applicationId 'com.example.myapp'
        minSdkVersion 15
        targetSdkVersion 28
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }

    flavorDimensions "tier"
    productFlavors {
        free {
            dimension "tier"
            applicationId 'com.example.myapp.free'
        }

        paid {
            dimension "tier"
            applicationId 'com.example.myapp.paid'
        }
    }
}

dependencies {
    implementation project(":lib")
    implementation 'com.android.support:appcompat-v7:28.0.0'
    implementation fileTree(dir: 'libs', include: ['*.jar'])
}

    """
}
