package com.itsaky.androidide.formatprovider

/**
 * A rule-based, simplified formatter for SQL.
 * It adds newlines before major keywords to improve readability.
 */
class SqlFormatter : CodeFormatter {

    override fun format(source: String): String {
        return try {
            val keywords = setOf(
                "SELECT", "FROM", "WHERE", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "OUTER JOIN",
                "ON", "GROUP BY", "ORDER BY", "LIMIT", "INSERT INTO", "VALUES",
                "UPDATE", "SET", "DELETE FROM"
            )
            
            var result = source.replace(Regex("\\s+"), " ")
            
            keywords.forEach { keyword ->
                result = result.replace(" $keyword ", "\n$keyword ", ignoreCase = true)
            }
            
            result.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            source
        }
    }
}