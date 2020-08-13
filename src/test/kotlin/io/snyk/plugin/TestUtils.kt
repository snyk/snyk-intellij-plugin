package io.snyk.plugin

import io.snyk.plugin.cli.ConsoleCommandRunner

fun getCliNotInstalledRunner(): ConsoleCommandRunner = object: ConsoleCommandRunner() {
    override fun execute(commands: List<String>, workDirectory: String): String {
        return "command not found"
    }
}
