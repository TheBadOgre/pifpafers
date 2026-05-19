package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import java.util.UUID

class RemoveLayerCommand(
    private val tokenId: UUID,
    private val side: TokenSide,
    private val layerId: UUID,
) : Command {
    override val label: String = "Remove layer"
    private var snapshot: Layer? = null
    private var snapshotIndex: Int = -1

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        val list = token.layers(side)
        snapshotIndex = list.indexOfFirst { it.id == layerId }
        if (snapshotIndex < 0) throw NoSuchElementException("Layer $layerId on $side")
        snapshot = list[snapshotIndex]
        bag.removeLayer(tokenId, side, layerId)
    }

    override fun undo(bag: TokenBag) {
        val s = snapshot ?: return
        bag.addLayer(tokenId, side, s, snapshotIndex)
    }

    override fun mergeWith(next: Command): Command? = null
}
