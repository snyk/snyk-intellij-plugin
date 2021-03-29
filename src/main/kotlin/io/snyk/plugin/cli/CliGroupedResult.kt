package io.snyk.plugin.cli

class CliGroupedResult(
    val id2vulnerabilities: Map<String, List<Vulnerability>>,
    val uniqueCount: Int,
    val pathsCount: Int,
    val projectName: String,
    val displayTargetFile: String,
    val path: String)
