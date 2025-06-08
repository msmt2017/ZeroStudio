# Gradle MCP Server

A Model Context Protocol (MCP) server that enables AI tools to interact with Gradle projects programmatically. It uses the [Gradle Tooling API](https://docs.gradle.org/current/userguide/tooling_api.html) to query project information and execute tasks.

## Features

Provides tools for:

-   **Inspecting Projects**: Retrieve detailed, structured information about a Gradle project, including:
    -   Build structure (root project, subprojects)
    -   Available tasks (in the root project)
    -   Build environment details (Gradle version, Java version, JVM args)
    -   Root project details (name, path, description, build script path)
    -   Allows selective querying of information categories.
-   **Executing Tasks**: Run specific Gradle tasks (e.g., `clean`, `build`, `assemble`) with custom arguments, JVM arguments, and environment variables. Returns formatted text output including stdout/stderr and status.
-   **Running Tests Hierarchically**: Execute Gradle test tasks (e.g., `test`) and receive detailed, structured results in a hierarchical JSON format (Suite -> Class -> Method). Includes:
    -   Outcome (passed, failed, skipped) for each node.
    -   Failure messages and filtered/truncated output lines (stdout/stderr) primarily for failed tests (configurable).
    -   Support for test filtering via patterns (`--tests`).
    -   Options to control output inclusion and log line limits.

## Requirements

-   JDK 21 or higher (as configured in `build.gradle.kts`)

## Getting Started

### Build

Build the application and its dependencies:

```bash
./gradlew build
```

### Package

Create a self-contained runnable JAR:

```bash
./gradlew shadowJar
```

The JAR file will be located in `build/libs/`.

### Run

The server can be run in different modes using command-line arguments passed after `--args`.

1.  **Standard I/O Mode (Default)**:
    Communicates over `stdin` and `stdout`. This is the default if no mode argument is provided.

    ```bash
    # Run directly via Gradle
    ./gradlew run

    # Run the packaged JAR
    java -jar build/libs/gradle-mcp-server-*-all.jar
    ```

2.  **Server-Sent Events (SSE) Mode**:
    Runs an HTTP server using Ktor, exposing an MCP endpoint via SSE.

    ```bash
    # Run via Gradle on default port 3001
    ./gradlew run --args="--sse"

    # Run via Gradle on a specific port (e.g., 8080)
    ./gradlew run --args="--sse 8080"

    # Run the packaged JAR on default port 3001
    java -jar build/libs/gradle-mcp-server-*-all.jar --sse

    # Run the packaged JAR on a specific port (e.g., 8080)
    java -jar build/libs/gradle-mcp-server-*-all.jar --sse 8080
    ```

    Connect MCP clients (like the Anthropic Console Inspector) to `http://localhost:<port>/sse`.

3.  **Debug Mode**:
    Enable detailed server-side logging by adding the `--debug` flag. This can be combined with other modes.

    ```bash
    # Run in stdio mode with debug logs
    ./gradlew run --args="--debug"

    # Run in SSE mode on port 3001 with debug logs
    ./gradlew run --args="--sse --debug"

    # Run the packaged JAR in SSE mode on port 8080 with debug logs
    java -jar build/libs/gradle-mcp-server-*-all.jar --sse 8080 --debug
    ```

## Configuration

The server behavior is controlled via command-line arguments:

-   `--stdio`: (Default) Use standard input/output for MCP communication.
-   `--sse [port]`: Run as an SSE server on the specified `port` (defaults to 3001 if port is omitted).
-   `--debug`: Enable verbose logging on the server console.

## Available Tools

The server exposes the following tools via the Model Context Protocol:

1.  **`Get Gradle Project Info`**
    -   **Description**: Retrieves specific details about a Gradle project, returning structured JSON. Allows requesting only necessary information categories (`buildStructure`, `tasks`, `environment`, `projectDetails`). If `requestedInfo` is omitted, all categories are fetched.
    -   **Key Inputs**:
        -   `projectPath` (string, required): Absolute path to the Gradle project root.
        -   `requestedInfo` (array of strings, optional): List of categories to retrieve (e.g., `["tasks", "environment"]`).
    -   **Output**: JSON object (`GradleProjectInfoResponse`) containing the requested data fields and potential errors.

2.  **`Execute Gradle Task`**
    -   **Description**: Executes general Gradle tasks (like `build`, `clean`). **Not recommended for running tests if detailed results are needed** (use the test tool instead). Returns formatted text output summarizing execution and including captured stdout/stderr.
    -   **Key Inputs**:
        -   `projectPath` (string, required): Absolute path to the Gradle project root.
        -   `tasks` (array of strings, required): List of task names to execute (e.g., `["clean", "assemble"]`).
        -   `arguments` (array of strings, optional): Gradle command-line arguments (e.g., `["--info", "-PmyProp=value"]`).
        -   `jvmArguments` (array of strings, optional): JVM arguments for Gradle (e.g., `["-Xmx4g"]`).
        -   `environmentVariables` (object, optional): Environment variables for the build (e.g., `{"CI": "true"}`).
    -   **Output**: Formatted text response with execution summary, final status (`Success`/`Failure`), and combined stdout/stderr.

3.  **`Run Gradle Tests`**
    -   **Description**: Executes Gradle test tasks and returns results as a structured JSON hierarchy (Suite > Class > Test). Filters/truncates output lines by default, focusing on failures. Provides options to include output for passed tests and control log limits.
    -   **Key Inputs**:
        -   `projectPath` (string, required): Absolute path to the Gradle project root.
        -   `gradleTasks` (array of strings, optional): Test tasks to run (defaults to `["test"]`).
        -   `arguments` (array of strings, optional): Additional Gradle arguments (verbose flags like `--info`/`--debug` are filtered out).
        -   `environmentVariables` (object, optional): Environment variables for the test execution.
        -   `testPatterns` (array of strings, optional): Test filter patterns passed via `--tests` (e.g., `["*.MyTestClass"]`).
        -   `includeOutputForPassed` (boolean, optional): Set to `true` to include output for passed tests (default `false`).
        -   `maxLogLines` (integer, optional): Override the default limit on output lines per test (0 for unlimited).
        -   `defaultMaxLogLines` (integer, optional): Set the default output line limit (defaults internally to 100).
    -   **Output**: JSON object (`GradleHierarchicalTestResponse`) containing execution details, overall build success status, informative notes, and the `test_hierarchy` tree. Each node includes display name, type, outcome, failure message (if any), filtered/truncated output lines, and children.

## Dependencies

-   [Gradle Tooling API](https://docs.gradle.org/current/userguide/tooling_api.html) (Version specified in `build.gradle.kts`)
-   [Anthropic MCP Kotlin SDK](https://github.com/wiremock-inc/anthropic-mcp-kotlin-sdk)
-   [Ktor](https://ktor.io/) (for SSE server mode)
-   [Logback](https://logback.qos.ch/) (for logging)
