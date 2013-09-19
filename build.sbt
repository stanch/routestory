import scalariform.formatter.preferences._

androidDefaults

scalariformSettings

ScalariformKeys.preferences := FormattingPreferences()
.setPreference(RewriteArrowSymbols, true)
.setPreference(PreserveDanglingCloseParenthesis, true)

name := "RouteStory"

version := "1.0"

versionCode := 0

scalaVersion := "2.10.2"

scalaOrganization := "org.scala-lang"

platformName := "android-17"

compileOrder := CompileOrder.JavaThenScala

autoCompilerPlugins := true

libraryDependencies <+= scalaVersion {
    v => compilerPlugin("org.scala-lang.plugins" % "continuations" % v)
}

scalacOptions += "-P:continuations:enable"

resolvers ++= Seq(
	"Couchbase" at "http://files.couchbase.com/maven2/",
	Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
	"org.scaloid" % "scaloid" % "1.1_8_2.10",
	"org.macroid" %% "macroid" % "1.0.0-20130919",
	"com.typesafe.akka" %% "akka-dataflow" % "2.2.0-RC1",
	"com.scalarx" %% "scalarx" % "0.1",
	"me.lessis" %% "retry-core" % "0.1.0",
	aarlib("com.couchbase.cblite" % "CBLite" % "1.0.0-beta"),
	aarlib("com.couchbase.cblite" % "CBLiteEktorp" % "1.0.0-beta"),
	"com.android.support" % "support-v13" % "13.0.0",
	aarlib("com.google.android.gms" % "play-services" % "3.1.36")
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