package io.snyk.plugin.cli

data class CliGroupedResult(
    val id2vulnerabilities: Map<String, List<Vulnerability>>,
    val uniqueCount: Int,
    val pathsCount: Int
)
