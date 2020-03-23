package io.snyk.plugin

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import io.snyk.plugin.datamodel.SnykMavenArtifact
import io.snyk.plugin.ui.state.SnykPluginState

import scala.io.{Codec, Source}
import scala.util.Try

class SnykMavenRedUnderlineAnnotatorTest extends LightCodeInsightFixtureTestCase() {

  def testSnykMavenRedUnderlineAnnotatorHighlighting(): Unit = {
    SnykPluginState.mockForProject(myModule.getProject, mockResponder = sampleMockResponder)

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
                        <groupId><error descr="Vulnerable package: org.codehaus.jackson:jackson-mapper-asl:1.8.5">org.codehaus.jackson</error></groupId>
                        <artifactId><error descr="Vulnerable package: org.codehaus.jackson:jackson-mapper-asl:1.8.5">jackson-mapper-asl</error></artifactId>
                        <version><error descr="Vulnerable package: org.codehaus.jackson:jackson-mapper-asl:1.8.5">1.8.5</error></version>
                      </dependency>
                    </dependencies>
                  </project>
    """)

    myFixture.checkHighlighting(true, false, true)
  }

  def testSnykMavenRedUnderlineAnnotatorHighlightingExcludingProperties(): Unit = {
    SnykPluginState.mockForProject(myModule.getProject, mockResponder = samplePropertiesMockResponder)

    myFixture.configureByText("pom.xml",
      """<?xml version="1.0" encoding="UTF-8"?>
                  <project xmlns="http://maven.apache.org/POM/4.0.0"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>simpleMaven</groupId>
                    <artifactId>simpleMaven</artifactId>
                    <version>1.0</version>

                    <properties>
                      <mysql.version>5.1.36</mysql.version>
                    </properties>

                    <dependencies>
                      <dependency>
                          <groupId><error descr="Vulnerable package: mysql:mysql-connector-java:5.1.36">mysql</error></groupId>
                          <artifactId><error descr="Vulnerable package: mysql:mysql-connector-java:5.1.36">mysql-connector-java</error></artifactId>
                          <version><error descr="Vulnerable package: mysql:mysql-connector-java:5.1.36">${mysql.version}</error></version>
                      </dependency>
                    </dependencies>
                  </project>
    """)

    myFixture.checkHighlighting(true, false, true)
  }

  def testSnykMavenRedUnderlineAnnotatorHighlightingForFromDependencies(): Unit = {
    SnykPluginState.mockForProject(myModule.getProject, mockResponder = sampleForFromDependenciesMockResponder)

    myFixture.configureByText("pom.xml",
      """<?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>simpleMaven</groupId>
                    <artifactId>simpleMaven</artifactId>
                    <version>1.0</version>

                    <properties>
                        <hibernate.entitymanager.version>5.0.0.CR2</hibernate.entitymanager.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId><error descr="Vulnerable package: org.hibernate:hibernate-entitymanager:5.0.0.CR2">org.hibernate</error></groupId>
                            <artifactId><error descr="Vulnerable package: org.hibernate:hibernate-entitymanager:5.0.0.CR2">hibernate-entitymanager</error></artifactId>
                            <version><error descr="Vulnerable package: org.hibernate:hibernate-entitymanager:5.0.0.CR2">${hibernate.entitymanager.version}</error></version>
                            <scope>provided</scope>
                        </dependency>
                    </dependencies>
                </project>
    """)

    myFixture.checkHighlighting(true, false, true)
  }

  private[this] def sampleMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sample-response-for-annotator-test.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }

  private[this] def samplePropertiesMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("properties-response-for-annotator-test.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }

  private[this] def sampleForFromDependenciesMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sample-response-for-annotator-from-dependencies-test.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }
}
