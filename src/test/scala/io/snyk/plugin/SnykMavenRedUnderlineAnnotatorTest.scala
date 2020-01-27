package io.snyk.plugin

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import io.snyk.plugin.datamodel.SnykMavenArtifact
import io.snyk.plugin.ui.state.SnykPluginState

import scala.io.{Codec, Source}
import scala.util.Try

class SnykMavenRedUnderlineAnnotatorTest extends LightCodeInsightFixtureTestCase() {

  def testHighlighting(): Unit = {
    SnykPluginState.mockForProject(myModule.getProject, mockResponder = myMockResponder)

    myFixture.configureByText("pom.xml",
      """<?xml version="1.0" encoding="UTF-8"?>
                  <project xmlns="http://maven.apache.org/POM/4.0.0"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>simpleMaven</groupId>
                    <artifactId>simpleMaven</artifactId>
                    <version>1.0</version>

                    <dependencies>
                      <dependency>
                        <groupId><error>org.codehaus.jackson</error></groupId>
                        <artifactId><error>jackson-mapper-asl</error></artifactId>
                        <version>1.8.5</version>
                      </dependency>
                    </dependencies>
                  </project>
    """)

    myFixture.checkHighlighting(true, false, true)
  }

  private[this] def myMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sample-response-for-annotator-test.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }
}
