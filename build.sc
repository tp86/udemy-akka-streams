import mill._, scalalib._

object versions {
  val scala = "2.13.6"
  val akka = "2.6.15"
  val scalaTest = "3.2.9"
}

object playground extends ScalaModule {
  def scalaVersion = versions.scala
  def ivyDeps = Agg(
    ivy"com.typesafe.akka::akka-stream:${versions.akka}",
    ivy"com.typesafe.akka::akka-stream-testkit:${versions.akka}",
    ivy"com.typesafe.akka::akka-testkit:${versions.akka}",
    ivy"org.scalatest::scalatest:${versions.scalaTest}"
  )
}
