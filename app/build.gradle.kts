import java.util.Properties

plugins {
    id("com.android.application")
}

// The release key lives outside the project (default ~/.minimal-launcher-keystore/keystore.properties, or the file named by
// the MINIMAL_KEYSTORE_PROPERTIES environment variable) and is never committed. Without it, a release build is unsigned.
val keystoreProps = Properties().apply {
    val file = System.getenv("MINIMAL_KEYSTORE_PROPERTIES")?.let { File(it) }
        ?: File(System.getProperty("user.home"), ".minimal-launcher-keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.inkwell.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.inkwell.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "1.1"
    }

    lint {
        lintConfig = file("lint.xml")
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = File(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Not minified: the release build behaves exactly like the one that was tested.
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}
