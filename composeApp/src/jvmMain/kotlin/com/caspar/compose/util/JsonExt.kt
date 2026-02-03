package com.caspar.compose.util
import kotlinx.serialization.json.*
import java.io.*
import java.util.zip.*

val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

// ==================== 【序列化】对象/列表 → JSON ====================
/**
 * 将任意 @Serializable 对象（含 List<T>）转为 JSON 字符串
 * 用法: user.toJsonString() / userList.toJsonString()
 */
inline fun <reified T> T.toJsonString(): String = json.encodeToString(this)

/**
 * 将任意 @Serializable 对象（含 List<T>）写入文件（自动识别 .gz/.zip 压缩）
 * 用法: config.writeJson(File("cfg.json")) / list.writeJson(File("data.zip"))
 */
inline fun <reified T> T.writeJson(file: File) {
    val content = this.toJsonString()
    when {
        file.extension.equals("gz", ignoreCase = true) ->
            GZIPOutputStream(file.outputStream()).bufferedWriter().use { it.write(content) }
        file.extension.equals("zip", ignoreCase = true) ->
            ZipOutputStream(file.outputStream()).use { zip ->
                val entryName = file.nameWithoutExtension + ".json"
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        else -> file.writeText(content)
    }
}

// ==================== 【反序列化】JSON 字符串 → 对象/列表 ====================
/**
 * 将 JSON 字符串解析为指定类型（支持单对象 / List<T>）
 * 用法: jsonString.fromJson<User>() / jsonString.fromJson<List<User>>()
 */
inline fun <reified T> String.fromJson(): T = json.decodeFromString(this)

// ==================== 【文件读取】文件（含压缩）→ 对象/列表 ====================
/**
 * 从文件（.json/.gz/.zip）读取并解析为指定类型（自动解压）
 * 用法: File("data.json").readJson<User>() / File("backup.zip").readJson<List<Item>>()
 */
inline fun <reified T> File.readJson(): T {
    val content = when {
        extension.equals("gz", ignoreCase = true) ->
            GZIPInputStream(inputStream()).bufferedReader().use { it.readText() }
        extension.equals("zip", ignoreCase = true) ->
            ZipInputStream(inputStream()).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { !it.isDirectory }
                    ?.let { zip.reader().use { r -> r.readText() } }
                    ?: error("ZIP 文件中未找到有效 JSON 内容")
            }
        else -> readText()
    }
    return content.fromJson<T>()
}

// ==================== 【辅助扩展】JSON 字符串 → 文件（含压缩） ====================
/**
 * 将已有的 JSON 字符串写入文件（自动压缩）
 * 用法: jsonString.writeJsonFile(File("out.gz"))
 */
fun String.writeJsonFile(file: File) {
    when {
        file.extension.equals("gz", ignoreCase = true) ->
            GZIPOutputStream(file.outputStream()).bufferedWriter().use { it.write(this) }
        file.extension.equals("zip", ignoreCase = true) ->
            ZipOutputStream(file.outputStream()).use { zip ->
                val entryName = file.nameWithoutExtension + ".json"
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(this.toByteArray())
                zip.closeEntry()
            }
        else -> file.writeText(this)
    }
}