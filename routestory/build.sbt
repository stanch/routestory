// Include the Android plugin
androidDefaults

// Name of your app
name := "RouteStory"

// Version of your app
version := "1.0"

// Version number of your app
versionCode := 0

// Version of Scala
scalaVersion := "2.10.1"

// Version of the Android platform SDK
platformName := "android-17"

keyalias := "qwe"

compileOrder := CompileOrder.JavaThenScala

resolvers ++= Seq(
	"Couchbase" at "http://maven.hq.couchbase.com/nexus/content/repositories/releases/",
	"Google Repository2" at "file:///C:/Program Files (x86)/Android/android-sdk/extras/google/m2repository/",
	"Android Repository2" at "file:///C:/Program Files (x86)/Android/android-sdk/extras/android/m2repository/"
)

libraryDependencies ++= Seq(
	"org.scaloid" % "scaloid" % "1.1_8_2.10",
	apklib("com.actionbarsherlock" % "actionbarsherlock" % "4.3.1") exclude ("com.google.android", "support-v4"),
	aarlib("com.couchbase.cblite" % "CBLite" % "0.7"),
	aarlib("com.couchbase.cblite" % "CBLiteEktorp" % "0.7"),
	"com.android.support" % "support-v13" % "13.0.0",
	aarlib("com.google.android.gms" % "play-services" % "3.1.36")
)

proguardOptions ++= Seq(
	"-keepattributes *Annotation*,EnclosingMethod",
	"-keep public enum * { public static **[] values(); public static ** valueOf(java.lang.String); }",
	"-keepnames class com.codehaus.jackson.** { *; }",
	"-keep class com.actionbarsherlock.** { *; }",
	"-keep interface com.actionbarsherlock.** { *; }"
)