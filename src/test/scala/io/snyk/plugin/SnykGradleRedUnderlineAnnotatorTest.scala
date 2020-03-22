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
                compile <error descr="Vulnerable package: axis:axis:1.4">'axis:axis:1.4'</error>
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
                compile <error descr="Vulnerable package: org.codehaus.jackson:jackson-mapper-asl:1.9.13">group: 'org.codehaus.jackson', name: 'jackson-mapper-asl', version: '1.9.13'</error>
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
                compile <error descr="Vulnerable package: axis:axis:1.4">'axis:axis:1.4'</error>
                compile <error descr="Vulnerable package: org.codehaus.jackson:jackson-mapper-asl:1.9.13">group: 'org.codehaus.jackson', name: 'jackson-mapper-asl', version: '1.9.13'</error>
               }""")

    myFixture.checkHighlighting(true, false, true)
  }

  def testGradleHighlightingForDifferentTypesOfDependencyConfigurations(): Unit = {
    SnykPluginState.mockForProject(myModule.getProject, mockResponder = cliDifferentConfigTypesMockResponder)

    myFixture.configureByText("build.groovy",
      """apply plugin: 'application'
               mainClassName = 'io.snyk.MainTest'

               repositories {
                mavenCentral()
               }

               dependencies {
                compile <error descr="Vulnerable package: org.codehaus.jackson:jackson-mapper-asl:1.9.13">group: 'org.codehaus.jackson', name: 'jackson-mapper-asl', version: '1.9.13'</error>
                implementation <error descr="Vulnerable package: org.springframework.cloud:spring-cloud-config-server:2.2.0.RELEASE">group: 'org.springframework.cloud', name: 'spring-cloud-config-server', version: '2.2.0.RELEASE'</error>
                compileOnly <error descr="Vulnerable package: org.apache.geode:geode-core:1.10.0">group: 'org.apache.geode', name: 'geode-core', version: '1.10.0'</error>
                runtimeOnly <error descr="Vulnerable package: io.argonaut:argonaut_2.11:6.1">group: 'io.argonaut', name: 'argonaut_2.11', version: '6.1'</error>
                runtimeClasspath <error descr="Vulnerable package: org.apache.commons:commons-configuration2:2.2">group: 'org.apache.commons', name: 'commons-configuration2', version: '2.2'</error>
                testCompile <error descr="Vulnerable package: com.googlecode.gwtupload:gwtupload-samples:0.6.6">group: 'com.googlecode.gwtupload', name: 'gwtupload-samples', version: '0.6.6'</error>
                testImplementation <error descr="Vulnerable package: org.jyaml:jyaml:1.2">group: 'org.jyaml', name: 'jyaml', version: '1.2'</error>
                testCompileOnly <error descr="Vulnerable package: axis:axis:1.0">group: 'axis', name: 'axis', version: '1.0'</error>
                testCompileClasspath <error descr="Vulnerable package: io.netty:netty-all:4.1.44.Final">group: 'io.netty', name: 'netty-all', version: '4.1.44.Final'</error>
                testRuntime <error descr="Vulnerable package: org.cryptacular:cryptacular:1.2.4">group: 'org.cryptacular', name: 'cryptacular', version: '1.2.4'</error>
                testRuntimeOnly <error descr="Vulnerable package: org.apache.xmlrpc:xmlrpc:3.1">group: 'org.apache.xmlrpc', name: 'xmlrpc', version: '3.1'</error>
                testRuntimeClasspath <error descr="Vulnerable package: com.itextpdf:sign:7.1.5">group: 'com.itextpdf', name: 'sign', version: '7.1.5'</error>
                compile <error descr="Vulnerable package: org.jolokia:jolokia-core:1.2.0">group: 'org.jolokia', name: 'jolokia-core', version: '1.2.0'</error>
               }""")

    myFixture.checkHighlighting(true, false, true)
  }

  private[this] def cliMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sample-response-for-gradle-annotator-test.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }

  private[this] def cliDifferentConfigTypesMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sample-response-for-gradle-annotator-different-config-types.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }
}
