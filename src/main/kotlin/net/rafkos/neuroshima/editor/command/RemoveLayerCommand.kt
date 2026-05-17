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
