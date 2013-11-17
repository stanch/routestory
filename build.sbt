androidDefaults

name := "RouteStory"

version := "1.0"

versionCode := 0

scalaVersion := "2.10.3"

platformName := "android-18"

compileOrder := CompileOrder.JavaThenScala

//scalacOptions += "-Ymacro-debug-lite"

resolvers ++= Seq(
	"Couchbase" at "http://files.couchbase.com/maven2/",
	Resolver.sonatypeRepo("snapshots"),
	Resolver.sonatypeRepo("releases"),
	"typesafe repo" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
	"org.macroid" %% "macroid" % "1.1.0-20131112",
	"com.scalarx" %% "scalarx" % "0.1",
	"me.lessis" %% "retry-core" % "0.1.0",
	"com.typesafe.play" %% "play-json" % "2.2.0",
	"com.loopj.android" % "android-async-http" % "1.4.4",
	"org.scala-lang.modules" %% "scala-async" % "0.9.0-M2",
	aarlib("com.couchbase.cblite" % "CBLite" % "1.0.0-beta"),
	aarlib("com.couchbase.cblite" % "CBLiteEktorp" % "1.0.0-beta"),
	"com.android.support" % "support-v13" % "18.0.0",
	aarlib("com.google.android.gms" % "play-services" % "3.1.59"),
	"com.github.chrisbanes.photoview" % "library" % "1.2.1",
	apklib("com.viewpagerindicator" % "library" % "2.4.1") exclude ("com.google.android", "support-v4")
)

proguardOptions ++= Seq(
	"-keepattributes *Annotation*,EnclosingMethod",
	"-keep public enum * { public static **[] values(); public static ** valueOf(java.lang.String); }",
	"-keepnames class com.codehaus.jackson.** { *; }",
	"-keep class org.ektorp.** { *; }",
	"-keep class com.couchbase.cblite.router.CBLRouter { *; }",
	"-keep class com.couchbase.touchdb.TDCollateJSON { *; }",
	"-keepclasseswithmembers class * { native <methods>; }"
)