package net.rafkos.neuroshima.editor.persistence

import kotlinx.serialization.json.Json
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.PageFormat
import net.rafkos.neuroshima.editor.model.PublishSettings
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

const val CURRENT_SCHEMA_VERSION = 3

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
            printSettings = bag.printSettings.toDto(),
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
        for (t in dto.tokens) {
            for (l in t.front) {
                val ap = AssetPath.parse(l.asset)
                if (!assetResolver(ap)) missing += ap
            }
            for (l in t.back) {
                val ap = AssetPath.parse(l.asset)
                if (!assetResolver(ap)) missing += ap
            }
        }
        if (missing.isNotEmpty()) throw MissingAssetsException(missing)

        val bag = TokenBag().apply {
            name = dto.name
            updatePrintSettings(dto.printSettings.toModel())
        }
        for (t in dto.tokens) bag.addToken(t.toModel())
        return bag
    }

    private fun Token.toDto(): TokenDto = TokenDto(
        id = id.toString(),
        kind = kind.name,
        maskId = maskId,
        sameSides = sameSides,
        front = layers(TokenSide.FRONT).map { it.toDto() },
        back = layers(TokenSide.BACK).map { it.toDto() },
    )

    private fun Layer.toDto(): LayerDto = LayerDto(
        id = id.toString(),
        asset = assetPath.uri,
        props = props.toDto(),
    )

    private fun LayerProperties.toDto(): PropsDto = PropsDto(
        offsetX, offsetY, rotation, scale, opacity, hue, saturation, brightness, colorize
    )

    private fun PublishSettings.toDto(): PublishSettingsDto = PublishSettingsDto(
        dpi = dpi,
        pageFormat = pageFormat.name,
        invertBackSide = invertBackSide,
        renderOverlay = renderOverlay,
        marginMm = marginMm,
    )

    private fun PublishSettingsDto.toModel(): PublishSettings = PublishSettings(
        dpi = dpi,
        pageFormat = PageFormat.valueOf(pageFormat),
        invertBackSide = invertBackSide,
        renderOverlay = renderOverlay,
        marginMm = marginMm,
    )

    private fun TokenDto.toModel(): Token {
        val t = Token(UUID.fromString(id), TokenKind.valueOf(kind),
            maskId = maskId, sameSides = sameSides)
        for (l in front) t.addLayer(TokenSide.FRONT, l.toModel())
        for (l in back) t.addLayer(TokenSide.BACK, l.toModel())
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
