import android.Keys._
import android.Dependencies.{apklib, aar}

android.Plugin.androidBuild

name := "RouteStory"

net.virtualvoid.sbt.graph.Plugin.graphSettings

version := "1.0"

scalaVersion := "2.10.3"

platformTarget in Android := "android-19"

compileOrder := CompileOrder.JavaThenScala

resolvers ++= Seq(
  "Couchbase" at "http://files.couchbase.com/maven2/",
  "Typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  "Stanch" at "http://dl.bintray.com/stanch/maven",
  "JTO snapshots" at "https://raw.github.com/jto/mvn-repo/master/snapshots",
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  "org.macroid" %% "macroid" % "2.0.0-M1",
  "org.macroid" %% "macroid-viewable" % "1.0.0-SNAPSHOT",
  "org.macroid" %% "macroid-akka-fragments" % "1.0.0-SNAPSHOT",
  compilerPlugin("org.brianmckenna" %% "wartremover" % "0.10"),
  "org.brianmckenna" %% "wartremover" % "0.10",
  "org.resolvable" %% "resolvable" % "2.0.0-M4",
  "org.resolvable" %% "resolvable-flickr" % "1.0.0-SNAPSHOT",
  "org.resolvable" %% "resolvable-foursquare" % "1.0.0-SNAPSHOT",
  "com.scalarx" %% "scalarx" % "0.1" exclude ("com.typesafe.akka", "akka-actor"),
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "org.scala-lang.modules" %% "scala-async" % "0.9.1",
  "org.apache.commons" % "commons-io" % "1.3.2"
)

scalacOptions in (Compile, compile) ++= Seq(
  "-P:wartremover:cp:" + (dependencyClasspath in Compile).value
    .files.map(_.toURL.toString)
    .find(_.contains("org.macroid/macroid_")).get,
  "-P:wartremover:traverser:macroid.warts.CheckUi"
)

// Android stuff
libraryDependencies ++= Seq(
  "com.loopj.android" % "android-async-http" % "1.4.4",
  "com.android.support" % "support-v13" % "19.1.0",
  "com.github.chrisbanes.photoview" % "library" % "1.2.1",
  "com.couchbase.lite" %% "couchbase-lite-android-core" % "1.0.0-SNAPSHOT",
  //aarlib("com.couchbase.cblite" % "CBLite" % "1.0.0-beta"),
  aar("com.google.android.gms" % "play-services" % "4.0.30"),
  aar("org.apmem.tools" % "layouts" % "1.0"),
  apklib("com.viewpagerindicator" % "library" % "2.4.1") exclude ("com.google.android", "support-v4")
)

unmanagedClasspath in Compile <<= (unmanagedClasspath in Compile) map { cp =>
  cp filterNot (_.data.getName == "android-support-v4.jar")
}

apkbuildExcludes in Android ++= Seq(
  "META-INF/LICENSE.txt",
  "META-INF/LICENSE",
  "META-INF/NOTICE.txt",
  "META-INF/NOTICE"
)

dexMaxHeap in Android := "2000m"

proguardScala in Android := true

proguardCache in Android ++= Seq(
  ProguardCache("akka.actor") % "com.typesafe.akka",
  ProguardCache("android.support.v4") % "com.android.support"
)

proguardOptions in Android ++= Seq(
  "-ignorewarnings",
  "-keep class scala.Dynamic",
  "-keep class scala.reflect.ScalaSignature",
  "-keep class scala.Function0",
  "-keep class scala.Function1",
  "-keepattributes *Annotation*,EnclosingMethod",
  "-keep public enum * { public static **[] values(); public static ** valueOf(java.lang.String); }",
  "-keepnames class com.codehaus.jackson.** { *; }",
  "-keepnames class com.fasterxml.jackson.** { *; }",
  "-keep class com.couchbase.cblite.router.CBLRouter { *; }",
  "-keep class com.couchbase.touchdb.TDCollateJSON { *; }",
  "-keepclasseswithmembers class * { native <methods>; }"
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
  "-keep class org.macroid.akkafragments.AkkaAndroidLogger { *; }",
  "-keep class akka.event.Logging$LogExt { *; }"
)