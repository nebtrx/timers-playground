import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
  lazy val monix = "io.monix" %% "monix" % "3.0.0-RC1"
  lazy val catsEffects = "org.typelevel" %% "cats-effect" % "1.0.0-RC2"
}
