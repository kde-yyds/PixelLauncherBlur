
plugins {
    id("com.android.application")
    
}

android {
    namespace = "me.kdeyyds.pixellauncherblur"
    compileSdk = 33

    defaultConfig {
        applicationId = "me.kdeyyds.pixellauncherblur"
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        
    }
    
}

dependencies {

    compileOnly("de.robv.android.xposed:api:82")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.github.JailedBird:EdgeUtils:1.0.0")
   

}
