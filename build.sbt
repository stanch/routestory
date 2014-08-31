import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

name := "routestory-bag"

scalaVersion := "2.11.2"

Revolver.settings

packageArchetype.java_application

resolvers ++= Seq(
	"spray repo" at "http://repo.spray.io",
	"typesafe repo" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
	"io.spray" %% "spray-can" % "1.3.1",
	"io.spray" %% "spray-client" % "1.3.1",
	"io.spray" %% "spray-routing" % "1.3.1",
	"io.spray" %% "spray-testkit" % "1.3.1" % "test",
  "com.github.dzsessona" %% "scamandrill" % "1.1.0",
	"com.typesafe.play" %% "play-json" % "2.3.0",
	"com.typesafe.akka" %% "akka-actor" % "2.3.4",
  "org.scala-lang.modules" %% "scala-async" % "0.9.2",
	"org.scalatest" %% "scalatest" % "2.2.1" % "test"
)
