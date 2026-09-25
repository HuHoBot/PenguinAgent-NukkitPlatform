package cn.huohuas001.huhobotPenguin.adapter.config

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 基于行的 YAML 定点写入器。
 *
 * 与 snakeyaml 的「整体重新序列化」不同，本类只改写目标键所在的行或块，
 * 因此配置文件里的注释、空行、键顺序都能原样保留 —— 这对一份满是说明注释的
 * config.yml 很重要：WebUI 保存、扫码登录写回凭据、自动收录群号都不该把注释抹掉。
 *
 * 已知限制（有意为之，避免过度设计）：
 * - 仅支持块状（block style）YAML，不支持 flow style（`{a: 1}` / `[1, 2]`）的定点改写；
 *   这类值会被整体替换成块状写法。
 * - 同名键重复出现时只改写**第一个**。
 * - 锚点/别名（`&a` / `*a`）与多行标量（`|` / `>`）会被当作普通标量覆盖。
 */
class YamlFileEditor(private val file: File) {

    private val lines: MutableList<String> =
        if (file.isFile) file.readLines(Charsets.UTF_8).toMutableList() else mutableListOf()

    private var dirty = false

    /** 设置 [path]（dotted path，如 `agent.base-url`）的值为 [value]。 */
    fun set(path: String, value: Any?) {
        val segments = path.split('.').map(String::trim).filter(String::isNotEmpty)
        if (segments.isEmpty()) return
        dirty = true

        val found = locate(segments)
        if (found >= 0) {
            val indent = indentOf(lines[found])
            val end = blockEnd(found, indent)
            val rendered = renderEntry(segments.last(), value, indent)
            lines.subList(found, end + 1).clear()
            lines.addAll(found, rendered)
            return
        }

        // 键不存在：挂到最深的已存在祖先块末尾，缺失的中间层一并补出来。
        val ancestorDepth = deepestExistingAncestor(segments)
        val insertAt = if (ancestorDepth == 0) lines.size else blockEndOfPath(segments.take(ancestorDepth)) + 1
        val baseIndent = if (ancestorDepth == 0) 0 else indentOfLineOfPath(segments.take(ancestorDepth)) + 2

        val rendered = mutableListOf<String>()
        for (depth in ancestorDepth until segments.size - 1) {
            rendered.add(" ".repeat(baseIndent + (depth - ancestorDepth) * 2) + "${segments[depth]}:")
        }
        rendered.addAll(
            renderEntry(
                segments.last(),
                value,
                baseIndent + (segments.size - 1 - ancestorDepth) * 2
            )
        )
        lines.addAll(insertAt.coerceIn(0, lines.size), rendered)
    }

    /** 写回文件；无改动时不做任何事。 */
    fun commit() {
        if (!dirty) return
        file.parentFile?.mkdirs()
        val content = lines.joinToString("\n").trimEnd('\n') + "\n"
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        dirty = false
    }

    // ------------------------------------------------------------------ 查找

    /** 返回值为 [segments] 的那一行下标；找不到返回 -1。 */
    private fun locate(segments: List<String>): Int {
        val stack = ArrayDeque<Pair<Int, String>>() // (indent, key)
        for (index in lines.indices) {
            val parsed = parseMappingLine(lines[index]) ?: continue
            while (stack.isNotEmpty() && stack.last().first >= parsed.first) stack.removeLast()
            stack.addLast(parsed.first to parsed.second)
            if (stack.size == segments.size && stack.map { it.second } == segments) return index
        }
        return -1
    }

    /** [segments] 中已存在的最长前缀长度。 */
    private fun deepestExistingAncestor(segments: List<String>): Int {
        for (depth in segments.size - 1 downTo 1) {
            if (locate(segments.take(depth)) >= 0) return depth
        }
        return 0
    }

    private fun indentOfLineOfPath(segments: List<String>): Int {
        val index = locate(segments)
        return if (index >= 0) indentOf(lines[index]) else 0
    }

    /** [segments] 对应块的最后一个内容行下标。 */
    private fun blockEndOfPath(segments: List<String>): Int {
        val index = locate(segments)
        if (index < 0) return lines.size - 1
        return blockEnd(index, indentOf(lines[index]))
    }

    /**
     * 返回 [start] 所在块的最后一个**内容行**下标。
     * 块内尾部的空行与注释行不算在内，避免吃掉父级注释。
     */
    private fun blockEnd(start: Int, indent: Int): Int {
        var last = start
        var index = start + 1
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                index++
                continue
            }
            if (indentOf(line) <= indent) break
            last = index
            index++
        }
        return last
    }

    // ------------------------------------------------------------------ 渲染

    private fun renderEntry(key: String, value: Any?, indent: Int): List<String> {
        val pad = " ".repeat(indent)
        return when (value) {
            is Map<*, *> -> renderMapEntry(key, value, indent)
            is Iterable<*> -> renderListEntry(key, value, indent)
            is Array<*> -> renderListEntry(key, value.toList(), indent)
            else -> listOf("$pad$key: ${scalar(value)}")
        }
    }

    private fun renderMapEntry(key: String, value: Map<*, *>, indent: Int): List<String> {
        val pad = " ".repeat(indent)
        if (value.isEmpty()) return listOf("$pad$key: {}")
        val out = mutableListOf("$pad$key:")
        value.forEach { (k, v) ->
            out.addAll(renderEntry(k.toString(), v, indent + 2))
        }
        return out
    }

    private fun renderListEntry(key: String, value: Iterable<*>, indent: Int): List<String> {
        val pad = " ".repeat(indent)
        val items = value.toList()
        if (items.isEmpty()) return listOf("$pad$key: []")
        val out = mutableListOf("$pad$key:")
        val itemPad = " ".repeat(indent + 2)
        items.forEach { item ->
            when (item) {
                is Map<*, *> -> {
                    if (item.isEmpty()) {
                        out.add("$itemPad- {}")
                    } else {
                        var first = true
                        item.forEach { (k, v) ->
                            val entry = renderEntry(k.toString(), v, if (first) 0 else indent + 4)
                            if (first) {
                                out.add("$itemPad- ${entry.first().trimStart()}")
                                out.addAll(entry.drop(1))
                                first = false
                            } else {
                                out.addAll(entry)
                            }
                        }
                    }
                }

                is Iterable<*> -> out.add("$itemPad- ${item.joinToString(", ") { scalar(it) }}")
                else -> out.add("$itemPad- ${scalar(item)}")
            }
        }
        return out
    }

    private fun scalar(value: Any?): String = when (value) {
        null -> "''"
        is Boolean -> value.toString()
        is Number -> value.toString()
        else -> quoteIfNeeded(value.toString())
    }

    private fun quoteIfNeeded(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return "''"
        val looksReserved = text.lowercase() in RESERVED
        val needsQuote = looksReserved ||
            text != raw ||
            text.startsWith("-") || text.startsWith("?") || text.startsWith(":") ||
            text.startsWith("*") || text.startsWith("&") || text.startsWith("!") ||
            text.startsWith("|") || text.startsWith(">") || text.startsWith("%") ||
            text.startsWith("@") || text.startsWith("`") || text.startsWith("[") ||
            text.startsWith("{") || text.startsWith("#") || text.startsWith("\"") ||
            text.startsWith("'") || text.contains(": ") || text.endsWith(":") ||
            text.contains(" #") || text.contains('\n')
        return if (needsQuote) "'" + text.replace("'", "''") + "'" else text
    }

    private companion object {
        val RESERVED = setOf(
            "true", "false", "null", "~", "yes", "no", "on", "off", "y", "n"
        )

        val MAPPING = Regex("^(\\s*)([^#\\s][^:]*?)\\s*:(\\s.*|)$")

        fun indentOf(line: String): Int = line.takeWhile { it == ' ' }.length

        fun parseMappingLine(line: String): Pair<Int, String>? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) return null
            val match = MAPPING.matchEntire(line) ?: return null
            val key = match.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
            if (key.isEmpty()) return null
            return match.groupValues[1].length to key
        }
    }
}
