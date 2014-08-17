name := "routestory-model"

version := "1.0"

scalaVersion := "2.10.4"

exportJars := true

resolvers ++= Seq(
  "Stanch" at "http://dl.bintray.com/stanch/maven",
  "JTO snapshots" at "https://raw.github.com/jto/mvn-repo/master/snapshots",
  "Couchbase" at "http://files.couchbase.com/maven2"
)

libraryDependencies ++= Seq(
  "org.resolvable" %% "resolvable" % "2.0.0-M6",
  "com.javadocmd" % "simplelatlng" % "1.3.0",
  "org.apache.commons" % "commons-io" % "1.3.2",
  "com.couchbase.lite" % "couchbase-lite-java-core" % "1.0.1",
  "org.scalatest" %% "scalatest" % "2.1.5" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "test"
)
