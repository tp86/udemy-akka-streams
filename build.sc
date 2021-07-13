import $ivy.`com.goyeau::mill-scalafix:0.2.4`
import com.goyeau.mill.scalafix.ScalafixModule
import mill._, scalalib._, scalafmt._

object versions {
  val scala = "2.13.6"
  val akka = "2.6.15"
  val scalaTest = "3.2.9"
  object mill {
    val organizeImports = "0.5.0"
  }
}

trait AkkaModule extends ScalaModule with ScalafmtModule with ScalafixModule {
  def scalaVersion = versions.scala
  def ivyDeps = Agg(
    ivy"com.typesafe.akka::akka-stream:${versions.akka}",
    ivy"com.typesafe.akka::akka-stream-testkit:${versions.akka}",
    ivy"com.typesafe.akka::akka-testkit:${versions.akka}",
    ivy"org.scalatest::scalatest:${versions.scalaTest}",
  )
  def scalacOptions = super.scalacOptions() ++ Seq(
    "-language:postfixOps",
    "-deprecation",
    "-Wunused:imports",
  )
  def scalafixIvyDeps = Agg(ivy"com.github.liancheng::organize-imports:${versions.mill.organizeImports}")
}

object playground extends AkkaModule

object part2_primer extends AkkaModule

object part3_graphs extends AkkaModule

object part4_techniques extends AkkaModule {
  object testing extends Tests with TestModule.ScalaTest
}

object part5_advanced extends AkkaModule
