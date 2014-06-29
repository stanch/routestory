name := "routestory-model"

version := "1.0"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "Stanch" at "http://dl.bintray.com/stanch/maven",
  "JTO snapshots" at "https://raw.github.com/jto/mvn-repo/master/snapshots"
)

libraryDependencies ++= Seq(
  "org.resolvable" %% "resolvable" % "2.0.0-M6",
  "com.javadocmd" % "simplelatlng" % "1.3.0",
  "org.apache.commons" % "commons-io" % "1.3.2",
  "org.scalatest" %% "scalatest" % "2.1.5" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "test",
  "org.spire-math" %% "spire" % "0.7.5" % "test"
)
