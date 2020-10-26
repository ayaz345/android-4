android {
  sourceSets {
    getByName("main") {
      java {
        srcDir("javaSource1")
        srcDirs("javaSource2")
        include("javaInclude1")
        include("javaInclude2")
        exclude("javaExclude1", "javaExclude2")
      }
      jni {
        setSrcDirs(listOf("jniSource1", "jniSource2"))
        include("jniInclude1", "jniInclude2")
        exclude("jniExclude1")
        exclude("jniExclude2")
      }
    }
  }
}
