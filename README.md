# Snyk

[![Version](https://img.shields.io/jetbrains/plugin/v/10972.svg)](https://plugins.jetbrains.com/plugin/10972)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/10972.svg)](https://plugins.jetbrains.com/plugin/10972)

<!-- Plugin description start -->
Snyk Security for Code, Open Source Dependencies, Container, and IaC Configurations helps you find and fix security
vulnerabilities and code quality issues in your projects, all from within your favorite IDE. Within a few seconds, the
plugin will provide a list of all the different types of issues identified, bucketed into categories, together with
actionable fix advice:

- **Open Source Security** - known vulnerabilities in both the direct and in-direct (transitive) open source dependencies you are pulling into the project.
- **Code Security** - security weaknesses identified in your own code.
- **Code Quality** - code quality issues in your own code.
- **Configuration Issues** - misconfigurations in Terraform, CloudFormation, Kubernetes, and ARM templates.
- **Container Vulnerabilities** - vulnerabilities in your container images found in Kubernetes workload files.
- **Open Source Advisor** - health test for the direct dependencies you are using. Including: popularity, maintenance, risk & community insights.

**Snyk detects the critical vulnerability Log4Shell, which was found in the open source Java library log4j-core - a component of one of the most popular Java logging frameworks, Log4J. The vulnerability was categorized as Critical with a CVSS score of 10, and with a mature exploit level.**

### Useful links

- This plugin works with projects written in Java, JavaScript, .NET and many more languages. See the [full list of languages and package managers Snyk supports](https://snyk.co/ucWSd)
- [Bug tracker](https://github.com/snyk/snyk-intellij-plugin/issues)

<!-- Plugin description end -->

### Proxy Setup
If you are a behind a proxy, please configure the proxy in the IDE. Currently, http and https proxies are supported by the plugin.

### Environment setup
The plugin uses the Snyk CLI to perform vulnerability scans. In order for this to function correctly,
certain environment variables need to be set.

1. `JAVA_HOME` to analyse Java JVM-based projects via Snyk CLI
2. `PATH` to find maven when analysing Maven projects, to find python for python projects, etc
