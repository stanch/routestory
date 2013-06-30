androidDefaults

name := "RouteStory"

version := "1.0"

versionCode := 0

scalaVersion := "2.10.1"

platformName := "android-17"

compileOrder := CompileOrder.JavaThenScala

autoCompilerPlugins := true

libraryDependencies <+= scalaVersion {
    v => compilerPlugin("org.scala-lang.plugins" % "continuations" % v)
}

scalacOptions += "-P:continuations:enable"

resolvers ++= Seq(
	"Couchbase" at "http://maven.hq.couchbase.com/nexus/content/repositories/releases/"
)

libraryDependencies ++= Seq(
	"org.scaloid" % "scaloid" % "1.1_8_2.10",
	"com.typesafe.akka" %% "akka-dataflow" % "2.2.0-RC1",
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
	"-keep interface com.actionbarsherlock.** { *; }",
	"-keep class com.couchbase.cblite.router.CBLRouter { *; }",
	"-keep class com.couchbase.touchdb.TDCollateJSON { *; }",
	"-keepclasseswithmembers class * { native <methods>; }"
)