# Snyk


[![Version](https://img.shields.io/jetbrains/plugin/v/10972.svg)](https://plugins.jetbrains.com/plugin/10972)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/10972.svg)](https://plugins.jetbrains.com/plugin/10972)

<!-- Plugin description start -->
### Secure development for developers and teams
Snyk Security finds and fixes security vulnerabilities, infrastructure misconfigurations, and code quality issues
in your projects early in the development lifecycle to help you ace your security reviews and avoid a costly fix later
down the line. If you’re an individual developer, open-source contributor, or maintainer at a large organization, Snyk
helps you ship secure code, faster.

Snyk scans for issue types around:

- **[Open Source Security](https://snyk.io/product/open-source-security-management/)** - security vulnerabilities in both the direct and in-direct (transitive) open-source dependencies you are pulling into the project.
- **[Code Security](https://snyk.io/product/snyk-code/)** - security vulnerabilities identified in your own code.
- **[Container Security](https://snyk.io/product/container-vulnerability-management/)** - security vulnerabilities in your base images
- **[Infrastructure as Code (IaC) Security](https://snyk.io/product/infrastructure-as-code-security/)** - configuration issues in your IaC templates (Terraform, Kubernetes, CloudFormation, and Azure Resource Manager)
- **[Code Quality](https://snyk.io/product/snyk-code/)** - code quality issues in your own code
- **[Open Source Advisor](https://snyk.io/advisor/)** - package health of the direct dependencies you are using including popularity, maintenance, risk & community insights.

### Security for your entire application
Comprehensive security for proprietary code, open-source dependencies, container, and infrastructure as code (IaC)
configurations—all in one plugin. Whether you’re looking for a Java vulnerability scanner, a custom code vulnerability
scanner, or open-source security scanner, or an application security plugin.

### Fast, free and accurate results
Get security analysis of your code, containers, and configurations free of charge. Snyk scans for vulnerabilities
and misconfigurations in seconds. When returning your results, Snyk Security categorizes security issues by issue type
and severity.

### Easy and actionable fixes in your IDE
Get instant context on the issue, impact, and fix guidance in line with code from within your favorite IDE.
For open-source, receive automated algorithm-based fix suggestions for both direct and transitive dependencies.
For containers, you can automate upgrades to the most secure base image to quickly resolve numerous vulnerabilities.

### Snyk Security supported languages and formats:
**Java** | **JavaScript** | **Python** | **Kubernetes** | **Terraform** | **CloudFormation** | **Azure Resource Manager (ARM)**

See the [full list of languages and package managers Snyk supports](https://snyk.co/ucWSd).

### Speed up security
By fixing issues early, Snyk Security helps you ace security reviews later down the line and avoid time-intensive
or costly fixes downstream in a build process.

### Stay in flow
With automated and guided fixes in-line with code, Snyk provides the context and know-how to apply a fix while keeping
you in your IDE.

### Snyk Vulnerability Database
Snyk Security relies on the [Snyk Vulnerability DB](https://security.snyk.io/), the most comprehensive, accurate,
and timely database for open source vulnerabilities. With 370% better coverage than next  largest publicly available
database and 25 days faster vulnerability discovery than GitHub’s advisory DB. In the case of Javascript vulnerabilities
92.5% were disclosed faster than the NVD.

### Snyk Code AI and ML
Snyk Code learns from the knowledge of the global developer community using an unique human guided process which makes
it industry-leading in its speed and accuracy. Fix guidance is offer in-line with code with additional explanations
and example fixes from open source projects that fixed similar issues. Address issues in the comfort of your workbench
even before issues get stored into the source code management.

### How to install

1. Open Settings/ Preferences in your IDE
2. Search for ‘Snyk’ in the Marketplace
3. Click Install
4. Authenticate with Snyk

When navigating back to your IDE, your first scan should automatically start.

### FAQ
**Q: What do I need to use Snyk Security?**<br>
A: Snyk plugins require an API token to connect Snyk’s security database with your IDE. If you haven’t already, sign up for a free Snyk account to get your token.

**Q: How do I install Snyk Security?**<br>
A: Open the Settings/Preferences,  search for ‘Snyk Security’ in the Marketplace tab and click Install. Once your IDE has reloaded you can authenticate with Snyk, from there your first security scan will automatically kick off.

**Q: Which JetBrains IDEs does Snyk Security support?**<br>
A: Snyk provides plugins for all major JetBrains IDEs, including [IntelliJ IDEA](https://snyk.io/lp/intellij-ide-plugin/), [WebStorm](https://snyk.io/lp/webstorm-ide-plugin/), PyCharm, GoLand, PhpStorm, Android Studio, AppCode, Rider and RubyMine.

**Q: Why should I test in my IDE?**<br>
A: Testing your code within your IDE ensures you are identifying issues early on in development as opposed to finding them later in the process, when it is much more time intensive and costly to fix.

_If you aren't addressing problems during the developer workflow and you're finding them and dealing with them in QA, it will take you 10 times longer to fix. That’s where Snyk comes in.” Ryan Kimber, Founder and CEO, FormHero._

**Q: Is Snyk Security free?**<br>
A: Yes! Anyone can use Snyk Security with zero limitations, free of charge. First, install, and then authenticate with Snyk. If you already have a free Snyk account, you can connect your Snyk Jetbrains plugin back to Snyk in two clicks. If you’re new to Snyk, you can signup for a free account and follow the install instructions to authenticate from there.

Snyk Security is open source, so feel free to contribute to development or leave feedback in the reviews.

**Q: Can I run Snyk Security locally?**<br>
A: The plugin operates using the Snyk CLI. Once Snyk Security is  installed, it will automatically download the latest version of the Snyk CLI and use it to run scans.

**Q: Does Snyk Security work in multiple IDEs?**<br>
A: Yes, Snyk also has plugins for VS Code, Eclipse, and Visual Studio. Read more about it [here](https://snyk.io/ide-plugins/).

**Q: I have feedback on the plugin, how do I report it?**<br>
A: You can always use the [official’s Snyk support channel](https://support.snyk.io/hc/en-us/requests/new) to open a ticket.
<!-- Plugin description end -->

### Proxy Setup
If you are a behind a proxy, please configure the proxy in the IDE. Currently, http and https proxies are supported by the plugin.

### Environment setup
The plugin uses the Snyk CLI to perform vulnerability scans. In order for this to function correctly,
certain environment variables need to be set.

1. `JAVA_HOME` to analyse Java JVM-based projects via Snyk CLI
2. `PATH` to find maven when analysing Maven projects, to find python for python projects, etc
