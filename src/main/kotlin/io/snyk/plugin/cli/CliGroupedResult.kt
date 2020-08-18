package io.snyk.plugin.cli

class CliGroupedResult(
    val vulnerabilitiesMap: Map<String, List<Vulnerability>>,
    val uniqueCount: Int,
    val pathsCount: Int,
    val projectName: String,
    val displayTargetFile: String,
    val path: String)
