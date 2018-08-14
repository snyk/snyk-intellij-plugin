import io.circe.parser.decode
import io.snyk.plugin.datamodel.{MiniVuln2, SnykVulnResponse}
import io.snyk.plugin.datamodel.SnykVulnResponse.Decoders._

import scala.io.Source

object TestMiniVuln2 extends App {
  val inputStream =  getClass.getClassLoader.getResourceAsStream("sample-response-2.json")
  val inputSource = Source.fromInputStream(inputStream)
  val input = inputSource.mkString
  val tryOutput = decode[SnykVulnResponse](input)
  val output = tryOutput.right.get


  val vulns = output.vulnerabilities
  val miniVulns = vulns.map(MiniVuln2.from)
//  printout(miniVulns)

  println()
  println("**************************")
  println("********* MERGED *********")
  println("**************************")
  println()

  val merged = MiniVuln2.merge(miniVulns)
//  printout(merged)

  println(s"Full Size: ${miniVulns.size}")
  println(s"Merged Size: ${merged.size}")

  import io.circe.syntax._
  println(merged.asJson.spaces2)

  def printout(mvs: Seq[MiniVuln2]): Unit = {
    mvs foreach { mv =>
      mv.spec.toMultiString foreach println
      println()
      mv.derivations foreach { d =>
        d.treeString foreach println
      }
      println("============================================================")
    }
  }

}
