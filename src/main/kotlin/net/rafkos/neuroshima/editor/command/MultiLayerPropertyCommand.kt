package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import java.util.UUID

class MultiLayerPropertyCommand(
    val property: LayerProperty,
    val targets: List<Target>,
) : Command {
    data class Target(
        val tokenId: UUID,
        val side: TokenSide,
        val layerId: UUID,
        val oldValue: Double,
        val newValue: Double,
    )

    override val label: String = "Set ${property.name.lowercase()} (multi)"

    override fun execute(bag: TokenBag) {
        for (tgt in targets) {
            val token = bag.findToken(tgt.tokenId) ?: continue
            val layer = token.findLayer(tgt.side, tgt.layerId) ?: continue
            bag.updateLayerProps(tgt.tokenId, tgt.side, tgt.layerId, property.apply(layer.props, tgt.newValue))
        }
    }

    override fun undo(bag: TokenBag) {
        for (tgt in targets) {
            val token = bag.findToken(tgt.tokenId) ?: continue
            val layer = token.findLayer(tgt.side, tgt.layerId) ?: continue
            bag.updateLayerProps(tgt.tokenId, tgt.side, tgt.layerId, property.apply(layer.props, tgt.oldValue))
        }
    }

    override fun mergeWith(next: Command): Command? {
        if (next !is MultiLayerPropertyCommand) return null
        if (next.property != property) return null
        val mineKeys = targets.map { Triple(it.tokenId, it.side, it.layerId) }.toSet()
        val theirKeys = next.targets.map { Triple(it.tokenId, it.side, it.layerId) }.toSet()
        if (mineKeys != theirKeys) return null
        val merged = targets.map { old ->
            val newer = next.targets.first {
                it.tokenId == old.tokenId && it.side == old.side && it.layerId == old.layerId
            }
            old.copy(newValue = newer.newValue)
        }
        return MultiLayerPropertyCommand(property, merged)
    }
}
