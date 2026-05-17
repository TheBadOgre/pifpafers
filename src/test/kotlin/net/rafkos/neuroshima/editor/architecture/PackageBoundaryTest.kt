package net.rafkos.neuroshima.editor.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path

private data class Rule(
    val name: String,
    val source: String,
    val forbidden: List<String>,
    val exempt: List<String> = emptyList(),
    val allowedImports: List<String> = emptyList(),
)

private const val BASE = "net.rafkos.neuroshima.editor"

private val rules: List<Rule> = emptyList()

private data class SourceFile(val path: Path, val pkg: String, val imports: List<String>)

private fun scan(root: Path): List<SourceFile> = root.toFile().walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .map { f ->
        val lines = f.readLines()
        val pkg = lines.firstOrNull { it.startsWith("package ") }
            ?.removePrefix("package ")?.trim().orEmpty()
        val imports = lines.filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").substringBefore(" as ").trim() }
        SourceFile(f.toPath(), pkg, imports)
    }.toList()

class PackageBoundaryTest {

    @TestFactory
    fun packageBoundaries(): List<DynamicTest> {
        if (rules.isEmpty()) return listOf(
            DynamicTest.dynamicTest("no rules configured (placeholder)") { assertTrue(true) }
        )
        val files = scan(Path.of("src/main/kotlin"))
        return rules.map { rule ->
            DynamicTest.dynamicTest(rule.name) {
                val violations = files
                    .filter { it.pkg.startsWith(rule.source) }
                    .filterNot { sf -> rule.exempt.any { sf.pkg.startsWith(it) } }
                    .flatMap { sf ->
                        sf.imports
                            .filter { imp -> rule.forbidden.any { imp.startsWith(it) } }
                            .filterNot { imp -> rule.allowedImports.any { imp.startsWith(it) } }
                            .map { "${sf.path}: $it" }
                    }
                assertTrue(violations.isEmpty()) {
                    "Architecture violations — ${rule.name}:\n  " +
                        violations.joinToString("\n  ")
                }
            }
        }
    }
}
