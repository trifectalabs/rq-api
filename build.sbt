name := "road_quality"

scalaVersion in ThisBuild := "2.11.8"

lazy val root = (project in file("."))
  .aggregate(web, api)

lazy val web = project
  .settings(name:= "roadquality_web")
  .dependsOn(api)
  .enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(dockerSettings: _*)

lazy val api = project
  .settings(name:= "roadquality_api")
  .enablePlugins(PlayScala)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "buildInfo")
  .settings(commonSettings: _*)
  .settings(dockerSettings: _*)
  .settings(routesImport += "com.trifectalabs.roadquality.v0.Bindables._")

lazy val commonSettings = Seq(
  organization := "com.github.trifectalabs",
  version := "git describe --dirty --tags --always".!!.stripPrefix("v").trim,
  resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases",
  resolvers += "Typesafe Maven Repository" at "http://repo.typesafe.com/typesafe/maven-releases/",
  libraryDependencies ++= Seq(
    "com.vividsolutions"      % "jts"                    % "1.13",
    "com.github.trifectalabs" %% "polyline-scala"        % "1.2.0",
    "com.typesafe.play"       %% "play-slick"            % "2.0.2",
    "com.typesafe.play"       %% "play-slick-evolutions" % "2.0.2",
    "org.postgresql"          % "postgresql"             % "9.4.1212",
    "com.github.tminglei"     %% "slick-pg"              % "0.14.6",
    "com.github.tminglei"     %% "slick-pg_joda-time"    % "0.14.6",
    "com.github.tminglei"     %% "slick-pg_play-json"    % "0.14.6",
    "com.github.tminglei"     %% "slick-pg_jts"          % "0.14.6",
    "com.pauldijou"           %% "jwt-core"              % "0.12.1",
    "org.scalatestplus.play"  %% "scalatestplus-play"    % "2.0.0" % "test",
    specs2,
    ws,
    filters
  )
)

lazy val dockerSettings: Seq[Setting[_]] = Seq(
  dockerRepository := Some("kiambogo"),
  packageName in Docker := s"roadquality_${name.value}",
  maintainer in Docker := "Christopher Poenaru <kiambogo@gmail.com>",
  dockerBaseImage := "openjdk",
  dockerExposedPorts := Seq(9000),
  dockerExposedVolumes := Seq("/opt/docker/logs"),
  version in Docker := version.value
)

addCommandAlias("buildWeb", "web/docker:publishLocal")
addCommandAlias("buildApi", "api/docker:publishLocal")

