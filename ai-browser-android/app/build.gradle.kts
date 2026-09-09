plugins { id("com.android.application") }

android {
    namespace = "com.aibrowser.knowledgehub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aibrowser.knowledgehub"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
