package io.snyk.plugin

fun setupDummyCliFile() {
    val cliFile = getCliFile()

    if (!cliFile.exists()) {
        cliFile.createNewFile()
    }
}
