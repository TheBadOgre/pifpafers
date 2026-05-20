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

private val rules: List<Rule> = listOf(
    Rule(
        name = "R1: model is Swing/AWT-free (java.awt.geom allowed)",
        source = "$BASE.model",
        forbidden = listOf("javax.swing", "java.awt"),
        allowedImports = listOf("java.awt.geom"),
    ),
    Rule(
        name = "R2: command depends on model only",
        source = "$BASE.command",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui"),
    ),
    Rule(
        name = "R3: persistence has no Swing/AWT/ui/command (assets allowed)",
        source = "$BASE.persistence",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui", "$BASE.command"),
    ),
    Rule(
        name = "R4: assets has no Swing, no ui (java.awt.image allowed)",
        source = "$BASE.assets",
        forbidden = listOf("javax.swing", "$BASE.ui", "$BASE.command"),
        allowedImports = listOf("java.awt.image"),
    ),
    Rule(
        name = "R5: render has no Swing widgets and no ui (java.awt allowed)",
        source = "$BASE.render",
        forbidden = listOf("javax.swing", "$BASE.ui", "$BASE.command"),
        allowedImports = listOf("java.awt"),
    ),
    Rule(
        name = "R6: i18n is leaf",
        source = "$BASE.i18n",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui", "$BASE.model", "$BASE.command"),
    ),
    Rule(
        name = "R7: prefs is leaf",
        source = "$BASE.prefs",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui", "$BASE.model"),
    ),
    Rule(
        name = "R8: ui.tools may not depend on persistence",
        source = "$BASE.ui.tools",
        forbidden = listOf("$BASE.persistence"),
    ),
    Rule(
        name = "R9: publish must not depend on ui/command",
        source = "$BASE.publish",
        forbidden = listOf("javax.swing", "$BASE.ui", "$BASE.command"),
    ),
)

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
