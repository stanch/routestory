import android.Keys._
import android.Dependencies.{apklib, aar}

android.Plugin.androidBuild

name := "RouteStory"

version := "1.0"

scalaVersion := "2.10.4"

platformTarget in Android := "android-19"

compileOrder := CompileOrder.JavaThenScala

run <<= run in Android

lazy val model = RootProject(file("../model"))

lazy val root = Project("root", file(".")).dependsOn(model)

scalacOptions in (Compile, compile) ++=
  (dependencyClasspath in Compile).value.files.map("-P:wartremover:cp:" + _.toURI.toURL)

scalacOptions in (Compile, compile) ++= Seq(
  "-P:wartremover:traverser:macroid.warts.CheckUi"
)

resolvers ++= Seq(
  "Couchbase" at "http://files.couchbase.com/maven2/",
  "Typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  "Stanch" at "http://dl.bintray.com/stanch/maven",
  "JTO snapshots" at "https://raw.github.com/jto/mvn-repo/master/snapshots",
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  aar("org.macroid" %% "macroid" % "2.0.0-M3"),
  aar("org.macroid" %% "macroid-viewable" % "2.0.0-M3"),
  aar("org.macroid" %% "macroid-akka-fragments" % "2.0.0-M3"),
  compilerPlugin("org.brianmckenna" %% "wartremover" % "0.10"),
  "org.resolvable" %% "resolvable" % "2.0.0-M6",
  "io.dylemma" %% "scala-frp" % "1.1",
  "com.scalarx" %% "scalarx" % "0.1" exclude ("com.typesafe.akka", "akka-actor"),
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "org.scala-lang.modules" %% "scala-async" % "0.9.1",
  "org.apache.commons" % "commons-io" % "1.3.2"
)

// Android stuff
libraryDependencies ++= Seq(
  "com.loopj.android" % "android-async-http" % "1.4.4",
  "com.android.support" % "support-v4" % "20.0.0",
  "com.android.support" % "support-v13" % "19.1.0",
  "com.github.chrisbanes.photoview" % "library" % "1.2.2",
  aar("com.applause" % "applause-sdk-preprod" % "2.2.0"),
  aar("com.android.support" % "cardview-v7" % "21.0.0-rc1"),
  aar("com.couchbase.lite" % "couchbase-lite-android" % "1.0.1"),
  aar("com.google.android.gms" % "play-services" % "5.0.77"),
  aar("org.apmem.tools" % "layouts" % "1.0"),
  aar("com.etsy.android.grid" % "library" % "1.0.5")
)

apkbuildExcludes in Android ++= Seq(
  "META-INF/LICENSE.txt",
  "META-INF/LICENSE",
  "META-INF/NOTICE.txt",
  "META-INF/NOTICE",
  "META-INF/ASL2.0"
)

dexMaxHeap in Android := "3000m"

debugIncludesTests in Android := false

proguardScala in Android := true

typedResources in Android := false

//proguardCache in Android ++= Seq(
//  ProguardCache("akka.actor") % "com.typesafe.akka",
//  ProguardCache("android.support.v4") % "com.android.support"
//)

proguardCache in Android := Seq.empty

proguardOptions in Android ++= Seq(
  "-ignorewarnings",
  "-keep class scala.Dynamic",
  "-keep class scala.reflect.ScalaSignature",
  "-keep class scala.Function0",
  "-keep class scala.Function1",
  "-keep class scala.collection.Set.hashCode { *; }",
  "-keepattributes **",
  "-keep public enum * { public static **[] values(); public static ** valueOf(java.lang.String); }",
  "-keepnames class com.codehaus.jackson.** { *; }",
  "-keepnames class com.fasterxml.jackson.** { *; }",
  "-keep class com.couchbase.touchdb.TDCollateJSON { *; }",
  "-keepclasseswithmembers class * { native <methods>; }",
  "-keepclassmembers class * { @ext.com.google.inject.Inject <init>(...); }",
  "-keep class ext.com.google.inject.** { *; }",
  "-keep class ext.com.google.inject.Binder",
  "-keep class com.couchbase.lite.android.AndroidLogger { *; }",
  "-keep class com.couchbase.lite.android.AndroidSQLiteStorageEngine { *; }"
)

// play services
proguardOptions in Android ++= Seq(
  "-keep class * extends java.util.ListResourceBundle { protected Object[][] getContents(); }",
  "-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable { public static final *** NULL; }",
  "-keepnames @com.google.android.gms.common.annotation.KeepName class *",
  "-keepclassmembernames class * { @com.google.android.gms.common.annotation.KeepName *; }",
  "-keepnames class * implements android.os.Parcelable { public static final ** CREATOR; }"
)

// akka
proguardOptions in Android ++= Seq(
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
  "-keep class macroid.akkafragments.AkkaAndroidLogger { *; }",
  "-keep class akka.event.Logging$LogExt { *; }"
)