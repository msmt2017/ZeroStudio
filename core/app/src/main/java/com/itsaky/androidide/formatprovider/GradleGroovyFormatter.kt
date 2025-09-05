package com.itsaky.androidide.formatprovider

import com.itsaky.androidide.formatprovider.treesitter.TreeSitterFormatter
import com.itsaky.androidide.formatprovider.treesitter.TreeSitterQuery
import java.util.regex.Pattern

/**
 * An advanced formatter for `build.gradle` files using the Groovy DSL.
 *
 * This formatter leverages Tree-sitter for accurate, syntax-aware indentation of closures and blocks.
 * It also applies additional rule-based formatting to standardize spacing for common Gradle
 * configurations like `dependencies`, `plugins`, and property assignments, resulting in a clean
 * and highly readable build script.
 *
 * @param indentSize The number of spaces to use for each indentation level.
 */
class GradleGroovyFormatter(private val indentSize: Int = 4) : TreeSitterFormatter("groovy", TreeSitterQuery.GROOVY_GRADLE_QUERY, indentSize) {

    /**
     * Overrides the base format method to apply Gradle-specific post-processing rules
     * after the initial Tree-sitter based indentation is done.
     */
    override fun format(source: String): String {
        // 1. First, perform the primary syntax-aware indentation using Tree-sitter
        val treeSitterFormatted = super.format(source)

        // 2. Then, apply additional rule-based fine-tuning for Gradle DSL specifics
        return applyGradleSpecificRules(treeSitterFormatted)
    }

    /**
     * Applies fine-grained formatting rules specific to Gradle's Groovy DSL.
     */
    private fun applyGradleSpecificRules(source: String): String {
        val lines = source.lines()
        val result = StringBuilder()
        
        // Regex to detect dependency configurations (e.g., implementation, api, testImplementation)
        val dependencyPattern = Pattern.compile("^\\s*(testImplementation|implementation|api|kapt|annotationProcessor|compileOnly|runtimeOnly|testApi)[\\s(].*")

        for (i in lines.indices) {
            var line = lines[i]
            
            // --- Rule 1: Standardize spacing around equals signs for assignments ---
            // Example: `versionCode = 1` becomes `versionCode = 1`
            if (!line.contains("==") && line.contains("=")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2 && !parts[0].trim().endsWith(":") && !parts[0].trim().endsWith("?") ) {
                    line = "${parts[0].trim()} = ${parts[1].trim()}"
                }
            }
            
            // --- Rule 2: Ensure a blank line before top-level blocks like `android`, `dependencies` ---
            val trimmedLine = line.trim()
            if (i > 0 && (trimmedLine.startsWith("android {") || trimmedLine.startsWith("dependencies {") || trimmedLine.startsWith("buildscript {") || trimmedLine.startsWith("plugins {"))) {
                if (lines[i - 1].isNotBlank()) {
                    result.append('\n')
                }
            }

            // --- Rule 3: Add extra indentation for lines within a dependency block for better readability ---
            if (dependencyPattern.matcher(line).matches()) {
                val indent = " ".repeat(indentSize) // Add one extra level of indent
                result.append(indent).append(line.trim()).append('\n')
            } else {
                result.append(line).append('\n')
            }
        }
        
        return result.toString().trim()
    }
}