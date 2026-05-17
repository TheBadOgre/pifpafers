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
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
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
        offsetX, offsetY, rotation, scale, opacity, hue, saturation, brightness, colorize
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
            colorize = props.colorize,
        ),
    )
}
