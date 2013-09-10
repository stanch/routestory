name := "RouteStory-bag"

scalaVersion := "2.10.2"

autoCompilerPlugins := true

libraryDependencies ++= Seq(
  compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.2")
)

scalacOptions += "-P:continuations:enable"

resolvers ++= Seq(
	"spray repo" at "http://repo.spray.io",
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
	"mutate-snapshots" at "http://stanch.github.com/mutate/snapshots/",
	Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
	"io.spray" % "spray-can" % "1.1-M8",
	"io.spray" % "spray-client" % "1.1-M8",
	"io.spray" % "spray-routing" % "1.1-M8",
	"io.spray" %% "spray-json" % "1.2.5",
	"net.virtual-void" %% "json-lenses" % "0.5.3",
	"com.typesafe.akka" %% "akka-actor" % "2.1.4",
	"com.typesafe.akka" %% "akka-dataflow" % "2.1.4",
	"me.stanch" %% "mutate" % "0.1-SNAPSHOT",
	"org.scalaz" %% "scalaz-core" % "7.0.0"
)