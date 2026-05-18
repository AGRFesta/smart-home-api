---
name: github-fix-dependabot-alerts
description: "Evaluating and resolving GitHub Dependabot security alerts and vulnerabilities for a Spring Boot / Kotlin project using Gradle. Generates and proposes exact modifications to the libs.versions.toml or build.gradle.kts files. Read-only operation."
allowed-tools: [Read, Bash(uv run *)]
---

# Dependabot Vulnerability Resolution (Gradle/Kotlin Stack)

You are a Security Engineer acting as a critical filter to secure the dependencies of this Spring Boot project based on Kotlin and Gradle.

## 1. Context Acquisition (Strict)
Execute `uv run .claude/skills/github-fix-dependabot-alerts/scripts/get_dependabot_alerts.py` to get a structured JSON output of the open Dependabot vulnerabilities (Maven/Gradle ecosystem).

## 2. Limits and Anti-Definitions (STRICT CONSTRAINTS)
* **NO INSTALLATION CLI COMMANDS:** In the Gradle ecosystem, the CLI is not used to update dependencies. NEVER propose invented commands like `gradle update dependency` or `npm install`.
* **DO NOT apply changes blindly:** You must always propose the file modification (diff) in the terminal and ask for confirmation before modifying local files.
* **Package-to-Alias Mapping:** Dependabot will provide the Maven package name (e.g., `org.springframework:spring-core`). In Version Catalogs, this corresponds to an alias. You must find which `[version]` that package is mapped to.

## 3. Decision Tree
For each vulnerability in the JSON:
1. **Identify the target file:** Read the `gradle/libs.versions.toml` file (or alternatively `build.gradle.kts` if the library is not in the catalog).
2. **Find the mapping:** Search for the vulnerable `package_name` within the file (e.g., look under the `[libraries]` block).
3. **Propose the textual fix:** Show the user the exact line under the `[versions]` block that needs to be modified.
    * *Desired output example:* "To resolve the Jackson CVE, we need to update the version in `libs.versions.toml`. Change the line `jackson = "2.14.0"` to `jackson = "2.15.2"`."
4. **Ask for confirmation:** Ask the user: "Would you like me to modify the `libs.versions.toml` file for you?"
