name := "RouteStory-bag"

scalaVersion := "2.10.3"

resolvers ++= Seq(
	"spray repo" at "http://repo.spray.io",
	"spray nightlies" at "http://nightlies.spray.io",
	"typesafe repo" at "http://repo.typesafe.com/typesafe/releases/",
	Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
	"io.spray" % "spray-can" % "1.1-20130822",
	"io.spray" % "spray-client" % "1.1-20130822",
	"io.spray" % "spray-routing" % "1.1-20130822",
	"io.spray" % "spray-testkit" % "1.1-20130822" % "test",
	"com.typesafe.play" %% "play-json" % "2.2.0",
	"com.typesafe.akka" %% "akka-actor" % "2.1.4",
	"org.scalatest" %% "scalatest" % "1.9.2" % "test"
)