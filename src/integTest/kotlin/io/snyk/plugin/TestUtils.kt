package io.snyk.plugin

fun setupDummyCliFile() {
    val cliFile = getCliFile()

    if (!cliFile.exists()) {
        if (!cliFile.parentFile.exists()) cliFile.mkdirs()
        cliFile.createNewFile()
    }
}
