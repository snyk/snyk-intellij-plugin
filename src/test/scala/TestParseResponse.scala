
import Predef.{any2stringadd => _, _}
import io.circe.parser._

import scala.io.Source
import io.snyk.plugin.model.SnykVulnResponse
import io.snyk.plugin.model.SnykVulnResponse.Decoders._

object TestParseResponse extends App {

  def boxes(xs: Seq[Seq[Seq[String]]]): String = {
    val width = xs.flatten.flatten.map(_.length).max
    xs.map{ box =>
      box.map{ panel =>
        panel.collect{
          case line if line.trim.nonEmpty => "║ " + line.padTo(width, ' ') + " ║"
        }.mkString("\n")
      }.mkString(s"\n╟┈${"┈" * width}┈╢\n")
    }.mkString(s"╔═${"═" * width}═╗\n", s"\n╠═${"═" * width}═╣\n", s"\n╚═${"═" * width}═╝")
  }

  def descendingTree(xs: Seq[String]): String = {
    val tail = xs.tail.zipWithIndex.map{case (x,i) => (" " * i) + "➥ " + x}
    (xs.head +: tail).mkString("\n  ")
  }

  def columnise(xs: (String, String)*): Seq[String] = {
    //first, make sure we're only dealing with single lines
    val linePairs = xs.flatMap{case (a,b) => a.split('\n').zipAll(b.split('\n'), "", "")}
    val w1 = linePairs.map(_._1.length).max
    val w2 = linePairs.map(_._2.length).max
    linePairs.map{case (a,b) => a.padTo(w1, ' ') + " " + b.padTo(w2, ' ')}
  }

  val inputStream =  getClass.getClassLoader.getResourceAsStream("sample-response.json")
  val inputSource = Source.fromInputStream(inputStream)
  val input = inputSource.mkString
  val output = decode[SnykVulnResponse](input)

//  import sext._
//  println(output.valueTreeString)

  val vulns = output.right.toSeq.flatMap(_.vulnerabilities)
  val tree = vulns map { v =>
    Seq(
      Seq(
        s"${v.combinedId}",
        s"${v.title} in ${v.moduleName}"
      ),
      columnise(
        "via:"        -> descendingTree(v.from),
        "vulnerable:" -> { if(v.isUpgradable) v.semver.splitVulnerable.mkString("\n") else "n/a" },
        "upgrade:"    -> descendingTree(v.normalisedUpgradePath),
        "patchable:"  -> v.isPatchable.toString
      )
    )
  }
  println(boxes(tree))
}
