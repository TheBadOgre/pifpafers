# Neuroshima Hex Army Editor — Plan A: Headless Core

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement everything the army editor needs except the GUI: domain model, JSON persistence, asset library (bundled + user overlay), command/history with merge, image rendering pipeline. End state: open a `.box` file, render any token to a `BufferedImage`, all unit + integration tests pass — without a single Swing widget.

**Architecture:** MVC + Command pattern. Mutable `model` package broadcasts `ModelChanged` events. `command` package wraps every mutation in a `Command` (do/undo) routed through a `CommandHistory` with a 500 ms merge window. `persistence` round-trips a single JSON `.box` file. `assets` merges a read-only bundled root with a writable user root behind a sealed `AssetPath` type. `render` walks the layer stack, applies HSB / opacity / affine via `RescaleOp`, `LookupOp`, and `AffineTransform`, and produces a `BufferedImage`. No Swing imports anywhere in this plan.

**Tech Stack:** Kotlin 2.3.10 on JDK 21, Gradle Kotlin DSL, `kotlinx-serialization-json`, `kotlinx-coroutines-core`, log4j 2.24.x, JUnit 5 Jupiter, Mockito + mockito-kotlin.

**Reference spec:** `docs/superpowers/specs/2026-05-15-neuroshima-hex-army-editor-design.md`.

**Conventions used throughout this plan:**
- Source root: `src/main/kotlin/net/rafkos/neuroshima/editor/`.
- Test root: `src/test/kotlin/net/rafkos/neuroshima/editor/`.
- Resources root: `src/main/resources/`.
- After each task, run `./gradlew test` and commit only when green.
- Commit messages: Conventional Commits (`feat:`, `test:`, `chore:`, `refactor:`).

---

## Task 1: Update Gradle build

**Files:**
- Modify: `build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Replace `build.gradle.kts` with the full build**

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    application
}

group = "net.rafkos.neuroshima.editor"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("net.rafkos.neuroshima.editor.app.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Verify `settings.gradle.kts` is unchanged**

Run: `cat settings.gradle.kts`
Expected output contains: `rootProject.name = "pifpafers"` (leave as-is — release artifact naming is Plan B's concern).

- [ ] **Step 3: Run build to verify Gradle accepts new config**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "chore: add serialization, coroutines, log4j, junit5, mockito deps"
```

---

## Task 2: Extend `.gitignore`

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Append project-specific ignores**

Append these lines to `.gitignore`:

```
# Project-specific
last_run_tmp/
output/
local_resources/log4j2-*.log
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: ignore last_run_tmp, output, log files"
```

---

## Task 3: Delete the bootstrap `Main.kt` and create the real package skeleton

**Files:**
- Delete: `src/main/kotlin/Main.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/app/Main.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/util/Logging.kt`

- [ ] **Step 1: Remove the bootstrap file**

```bash
git rm src/main/kotlin/Main.kt
```

- [ ] **Step 2: Create the logging helper**

`src/main/kotlin/net/rafkos/neuroshima/editor/util/Logging.kt`:

```kotlin
package net.rafkos.neuroshima.editor.util

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

inline fun <reified T> logger(): Logger = LogManager.getLogger(T::class.java)
```

- [ ] **Step 3: Create the new `Main.kt`**

`src/main/kotlin/net/rafkos/neuroshima/editor/app/Main.kt`:

```kotlin
package net.rafkos.neuroshima.editor.app

fun main() {
    println("Neuroshima Hex Army Editor — headless core")
}
```

- [ ] **Step 4: Verify build + run**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

Run: `./gradlew run`
Expected stdout includes: `Neuroshima Hex Army Editor — headless core`.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/kotlin
git commit -m "chore: scaffold app package, remove bootstrap Main.kt"
```

---

## Task 4: log4j2 configuration on the classpath

**Files:**
- Create: `src/main/resources/log4j2.xml`

- [ ] **Step 1: Create the log4j config**

`src/main/resources/log4j2.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout pattern="%d{HH:mm:ss.SSS} %-5level [%t] %logger{1.} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Logger name="net.rafkos.neuroshima.editor.model" level="info"/>
    <Logger name="net.rafkos.neuroshima.editor.command" level="info"/>
    <Logger name="net.rafkos.neuroshima.editor.render" level="debug"/>
    <Logger name="net.rafkos.neuroshima.editor.assets" level="debug"/>
    <Root level="info">
      <AppenderRef ref="Console"/>
    </Root>
  </Loggers>
</Configuration>
```

- [ ] **Step 2: Run app to verify no log4j warnings**

Run: `./gradlew run -q`
Expected: no `ERROR StatusLogger` lines about missing configuration.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/log4j2.xml
git commit -m "chore: add log4j2 classpath config"
```

---

## Task 5: `PackageBoundaryTest` scaffold (empty rule set)

**Files:**
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/architecture/PackageBoundaryTest.kt`

- [ ] **Step 1: Create the test class with the scanner and an empty rule list**

`src/test/kotlin/net/rafkos/neuroshima/editor/architecture/PackageBoundaryTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test (placeholder passes)**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.architecture.PackageBoundaryTest"`
Expected: PASS (1 placeholder test).

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/architecture
git commit -m "test: add PackageBoundaryTest scaffold"
```

---

## Task 6: `AssetPath` sealed type

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/model/AssetPath.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/model/AssetPathTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/model/AssetPathTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AssetPathTest {

    @Test
    fun `BundledPath formats as bundled URI`() {
        assertEquals("bundled://units/red.png", AssetPath.Bundled("units/red.png").uri)
    }

    @Test
    fun `UserPath formats as user URI`() {
        assertEquals("user://my/icon.png", AssetPath.User("my/icon.png").uri)
    }

    @Test
    fun `parse round-trips bundled URI`() {
        val p = AssetPath.parse("bundled://units/red.png")
        assertInstanceOf(AssetPath.Bundled::class.java, p)
        assertEquals("units/red.png", p.relativePath)
    }

    @Test
    fun `parse round-trips user URI`() {
        val p = AssetPath.parse("user://x/y.png")
        assertInstanceOf(AssetPath.User::class.java, p)
        assertEquals("x/y.png", p.relativePath)
    }

    @Test
    fun `parse rejects unknown scheme`() {
        assertThrows(IllegalArgumentException::class.java) {
            AssetPath.parse("file:///abs/path.png")
        }
    }

    @Test
    fun `parse rejects malformed URI`() {
        assertThrows(IllegalArgumentException::class.java) { AssetPath.parse("nope") }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.AssetPathTest"`
Expected: FAIL (unresolved reference `AssetPath`).

- [ ] **Step 3: Implement `AssetPath`**

`src/main/kotlin/net/rafkos/neuroshima/editor/model/AssetPath.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

sealed class AssetPath {
    abstract val relativePath: String
    abstract val scheme: String
    val uri: String get() = "$scheme://$relativePath"

    data class Bundled(override val relativePath: String) : AssetPath() {
        override val scheme = "bundled"
    }

    data class User(override val relativePath: String) : AssetPath() {
        override val scheme = "user"
    }

    companion object {
        fun parse(uri: String): AssetPath {
            val idx = uri.indexOf("://")
            require(idx > 0) { "Malformed AssetPath URI: $uri" }
            val scheme = uri.substring(0, idx)
            val rel = uri.substring(idx + 3)
            return when (scheme) {
                "bundled" -> Bundled(rel)
                "user" -> User(rel)
                else -> throw IllegalArgumentException("Unknown AssetPath scheme: $scheme")
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.AssetPathTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/model/AssetPath.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/model/AssetPathTest.kt
git commit -m "feat(model): add AssetPath sealed type with bundled/user variants"
```

---

## Task 7: `LayerProperties` data class

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/model/LayerProperties.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/model/LayerPropertiesTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/model/LayerPropertiesTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LayerPropertiesTest {

    @Test
    fun `default has identity values`() {
        val p = LayerProperties()
        assertEquals(0, p.offsetX)
        assertEquals(0, p.offsetY)
        assertEquals(0f, p.rotation)
        assertEquals(1f, p.scale)
        assertEquals(1f, p.opacity)
        assertEquals(0f, p.hue)
        assertEquals(1f, p.saturation)
        assertEquals(1f, p.brightness)
    }

    @Test
    fun `copy with rotation produces normalized value`() {
        val p = LayerProperties().copy(rotation = 375f).normalized()
        assertEquals(15f, p.rotation)
    }

    @Test
    fun `copy with negative rotation produces normalized value`() {
        val p = LayerProperties().copy(rotation = -45f).normalized()
        assertEquals(315f, p.rotation)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.LayerPropertiesTest"`
Expected: FAIL (unresolved `LayerProperties`).

- [ ] **Step 3: Implement `LayerProperties`**

`src/main/kotlin/net/rafkos/neuroshima/editor/model/LayerProperties.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

data class LayerProperties(
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val hue: Float = 0f,
    val saturation: Float = 1f,
    val brightness: Float = 1f,
) {
    fun normalized(): LayerProperties =
        copy(rotation = ((rotation % 360f) + 360f) % 360f)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.LayerPropertiesTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/model/LayerProperties.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/model/LayerPropertiesTest.kt
git commit -m "feat(model): add LayerProperties with normalized rotation"
```

---

## Task 8: `Layer` data class

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/model/Layer.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/model/LayerTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/model/LayerTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class LayerTest {

    @Test
    fun `layer holds id, asset path, and properties`() {
        val id = UUID.randomUUID()
        val asset = AssetPath.Bundled("a.png")
        val props = LayerProperties(offsetX = 10)
        val layer = Layer(id = id, assetPath = asset, props = props)
        assertEquals(id, layer.id)
        assertEquals(asset, layer.assetPath)
        assertEquals(10, layer.props.offsetX)
    }

    @Test
    fun `factory generates unique ids`() {
        val a = Layer.create(AssetPath.Bundled("x.png"))
        val b = Layer.create(AssetPath.Bundled("x.png"))
        assertNotEquals(a.id, b.id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.LayerTest"`
Expected: FAIL (unresolved `Layer`).

- [ ] **Step 3: Implement `Layer`**

`src/main/kotlin/net/rafkos/neuroshima/editor/model/Layer.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import java.util.UUID

data class Layer(
    val id: UUID,
    val assetPath: AssetPath,
    val props: LayerProperties = LayerProperties(),
) {
    companion object {
        fun create(assetPath: AssetPath, props: LayerProperties = LayerProperties()): Layer =
            Layer(UUID.randomUUID(), assetPath, props)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.LayerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/model/Layer.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/model/LayerTest.kt
git commit -m "feat(model): add Layer with UUID identity and factory"
```

---

## Task 9: `Token` + `TokenKind`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/model/Token.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/model/TokenTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/model/TokenTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TokenTest {

    @Test
    fun `unit token has UNIT kind`() {
        val t = Token.createUnit()
        assertEquals(TokenKind.UNIT, t.kind)
        assertEquals(0, t.layers.size)
    }

    @Test
    fun `modifier token has MODIFIER kind`() {
        val t = Token.createModifier()
        assertEquals(TokenKind.MODIFIER, t.kind)
    }

    @Test
    fun `factory generates unique ids`() {
        assertNotEquals(Token.createUnit().id, Token.createUnit().id)
    }

    @Test
    fun `addLayer appends to top`() {
        val t = Token.createUnit()
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        val l2 = Layer.create(AssetPath.Bundled("b.png"))
        t.addLayer(l1)
        t.addLayer(l2)
        assertEquals(listOf(l1, l2), t.layers.toList())
    }

    @Test
    fun `removeLayer by id removes matching layer`() {
        val t = Token.createUnit()
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(l1)
        t.removeLayer(l1.id)
        assertEquals(0, t.layers.size)
    }

    @Test
    fun `removeLayer with unknown id throws`() {
        val t = Token.createUnit()
        assertThrows(NoSuchElementException::class.java) {
            t.removeLayer(java.util.UUID.randomUUID())
        }
    }

    @Test
    fun `reorderLayer moves layer to new index`() {
        val t = Token.createUnit()
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        val l2 = Layer.create(AssetPath.Bundled("b.png"))
        val l3 = Layer.create(AssetPath.Bundled("c.png"))
        t.addLayer(l1); t.addLayer(l2); t.addLayer(l3)
        t.reorderLayer(l1.id, 2)
        assertEquals(listOf(l2, l3, l1), t.layers.toList())
    }

    @Test
    fun `updateLayerProps replaces the layer's properties`() {
        val t = Token.createUnit()
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(l)
        t.updateLayerProps(l.id, LayerProperties(offsetX = 50))
        assertEquals(50, t.layers.first().props.offsetX)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.TokenTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `Token` + `TokenKind`**

`src/main/kotlin/net/rafkos/neuroshima/editor/model/Token.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import java.util.UUID

enum class TokenKind { UNIT, MODIFIER }

class Token(
    val id: UUID,
    val kind: TokenKind,
) {
    private val _layers: MutableList<Layer> = mutableListOf()
    val layers: List<Layer> get() = _layers

    fun addLayer(layer: Layer, index: Int = _layers.size) {
        _layers.add(index, layer)
    }

    fun removeLayer(layerId: UUID) {
        val idx = _layers.indexOfFirst { it.id == layerId }
        if (idx < 0) throw NoSuchElementException("Layer $layerId not in token $id")
        _layers.removeAt(idx)
    }

    fun reorderLayer(layerId: UUID, newIndex: Int) {
        val cur = _layers.indexOfFirst { it.id == layerId }
        if (cur < 0) throw NoSuchElementException("Layer $layerId not in token $id")
        val layer = _layers.removeAt(cur)
        _layers.add(newIndex.coerceIn(0, _layers.size), layer)
    }

    fun updateLayerProps(layerId: UUID, newProps: LayerProperties) {
        val idx = _layers.indexOfFirst { it.id == layerId }
        if (idx < 0) throw NoSuchElementException("Layer $layerId not in token $id")
        _layers[idx] = _layers[idx].copy(props = newProps)
    }

    fun findLayer(layerId: UUID): Layer? = _layers.firstOrNull { it.id == layerId }

    companion object {
        fun createUnit() = Token(UUID.randomUUID(), TokenKind.UNIT)
        fun createModifier() = Token(UUID.randomUUID(), TokenKind.MODIFIER)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.TokenTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/model/Token.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/model/TokenTest.kt
git commit -m "feat(model): add Token with mutable layer list and kind"
```

---

## Task 10: `TokenBag` with `ModelListener` events

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/model/ModelEvent.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/model/TokenBag.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/model/TokenBagTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/model/TokenBagTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenBagTest {

    @Test
    fun `new bag is empty with default name`() {
        val bag = TokenBag()
        assertEquals("", bag.name)
        assertEquals(0, bag.tokens.size)
    }

    @Test
    fun `addToken appends and notifies listeners`() {
        val bag = TokenBag()
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        val t = Token.createUnit()
        bag.addToken(t)
        assertEquals(listOf(t), bag.tokens.toList())
        assertEquals(1, events.size)
        assertTrue(events.first() is ModelEvent.TokenAdded)
        assertEquals(t.id, (events.first() as ModelEvent.TokenAdded).tokenId)
    }

    @Test
    fun `removeToken notifies listeners`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        bag.removeToken(t.id)
        assertEquals(0, bag.tokens.size)
        assertTrue(events.single() is ModelEvent.TokenRemoved)
    }

    @Test
    fun `removeToken with unknown id throws`() {
        val bag = TokenBag()
        assertThrows(NoSuchElementException::class.java) {
            bag.removeToken(java.util.UUID.randomUUID())
        }
    }

    @Test
    fun `removeListener stops further notifications`() {
        val bag = TokenBag()
        val events = mutableListOf<ModelEvent>()
        val l: (ModelEvent) -> Unit = { events += it }
        bag.addListener(l)
        bag.addToken(Token.createUnit())
        bag.removeListener(l)
        bag.addToken(Token.createUnit())
        assertEquals(1, events.size)
    }

    @Test
    fun `setName fires NameChanged`() {
        val bag = TokenBag()
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        bag.name = "Army of Light"
        assertEquals(ModelEvent.NameChanged, events.single())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.TokenBagTest"`
Expected: FAIL (unresolved).

- [ ] **Step 3: Implement `ModelEvent`**

`src/main/kotlin/net/rafkos/neuroshima/editor/model/ModelEvent.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import java.util.UUID

sealed class ModelEvent {
    data object NameChanged : ModelEvent()
    data class TokenAdded(val tokenId: UUID, val index: Int) : ModelEvent()
    data class TokenRemoved(val tokenId: UUID) : ModelEvent()
    data class TokensReordered(val order: List<UUID>) : ModelEvent()
    data class LayerAdded(val tokenId: UUID, val layerId: UUID, val index: Int) : ModelEvent()
    data class LayerRemoved(val tokenId: UUID, val layerId: UUID) : ModelEvent()
    data class LayerReordered(val tokenId: UUID, val layerId: UUID, val newIndex: Int) : ModelEvent()
    data class LayerPropsChanged(val tokenId: UUID, val layerId: UUID) : ModelEvent()
}
```

- [ ] **Step 4: Implement `TokenBag`**

`src/main/kotlin/net/rafkos/neuroshima/editor/model/TokenBag.kt`:

```kotlin
package net.rafkos.neuroshima.editor.model

import java.util.UUID

class TokenBag {
    var schemaVersion: Int = 1
        internal set

    var name: String = ""
        set(value) {
            if (field != value) {
                field = value
                fire(ModelEvent.NameChanged)
            }
        }

    private val _tokens: MutableList<Token> = mutableListOf()
    val tokens: List<Token> get() = _tokens

    private val listeners: MutableList<(ModelEvent) -> Unit> = mutableListOf()

    fun addListener(l: (ModelEvent) -> Unit) { listeners += l }
    fun removeListener(l: (ModelEvent) -> Unit) { listeners -= l }

    private fun fire(event: ModelEvent) {
        for (l in listeners.toList()) l(event)
    }

    fun addToken(token: Token, index: Int = _tokens.size) {
        _tokens.add(index, token)
        fire(ModelEvent.TokenAdded(token.id, index))
    }

    fun removeToken(tokenId: UUID) {
        val idx = _tokens.indexOfFirst { it.id == tokenId }
        if (idx < 0) throw NoSuchElementException("Token $tokenId not in bag")
        _tokens.removeAt(idx)
        fire(ModelEvent.TokenRemoved(tokenId))
    }

    fun findToken(tokenId: UUID): Token? = _tokens.firstOrNull { it.id == tokenId }

    /** Layer-level mutators route through the bag so listeners can subscribe centrally. */
    fun addLayer(tokenId: UUID, layer: Layer, index: Int? = null) {
        val token = requireToken(tokenId)
        val effective = index ?: token.layers.size
        token.addLayer(layer, effective)
        fire(ModelEvent.LayerAdded(tokenId, layer.id, effective))
    }

    fun removeLayer(tokenId: UUID, layerId: UUID) {
        val token = requireToken(tokenId)
        token.removeLayer(layerId)
        fire(ModelEvent.LayerRemoved(tokenId, layerId))
    }

    fun reorderLayer(tokenId: UUID, layerId: UUID, newIndex: Int) {
        val token = requireToken(tokenId)
        token.reorderLayer(layerId, newIndex)
        fire(ModelEvent.LayerReordered(tokenId, layerId, newIndex))
    }

    fun updateLayerProps(tokenId: UUID, layerId: UUID, newProps: LayerProperties) {
        val token = requireToken(tokenId)
        token.updateLayerProps(layerId, newProps)
        fire(ModelEvent.LayerPropsChanged(tokenId, layerId))
    }

    private fun requireToken(tokenId: UUID): Token =
        findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId not in bag")
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.model.TokenBagTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/model \
        src/test/kotlin/net/rafkos/neuroshima/editor/model/TokenBagTest.kt
git commit -m "feat(model): add TokenBag with ModelEvent broadcasting"
```

---

## Task 11: `JsonBagStore` round-trip (basic)

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/persistence/BagDto.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStore.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt`

- [ ] **Step 1: Write the failing test (round-trip only)**

`src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.persistence

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JsonBagStoreTest {

    @Test
    fun `round-trip preserves name, kinds, layers, and props`(@TempDir tmp: Path) {
        val bag = TokenBag().apply { name = "My Army" }
        val unit = Token.createUnit()
        unit.addLayer(Layer.create(AssetPath.Bundled("bg/red.png")))
        unit.addLayer(
            Layer.create(
                AssetPath.User("custom/icon.png"),
                LayerProperties(offsetX = 5, rotation = 90f, scale = 0.5f, opacity = 0.75f),
            )
        )
        bag.addToken(unit)
        bag.addToken(Token.createModifier())

        val file = tmp.resolve("army.box")
        // resolver returns true for any path in this test (no asset validation here)
        val store = JsonBagStore(assetResolver = { true })
        store.save(bag, file)
        val loaded = store.load(file)

        assertEquals(bag.name, loaded.name)
        assertEquals(bag.tokens.size, loaded.tokens.size)
        assertEquals(TokenKind.UNIT, loaded.tokens[0].kind)
        assertEquals(TokenKind.MODIFIER, loaded.tokens[1].kind)
        assertEquals(2, loaded.tokens[0].layers.size)
        assertEquals(AssetPath.Bundled("bg/red.png"), loaded.tokens[0].layers[0].assetPath)
        assertEquals(AssetPath.User("custom/icon.png"), loaded.tokens[0].layers[1].assetPath)
        assertEquals(5, loaded.tokens[0].layers[1].props.offsetX)
        assertEquals(90f, loaded.tokens[0].layers[1].props.rotation)
        assertEquals(0.5f, loaded.tokens[0].layers[1].props.scale)
        assertEquals(0.75f, loaded.tokens[0].layers[1].props.opacity)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.persistence.JsonBagStoreTest"`
Expected: FAIL (unresolved `JsonBagStore`).

- [ ] **Step 3: Implement DTOs**

`src/main/kotlin/net/rafkos/neuroshima/editor/persistence/BagDto.kt`:

```kotlin
package net.rafkos.neuroshima.editor.persistence

import kotlinx.serialization.Serializable

@Serializable
internal data class BagDto(
    val schemaVersion: Int,
    val name: String,
    val tokens: List<TokenDto>,
)

@Serializable
internal data class TokenDto(
    val id: String,
    val kind: String,
    val layers: List<LayerDto>,
)

@Serializable
internal data class LayerDto(
    val id: String,
    val asset: String,
    val props: PropsDto,
)

@Serializable
internal data class PropsDto(
    val offsetX: Int,
    val offsetY: Int,
    val rotation: Float,
    val scale: Float,
    val opacity: Float,
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
)
```

- [ ] **Step 4: Implement `JsonBagStore`**

`src/main/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStore.kt`:

```kotlin
package net.rafkos.neuroshima.editor.persistence

import kotlinx.serialization.json.Json
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

const val CURRENT_SCHEMA_VERSION = 1

class SchemaVersionException(val found: Int) :
    RuntimeException("Unsupported schema version: $found (current = $CURRENT_SCHEMA_VERSION)")

class MissingAssetsException(val missing: List<AssetPath>) :
    RuntimeException("Missing assets: ${missing.joinToString { it.uri }}")

class JsonBagStore(
    private val assetResolver: (AssetPath) -> Boolean,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    fun save(bag: TokenBag, file: Path) {
        val dto = BagDto(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            name = bag.name,
            tokens = bag.tokens.map { it.toDto() },
        )
        val text = json.encodeToString(BagDto.serializer(), dto)
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(tmp, text)
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun load(file: Path): TokenBag {
        val text = Files.readString(file)
        val dto = json.decodeFromString(BagDto.serializer(), text)
        if (dto.schemaVersion != CURRENT_SCHEMA_VERSION) throw SchemaVersionException(dto.schemaVersion)

        val missing = mutableListOf<AssetPath>()
        for (t in dto.tokens) for (l in t.layers) {
            val ap = AssetPath.parse(l.asset)
            if (!assetResolver(ap)) missing += ap
        }
        if (missing.isNotEmpty()) throw MissingAssetsException(missing)

        val bag = TokenBag().apply { name = dto.name }
        for (t in dto.tokens) bag.addToken(t.toModel())
        return bag
    }

    private fun Token.toDto(): TokenDto = TokenDto(
        id = id.toString(),
        kind = kind.name,
        layers = layers.map { it.toDto() },
    )

    private fun Layer.toDto(): LayerDto = LayerDto(
        id = id.toString(),
        asset = assetPath.uri,
        props = props.toDto(),
    )

    private fun LayerProperties.toDto(): PropsDto = PropsDto(
        offsetX, offsetY, rotation, scale, opacity, hue, saturation, brightness
    )

    private fun TokenDto.toModel(): Token {
        val t = Token(UUID.fromString(id), TokenKind.valueOf(kind))
        for (l in layers) t.addLayer(l.toModel())
        return t
    }

    private fun LayerDto.toModel(): Layer = Layer(
        id = UUID.fromString(id),
        assetPath = AssetPath.parse(asset),
        props = LayerProperties(
            offsetX = props.offsetX, offsetY = props.offsetY,
            rotation = props.rotation, scale = props.scale,
            opacity = props.opacity,
            hue = props.hue, saturation = props.saturation, brightness = props.brightness,
        ),
    )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.persistence.JsonBagStoreTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/persistence \
        src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt
git commit -m "feat(persistence): add JsonBagStore round-trip via DTOs"
```

---

## Task 12: `JsonBagStore` rejects unknown `schemaVersion`

**Files:**
- Modify: `src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt`

- [ ] **Step 1: Add a failing test for schema rejection**

Append to `JsonBagStoreTest`:

```kotlin
    @Test
    fun `load rejects unknown schema version`(@TempDir tmp: java.nio.file.Path) {
        val file = tmp.resolve("future.box")
        java.nio.file.Files.writeString(
            file,
            """{"schemaVersion": 999, "name": "x", "tokens": []}"""
        )
        val store = JsonBagStore(assetResolver = { true })
        val ex = org.junit.jupiter.api.Assertions.assertThrows(
            SchemaVersionException::class.java
        ) { store.load(file) }
        org.junit.jupiter.api.Assertions.assertEquals(999, ex.found)
    }
```

- [ ] **Step 2: Run the new test**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.persistence.JsonBagStoreTest"`
Expected: PASS (Task 11 already implemented this; this test locks the behavior in).

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt
git commit -m "test(persistence): assert SchemaVersionException on unknown version"
```

---

## Task 13: `JsonBagStore` raises `MissingAssetsException` on missing referenced assets

**Files:**
- Modify: `src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt`

- [ ] **Step 1: Add a failing test**

Append to `JsonBagStoreTest`:

```kotlin
    @Test
    fun `load reports every missing asset`(@TempDir tmp: java.nio.file.Path) {
        val bag = TokenBag()
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("missing/a.png")))
        t.addLayer(Layer.create(AssetPath.User("missing/b.png")))
        t.addLayer(Layer.create(AssetPath.Bundled("ok/c.png")))
        bag.addToken(t)
        val file = tmp.resolve("army.box")
        JsonBagStore(assetResolver = { true }).save(bag, file)

        val strictResolver: (AssetPath) -> Boolean = { ap -> ap.relativePath == "ok/c.png" }
        val store = JsonBagStore(assetResolver = strictResolver)
        val ex = org.junit.jupiter.api.Assertions.assertThrows(
            MissingAssetsException::class.java
        ) { store.load(file) }
        org.junit.jupiter.api.Assertions.assertEquals(
            listOf("bundled://missing/a.png", "user://missing/b.png"),
            ex.missing.map { it.uri },
        )
    }
```

- [ ] **Step 2: Run the new test**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.persistence.JsonBagStoreTest"`
Expected: PASS (Task 11 already wired this in).

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt
git commit -m "test(persistence): assert MissingAssetsException aggregates all paths"
```

---

## Task 14: `JsonBagStore` save is atomic (no half-written file on crash)

**Files:**
- Modify: `src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt`

- [ ] **Step 1: Add a failing test**

Append to `JsonBagStoreTest`:

```kotlin
    @Test
    fun `save writes via temp file then rename`(@TempDir tmp: java.nio.file.Path) {
        val file = tmp.resolve("army.box")
        val store = JsonBagStore(assetResolver = { true })
        store.save(TokenBag().apply { name = "n" }, file)
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(file))
        org.junit.jupiter.api.Assertions.assertFalse(
            java.nio.file.Files.exists(file.resolveSibling("army.box.tmp"))
        )
    }
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.persistence.JsonBagStoreTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/persistence/JsonBagStoreTest.kt
git commit -m "test(persistence): verify save uses temp-file rename"
```

---

## Task 15: `LocaleService` + i18n bundles

**Files:**
- Create: `src/main/resources/i18n/messages.properties`
- Create: `src/main/resources/i18n/messages_pl.properties`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/i18n/LocaleService.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/i18n/LocaleServiceTest.kt`

- [ ] **Step 1: Create the English bundle**

`src/main/resources/i18n/messages.properties`:

```
app.title=Neuroshima Hex Army Editor
error.missing.assets=Missing assets:
error.schema.version=Unsupported file version: {0}
```

- [ ] **Step 2: Create the Polish bundle**

`src/main/resources/i18n/messages_pl.properties`:

```
app.title=Edytor armii do Neuroshima Hex
error.missing.assets=Brakujace zasoby:
error.schema.version=Nieobslugiwana wersja pliku: {0}
```

(Note: ASCII to avoid encoding pitfalls in the .properties file. Real translation can add accented chars later via `\uXXXX` escapes or by switching the loader to UTF-8 — handled in the Plan B UI work.)

- [ ] **Step 3: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/i18n/LocaleServiceTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class LocaleServiceTest {

    @Test
    fun `english locale loads default bundle`() {
        val svc = LocaleService(Locale.ENGLISH)
        assertEquals("Neuroshima Hex Army Editor", svc.t("app.title"))
    }

    @Test
    fun `polish locale loads pl bundle`() {
        val svc = LocaleService(Locale("pl"))
        assertEquals("Edytor armii do Neuroshima Hex", svc.t("app.title"))
    }

    @Test
    fun `unknown locale falls back to english`() {
        val svc = LocaleService(Locale("xx"))
        assertEquals("Neuroshima Hex Army Editor", svc.t("app.title"))
    }

    @Test
    fun `format substitutes positional arguments`() {
        val svc = LocaleService(Locale.ENGLISH)
        assertEquals("Unsupported file version: 7", svc.t("error.schema.version", 7))
    }

    @Test
    fun `unknown key returns the key`() {
        val svc = LocaleService(Locale.ENGLISH)
        assertEquals("missing.key", svc.t("missing.key"))
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.i18n.LocaleServiceTest"`
Expected: FAIL.

- [ ] **Step 5: Implement `LocaleService`**

`src/main/kotlin/net/rafkos/neuroshima/editor/i18n/LocaleService.kt`:

```kotlin
package net.rafkos.neuroshima.editor.i18n

import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

class LocaleService(locale: Locale = Locale.getDefault()) {

    private val bundle: ResourceBundle = ResourceBundle.getBundle("i18n.messages", locale)

    fun t(key: String, vararg args: Any?): String = try {
        val raw = bundle.getString(key)
        if (args.isEmpty()) raw else MessageFormat.format(raw, *args)
    } catch (_: MissingResourceException) {
        key
    }
}
```

`ResourceBundle.getBundle` automatically falls back from a missing locale to the base `messages.properties`, which is exactly the test's "unknown locale falls back to English" requirement — no explicit fallback logic needed.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.i18n.LocaleServiceTest"`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/i18n \
        src/main/kotlin/net/rafkos/neuroshima/editor/i18n \
        src/test/kotlin/net/rafkos/neuroshima/editor/i18n
git commit -m "feat(i18n): add LocaleService with EN and PL bundles"
```

---

## Task 16: `ImageCache` (LRU with `SoftReference`)

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/assets/ImageCache.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/assets/ImageCacheTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/assets/ImageCacheTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ImageCacheTest {

    private fun img(): BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    @Test
    fun `put and get returns same instance`() {
        val cache = ImageCache(maxEntries = 4)
        val key = AssetPath.Bundled("a.png")
        val i = img()
        cache.put(key, i)
        assertSame(i, cache.get(key))
    }

    @Test
    fun `get on empty cache returns null`() {
        assertNull(ImageCache(2).get(AssetPath.Bundled("x.png")))
    }

    @Test
    fun `LRU evicts the least recently used`() {
        val cache = ImageCache(maxEntries = 2)
        val a = AssetPath.Bundled("a.png")
        val b = AssetPath.Bundled("b.png")
        val c = AssetPath.Bundled("c.png")
        cache.put(a, img())
        cache.put(b, img())
        cache.get(a) // a is now most-recent
        cache.put(c, img()) // should evict b
        assertNotNull(cache.get(a))
        assertNull(cache.get(b))
        assertNotNull(cache.get(c))
    }

    @Test
    fun `size reports current entry count`() {
        val cache = ImageCache(maxEntries = 4)
        assertEquals(0, cache.size())
        cache.put(AssetPath.Bundled("a.png"), img())
        cache.put(AssetPath.Bundled("b.png"), img())
        assertEquals(2, cache.size())
    }

    @Test
    fun `clear removes all entries`() {
        val cache = ImageCache(maxEntries = 4)
        cache.put(AssetPath.Bundled("a.png"), img())
        cache.clear()
        assertEquals(0, cache.size())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.assets.ImageCacheTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `ImageCache`**

`src/main/kotlin/net/rafkos/neuroshima/editor/assets/ImageCache.kt`:

```kotlin
package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference

class ImageCache(private val maxEntries: Int) {

    private val map: LinkedHashMap<AssetPath, SoftReference<BufferedImage>> =
        object : LinkedHashMap<AssetPath, SoftReference<BufferedImage>>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<AssetPath, SoftReference<BufferedImage>>
            ): Boolean = size > maxEntries
        }

    @Synchronized
    fun put(key: AssetPath, image: BufferedImage) {
        map[key] = SoftReference(image)
    }

    @Synchronized
    fun get(key: AssetPath): BufferedImage? {
        val ref = map[key] ?: return null
        val img = ref.get()
        if (img == null) map.remove(key)
        return img
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun clear() = map.clear()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.assets.ImageCacheTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/assets/ImageCache.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/assets/ImageCacheTest.kt
git commit -m "feat(assets): add LRU ImageCache with SoftReference values"
```

---

## Task 17: `AssetLibrary` — bundled + user merge with conflict rule

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/assets/AssetLibrary.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/assets/AssetTreeNode.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/assets/AssetLibraryTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/assets/AssetLibraryTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AssetLibraryTest {

    private fun touch(p: Path) {
        Files.createDirectories(p.parent)
        Files.writeString(p, "x")
    }

    @Test
    fun `lists assets from bundled root only`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        touch(bundled.resolve("units/red.png"))
        touch(bundled.resolve("modifiers/dmg.png"))
        val lib = AssetLibrary(bundledRoot = bundled, userRoot = tmp.resolve("user"))
        lib.scan()
        val all = lib.allAssets().map { it.uri }.toSet()
        assertEquals(setOf("bundled://units/red.png", "bundled://modifiers/dmg.png"), all)
    }

    @Test
    fun `merges bundled and user; bundled wins on conflict`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        touch(bundled.resolve("icons/a.png"))     // bundled-only
        touch(user.resolve("icons/b.png"))         // user-only
        touch(bundled.resolve("shared.png"))       // conflict
        touch(user.resolve("shared.png"))          // conflict
        val lib = AssetLibrary(bundled, user)
        lib.scan()
        val all = lib.allAssets().map { it.uri }.toSet()
        assertEquals(
            setOf("bundled://icons/a.png", "user://icons/b.png", "bundled://shared.png"),
            all,
        )
    }

    @Test
    fun `ignores non-png files`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        touch(bundled.resolve("ok.png"))
        touch(bundled.resolve("notes.txt"))
        val lib = AssetLibrary(bundled, tmp.resolve("user"))
        lib.scan()
        assertEquals(listOf("bundled://ok.png"), lib.allAssets().map { it.uri })
    }

    @Test
    fun `resolveFile returns concrete path for bundled and user`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        val bFile = bundled.resolve("a.png"); touch(bFile)
        val uFile = user.resolve("b.png"); touch(uFile)
        val lib = AssetLibrary(bundled, user)
        lib.scan()
        assertEquals(bFile, lib.resolveFile(AssetPath.Bundled("a.png")))
        assertEquals(uFile, lib.resolveFile(AssetPath.User("b.png")))
        assertNull(lib.resolveFile(AssetPath.Bundled("nope.png")))
    }

    @Test
    fun `tree groups assets by folder`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        touch(bundled.resolve("units/red.png"))
        touch(bundled.resolve("units/blue.png"))
        touch(bundled.resolve("modifiers/dmg.png"))
        val lib = AssetLibrary(bundled, tmp.resolve("user"))
        lib.scan()
        val root = lib.tree()
        assertEquals(setOf("modifiers", "units"), root.childFolders.map { it.name }.toSet())
        val units = root.childFolders.first { it.name == "units" }
        assertEquals(setOf("red.png", "blue.png"), units.assets.map { it.relativePath.substringAfterLast('/') }.toSet())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.assets.AssetLibraryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `AssetTreeNode`**

`src/main/kotlin/net/rafkos/neuroshima/editor/assets/AssetTreeNode.kt`:

```kotlin
package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath

class AssetTreeNode(val name: String) {
    val childFolders: MutableList<AssetTreeNode> = mutableListOf()
    val assets: MutableList<AssetPath> = mutableListOf()
}
```

- [ ] **Step 4: Implement `AssetLibrary`**

`src/main/kotlin/net/rafkos/neuroshima/editor/assets/AssetLibrary.kt`:

```kotlin
package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath
import java.nio.file.Files
import java.nio.file.Path

class AssetLibrary(
    private val bundledRoot: Path,
    private val userRoot: Path,
) {
    private val bundledAssets: MutableMap<String, Path> = mutableMapOf() // rel → file
    private val userAssets: MutableMap<String, Path> = mutableMapOf()

    fun scan() {
        bundledAssets.clear()
        userAssets.clear()
        scanRoot(bundledRoot, bundledAssets)
        scanRoot(userRoot, userAssets)
    }

    fun refreshUser() {
        userAssets.clear()
        scanRoot(userRoot, userAssets)
    }

    private fun scanRoot(root: Path, into: MutableMap<String, Path>) {
        if (!Files.isDirectory(root)) return
        Files.walk(root).use { stream ->
            for (p in stream) {
                if (!Files.isRegularFile(p)) continue
                if (!p.fileName.toString().endsWith(".png", ignoreCase = true)) continue
                val rel = root.relativize(p).toString().replace('\\', '/')
                into[rel] = p
            }
        }
    }

    fun allAssets(): List<AssetPath> {
        val out = mutableListOf<AssetPath>()
        for (rel in bundledAssets.keys) out += AssetPath.Bundled(rel)
        for (rel in userAssets.keys) {
            if (rel !in bundledAssets) out += AssetPath.User(rel)
        }
        return out
    }

    fun resolveFile(path: AssetPath): Path? = when (path) {
        is AssetPath.Bundled -> bundledAssets[path.relativePath]
        is AssetPath.User -> userAssets[path.relativePath]
    }

    fun assetExists(path: AssetPath): Boolean = resolveFile(path) != null

    fun tree(): AssetTreeNode {
        val root = AssetTreeNode("")
        for (asset in allAssets().sortedBy { it.relativePath }) insertIntoTree(root, asset)
        return root
    }

    private fun insertIntoTree(root: AssetTreeNode, asset: AssetPath) {
        val parts = asset.relativePath.split('/')
        var current = root
        for (i in 0 until parts.size - 1) {
            val folderName = parts[i]
            val child = current.childFolders.firstOrNull { it.name == folderName }
                ?: AssetTreeNode(folderName).also { current.childFolders += it }
            current = child
        }
        current.assets += asset
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.assets.AssetLibraryTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/assets/AssetLibrary.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/assets/AssetTreeNode.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/assets/AssetLibraryTest.kt
git commit -m "feat(assets): add AssetLibrary with bundled-wins merge and tree"
```

---

## Task 18: `refreshUser()` rescans only the user root

**Files:**
- Modify: `src/test/kotlin/net/rafkos/neuroshima/editor/assets/AssetLibraryTest.kt`

- [ ] **Step 1: Add a failing test**

Append to `AssetLibraryTest`:

```kotlin
    @Test
    fun `refreshUser picks up new user files but does not rescan bundled`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        touch(bundled.resolve("b.png"))
        val lib = AssetLibrary(bundled, user)
        lib.scan()
        assertEquals(listOf("bundled://b.png"), lib.allAssets().map { it.uri })

        // Add to bundled AFTER initial scan — should NOT appear after refreshUser
        touch(bundled.resolve("late_bundled.png"))
        // Add to user — SHOULD appear after refreshUser
        touch(user.resolve("u.png"))

        lib.refreshUser()
        val after = lib.allAssets().map { it.uri }.toSet()
        assertEquals(setOf("bundled://b.png", "user://u.png"), after)
    }
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.assets.AssetLibraryTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/assets/AssetLibraryTest.kt
git commit -m "test(assets): verify refreshUser scope"
```

---

## Task 19: Eager image preload off-EDT via coroutines

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/assets/ImagePreloader.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/assets/ImagePreloaderTest.kt`

- [ ] **Step 1: Add a tiny PNG fixture to test resources**

```bash
mkdir -p src/test/resources/fixtures
```

Write a 2×2 transparent PNG programmatically once via a one-off Kotlin script — OR use this trick: commit a tiny known-good PNG. To avoid binary commits, generate it in the test instead. Skip this step; the test below will create the PNG in a `@TempDir` at runtime.

- [ ] **Step 2: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/assets/ImagePreloaderTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.assets

import kotlinx.coroutines.runBlocking
import net.rafkos.neuroshima.editor.model.AssetPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class ImagePreloaderTest {

    private fun writePng(p: Path, w: Int = 2, h: Int = 2) {
        Files.createDirectories(p.parent)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
    }

    @Test
    fun `preloads all referenced assets into cache`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        writePng(bundled.resolve("a.png"))
        writePng(user.resolve("b.png"))
        val lib = AssetLibrary(bundled, user)
        lib.scan()
        val cache = ImageCache(maxEntries = 16)

        runBlocking {
            ImagePreloader(lib, cache).preload(
                listOf(AssetPath.Bundled("a.png"), AssetPath.User("b.png"))
            )
        }

        assertNotNull(cache.get(AssetPath.Bundled("a.png")))
        assertNotNull(cache.get(AssetPath.User("b.png")))
        assertEquals(2, cache.size())
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.assets.ImagePreloaderTest"`
Expected: FAIL.

- [ ] **Step 4: Implement `ImagePreloader`**

`src/main/kotlin/net/rafkos/neuroshima/editor/assets/ImagePreloader.kt`:

```kotlin
package net.rafkos.neuroshima.editor.assets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.rafkos.neuroshima.editor.model.AssetPath
import javax.imageio.ImageIO

class ImagePreloader(
    private val library: AssetLibrary,
    private val cache: ImageCache,
) {
    suspend fun preload(paths: Collection<AssetPath>) = coroutineScope {
        paths.distinct().map { ap ->
            async(Dispatchers.IO) {
                val file = library.resolveFile(ap) ?: return@async
                val img = file.toFile().inputStream().use { ImageIO.read(it) }
                if (img != null) cache.put(ap, img)
            }
        }.awaitAll()
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.assets.ImagePreloaderTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/assets/ImagePreloader.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/assets/ImagePreloaderTest.kt
git commit -m "feat(assets): preload referenced images off-EDT via coroutines"
```

---

## Task 20: `Command` interface + `CommandHistory` (do / undo / redo)

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/Command.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/CommandHistory.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/CommandHistoryTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/CommandHistoryTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class TrackingCommand(
    override val label: String,
    val onDo: () -> Unit,
    val onUndo: () -> Unit,
) : Command {
    override fun execute(bag: TokenBag) { onDo() }
    override fun undo(bag: TokenBag) { onUndo() }
    override fun mergeWith(next: Command): Command? = null
}

class CommandHistoryTest {

    @Test
    fun `execute pushes to done stack and runs command`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var did = 0
        history.execute(bag, TrackingCommand("x", { did++ }, { }))
        assertEquals(1, did)
        assertTrue(history.canUndo())
        assertFalse(history.canRedo())
    }

    @Test
    fun `undo runs command undo and moves to undone stack`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var undid = 0
        history.execute(bag, TrackingCommand("x", { }, { undid++ }))
        history.undo(bag)
        assertEquals(1, undid)
        assertFalse(history.canUndo())
        assertTrue(history.canRedo())
    }

    @Test
    fun `redo re-executes`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var redid = 0
        history.execute(bag, TrackingCommand("x", { redid++ }, { }))
        history.undo(bag)
        history.redo(bag)
        assertEquals(2, redid)
        assertTrue(history.canUndo())
        assertFalse(history.canRedo())
    }

    @Test
    fun `new execute clears undone stack`() {
        val bag = TokenBag()
        val history = CommandHistory()
        history.execute(bag, TrackingCommand("a", { }, { }))
        history.undo(bag)
        history.execute(bag, TrackingCommand("b", { }, { }))
        assertFalse(history.canRedo())
    }

    @Test
    fun `clear empties both stacks`() {
        val bag = TokenBag()
        val history = CommandHistory()
        history.execute(bag, TrackingCommand("a", { }, { }))
        history.undo(bag)
        history.clear()
        assertFalse(history.canUndo())
        assertFalse(history.canRedo())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.CommandHistoryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `Command`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/Command.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag

interface Command {
    val label: String
    fun execute(bag: TokenBag)
    fun undo(bag: TokenBag)
    fun mergeWith(next: Command): Command?
}
```

- [ ] **Step 4: Implement `CommandHistory`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/CommandHistory.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag

class CommandHistory(
    private val mergeWindowMs: Long = 500L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val command: Command, val timestampMs: Long)

    private val done: ArrayDeque<Entry> = ArrayDeque()
    private val undone: ArrayDeque<Entry> = ArrayDeque()

    fun execute(bag: TokenBag, command: Command) {
        command.execute(bag)
        val now = clock()
        val top = done.lastOrNull()
        if (top != null && now - top.timestampMs < mergeWindowMs) {
            val merged = top.command.mergeWith(command)
            if (merged != null) {
                done.removeLast()
                done.addLast(Entry(merged, now))
                undone.clear()
                return
            }
        }
        done.addLast(Entry(command, now))
        undone.clear()
    }

    fun undo(bag: TokenBag) {
        val e = done.removeLastOrNull() ?: return
        e.command.undo(bag)
        undone.addLast(e)
    }

    fun redo(bag: TokenBag) {
        val e = undone.removeLastOrNull() ?: return
        e.command.execute(bag)
        done.addLast(e)
    }

    fun canUndo(): Boolean = done.isNotEmpty()
    fun canRedo(): Boolean = undone.isNotEmpty()

    fun clear() {
        done.clear()
        undone.clear()
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.CommandHistoryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command \
        src/test/kotlin/net/rafkos/neuroshima/editor/command
git commit -m "feat(command): add Command interface and CommandHistory"
```

---

## Task 21: `CommandHistory` merge window

**Files:**
- Modify: `src/test/kotlin/net/rafkos/neuroshima/editor/command/CommandHistoryTest.kt`

- [ ] **Step 1: Add a failing test that exercises merge**

Append to `CommandHistoryTest`:

```kotlin
    private class Mergeable(
        override val label: String,
        val tag: String,
        var value: Int,
    ) : Command {
        override fun execute(bag: TokenBag) {}
        override fun undo(bag: TokenBag) {}
        override fun mergeWith(next: Command): Command? =
            if (next is Mergeable && next.tag == tag) Mergeable(label, tag, next.value) else null
    }

    @Test
    fun `consecutive mergeable commands within window collapse into one entry`() {
        val bag = TokenBag()
        var t = 0L
        val history = CommandHistory(mergeWindowMs = 500L, clock = { t })
        history.execute(bag, Mergeable("set", "opacity", 50))
        t = 100L
        history.execute(bag, Mergeable("set", "opacity", 60))
        t = 200L
        history.execute(bag, Mergeable("set", "opacity", 70))

        // 3 executes -> 1 entry
        // 1 undo should empty the stack
        history.undo(bag)
        assertFalse(history.canUndo())
    }

    @Test
    fun `commands outside merge window do not collapse`() {
        val bag = TokenBag()
        var t = 0L
        val history = CommandHistory(mergeWindowMs = 500L, clock = { t })
        history.execute(bag, Mergeable("set", "opacity", 50))
        t = 1_000L
        history.execute(bag, Mergeable("set", "opacity", 60))
        history.undo(bag)
        assertTrue(history.canUndo())
    }

    @Test
    fun `non-mergeable next does not collapse`() {
        val bag = TokenBag()
        val history = CommandHistory()
        history.execute(bag, Mergeable("set", "opacity", 50))
        history.execute(bag, TrackingCommand("other", { }, { }))
        history.undo(bag)
        assertTrue(history.canUndo())
    }
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.CommandHistoryTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/command/CommandHistoryTest.kt
git commit -m "test(command): assert merge-window collapses consecutive same-prop edits"
```

---

## Task 22: `AddTokenCommand`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/AddTokenCommand.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/AddTokenCommandTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/AddTokenCommandTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AddTokenCommandTest {

    @Test
    fun `executes adds a unit token at given index`() {
        val bag = TokenBag()
        val cmd = AddTokenCommand(TokenKind.UNIT, atIndex = 0)
        cmd.execute(bag)
        assertEquals(1, bag.tokens.size)
        assertEquals(TokenKind.UNIT, bag.tokens.first().kind)
    }

    @Test
    fun `undo removes the same token`() {
        val bag = TokenBag()
        val cmd = AddTokenCommand(TokenKind.MODIFIER, atIndex = 0)
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(0, bag.tokens.size)
    }

    @Test
    fun `redo re-adds with same id`() {
        val bag = TokenBag()
        val cmd = AddTokenCommand(TokenKind.UNIT, atIndex = 0)
        cmd.execute(bag)
        val id = bag.tokens.first().id
        cmd.undo(bag)
        cmd.execute(bag)
        assertEquals(id, bag.tokens.first().id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.AddTokenCommandTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `AddTokenCommand`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/AddTokenCommand.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import java.util.UUID

class AddTokenCommand(
    private val kind: TokenKind,
    private val atIndex: Int,
) : Command {
    override val label: String = "Add ${kind.name.lowercase()} token"
    private var createdId: UUID? = null

    override fun execute(bag: TokenBag) {
        val token = when (kind) {
            TokenKind.UNIT -> Token.createUnit()
            TokenKind.MODIFIER -> Token.createModifier()
        }.let { existing ->
            createdId?.let { Token(it, kind) } ?: existing
        }
        createdId = token.id
        bag.addToken(token, atIndex.coerceIn(0, bag.tokens.size))
    }

    override fun undo(bag: TokenBag) {
        val id = createdId ?: return
        bag.removeToken(id)
    }

    override fun mergeWith(next: Command): Command? = null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.AddTokenCommandTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command/AddTokenCommand.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/command/AddTokenCommandTest.kt
git commit -m "feat(command): add AddTokenCommand"
```

---

## Task 23: `RemoveTokenCommand`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/RemoveTokenCommand.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/RemoveTokenCommandTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/RemoveTokenCommandTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoveTokenCommandTest {

    @Test
    fun `executes removes token at known id`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("a.png")))
        bag.addToken(t)
        val cmd = RemoveTokenCommand(t.id)
        cmd.execute(bag)
        assertEquals(0, bag.tokens.size)
    }

    @Test
    fun `undo restores token at original index with original layers`() {
        val bag = TokenBag()
        val before = Token.createUnit()
        before.addLayer(Layer.create(AssetPath.Bundled("a.png")))
        bag.addToken(Token.createUnit())
        bag.addToken(before)
        bag.addToken(Token.createUnit())
        val cmd = RemoveTokenCommand(before.id)
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(before.id, bag.tokens[1].id)
        assertEquals(1, bag.tokens[1].layers.size)
        assertEquals(AssetPath.Bundled("a.png"), bag.tokens[1].layers.first().assetPath)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.RemoveTokenCommandTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `RemoveTokenCommand`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/RemoveTokenCommand.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class RemoveTokenCommand(
    private val tokenId: UUID,
) : Command {
    override val label: String = "Remove token"
    private var snapshotKind: net.rafkos.neuroshima.editor.model.TokenKind? = null
    private var snapshotLayers: List<Layer> = emptyList()
    private var snapshotIndex: Int = -1

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        snapshotKind = token.kind
        snapshotLayers = token.layers.toList()
        snapshotIndex = bag.tokens.indexOfFirst { it.id == tokenId }
        bag.removeToken(tokenId)
    }

    override fun undo(bag: TokenBag) {
        val kind = snapshotKind ?: return
        val restored = Token(tokenId, kind)
        for (l in snapshotLayers) restored.addLayer(l)
        bag.addToken(restored, snapshotIndex.coerceIn(0, bag.tokens.size))
    }

    override fun mergeWith(next: Command): Command? = null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.RemoveTokenCommandTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command/RemoveTokenCommand.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/command/RemoveTokenCommandTest.kt
git commit -m "feat(command): add RemoveTokenCommand with restore snapshot"
```

---

## Task 24: `AddLayerCommand`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/AddLayerCommand.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/AddLayerCommandTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/AddLayerCommandTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AddLayerCommandTest {

    @Test
    fun `execute adds layer on top by default`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        assertEquals(1, t.layers.size)
        assertEquals(AssetPath.Bundled("x.png"), t.layers.first().assetPath)
    }

    @Test
    fun `undo removes the same layer`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(0, t.layers.size)
    }

    @Test
    fun `redo reuses same layer id`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        val originalId = t.layers.first().id
        cmd.undo(bag)
        cmd.execute(bag)
        assertEquals(originalId, t.layers.first().id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.AddLayerCommandTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `AddLayerCommand`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/AddLayerCommand.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class AddLayerCommand(
    private val tokenId: UUID,
    private val assetPath: AssetPath,
    private val props: LayerProperties = LayerProperties(),
    private val atIndex: Int? = null,
) : Command {
    override val label: String = "Add layer"
    private var layerId: UUID? = null

    override fun execute(bag: TokenBag) {
        val id = layerId ?: UUID.randomUUID().also { layerId = it }
        val layer = Layer(id = id, assetPath = assetPath, props = props)
        bag.addLayer(tokenId, layer, atIndex)
    }

    override fun undo(bag: TokenBag) {
        val id = layerId ?: return
        bag.removeLayer(tokenId, id)
    }

    override fun mergeWith(next: Command): Command? = null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.AddLayerCommandTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command/AddLayerCommand.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/command/AddLayerCommandTest.kt
git commit -m "feat(command): add AddLayerCommand"
```

---

## Task 25: `RemoveLayerCommand`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/RemoveLayerCommand.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/RemoveLayerCommandTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/RemoveLayerCommandTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoveLayerCommandTest {

    @Test
    fun `execute removes layer; undo restores at original index with same props`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val a = Layer.create(AssetPath.Bundled("a.png"))
        val b = Layer.create(AssetPath.Bundled("b.png"), LayerProperties(offsetX = 7))
        val c = Layer.create(AssetPath.Bundled("c.png"))
        t.addLayer(a); t.addLayer(b); t.addLayer(c)

        val cmd = RemoveLayerCommand(t.id, b.id)
        cmd.execute(bag)
        assertEquals(listOf(a.id, c.id), t.layers.map { it.id })

        cmd.undo(bag)
        assertEquals(listOf(a.id, b.id, c.id), t.layers.map { it.id })
        assertEquals(7, t.layers[1].props.offsetX)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.RemoveLayerCommandTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `RemoveLayerCommand`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/RemoveLayerCommand.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class RemoveLayerCommand(
    private val tokenId: UUID,
    private val layerId: UUID,
) : Command {
    override val label: String = "Remove layer"
    private var snapshot: Layer? = null
    private var snapshotIndex: Int = -1

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        snapshotIndex = token.layers.indexOfFirst { it.id == layerId }
        if (snapshotIndex < 0) throw NoSuchElementException("Layer $layerId")
        snapshot = token.layers[snapshotIndex]
        bag.removeLayer(tokenId, layerId)
    }

    override fun undo(bag: TokenBag) {
        val s = snapshot ?: return
        bag.addLayer(tokenId, s, snapshotIndex)
    }

    override fun mergeWith(next: Command): Command? = null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.RemoveLayerCommandTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command/RemoveLayerCommand.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/command/RemoveLayerCommandTest.kt
git commit -m "feat(command): add RemoveLayerCommand"
```

---

## Task 26: `DuplicateLayerCommand`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/DuplicateLayerCommand.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/DuplicateLayerCommandTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/DuplicateLayerCommandTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DuplicateLayerCommandTest {

    @Test
    fun `execute inserts a copy directly above the source with new id and same props`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val src = Layer.create(AssetPath.Bundled("a.png"), LayerProperties(rotation = 90f))
        t.addLayer(src)

        val cmd = DuplicateLayerCommand(t.id, src.id)
        cmd.execute(bag)
        assertEquals(2, t.layers.size)
        assertEquals(src.id, t.layers[0].id)
        assertNotEquals(src.id, t.layers[1].id)
        assertEquals(90f, t.layers[1].props.rotation)
        assertEquals(AssetPath.Bundled("a.png"), t.layers[1].assetPath)
    }

    @Test
    fun `undo removes the duplicate`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val src = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(src)
        val cmd = DuplicateLayerCommand(t.id, src.id)
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(1, t.layers.size)
        assertEquals(src.id, t.layers.first().id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.DuplicateLayerCommandTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `DuplicateLayerCommand`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/DuplicateLayerCommand.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class DuplicateLayerCommand(
    private val tokenId: UUID,
    private val sourceLayerId: UUID,
) : Command {
    override val label: String = "Duplicate layer"
    private var newLayerId: UUID? = null

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        val srcIdx = token.layers.indexOfFirst { it.id == sourceLayerId }
        if (srcIdx < 0) throw NoSuchElementException("Layer $sourceLayerId")
        val src = token.layers[srcIdx]
        val id = newLayerId ?: UUID.randomUUID().also { newLayerId = it }
        val copy = Layer(id = id, assetPath = src.assetPath, props = src.props)
        bag.addLayer(tokenId, copy, srcIdx + 1)
    }

    override fun undo(bag: TokenBag) {
        val id = newLayerId ?: return
        bag.removeLayer(tokenId, id)
    }

    override fun mergeWith(next: Command): Command? = null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.DuplicateLayerCommandTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command/DuplicateLayerCommand.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/command/DuplicateLayerCommandTest.kt
git commit -m "feat(command): add DuplicateLayerCommand"
```

---

## Task 27: `ReorderLayerCommand`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/ReorderLayerCommand.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/ReorderLayerCommandTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/ReorderLayerCommandTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReorderLayerCommandTest {

    @Test
    fun `execute moves layer; undo restores original position`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val a = Layer.create(AssetPath.Bundled("a.png"))
        val b = Layer.create(AssetPath.Bundled("b.png"))
        val c = Layer.create(AssetPath.Bundled("c.png"))
        t.addLayer(a); t.addLayer(b); t.addLayer(c)

        val cmd = ReorderLayerCommand(t.id, a.id, newIndex = 2)
        cmd.execute(bag)
        assertEquals(listOf(b.id, c.id, a.id), t.layers.map { it.id })

        cmd.undo(bag)
        assertEquals(listOf(a.id, b.id, c.id), t.layers.map { it.id })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.ReorderLayerCommandTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `ReorderLayerCommand`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/ReorderLayerCommand.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class ReorderLayerCommand(
    private val tokenId: UUID,
    private val layerId: UUID,
    private val newIndex: Int,
) : Command {
    override val label: String = "Reorder layer"
    private var previousIndex: Int = -1

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        previousIndex = token.layers.indexOfFirst { it.id == layerId }
        if (previousIndex < 0) throw NoSuchElementException("Layer $layerId")
        bag.reorderLayer(tokenId, layerId, newIndex)
    }

    override fun undo(bag: TokenBag) {
        if (previousIndex < 0) return
        bag.reorderLayer(tokenId, layerId, previousIndex)
    }

    override fun mergeWith(next: Command): Command? = null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.ReorderLayerCommandTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command/ReorderLayerCommand.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/command/ReorderLayerCommandTest.kt
git commit -m "feat(command): add ReorderLayerCommand"
```

---

## Task 28: `SetLayerPropertyCommand` (per-prop + merge)

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/LayerProperty.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/SetLayerPropertyCommand.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/SetLayerPropertyCommandTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/SetLayerPropertyCommandTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SetLayerPropertyCommandTest {

    @Test
    fun `execute sets property; undo restores`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(l)

        val cmd = SetLayerPropertyCommand(
            tokenId = t.id, layerId = l.id,
            property = LayerProperty.OFFSET_X, oldValue = 0.0, newValue = 25.0
        )
        cmd.execute(bag)
        assertEquals(25, t.layers.first().props.offsetX)
        cmd.undo(bag)
        assertEquals(0, t.layers.first().props.offsetX)
    }

    @Test
    fun `mergeWith collapses consecutive same-prop edits on same layer`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(l)

        val first = SetLayerPropertyCommand(t.id, l.id, LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(t.id, l.id, LayerProperty.OPACITY, 0.5, 0.25)
        val merged = first.mergeWith(second)
        assertNotNull(merged)
        merged!!.execute(bag)
        assertEquals(0.25f, t.layers.first().props.opacity)
        merged.undo(bag)
        assertEquals(1f, t.layers.first().props.opacity)
    }

    @Test
    fun `mergeWith refuses different property`() {
        val first = SetLayerPropertyCommand(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(first.tokenId, first.layerId,
            LayerProperty.SCALE, 1.0, 0.5)
        assertNull(first.mergeWith(second))
    }

    @Test
    fun `mergeWith refuses different layer`() {
        val first = SetLayerPropertyCommand(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(first.tokenId, java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 0.5, 0.25)
        assertNull(first.mergeWith(second))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.SetLayerPropertyCommandTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `LayerProperty` enum**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/LayerProperty.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.LayerProperties

enum class LayerProperty {
    OFFSET_X, OFFSET_Y, ROTATION, SCALE, OPACITY, HUE, SATURATION, BRIGHTNESS;

    fun read(props: LayerProperties): Double = when (this) {
        OFFSET_X -> props.offsetX.toDouble()
        OFFSET_Y -> props.offsetY.toDouble()
        ROTATION -> props.rotation.toDouble()
        SCALE -> props.scale.toDouble()
        OPACITY -> props.opacity.toDouble()
        HUE -> props.hue.toDouble()
        SATURATION -> props.saturation.toDouble()
        BRIGHTNESS -> props.brightness.toDouble()
    }

    fun apply(props: LayerProperties, value: Double): LayerProperties = when (this) {
        OFFSET_X -> props.copy(offsetX = value.toInt())
        OFFSET_Y -> props.copy(offsetY = value.toInt())
        ROTATION -> props.copy(rotation = value.toFloat())
        SCALE -> props.copy(scale = value.toFloat())
        OPACITY -> props.copy(opacity = value.toFloat())
        HUE -> props.copy(hue = value.toFloat())
        SATURATION -> props.copy(saturation = value.toFloat())
        BRIGHTNESS -> props.copy(brightness = value.toFloat())
    }
}
```

- [ ] **Step 4: Implement `SetLayerPropertyCommand`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/SetLayerPropertyCommand.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class SetLayerPropertyCommand(
    val tokenId: UUID,
    val layerId: UUID,
    val property: LayerProperty,
    val oldValue: Double,
    val newValue: Double,
) : Command {
    override val label: String = "Set ${property.name.lowercase()}"

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        val layer = token.findLayer(layerId) ?: throw NoSuchElementException("Layer $layerId")
        bag.updateLayerProps(tokenId, layerId, property.apply(layer.props, newValue))
    }

    override fun undo(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: return
        val layer = token.findLayer(layerId) ?: return
        bag.updateLayerProps(tokenId, layerId, property.apply(layer.props, oldValue))
    }

    override fun mergeWith(next: Command): Command? {
        if (next !is SetLayerPropertyCommand) return null
        if (next.tokenId != tokenId || next.layerId != layerId || next.property != property) return null
        return SetLayerPropertyCommand(
            tokenId, layerId, property,
            oldValue = oldValue,           // keep original starting point
            newValue = next.newValue,      // collapse to final value
        )
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.SetLayerPropertyCommandTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command/LayerProperty.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/command/SetLayerPropertyCommand.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/command/SetLayerPropertyCommandTest.kt
git commit -m "feat(command): add SetLayerPropertyCommand with same-prop merge"
```

---

## Task 29: `MultiLayerPropertyCommand`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/command/MultiLayerPropertyCommand.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/command/MultiLayerPropertyCommandTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/command/MultiLayerPropertyCommandTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiLayerPropertyCommandTest {

    @Test
    fun `execute applies same value to all targets; undo restores per-target old values`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        val l2 = Layer.create(AssetPath.Bundled("b.png"))
        t.addLayer(l1); t.addLayer(l2)

        val cmd = MultiLayerPropertyCommand(
            property = LayerProperty.OPACITY,
            newValue = 0.5,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t.id, l1.id, oldValue = 1.0),
                MultiLayerPropertyCommand.Target(t.id, l2.id, oldValue = 1.0),
            ),
        )
        cmd.execute(bag)
        assertEquals(0.5f, t.layers[0].props.opacity)
        assertEquals(0.5f, t.layers[1].props.opacity)
        cmd.undo(bag)
        assertEquals(1f, t.layers[0].props.opacity)
        assertEquals(1f, t.layers[1].props.opacity)
    }

    @Test
    fun `merge collapses consecutive multi commands with identical target set and property`() {
        val t = java.util.UUID.randomUUID()
        val l1 = java.util.UUID.randomUUID()
        val l2 = java.util.UUID.randomUUID()
        val first = MultiLayerPropertyCommand(
            LayerProperty.OPACITY, newValue = 0.7,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t, l1, 1.0),
                MultiLayerPropertyCommand.Target(t, l2, 1.0),
            ),
        )
        val second = MultiLayerPropertyCommand(
            LayerProperty.OPACITY, newValue = 0.3,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t, l1, 0.7),
                MultiLayerPropertyCommand.Target(t, l2, 0.7),
            ),
        )
        val merged = first.mergeWith(second) as MultiLayerPropertyCommand
        assertEquals(0.3, merged.newValue)
        assertEquals(1.0, merged.targets[0].oldValue)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommandTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `MultiLayerPropertyCommand`**

`src/main/kotlin/net/rafkos/neuroshima/editor/command/MultiLayerPropertyCommand.kt`:

```kotlin
package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class MultiLayerPropertyCommand(
    val property: LayerProperty,
    val newValue: Double,
    val targets: List<Target>,
) : Command {
    data class Target(val tokenId: UUID, val layerId: UUID, val oldValue: Double)

    override val label: String = "Set ${property.name.lowercase()} (multi)"

    override fun execute(bag: TokenBag) {
        for (tgt in targets) {
            val token = bag.findToken(tgt.tokenId) ?: continue
            val layer = token.findLayer(tgt.layerId) ?: continue
            bag.updateLayerProps(tgt.tokenId, tgt.layerId, property.apply(layer.props, newValue))
        }
    }

    override fun undo(bag: TokenBag) {
        for (tgt in targets) {
            val token = bag.findToken(tgt.tokenId) ?: continue
            val layer = token.findLayer(tgt.layerId) ?: continue
            bag.updateLayerProps(tgt.tokenId, tgt.layerId, property.apply(layer.props, tgt.oldValue))
        }
    }

    override fun mergeWith(next: Command): Command? {
        if (next !is MultiLayerPropertyCommand) return null
        if (next.property != property) return null
        val mineKeys = targets.map { it.tokenId to it.layerId }.toSet()
        val theirKeys = next.targets.map { it.tokenId to it.layerId }.toSet()
        if (mineKeys != theirKeys) return null
        return MultiLayerPropertyCommand(
            property = property,
            newValue = next.newValue,
            targets = targets, // keep original old values
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommandTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/command/MultiLayerPropertyCommand.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/command/MultiLayerPropertyCommandTest.kt
git commit -m "feat(command): add MultiLayerPropertyCommand with merge"
```

---

## Task 30: `AffineBuilder`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/render/AffineBuilder.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/render/AffineBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/render/AffineBuilderTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.geom.Point2D

class AffineBuilderTest {

    private fun apply(transform: java.awt.geom.AffineTransform, x: Double, y: Double): Point2D {
        val out = Point2D.Double()
        transform.transform(Point2D.Double(x, y), out)
        return out
    }

    @Test
    fun `identity props place image center at canvas center`() {
        val canvasCenter = Point2D.Double(245.0, 245.0)
        val xform = AffineBuilder.build(
            props = LayerProperties(),
            canvasCenterX = canvasCenter.x,
            canvasCenterY = canvasCenter.y,
            imageWidth = 100,
            imageHeight = 100,
        )
        val mapped = apply(xform, 50.0, 50.0) // image center
        assertEquals(245.0, mapped.x, 1e-6)
        assertEquals(245.0, mapped.y, 1e-6)
    }

    @Test
    fun `offset shifts image center`() {
        val xform = AffineBuilder.build(
            props = LayerProperties(offsetX = 10, offsetY = -20),
            canvasCenterX = 0.0, canvasCenterY = 0.0,
            imageWidth = 40, imageHeight = 40,
        )
        val mapped = apply(xform, 20.0, 20.0) // image center
        assertEquals(10.0, mapped.x, 1e-6)
        assertEquals(-20.0, mapped.y, 1e-6)
    }

    @Test
    fun `scale halves image extent`() {
        val xform = AffineBuilder.build(
            props = LayerProperties(scale = 0.5f),
            canvasCenterX = 0.0, canvasCenterY = 0.0,
            imageWidth = 100, imageHeight = 100,
        )
        val mapped = apply(xform, 100.0, 100.0) // image bottom-right (offset +50, +50 in image space)
        assertEquals(25.0, mapped.x, 1e-6)
        assertEquals(25.0, mapped.y, 1e-6)
    }

    @Test
    fun `rotation 90 degrees rotates around canvas center`() {
        val xform = AffineBuilder.build(
            props = LayerProperties(rotation = 90f),
            canvasCenterX = 0.0, canvasCenterY = 0.0,
            imageWidth = 100, imageHeight = 100,
        )
        // image (100, 50) is right-middle; after 90° CW (Java is CW in screen coords) -> (0, +50)
        val mapped = apply(xform, 100.0, 50.0)
        assertEquals(0.0, mapped.x, 1e-6)
        assertEquals(50.0, mapped.y, 1e-6)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.AffineBuilderTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `AffineBuilder`**

`src/main/kotlin/net/rafkos/neuroshima/editor/render/AffineBuilder.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.geom.AffineTransform

object AffineBuilder {
    fun build(
        props: LayerProperties,
        canvasCenterX: Double,
        canvasCenterY: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): AffineTransform {
        val t = AffineTransform()
        // 1. Move to canvas center, plus user-specified offset
        t.translate(canvasCenterX + props.offsetX, canvasCenterY + props.offsetY)
        // 2. Rotate around that point
        t.rotate(Math.toRadians(props.rotation.toDouble()))
        // 3. Scale around that point
        t.scale(props.scale.toDouble(), props.scale.toDouble())
        // 4. Recenter the image (image space → its own center)
        t.translate(-imageWidth / 2.0, -imageHeight / 2.0)
        return t
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.AffineBuilderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/render/AffineBuilder.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/AffineBuilderTest.kt
git commit -m "feat(render): add AffineBuilder for layer transform"
```

---

## Task 31: `LayerRenderer` — HSB + opacity ops

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/render/LayerRenderer.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/render/LayerRendererTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/render/LayerRendererTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class LayerRendererTest {

    private fun solid(color: Color, w: Int = 4, h: Int = 4): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, w, h)
        g.dispose()
        return img
    }

    @Test
    fun `identity props returns the same image instance`() {
        val src = solid(Color.RED)
        val out = LayerRenderer.applyPixelOps(src, LayerProperties())
        assertSame(src, out)
    }

    @Test
    fun `opacity 0_5 halves alpha of fully opaque pixel`() {
        val src = solid(Color.RED)
        val out = LayerRenderer.applyPixelOps(src, LayerProperties(opacity = 0.5f))
        val argb = out.getRGB(0, 0)
        val alpha = (argb ushr 24) and 0xff
        // Allow rounding tolerance
        assert(alpha in 120..136) { "expected ~128, got $alpha" }
    }

    @Test
    fun `brightness 0 yields black RGB`() {
        val src = solid(Color.RED)
        val out = LayerRenderer.applyPixelOps(src, LayerProperties(brightness = 0f))
        val argb = out.getRGB(0, 0)
        val r = (argb ushr 16) and 0xff
        val g = (argb ushr 8) and 0xff
        val b = argb and 0xff
        assertEquals(0, r); assertEquals(0, g); assertEquals(0, b)
    }

    @Test
    fun `saturation 0 yields gray`() {
        val src = solid(Color.RED)
        val out = LayerRenderer.applyPixelOps(src, LayerProperties(saturation = 0f))
        val argb = out.getRGB(0, 0)
        val r = (argb ushr 16) and 0xff
        val g = (argb ushr 8) and 0xff
        val b = argb and 0xff
        assertEquals(r, g)
        assertEquals(g, b)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.LayerRendererTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `LayerRenderer`**

`src/main/kotlin/net/rafkos/neuroshima/editor/render/LayerRenderer.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.Color
import java.awt.image.BufferedImage

object LayerRenderer {

    /**
     * Applies HSB and opacity transforms in pixel space, returning either the
     * unchanged source (when props are identity) or a new BufferedImage.
     * Transforms (rotation/scale/offset) are applied via AffineTransform at draw time.
     */
    fun applyPixelOps(source: BufferedImage, props: LayerProperties): BufferedImage {
        val identity = props.opacity == 1f && props.hue == 0f &&
            props.saturation == 1f && props.brightness == 1f
        if (identity) return source

        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val hsb = FloatArray(3)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val argb = source.getRGB(x, y)
            val a = (argb ushr 24) and 0xff
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            Color.RGBtoHSB(r, g, b, hsb)
            val newHue = ((hsb[0] + props.hue) % 1f + 1f) % 1f
            val newSat = (hsb[1] * props.saturation).coerceIn(0f, 1f)
            val newBri = (hsb[2] * props.brightness).coerceIn(0f, 1f)
            val rgb = Color.HSBtoRGB(newHue, newSat, newBri) and 0x00ffffff
            val newAlpha = (a * props.opacity).toInt().coerceIn(0, 255)
            out.setRGB(x, y, (newAlpha shl 24) or rgb)
        }
        return out
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.LayerRendererTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/render/LayerRenderer.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/LayerRendererTest.kt
git commit -m "feat(render): apply HSB and opacity per pixel"
```

---

## Task 32: `ProcessedLayerCache` for `(assetPath, props.hash())` results

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/render/ProcessedLayerCache.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/render/ProcessedLayerCacheTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/render/ProcessedLayerCacheTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.LayerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ProcessedLayerCacheTest {

    private fun img() = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    @Test
    fun `same key returns cached instance`() {
        val cache = ProcessedLayerCache(maxEntries = 4)
        val key1 = ProcessedLayerCache.Key(AssetPath.Bundled("a.png"), LayerProperties(opacity = 0.5f))
        val v = img()
        cache.put(key1, v)
        val key2 = ProcessedLayerCache.Key(AssetPath.Bundled("a.png"), LayerProperties(opacity = 0.5f))
        assertSame(v, cache.get(key2))
    }

    @Test
    fun `different props miss`() {
        val cache = ProcessedLayerCache(maxEntries = 4)
        cache.put(
            ProcessedLayerCache.Key(AssetPath.Bundled("a.png"), LayerProperties(opacity = 0.5f)),
            img(),
        )
        val miss = cache.get(
            ProcessedLayerCache.Key(AssetPath.Bundled("a.png"), LayerProperties(opacity = 0.6f))
        )
        assertEquals(null, miss)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.ProcessedLayerCacheTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `ProcessedLayerCache`**

`src/main/kotlin/net/rafkos/neuroshima/editor/render/ProcessedLayerCache.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference

class ProcessedLayerCache(private val maxEntries: Int) {

    data class Key(val assetPath: AssetPath, val props: LayerProperties)

    private val map = object : LinkedHashMap<Key, SoftReference<BufferedImage>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Key, SoftReference<BufferedImage>>
        ): Boolean = size > maxEntries
    }

    @Synchronized
    fun put(key: Key, image: BufferedImage) {
        map[key] = SoftReference(image)
    }

    @Synchronized
    fun get(key: Key): BufferedImage? {
        val ref = map[key] ?: return null
        val img = ref.get()
        if (img == null) map.remove(key)
        return img
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.ProcessedLayerCacheTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/render/ProcessedLayerCache.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/ProcessedLayerCacheTest.kt
git commit -m "feat(render): add ProcessedLayerCache keyed by (asset, props)"
```

---

## Task 33: `TokenRenderer` — full token to `BufferedImage`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/render/TokenRenderer.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/render/TokenRendererTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/render/TokenRendererTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class TokenRendererTest {

    private fun solid(color: Color, w: Int = 100, h: Int = 100): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, w, h)
        g.dispose()
        return img
    }

    @Test
    fun `renders single-layer token at output size`() {
        val cache = ImageCache(16)
        val asset = AssetPath.Bundled("solid.png")
        cache.put(asset, solid(Color.RED))
        val token = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val renderer = TokenRenderer(cache, ProcessedLayerCache(16))
        val out = renderer.render(token, sizePx = 100)
        assertEquals(100, out.width)
        assertEquals(100, out.height)
        // Center pixel should be red, alpha > 0 (default scale = 1, image 100x100 centered)
        val argb = out.getRGB(50, 50)
        val alpha = (argb ushr 24) and 0xff
        val r = (argb ushr 16) and 0xff
        assert(alpha > 200) { "alpha=$alpha" }
        assertEquals(255, r)
    }

    @Test
    fun `renders empty token as transparent`() {
        val renderer = TokenRenderer(ImageCache(4), ProcessedLayerCache(4))
        val out = renderer.render(Token.createUnit(), sizePx = 50)
        val alpha = (out.getRGB(25, 25) ushr 24) and 0xff
        assertEquals(0, alpha)
    }

    @Test
    fun `respects layer stacking order (top wins)`() {
        val cache = ImageCache(16)
        val red = AssetPath.Bundled("red.png")
        val blue = AssetPath.Bundled("blue.png")
        cache.put(red, solid(Color.RED))
        cache.put(blue, solid(Color.BLUE))
        val token = Token.createUnit().apply {
            addLayer(Layer.create(red))
            addLayer(Layer.create(blue)) // top
        }
        val out = TokenRenderer(cache, ProcessedLayerCache(16)).render(token, 100)
        val argb = out.getRGB(50, 50)
        val r = (argb ushr 16) and 0xff
        val b = argb and 0xff
        assertEquals(0, r)
        assertEquals(255, b)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.TokenRendererTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `TokenRenderer`**

`src/main/kotlin/net/rafkos/neuroshima/editor/render/TokenRenderer.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.Token
import java.awt.RenderingHints
import java.awt.image.BufferedImage

class TokenRenderer(
    private val imageCache: ImageCache,
    private val processedCache: ProcessedLayerCache,
) {
    fun render(token: Token, sizePx: Int): BufferedImage {
        val out = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            val center = sizePx / 2.0
            for (layer in token.layers) {
                val source = imageCache.get(layer.assetPath) ?: continue
                val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                val processed = processedCache.get(key)
                    ?: LayerRenderer.applyPixelOps(source, layer.props).also {
                        processedCache.put(key, it)
                    }
                val xform = AffineBuilder.build(
                    props = layer.props,
                    canvasCenterX = center,
                    canvasCenterY = center,
                    imageWidth = processed.width,
                    imageHeight = processed.height,
                )
                g.drawImage(processed, xform, null)
            }
        } finally {
            g.dispose()
        }
        return out
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.TokenRendererTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/render/TokenRenderer.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/TokenRendererTest.kt
git commit -m "feat(render): add TokenRenderer composing full layer stack"
```

---

## Task 34: Bag-open orchestrator — load + validate + preload

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/persistence/BagOpener.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/persistence/BagOpenerTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/persistence/BagOpenerTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.persistence

import kotlinx.coroutines.runBlocking
import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class BagOpenerTest {

    private fun writePng(p: Path) {
        Files.createDirectories(p.parent)
        val img = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
    }

    @Test
    fun `open succeeds, returns bag, preloads referenced assets`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        writePng(bundled.resolve("a.png"))
        writePng(user.resolve("b.png"))
        val lib = AssetLibrary(bundled, user).also { it.scan() }

        // Create a bag referencing both assets
        val bag = TokenBag().apply { name = "test" }
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("a.png")))
        t.addLayer(Layer.create(AssetPath.User("b.png")))
        bag.addToken(t)
        val file = tmp.resolve("army.box")
        JsonBagStore(assetResolver = { lib.assetExists(it) }).save(bag, file)

        val cache = ImageCache(16)
        val opener = BagOpener(library = lib, imageCache = cache)
        val loaded = runBlocking { opener.open(file) }
        assertEquals("test", loaded.name)
        assertEquals(1, loaded.tokens.size)
        assertEquals(2, loaded.tokens.first().layers.size)
        assertNotNull(cache.get(AssetPath.Bundled("a.png")))
        assertNotNull(cache.get(AssetPath.User("b.png")))
    }

    @Test
    fun `open throws MissingAssetsException for unknown asset`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        Files.createDirectories(bundled)
        val lib = AssetLibrary(bundled, tmp.resolve("user")).also { it.scan() }

        val bag = TokenBag()
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("missing.png")))
        bag.addToken(t)
        val file = tmp.resolve("army.box")
        JsonBagStore(assetResolver = { true }).save(bag, file)

        val opener = BagOpener(library = lib, imageCache = ImageCache(4))
        assertThrows(MissingAssetsException::class.java) { runBlocking { opener.open(file) } }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.persistence.BagOpenerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `BagOpener`**

`src/main/kotlin/net/rafkos/neuroshima/editor/persistence/BagOpener.kt`:

```kotlin
package net.rafkos.neuroshima.editor.persistence

import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.assets.ImagePreloader
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.TokenBag
import java.nio.file.Path

class BagOpener(
    private val library: AssetLibrary,
    private val imageCache: ImageCache,
) {
    private val store = JsonBagStore(assetResolver = { library.assetExists(it) })
    private val preloader = ImagePreloader(library, imageCache)

    suspend fun open(file: Path): TokenBag {
        val bag = store.load(file)
        val referenced: List<AssetPath> = bag.tokens.flatMap { t -> t.layers.map { it.assetPath } }.distinct()
        preloader.preload(referenced)
        return bag
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.persistence.BagOpenerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/persistence/BagOpener.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/persistence/BagOpenerTest.kt
git commit -m "feat(persistence): add BagOpener (load + validate + preload)"
```

---

## Task 35: Integration test — open sample `.box`, render to PNG, compare to reference

**Files:**
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/integration/RoundTripRenderTest.kt`

This test generates everything at runtime (a fake bundled asset, a sample bag, a reference render). No binary fixtures committed to the repo.

- [ ] **Step 1: Write the integration test**

`src/test/kotlin/net/rafkos/neuroshima/editor/integration/RoundTripRenderTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.integration

import kotlinx.coroutines.runBlocking
import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.persistence.BagOpener
import net.rafkos.neuroshima.editor.persistence.JsonBagStore
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs

class RoundTripRenderTest {

    private fun writeSolidPng(p: Path, color: Color, w: Int = 100, h: Int = 100) {
        Files.createDirectories(p.parent)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, w, h)
        g.dispose()
        Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
    }

    private fun pixelDelta(a: BufferedImage, b: BufferedImage): Int {
        require(a.width == b.width && a.height == b.height)
        var max = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val pa = a.getRGB(x, y); val pb = b.getRGB(x, y)
            for (shift in intArrayOf(0, 8, 16, 24)) {
                val d = abs(((pa ushr shift) and 0xff) - ((pb ushr shift) and 0xff))
                if (d > max) max = d
            }
        }
        return max
    }

    @Test
    fun `save, reopen, render — two passes match within tolerance`(@TempDir tmp: Path) {
        // Setup asset roots
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        writeSolidPng(bundled.resolve("bg.png"), Color.YELLOW)
        writeSolidPng(user.resolve("dot.png"), Color.BLUE, w = 40, h = 40)
        val library = AssetLibrary(bundled, user).also { it.scan() }

        // Build a sample bag
        val bag = TokenBag().apply { name = "sample" }
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("bg.png")))
        t.addLayer(
            Layer.create(
                AssetPath.User("dot.png"),
                LayerProperties(offsetX = 20, offsetY = -10, opacity = 0.8f),
            )
        )
        bag.addToken(t)

        val box = tmp.resolve("sample.box")
        JsonBagStore(assetResolver = { library.assetExists(it) }).save(bag, box)

        // First render
        val cache1 = ImageCache(16)
        val opener1 = BagOpener(library, cache1)
        val loaded1 = runBlocking { opener1.open(box) }
        val render1 = TokenRenderer(cache1, ProcessedLayerCache(16))
            .render(loaded1.tokens.first(), sizePx = 100)

        // Second render via a fresh cache
        val cache2 = ImageCache(16)
        val opener2 = BagOpener(library, cache2)
        val loaded2 = runBlocking { opener2.open(box) }
        val render2 = TokenRenderer(cache2, ProcessedLayerCache(16))
            .render(loaded2.tokens.first(), sizePx = 100)

        // Round-trip + render is deterministic — should be byte-identical
        assertEquals(0, pixelDelta(render1, render2))

        // Sanity check: center is yellow background with non-zero alpha
        val center = render1.getRGB(50, 50)
        val alpha = (center ushr 24) and 0xff
        val red = (center ushr 16) and 0xff
        val green = (center ushr 8) and 0xff
        assertTrue(alpha > 200, "alpha=$alpha")
        assertTrue(red > 200, "r=$red")
        assertTrue(green > 200, "g=$green")
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.integration.RoundTripRenderTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/integration/RoundTripRenderTest.kt
git commit -m "test(integration): round-trip + render determinism check"
```

---

## Task 36: Activate `PackageBoundaryTest` rules

**Files:**
- Modify: `src/test/kotlin/net/rafkos/neuroshima/editor/architecture/PackageBoundaryTest.kt`

- [ ] **Step 1: Replace the empty rule list with the real rules**

Edit `PackageBoundaryTest.kt` — replace `private val rules: List<Rule> = emptyList()` with:

```kotlin
private val rules: List<Rule> = listOf(
    Rule(
        name = "R1: model is Swing/AWT-free (java.awt.geom allowed)",
        source = "$BASE.model",
        forbidden = listOf("javax.swing", "java.awt"),
        allowedImports = listOf("java.awt.geom"),
    ),
    Rule(
        name = "R2: command depends on model only (no Swing/AWT, no ui)",
        source = "$BASE.command",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui"),
    ),
    Rule(
        name = "R3: persistence does not depend on command or ui",
        source = "$BASE.persistence",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui", "$BASE.command"),
        // BagOpener legitimately uses assets package; that's allowed.
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
)
```

(`R7` and `R8` from the spec only become relevant once Plan B introduces `ui.tools` and `prefs` — they'll be added there.)

- [ ] **Step 2: Run the architecture test**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.architecture.PackageBoundaryTest"`
Expected: PASS (one DynamicTest per rule, all green).

- [ ] **Step 3: Run the full test suite to confirm nothing else broke**

Run: `./gradlew test`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/architecture/PackageBoundaryTest.kt
git commit -m "test(architecture): activate package-boundary rules for headless core"
```

---

## Task 37: Headless smoke entry point

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/app/Main.kt`

- [ ] **Step 1: Wire `Main.kt` to render a tiny demo bag**

Replace the file:

```kotlin
package net.rafkos.neuroshima.editor.app

import kotlinx.coroutines.runBlocking
import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.persistence.BagOpener
import net.rafkos.neuroshima.editor.persistence.JsonBagStore
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenRenderer
import net.rafkos.neuroshima.editor.util.logger
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private val log = logger<Main>()

private class Main

fun main() {
    val tmp = Files.createTempDirectory("nh-editor-smoke")
    val bundled = tmp.resolve("bundled")
    val user = tmp.resolve("user")
    writeSolidPng(bundled.resolve("bg.png"), Color.ORANGE)

    val library = AssetLibrary(bundled, user).also { it.scan() }

    val bag = TokenBag().apply { name = "smoke" }
    val t = Token.createUnit()
    t.addLayer(Layer.create(AssetPath.Bundled("bg.png")))
    bag.addToken(t)

    val box = tmp.resolve("smoke.box")
    JsonBagStore(assetResolver = library::assetExists).save(bag, box)

    val cache = ImageCache(16)
    val loaded = runBlocking { BagOpener(library, cache).open(box) }
    val rendered = TokenRenderer(cache, ProcessedLayerCache(16))
        .render(loaded.tokens.first(), sizePx = 256)

    val out = tmp.resolve("smoke.png")
    Files.newOutputStream(out).use { ImageIO.write(rendered, "png", it) }
    log.info("Smoke render written to {}", out)
    println("Smoke render: $out")
}

private fun writeSolidPng(p: Path, color: Color, w: Int = 100, h: Int = 100) {
    Files.createDirectories(p.parent)
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.color = color
    g.fillRect(0, 0, w, h)
    g.dispose()
    Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
}
```

- [ ] **Step 2: Verify `app` package satisfies architecture**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.architecture.PackageBoundaryTest"`
Expected: PASS. (`app` has no rule applied to it intentionally — it composes the layers.)

- [ ] **Step 3: Run smoke entry point**

Run: `./gradlew run -q`
Expected output (path varies):
```
Smoke render: /tmp/nh-editor-smoke.../smoke.png
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/app/Main.kt
git commit -m "feat(app): headless smoke run renders demo bag to PNG"
```

---

## Completion check

After Task 37 you should be able to:

- Run `./gradlew test` → all tests green (model, persistence, i18n, assets, command, render, architecture, integration).
- Run `./gradlew run` → produces a smoke PNG under a temp directory.
- Hold the entire app in your head: model → command → persistence → assets → render → app. No Swing yet.

Next step: **Plan B — UI + release** picks up from here, adds Swing panels, tools, dialogs, preferences, finalizes the architecture test (R7 + R8 rules), and adds the jpackage release scripts. The headless core does not change in Plan B except where the UI explicitly needs a new accessor.
