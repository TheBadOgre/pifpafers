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
            targets = targets,
        )
    }
}
