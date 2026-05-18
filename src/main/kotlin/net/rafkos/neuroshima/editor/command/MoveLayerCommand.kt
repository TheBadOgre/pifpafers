package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class MoveLayerCommand(
    val targets: List<Target>,
) : Command {
    data class Target(
        val tokenId: UUID, val layerId: UUID,
        val oldX: Int, val oldY: Int,
        val newX: Int, val newY: Int,
    )

    override val label: String = "Move"

    override fun execute(bag: TokenBag) {
        for (tgt in targets) {
            val token = bag.findToken(tgt.tokenId) ?: continue
            val layer = token.findLayer(tgt.layerId) ?: continue
            bag.updateLayerProps(tgt.tokenId, tgt.layerId, layer.props.copy(offsetX = tgt.newX, offsetY = tgt.newY))
        }
    }

    override fun undo(bag: TokenBag) {
        for (tgt in targets) {
            val token = bag.findToken(tgt.tokenId) ?: continue
            val layer = token.findLayer(tgt.layerId) ?: continue
            bag.updateLayerProps(tgt.tokenId, tgt.layerId, layer.props.copy(offsetX = tgt.oldX, offsetY = tgt.oldY))
        }
    }

    override fun mergeWith(next: Command): Command? {
        if (next !is MoveLayerCommand) return null
        val mineKeys = targets.map { it.tokenId to it.layerId }.toSet()
        val theirKeys = next.targets.map { it.tokenId to it.layerId }.toSet()
        if (mineKeys != theirKeys) return null
        val merged = targets.map { old ->
            val newer = next.targets.first { it.tokenId == old.tokenId && it.layerId == old.layerId }
            old.copy(newX = newer.newX, newY = newer.newY)
        }
        return MoveLayerCommand(merged)
    }
}
