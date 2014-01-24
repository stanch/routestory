androidDefaults

name := "RouteStory"

version := "1.0"

versionCode := 0

scalaVersion := "2.10.3"

platformName := "android-19"

compileOrder := CompileOrder.JavaThenScala

//scalacOptions += "-Ymacro-debug-lite"

resolvers ++= Seq(
  "Couchbase" at "http://files.couchbase.com/maven2/",
  "Typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  "Stanch" at "http://dl.bintray.com/stanch/maven",
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  "org.macroid" %% "macroid" % "2.0.0-SNAPSHOT",
  "org.macroid" %% "macroid-viewable" % "1.0.0-SNAPSHOT",
  "org.needs" %% "needs" % "1.0.0-RC4",
  "org.needs" %% "needs-flickr" % "1.0.0-SNAPSHOT",
  "org.needs" %% "needs-foursquare" % "1.0.0-SNAPSHOT",
  "com.scalarx" %% "scalarx" % "0.1" exclude ("com.typesafe.akka", "akka-actor"),
  "com.typesafe.play" %% "play-json" % "2.2.0",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "org.scala-lang.modules" %% "scala-async" % "0.9.0-M4",
  "org.apache.commons" % "commons-io" % "1.3.2"
)

// Android stuff
libraryDependencies ++= Seq(
  "com.loopj.android" % "android-async-http" % "1.4.4",
  "com.android.support" % "support-v13" % "19.0.0",
  "com.github.chrisbanes.photoview" % "library" % "1.2.1",
  "com.couchbase.lite" %% "couchbase-lite-android-core" % "1.0.0-SNAPSHOT",
  //aarlib("com.couchbase.cblite" % "CBLite" % "1.0.0-beta"),
  aarlib("com.google.android.gms" % "play-services" % "4.0.30"),
  apklib("com.viewpagerindicator" % "library" % "2.4.1") exclude ("com.google.android", "support-v4")
)

proguardOptions ++= Seq(
  "-keepattributes *Annotation*,EnclosingMethod",
  "-keep public enum * { public static **[] values(); public static ** valueOf(java.lang.String); }",
  "-keepnames class com.codehaus.jackson.** { *; }",
  "-keep class com.couchbase.cblite.router.CBLRouter { *; }",
  "-keep class com.couchbase.touchdb.TDCollateJSON { *; }",
  "-keepclasseswithmembers class * { native <methods>; }"
)

// play services
proguardOptions ++= Seq(
  "-keep class * extends java.util.ListResourceBundle { protected Object[][] getContents(); }",
  "-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable { public static final *** NULL; }",
  "-keepnames @com.google.android.gms.common.annotation.KeepName class *",
  "-keepclassmembernames class * { @com.google.android.gms.common.annotation.KeepName *; }",
  "-keepnames class * implements android.os.Parcelable { public static final ** CREATOR; }"
)

// akka
proguardOptions ++= Seq(
  "-keep class akka.actor.LightArrayRevolverScheduler { *; }",
  "-keep class akka.actor.LocalActorRefProvider { *; }",
  "-keep class akka.actor.CreatorFunctionConsumer { *; }",
  "-keep class akka.actor.TypedCreatorFunctionConsumer { *; }",
  "-keep class akka.dispatch.BoundedDequeBasedMessageQueueSemantics { *; }",
  "-keep class akka.dispatch.UnboundedMessageQueueSemantics { *; }",
  "-keep class akka.dispatch.UnboundedDequeBasedMessageQueueSemantics { *; }",
  "-keep class akka.dispatch.DequeBasedMessageQueueSemantics { *; }",
  "-keep class akka.actor.LocalActorRefProvider$Guardian { *; }",
  "-keep class akka.actor.LocalActorRefProvider$SystemGuardian { *; }",
  "-keep class akka.dispatch.UnboundedMailbox { *; }",
  "-keep class akka.actor.DefaultSupervisorStrategy { *; }",
  "-keep class net.routestory.util.AkkaAndroidLogger { *; }",
  "-keep class akka.event.Logging$LogExt { *; }"
)