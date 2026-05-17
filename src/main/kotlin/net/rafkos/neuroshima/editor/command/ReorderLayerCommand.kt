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
