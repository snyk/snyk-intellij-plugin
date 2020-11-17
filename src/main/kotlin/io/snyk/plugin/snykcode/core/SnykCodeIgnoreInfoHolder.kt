package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeIgnoreInfoHolderBase

class SnykCodeIgnoreInfoHolder private constructor() : DeepCodeIgnoreInfoHolderBase(
    HashContentUtils.instance
) {

    override fun getFilePath(file: Any): String = PDU.toPsiFile(file).virtualFile.path

    override fun getFileName(file: Any): String = PDU.toPsiFile(file).virtualFile.name

    override fun getProjectOfFile(file: Any): Any = PDU.toPsiFile(file).project

    override fun getDirPath(file: Any): String = PDU.toPsiFile(file).virtualFile.parent.path

    companion object{
        val instance = SnykCodeIgnoreInfoHolder()
    }
}
