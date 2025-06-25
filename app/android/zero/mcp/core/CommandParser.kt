package app.mcp.core

data class Command(val name: String, val params: Map<String, String>)

object CommandParser {
    fun parse(raw: String): Command {
        // 例：@File:WriteFile:#path=/a.txt,#content=hello
        val parts = raw.split(":", limit = 2)
        val name = parts[0].removePrefix("@") + (if (parts.size > 1) ":" + parts[1].split("#")[0] else "")
        val paramStr = raw.substringAfter("#", "")
        val params = paramStr.split(",")
            .filter { it.contains("=") }
            .map { it.split("=", limit = 2) }
            .associate { it[0].removePrefix("#") to it[1] }
        return Command(name, params)
    }
} 