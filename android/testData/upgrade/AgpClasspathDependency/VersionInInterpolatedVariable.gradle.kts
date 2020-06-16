buildscript {
    val agpVersion by extra("3.5.0")
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:$agpVersion")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
