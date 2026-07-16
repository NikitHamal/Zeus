
package com.zeus.util

import java.io.File

object FileUtil {
    fun listFiles(dir: File): List<File> = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    fun readFile(file: File): String = file.readText()
    fun writeFile(file: File, content: String) { file.parentFile?.mkdirs(); file.writeText(content) }
    fun isTextFile(file: File): Boolean {
        val textExt = setOf("kt","java","xml","json","md","txt","gradle","kts","yml","yaml","properties","js","ts","py","sh","html","css","c","cpp","h")
        return textExt.contains(file.extension.lowercase()) || file.length() < 500_000
    }
}
