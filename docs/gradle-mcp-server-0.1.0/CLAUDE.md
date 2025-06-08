# MCP Project Guidelines

## Build Commands
- Build project: `./gradlew build`
- Run application: `./gradlew run`
- Run all tests: `./gradlew test`
- Run single test: `./gradlew test --tests "FullyQualifiedTestClassName"`
- Package with Shadow: `./gradlew shadowJar`

## Code Style
- Follow official Kotlin style guide (`kotlin.code.style=official`)
- Use 4 spaces for indentation
- Max line length: 120 characters
- Imports: Group by package and sort alphabetically
- Naming: camelCase for properties/variables/functions, PascalCase for classes
- Use explicit types for public API, infer for local variables
- Use `val` over `var` when possible
- Handle errors with appropriate exceptions
- Include KDoc comments for public functions and classes
- Prefer extension functions for utility operations