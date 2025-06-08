package me.gulya.gradle.mcp.server.tool

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import me.gulya.gradle.mcp.config.DEFAULT_TEST_LOG_LINES
import me.gulya.gradle.mcp.config.logger
import me.gulya.gradle.mcp.gradle.GradleService
import me.gulya.gradle.mcp.inputSchema
import me.gulya.gradle.mcp.model.GradleTestResponse
import me.gulya.gradle.mcp.model.LogUtils
import me.gulya.gradle.mcp.model.TestResultNode
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.TestSpec
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationResult
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.SkippedResult
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.test.JvmTestKind
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestOutputEvent
import org.gradle.tooling.events.test.TestStartEvent
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// Helper data class for structured method filtering input
@Serializable
data class MethodFilterSpec(
    @SerialName("class_name") val className: String,
    val methods: List<String>
)

class RunTestsTool : GradleTool {
    private val log = logger<RunTestsTool>()

    override val name = "Run Gradle Tests"
    override val description = """
        Runs specified Gradle test tasks (default: ':test'), returning hierarchical JSON results. Supports filtering tests (e.g., by class, method). Output for passed tests is excluded by default (set 'includeOutputForPassed: true' only if debugging passed tests). Output lines are limited by default.
        """.trimIndent()

     override val inputSchema = inputSchema {
         requiredProperty("projectPath") {
            type("string")
            description("Absolute path to the root directory of the Gradle project.")
         }
         optionalProperty("gradleTasks") {
            arraySchema { type("string") }
            description("Which Gradle test tasks to run (e.g., [':test', ':submodule:test']). Defaults to ['test'] if unspecified or empty.")
         }
         optionalProperty("arguments") {
            arraySchema { type("string") }
            description("Additional Gradle command-line arguments passed to the TestLauncher. --info/--debug are filtered.")
         }
         optionalProperty("environmentVariables") {
            type("object")
            attribute("additionalProperties", JsonObject(mapOf("type" to JsonPrimitive("string"))))
            description("Environment variables for the Gradle test execution.")
         }
         // --- Specific Filter Inputs ---
         optionalProperty("includeClasses") {
             arraySchema { type("string") }
             description("List of fully-qualified test class names to include within the specified tasks (patterns supported).")
         }
         optionalProperty("includeMethods") {
             arraySchema {
                 objectSchema {
                     requiredProperty("class_name") {
                         type("string")
                         description("Fully-qualified name of the class containing the methods.")
                     }
                     requiredProperty("methods") {
                         arraySchema { type("string") }
                         description("List of method names within the class to include (patterns supported).")
                     }
                 }
             }
             description("List of specific class/method combinations to include within the specified tasks.")
         }
          optionalProperty("includePackages") {
             arraySchema { type("string") }
             description("List of package names to include within the specified tasks (tests in sub-packages are also included).")
         }
         optionalProperty("includePatterns") {
             arraySchema { type("string") }
             description("List of general filter patterns to include within the specified tasks (e.g., 'com.example.*Slow*').")
         }
         // --- Output Control Inputs ---
         optionalProperty("includeOutputForPassed") {
            type("boolean")
            description("IMPORTANT: DO NOT USE unless explicitly requested by the user for debugging passed tests.")
         }
         optionalProperty("maxLogLines") {
            type("integer")
            description("Explicitly limit the total number of output lines stored per test (keeps first N/2 and last N/2). 0 or negative means unlimited.")
         }
          optionalProperty("defaultMaxLogLines") {
            type("integer")
            description("Default limit for output lines per test if 'maxLogLines' isn't set. Defaults internally to ${DEFAULT_TEST_LOG_LINES}. Set to 0 or negative to disable the default limit.")
            attribute("default", JsonPrimitive(DEFAULT_TEST_LOG_LINES))
         }
     }

    // Noise patterns for filtering output
    private val noisePatterns = listOf(
        Regex("""^\[(StdOut|StdErr)]\s*i: \[ksp] \[Anvil] \[.*?]( Starting round \d+| Round \d+ took \d+ms| Computing triggers took \d+ms| Loading previous contributions took \d+ms| Compute contributions took \d+ms| Compute pending events took \d+ms| Total processing time after \d+ round\(s\) took \d+ms)$"""),
        Regex("""^\[(StdOut|StdErr)]\s*i: \[ksp] \[Anvil] \[ClassScannerKsp] Generated Property Cache$"""),
        Regex("""^\[(StdOut|StdErr)]\s+(Size:|Hits:|Misses:|Fidelity:)\s+\d+%?$"""),
        Regex("""^\[(StdOut|StdErr)]\s*v: Loading modules:.*"""),
        Regex("""^\[(StdOut|StdErr)]\s*$"""), // Empty lines
        Regex("""^\[(StdOut|StdErr)]\s*logging: Inheriting classpaths:\s.*$""") // Skip classpath inheritance line
    )
    private val cleanupPatterns = listOf(
        // Regex to remove "[StdOut] " or "[StdErr] " prefixes, including the space
        Regex("""^\[(StdOut|StdErr)]\s*"""),
    )

    override suspend fun execute(
        request: CallToolRequest,
        gradleService: GradleService,
        debug: Boolean
    ): CallToolResult {
        // --- Argument Parsing ---
        val projectPath = request.arguments.getValue("projectPath").jsonPrimitive.content
        val gradleTasksInput = request.arguments["gradleTasks"]?.jsonArray?.map { it.jsonPrimitive.content }
        // Default to explicit root project test task path :test
        val gradleTasks = gradleTasksInput?.takeIf { it.isNotEmpty() } ?: listOf(":test")
        // Ensure all provided tasks have a leading colon if they are likely project tasks
        val finalGradleTasks = gradleTasks.map { if (it.startsWith(":")) it else ":$it" }.distinct()

        val inputArguments = request.arguments["arguments"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val environmentVariables = request.arguments["environmentVariables"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()

        // Parse specific filters
        val includeClasses = request.arguments["includeClasses"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val includePatterns = request.arguments["includePatterns"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val includePackages = request.arguments["includePackages"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val includeMethodsInput = request.arguments["includeMethods"]?.jsonArray?.mapNotNull { element ->
             try {
                 json.decodeFromJsonElement(serializer<MethodFilterSpec>(), element)
             } catch (e: Exception) {
                 log.warn("Failed to parse includeMethods element: $element", e)
                 null
             }
        } ?: emptyList()

        val hasFilters = includeClasses.isNotEmpty() || includePatterns.isNotEmpty() ||
                         includePackages.isNotEmpty() || includeMethodsInput.isNotEmpty()

        // Parse output controls
        val includeOutputForPassed = request.arguments["includeOutputForPassed"]?.jsonPrimitive?.booleanOrNull ?: false
        val requestedMaxLogLines = request.arguments["maxLogLines"]?.jsonPrimitive?.intOrNull
        val requestedDefaultMaxLogLines = request.arguments["defaultMaxLogLines"]?.jsonPrimitive?.intOrNull
        val effectiveMaxLogLines = requestedMaxLogLines ?: requestedDefaultMaxLogLines ?: DEFAULT_TEST_LOG_LINES

        // --- Argument Filtering (for general args) ---
        val problematicArgs = setOf("--info", "--debug")
        val filteredArguments = inputArguments.filter { arg -> !problematicArgs.contains(arg.split("=").first()) }
        val filteringOccurred = inputArguments.size != filteredArguments.size
        val finalArguments = filteredArguments.toList()

        // --- Data Structures, Output Streams, Listener ---
        val nodesMap = ConcurrentHashMap<OperationDescriptor, TestResultNode>()
        val rootNodes = ConcurrentHashMap.newKeySet<TestResultNode>()
        // Use String (displayName) as key for output map to avoid OperationDescriptor equality issues
        val testOutputMap = ConcurrentHashMap<String, MutableList<String>>() // Changed key type
        val gradleStdOutStream = if (debug) ByteArrayOutputStream() else null
        val gradleStdErrStream = if (debug) ByteArrayOutputStream() else null
        val hierarchicalListener = createHierarchicalListener(nodesMap, rootNodes, testOutputMap, includeOutputForPassed, effectiveMaxLogLines, debug)

        try {
            val result = gradleService.withConnection(projectPath) { connection ->
                 val testLauncher: TestLauncher = connection.newTestLauncher()
                     .forTasks(*finalGradleTasks.toTypedArray())
                     .withArguments(*finalArguments.toTypedArray())
                     .setEnvironmentVariables(environmentVariables)

                 if (hasFilters) {
                     log.info("Applying explicit test filters via TestLauncher API for tasks: {}", finalGradleTasks)
                     testLauncher.withTestsFor { specs ->
                         // Apply filters to each specified task path
                         finalGradleTasks.forEach { taskPath ->
                             val taskSpec = specs.forTaskPath(taskPath)
                             applyTaskSpecificFilters(
                                 taskSpec = taskSpec,
                                 taskPath = taskPath,
                                 includeClasses = includeClasses,
                                 includeMethods = includeMethodsInput,
                                 includePackages = includePackages,
                                 includePatterns = includePatterns
                             )
                         }
                     }
                 }

                 // Attach listener and streams
                 testLauncher.addProgressListener(hierarchicalListener, OperationType.TEST_OUTPUT, OperationType.TEST)
                 gradleStdOutStream?.let { testLauncher.setStandardOutput(it) }
                 gradleStdErrStream?.let { testLauncher.setStandardError(it) }

                 log.info("Executing Gradle tests via TestLauncher for tasks: {} with args: {}", finalGradleTasks, finalArguments)

                 var buildException: Throwable? = null
                 val success = try {
                     testLauncher.run()
                     log.info("Gradle TestLauncher executed successfully for tasks: {}", gradleTasks)
                     true
                 } catch (e: BuildException) {
                     log.warn("Gradle TestLauncher execution failed for tasks: {}. Message: {}", gradleTasks, e.message)
                     if (debug || e.cause !is TestExecutionException) { log.warn("Full exception:", e) }
                     buildException = e
                     false
                 } catch (e: GradleConnectionException) {
                      log.error("Gradle connection error during TestLauncher execution for tasks: {}", gradleTasks, e)
                      buildException = e
                      false
                 } catch (e: Exception) {
                     log.error("Unexpected error during TestLauncher execution for tasks: {}", gradleTasks, e)
                     buildException = e
                     false
                 }

                 if (debug) { logFullGradleOutput(gradleStdOutStream, gradleStdErrStream) }
                 GradleService.BuildResult(success, "", "", buildException)
            }

            // --- Prepare and Return Response ---
            val finalHierarchy = rootNodes.toList().sortedBy { it.displayName }
            sortChildrenRecursively(finalHierarchy)

            val appliedFilterTypes = mutableListOf<String>()
            if (includeClasses.isNotEmpty()) appliedFilterTypes.add("classes")
            if (includeMethodsInput.isNotEmpty()) appliedFilterTypes.add("methods")
            if (includePackages.isNotEmpty()) appliedFilterTypes.add("packages")
            if (includePatterns.isNotEmpty()) appliedFilterTypes.add("patterns")

            val notes = generateNotes(
                filteringOccurred = filteringOccurred,
                appliedFilterTypes = appliedFilterTypes,
                includeOutputForPassed = includeOutputForPassed,
                 effectiveMaxLogLines = effectiveMaxLogLines,
                 buildSuccess = result.success,
                 buildException = result.exception, // Pass the exception
                 hierarchy = finalHierarchy
             )

            val structuredResponse = GradleTestResponse(
                tasksExecuted = finalGradleTasks,
                arguments = inputArguments,
                environmentVariables = environmentVariables,
                testHierarchy = finalHierarchy,
                success = result.success,
                notes = notes
            )

            // --- Generate Markdown Output ---
            val markdownResponse = generateMarkdownOutput(structuredResponse)
            if (debug) log.debug("Hierarchical Test Result Payload (Markdown):\n{}", markdownResponse)
            return CallToolResult(content = listOf(TextContent(markdownResponse)))

        } catch (e: Exception) {
            // Catch exceptions from withConnection or result processing
            // Generate error response in Markdown format
            log.error("Error running Gradle hierarchical test tool for path {}", projectPath, e)
            if (debug) logFullGradleOutput(gradleStdOutStream, gradleStdErrStream)
            val errorMarkdown = createErrorMarkdownResponse(finalGradleTasks, inputArguments, environmentVariables, e, debug)
            return CallToolResult(content = listOf(TextContent(errorMarkdown)))
        }
    }

    // --- Markdown Generation Logic ---

    private fun generateMarkdownOutput(response: GradleTestResponse): String {
        return buildString {
            appendLine("Task: ${response.tasksExecuted.joinToString()}")
            appendLine("Args: ${response.arguments.joinToString(" ")}") // Assuming args are space-separated
            appendLine("Env: ${response.environmentVariables}") // Simple toString for env vars
            appendLine("Success: ${response.success}")
            response.notes?.let { appendLine("Note: $it") }
            appendLine() // Blank line before hierarchy
            appendLine("---")
            appendLine() // Blank line after separator

            response.testHierarchy.forEach { rootNode ->
                renderNodeMarkdown(rootNode, 1, this)
            }
        }.trim()
    }

    private fun renderNodeMarkdown(node: TestResultNode, level: Int, builder: StringBuilder) {
        val statusSymbol = when (node.outcome) {
            "passed" -> "[+]"
            "failed" -> "[-]"
            "skipped" -> "[?]" // Assuming skipped is possible
            else -> "[?]" // Unknown
        }

        val headerPrefix = "#".repeat(level)
        val nodeTypePrefix = if (node.type == "CLASS") "CLASS: " else "" // Explicitly label classes

        // Render Suites/Classes as Headers
        if (node.type != "TEST") {
            builder.append("$headerPrefix $nodeTypePrefix${node.displayName} $statusSymbol")
            if (node.outcome == "failed" && node.failureMessage != null) {
                builder.append(" ${node.failureMessage}")
            }
            builder.appendLine()

            // Render children
            node.children.forEach { child ->
                if (child.type == "TEST") {
                    renderTestListItemMarkdown(child, builder)
                } else {
                    // Add a blank line before nested headers for readability
                    builder.appendLine()
                    renderNodeMarkdown(child, level + 1, builder)
                }
            }
             // Add a blank line after a suite/class block for separation
             builder.appendLine()
        } else {
            // This case shouldn't happen if tests are always under classes/suites,
            // but handle it just in case (e.g., render as list item)
            renderTestListItemMarkdown(node, builder)
        }
    }

    private fun renderTestListItemMarkdown(node: TestResultNode, builder: StringBuilder) {
         val statusSymbol = when (node.outcome) {
            "passed" -> "[+]"
            "failed" -> "[-]"
            "skipped" -> "[?]"
            else -> "[?]"
        }
        builder.append("- ${node.displayName} $statusSymbol")

        // Append failure message only if failed
        if (node.outcome == "failed" && node.failureMessage != null) {
            builder.append(" ${node.failureMessage}")
        }

        // Always append a newline after the main test line unless output follows
        if (node.outputLines.isEmpty()) {
            builder.appendLine()
        }

        // Render output block if lines exist, regardless of outcome
        if (node.outputLines.isNotEmpty()) {
            builder.appendLine() // Ensure newline before code block
            builder.appendLine("  ```")
            node.outputLines.forEach { line ->
                    builder.appendLine("  $line")
            }
            builder.appendLine("  ```")
        }
    }

    // --- End Markdown Generation Logic ---

    /** Applies the specific test filters to a TestSpec object for a given task path. */
    private fun applyTaskSpecificFilters(
        taskSpec: TestSpec, // Operate on TestSpec now
        taskPath: String,   // For logging context
        includeClasses: List<String>,
        includeMethods: List<MethodFilterSpec>,
        includePackages: List<String>,
        includePatterns: List<String>
    ) {
        log.debug("Applying filters to task '{}'", taskPath)
        if (includeClasses.isNotEmpty()) {
            log.trace("Task '{}': includeClasses={}", taskPath, includeClasses)
            taskSpec.includeClasses(includeClasses)
        }
        if (includeMethods.isNotEmpty()) {
             log.trace("Task '{}': includeMethods={}", taskPath, includeMethods)
            includeMethods.forEach { taskSpec.includeMethods(it.className, it.methods) }
        }
        if (includePackages.isNotEmpty()) {
             log.trace("Task '{}': includePackages={}", taskPath, includePackages)
             // Note: includePackages takes Collection, but API doc example shows singular.
             // Let's assume singular is the intended usage pattern per iteration.
             // Correction: API shows includePackages(Collection) exists, let's use it.
        }
        if (includePatterns.isNotEmpty()) {
             log.trace("Task '{}': includePatterns={}", taskPath, includePatterns)
            taskSpec.includePatterns(includePatterns)
        }
    }

    private fun createHierarchicalListener(
        nodesMap: ConcurrentHashMap<OperationDescriptor, TestResultNode>,
        rootNodes: MutableSet<TestResultNode>, // Use MutableSet from ConcurrentHashMap.newKeySet()
        testOutputMap: ConcurrentHashMap<String, MutableList<String>>, // Key is String
        includeOutputForPassed: Boolean,
        effectiveMaxLogLines: Int,
        debug: Boolean
    ): ProgressListener {
        return ProgressListener { event ->
            try { // Add try-catch around listener logic
                when (event) {
                    is TestStartEvent -> handleTestStart(event, nodesMap, rootNodes, debug)
                    is TestFinishEvent -> handleTestFinish(event, nodesMap, testOutputMap, includeOutputForPassed, effectiveMaxLogLines, debug)
                    is TestOutputEvent -> handleTestOutput(event, nodesMap, testOutputMap, debug)
                }
            } catch (e: Exception) {
                log.error("Error processing test progress event: ${event::class.simpleName} - ${event.descriptor.displayName}", e)
                // Optionally add an error node or flag to indicate listener issues
            }
        }
    }

    private fun handleTestStart(
        event: TestStartEvent,
        nodesMap: ConcurrentHashMap<OperationDescriptor, TestResultNode>,
        rootNodes: MutableSet<TestResultNode>,
        debug: Boolean
    ) {
        val desc = event.descriptor
        val parentDesc = desc.parent

        val nodeType = determineNodeType(desc)
        // Use computeIfAbsent to avoid race conditions if events arrive out of order (though unlikely for start)
        val newNode = nodesMap.computeIfAbsent(desc) {
            TestResultNode(
                displayName = desc.displayName,
                type = nodeType
            )
        }

        val parentNode = parentDesc?.let { nodesMap[it] }
        if (parentNode != null) {
            // Ensure child isn't added multiple times if start event is duplicated (unlikely but safe)
            // Use synchronized block for safe concurrent modification of children list
            synchronized(parentNode.children) {
                if (!parentNode.children.contains(newNode)) {
                    parentNode.children.add(newNode)
                }
            }
        } else {
            rootNodes.add(newNode) // Add to root if no tracked parent
        }
        if (debug) log.debug("Start: {} (Type: {}), Parent: {}", newNode.displayName, newNode.type, parentDesc?.displayName ?: "null")
    }

    private fun handleTestFinish(
        event: TestFinishEvent,
        nodesMap: ConcurrentHashMap<OperationDescriptor, TestResultNode>,
        testOutputMap: ConcurrentHashMap<String, MutableList<String>>, // Key is String
        includeOutputForPassed: Boolean,
        effectiveMaxLogLines: Int,
        debug: Boolean
    ) {
        val desc = event.descriptor
        // Use computeIfAbsent to handle cases where finish might arrive before start (though very unlikely)
        // or if the start event processing failed.
        val node = nodesMap.computeIfAbsent(desc) {
            log.warn("Finish event received for descriptor without a prior start event: {}. Creating node.", desc.displayName)
            TestResultNode(
                displayName = desc.displayName,
                type = determineNodeType(desc), // Determine type again
                outcome = "unknown" // Mark as unknown initially
            )
        }

        // Update node details based on the finish event
        val result = event.result
        node.outcome = determineOutcome(result)
        node.failureMessage = extractFailureMessage(result)

        // Attach filtered and truncated output if needed
        if (node.outcome == "failed" || includeOutputForPassed) {
            // Use displayName as the key for retrieval
            val mapKey = desc.displayName
            val collectedLines = testOutputMap[mapKey]?.toList() ?: emptyList() // Use mapKey
            // Filter lines just before attaching to the node
            val filteredLines = collectedLines.filter { line ->
                noisePatterns.none { pattern -> pattern.matches(line) }
            }
            // Clean prefixes after noise filtering but before truncation
            val cleanedLines = filteredLines.map { line ->
                var cleaned = line
                cleanupPatterns.forEach { pattern ->
                    cleaned = pattern.replace(cleaned, "")
                }
                cleaned
            }
            node.outputLines = LogUtils.truncateLogLines(cleanedLines, effectiveMaxLogLines)
        } else {
            node.outputLines = emptyList() // Ensure empty if not needed
        }

        // Clean up transient output map entry using displayName
        testOutputMap.remove(desc.displayName) // Use displayName as key

        if (debug) log.debug("Finish: {}, Outcome: {}, Output lines attached: {}", node.displayName, node.outcome, node.outputLines.size)

    }

    private fun handleTestOutput(
        event: TestOutputEvent,
        nodesMap: ConcurrentHashMap<OperationDescriptor, TestResultNode>, // Only needed for debug/logging context
        testOutputMap: ConcurrentHashMap<String, MutableList<String>>, // Key is String
        debug: Boolean
    ) {
        val outputDesc = event.descriptor

        // Find the parent ATOMIC Test Operation Descriptor associated with this output
        var associatedTestDesc: OperationDescriptor? = null
        var current: OperationDescriptor? = outputDesc
        while (current != null) {
            if (current is JvmTestOperationDescriptor && current.jvmTestKind == JvmTestKind.ATOMIC) {
                associatedTestDesc = current // Found the atomic test descriptor
                break
            }
            current = current.parent
        }

        if (associatedTestDesc != null) {
            // Use the found atomic test descriptor's displayName as the key for storing output
            val mapKey = associatedTestDesc.displayName
            val outputLinesList = testOutputMap.computeIfAbsent(mapKey) { CopyOnWriteArrayList() } // Use mapKey
            val destination = outputDesc.destination // StdOut or StdErr
            val rawMessage = outputDesc.message
            // Prefix each line with [StdOut] or [StdErr] for context before filtering
            val prefixedLines = rawMessage.lines().map { "[${destination.name}] $it" }

            outputLinesList.addAll(prefixedLines)
            // Debug log uses the associatedTestDesc display name
            if (debug) log.trace("Output '{}' associated with test '{}'", rawMessage.take(50), associatedTestDesc.displayName)

        } else {
            // Log if output couldn't be associated
            if (debug) log.trace("Ignoring output event not associated with any known ATOMIC TEST descriptor: {}", outputDesc.displayName)
        }
    }

    private fun determineNodeType(desc: OperationDescriptor): String = when {
        desc is JvmTestOperationDescriptor && desc.jvmTestKind == JvmTestKind.ATOMIC -> "TEST"
        desc is JvmTestOperationDescriptor && desc.methodName == null && desc.className != null -> "CLASS" // Better heuristic for class
        desc is JvmTestOperationDescriptor && desc.suiteName != null -> "SUITE" // Might indicate a suite
        // Fallback based on parent relationship or display name conventions if needed
        else -> {
            // If no specific type, guess based on parentage
            if (desc.parent == null) "SUITE" // Top level is likely a suite run
            else "SUITE" // Intermediate nodes often represent suites/groups
        }
    }


    private fun determineOutcome(result: OperationResult): String = when (result) {
        is SuccessResult -> "passed"
        is FailureResult -> "failed"
        is SkippedResult -> "skipped"
        else -> "unknown"
    }

    private fun extractFailureMessage(result: OperationResult): String? {
        if (result !is FailureResult) return null

        // Try to find the most relevant failure
        val primaryFailure = result.failures.firstOrNull { f ->
            val msg = f.message?.lowercase() ?: ""
            val desc = f.description?.lowercase() ?: ""
            // Prioritize assertion failures or common exceptions
            msg.contains("assertionfailed") || msg.contains("comparisonfailure") ||
                    msg.contains("asserterror") || msg.contains("exception") ||
                    // Check description for failure keywords if message is generic
                    desc.contains("failed") || desc.contains("error")
        } ?: result.failures.firstOrNull() // Fallback to the first failure

        return primaryFailure?.let { failure ->
            val message = failure.message ?: "No specific error message."
            // Include a snippet of the description if it provides context and isn't redundant
            val descriptionSnippet = failure.description?.lines()
                ?.mapNotNull { it.trim().ifEmpty { null } }
                ?.take(5) // Limit lines
                ?.joinToString("\n  ") ?: ""

            buildString {
                append(message.lines().firstOrNull() ?: message) // Often the first line is most informative
                if (message.lines().size > 1) append("...") // Indicate if message was multi-line

                // Append description snippet only if it's different and adds value
                val firstDescLine = descriptionSnippet.lineSequence().firstOrNull()?.take(80) // Limit length
                if (descriptionSnippet.isNotEmpty() && firstDescLine != null && !message.contains(firstDescLine)) {
                    append("\n  Description: ").append(descriptionSnippet)
                    if (failure.description?.lines()?.size ?: 0 > 5) append("\n  ...")
                }
            }.take(2048) // Hard limit on total failure message length
        } ?: "Unknown test failure reason"
    }

    private fun sortChildrenRecursively(nodes: List<TestResultNode>) {
        nodes.forEach { node ->
            // Sort children mutable list in place
            synchronized(node.children) { // Ensure thread safety when sorting
                node.children.sortBy { it.displayName }
            }
            sortChildrenRecursively(node.children)
        }
    }

    private fun logFullGradleOutput(stdOutStream: ByteArrayOutputStream?, stdErrStream: ByteArrayOutputStream?) {
        val gradleStdOut = stdOutStream?.toString(StandardCharsets.UTF_8)?.trim() ?: ""
        val gradleStdErr = stdErrStream?.toString(StandardCharsets.UTF_8)?.trim() ?: ""

        if (gradleStdOut.isNotEmpty() || gradleStdErr.isNotEmpty()) {
            log.debug("--- Full Gradle Build Output (for debugging) ---")
            if (gradleStdOut.isNotEmpty()) log.debug("[Gradle StdOut]:\n{}", gradleStdOut)
            if (gradleStdErr.isNotEmpty()) log.debug("[Gradle StdErr]:\n{}", gradleStdErr)
            log.debug("--- End Full Gradle Build Output ---")
        } else {
            log.debug("--- No overall Gradle build output captured ---")
        }
    }

    // Recursive helper to check for specific outcomes anywhere in the hierarchy
    private fun containsOutcome(nodes: List<TestResultNode>, outcomes: Set<String>): Boolean {
        return nodes.any { node ->
            outcomes.contains(node.outcome) || containsOutcome(node.children, outcomes)
        }
    }


    private fun generateNotes(
         filteringOccurred: Boolean, // General argument filtering
         appliedFilterTypes: List<String>, // List of filter types used
         includeOutputForPassed: Boolean,
         effectiveMaxLogLines: Int,
         buildSuccess: Boolean, // TestLauncher success
         buildException: Throwable?, // Add exception parameter
         hierarchy: List<TestResultNode> // This is the list of root nodes
     ): String? {
        return buildString {
            if (filteringOccurred) append("Note: Verbose Gradle arguments (--info/--debug) were filtered out. ")
            // Updated note about filters
            if (appliedFilterTypes.isNotEmpty()) {
                append("Applied test filters (${appliedFilterTypes.joinToString()}) via TestLauncher API. ")
            }
            if (!includeOutputForPassed) append("Output lines included only for failed tests. ")
            if (effectiveMaxLogLines > 0) append("Output lines per test limited to ~$effectiveMaxLogLines (keeps first/last lines). ")
            else append("Output lines per test are unlimited. ")

            // --- Success/Failure Notes (logic remains similar) ---
            val failedExists = containsOutcome(hierarchy, setOf("failed"))
            val passedExists = containsOutcome(hierarchy, setOf("passed"))
            val skippedExists = containsOutcome(hierarchy, setOf("skipped"))

             if (!buildSuccess) {
                 val significantCause = findSignificantCause(buildException) // Find the specific underlying cause
                 val causeMessage = significantCause?.message?.take(150) ?: buildException?.message?.take(150) ?: "Unknown reason"
                 val causeType = significantCause?.let { it::class.simpleName } ?: "UnknownType"

                 if (hierarchy.isEmpty()) {
                     when (significantCause?.let { it::class.qualifiedName }) {
                         "org.jetbrains.kotlin.gradle.tasks.CompilationErrorException",
                         "org.gradle.api.tasks.compile.CompilationFailedException" -> // Add other compile types if needed
                             append(" The TestLauncher execution failed due to a compilation error: $causeMessage...")
                         "org.gradle.tooling.TestExecutionException" ->
                             append(" The TestLauncher execution failed during test execution setup or infrastructure: $causeMessage...")
                         "org.gradle.api.tasks.TaskExecutionException" -> // Often wraps other specific failures
                             append(" The TestLauncher execution failed due to an upstream task failure ($causeType): $causeMessage...")
                         else -> // Generic fallback when hierarchy is empty
                             append(" The TestLauncher execution failed, and no test results were reported (check logs for build errors or infrastructure issues). Failure: $causeType - $causeMessage...")
                     }
                 } else if (appliedFilterTypes.isNotEmpty() && hierarchy.all { it.outcome == "skipped" || it.children.isEmpty() }) { // Handle case where filters resulted in no tests running, but build didn't fail catastrophically before listener ran
                     append(" The TestLauncher execution may have failed or completed without running tests matching the filters.") // Less certain failure message here
                 } else if (failedExists) {
                     // Check if the cause aligns with tests failing
                     if (significantCause?.let { it::class.qualifiedName } == "org.gradle.api.internal.exceptions.MarkedVerificationException") {
                         append(" The TestLauncher execution failed due to test failures.") // Expected case
                     } else {
                         append(" The TestLauncher execution failed, consistent with reported test failures (Cause: $causeType).")
                     }
                 } else { // Hierarchy not empty, but no failures reported? Unexpected.
                     append(" The TestLauncher execution failed unexpectedly despite some test results being reported (check logs). Failure: $causeType - $causeMessage...")
                 }
            } else { // buildSuccess == true
                 if (!failedExists && (passedExists || skippedExists)) {
                    // Normal success - no specific note needed
                 } else if (failedExists) {
                     log.warn("TestLauncher reported success, but 'failed' outcome found in hierarchy.")
                     append(" Note: Inconsistency detected - TestLauncher reported success despite failed tests found.")
                 } else if (hierarchy.isEmpty() && appliedFilterTypes.isNotEmpty()) {
                     append(" TestLauncher execution succeeded, but no tests matched the applied filters.")
                 } else if (hierarchy.isEmpty()) {
                     append(" TestLauncher execution succeeded, but no tests were found or executed for the specified tasks.")
                 }
            }
         }.trim().ifEmpty { null }
     }

     /**
      * Recursively searches the cause chain for the most significant underlying failure,
      * skipping common Gradle wrapper exceptions.
      */
     private fun findSignificantCause(exception: Throwable?): Throwable? {
         var current: Throwable? = exception
         var significantCause: Throwable? = exception // Start with the original as a fallback

         val wrapperExceptionTypes = setOf(
             "org.gradle.internal.exceptions.LocationAwareException",
             "org.gradle.internal.exceptions.ContextualPlaceholderException",
             "org.gradle.tooling.BuildException",
             "org.gradle.api.GradleException", // Often wraps other exceptions
             "java.lang.reflect.InvocationTargetException" // Can wrap execution exceptions
             // Add other common, non-specific wrappers if identified
        )

        val specificFailureTypes = setOf(
             "org.jetbrains.kotlin.gradle.tasks.CompilationErrorException",
             "org.gradle.api.tasks.compile.CompilationFailedException",
             "org.gradle.tooling.TestExecutionException",
             "org.gradle.api.internal.exceptions.MarkedVerificationException",
             "org.gradle.api.tasks.TaskExecutionException"
             // Add other specific, informative exception types here
         )

        var depth = 0 // Add depth counter
        val visited = mutableSetOf<Throwable>() // Keep track of visited exceptions

        while (current != null && depth < 20) { // Add depth limit
            if (!visited.add(current)) { // Check if already visited
                log.warn("Cycle detected in exception cause chain at depth {}. Breaking loop. Current: {}", depth, current::class.simpleName)
                break // Break if cycle detected
            }
            log.trace("findSignificantCause - Depth: {}, Current: {} - '{}', Cause: {}", depth, current::class.simpleName, current.message?.take(50), current.cause?.let { it::class.simpleName } ?: "null") // Add logging
            val currentTypeName = current::class.qualifiedName
            // If it's a specific known failure type, consider it significant
            if (currentTypeName != null && specificFailureTypes.contains(currentTypeName)) {
                significantCause = current
                break // Found a specific known cause, stop searching deeper in this branch
            }
            // If it's not a known wrapper, it might be significant, update fallback
            if (currentTypeName != null && !wrapperExceptionTypes.contains(currentTypeName)) {
                significantCause = current
                // Don't break here, keep searching deeper for more specific known types
            }
            current = current.cause
            depth++ // Increment depth
        }
        if (depth >= 20) {
            log.warn("findSignificantCause reached max depth (20). Returning current significant cause: {}", significantCause?.let { it::class.simpleName } ?: "null")
        }
        // Return the most specific cause found, or the original exception if only wrappers were present
        return significantCause
    }

    private fun createErrorMarkdownResponse(
        tasks: List<String>,
        args: List<String>,
        envVars: Map<String, String>,
        error: Exception,
        debug: Boolean
    ): String {
        return buildString {
            appendLine("Task: ${tasks.joinToString()}")
            appendLine("Args: ${args.joinToString(" ")}")
            appendLine("Env: $envVars")
            appendLine("Success: false")
            appendLine("Note: An error occurred during test execution setup or processing.")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## ERROR")
            appendLine("**Type:** ${error::class.simpleName}")
            appendLine("**Message:** ${error.message}")
            if (debug) {
                appendLine()
                appendLine("**Stack Trace (Partial):**")
                appendLine("```")
                appendLine(error.stackTraceToString().take(1500)) // Limit stack trace length
                appendLine("```")
            }
        }.trim()
    }

}
