
ThisBuild / name := "CS441HW2"

ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.12.18"

Compile / mainClass := Some("SparkAssignment")

lazy val root = (project in file("."))
  .settings(
    name := "CS441"
  )
run / fork := true

//https://github.com/lightbend/config
libraryDependencies += "com.typesafe" % "config" % "1.4.3"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

libraryDependencies += "com.knuddels" % "jtokkit" % "1.1.0"

libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.3.0"

//
libraryDependencies ++= Seq(
  "org.deeplearning4j" % "deeplearning4j-core" % "1.0.0-M2.1",
  "org.deeplearning4j" % "deeplearning4j-nlp" % "1.0.0-M2.1",
  "org.deeplearning4j" % "deeplearning4j-ui" % "1.0.0-M2.1",
  "org.deeplearning4j" %% "dl4j-spark-parameterserver" % "1.0.0-M2.1",
  "org.deeplearning4j" %% "dl4j-spark" % "1.0.0-M2.1",
  "org.nd4j" %% "nd4j-parameter-server-node" % "1.0.0-M2.1",
  "org.nd4j" % "nd4j-native" % "1.0.0-M2.1" classifier "macosx-arm64",
  "org.nd4j" % "nd4j-native-platform" % "1.0.0-M2.1",

//  "org.bytedeco" % "openblas" % "0.3.21-1.5.8",
)

//libraryDependencies += "io.vertx" % "vertx-web" % "3.9.16"

libraryDependencies ++= Seq(
  "org.scalactic" %% "scalactic" % "3.2.19",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

//assembly / logLevel := Level.Debug
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", "org.nd4j.linalg.factory.Nd4jBackend") => MergeStrategy.filterDistinctLines
  case PathList("META-INF", "services", "org.apache.hadoop.fs.FileSystem") => MergeStrategy.filterDistinctLines
  case PathList("META-INF", xs @ _*) =>
    xs match {
      case "MANIFEST.MF" :: Nil => MergeStrategy.discard
      case "native-image" :: _  => MergeStrategy.first
      case "services" :: _      => MergeStrategy.concat
      case _                    => MergeStrategy.discard
    }
  case "reference.conf"                    => MergeStrategy.concat
  case x if x.endsWith(".proto")           => MergeStrategy.rename
  case x if x.contains("hadoop")           => MergeStrategy.first
  case _                                   => MergeStrategy.first
}
