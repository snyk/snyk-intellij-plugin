package io.snyk.plugin

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import io.snyk.plugin.datamodel.SnykMavenArtifact
import io.snyk.plugin.ui.state.SnykPluginState

import scala.io.{Codec, Source}
import scala.util.Try

class SnykGradleRedUnderlineAnnotatorTest extends LightCodeInsightFixtureTestCase() {

  def testGradleHighlightingForSimpleDependencyDeclaration(): Unit = {
    SnykPluginState.mockForProject(myModule.getProject, mockResponder = cliMockResponder)

    myFixture.configureByText("build.groovy",
      """apply plugin: 'application'
               mainClassName = 'io.snyk.MainTest'

               repositories {
                mavenCentral()
               }

               dependencies {
                <error descr="Vulnerable package: axis:axis:1.4">compile 'axis:axis:1.4'</error>
               }""")

    myFixture.checkHighlighting(true, false, true)
  }

  def testGradleHighlightingForComplexDependencyDeclaration(): Unit = {
    SnykPluginState.mockForProject(myModule.getProject, mockResponder = cliMockResponder)

    myFixture.configureByText("build.groovy",
      """apply plugin: 'application'
               mainClassName = 'io.snyk.MainTest'

               repositories {
                mavenCentral()
               }

               dependencies {
                <error descr="Vulnerable package: org.codehaus.jackson:jackson-mapper-asl:1.9.13">compile group: 'org.codehaus.jackson', name: 'jackson-mapper-asl', version: '1.9.13'</error>
               }""")

    myFixture.checkHighlighting(true, false, true)
  }

  def testGradleHighlightingForComplexAndSimpleDependenciesDeclaration(): Unit = {
    SnykPluginState.mockForProject(myModule.getProject, mockResponder = cliMockResponder)

    myFixture.configureByText("build.groovy",
      """apply plugin: 'application'
               mainClassName = 'io.snyk.MainTest'

               repositories {
                mavenCentral()
               }

               dependencies {
                <error descr="Vulnerable package: axis:axis:1.4">compile 'axis:axis:1.4'</error>
                <error descr="Vulnerable package: org.codehaus.jackson:jackson-mapper-asl:1.9.13">compile group: 'org.codehaus.jackson', name: 'jackson-mapper-asl', version: '1.9.13'</error>
               }""")

    myFixture.checkHighlighting(true, false, true)
  }

  private[this] def cliMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sample-response-for-gradle-annotator-test.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }
}
