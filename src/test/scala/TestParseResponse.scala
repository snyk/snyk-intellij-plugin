

import io.circe.parser._

import scala.io.Source
import io.snyk.plugin.SnykVulnResponse
import io.snyk.plugin.SnykVulnResponse.Decoders._

object TestParseResponse extends App {

  val inputStream =  getClass.getClassLoader.getResourceAsStream("sample-response.json")
  val inputSource = Source.fromInputStream(inputStream)
  val input = inputSource.mkString
  //val json = parse(input).right.get
  //val output = json.as[SnykVulnResponse]
  val output = decode[SnykVulnResponse](input)
//  pprint.pprintln(output)

  import sext._
  println(output.valueTreeString)

  val vulns = output.right.toSeq.flatMap(_.vulnerabilities)
  vulns foreach { v =>
    val cwe = v.identifiers.get("CWE").flatMap(_.headOption).getOrElse("???")

    val upgradePath = if(v.isUpgradable) {
      (v.upgradePath map {
        case Left(x) => "◉"
        case Right(x) => x
      }).mkString(" ⇒ ")
    } else "n/a"

    println(s"┏${"━" * 120}")
    println(s"┃ ${v.id} ($cwe) - ${v.title} in ${v.moduleName}")
    println(s"┃  via: ${v.from mkString " ⇒ "}")
    println(s"┃  vulnerable: ${v.semver.vulnerable}")
    if(v.semver.unaffected.nonEmpty) println(s"┃  unaffected: ${v.semver.unaffected}")
    println(s"┃  upgrade: $upgradePath (patchable: ${v.isPatchable})")
    println(s"┗━")


  }
}
