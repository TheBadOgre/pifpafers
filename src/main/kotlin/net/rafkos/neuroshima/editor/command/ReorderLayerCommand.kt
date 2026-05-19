package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import java.util.UUID

class ReorderLayerCommand(
    private val tokenId: UUID,
    private val side: TokenSide,
    private val layerId: UUID,
    private val newIndex: Int,
) : Command {
    override val label: String = "Reorder layer"
    private var previousIndex: Int = -1

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        previousIndex = token.layers(side).indexOfFirst { it.id == layerId }
        if (previousIndex < 0) throw NoSuchElementException("Layer $layerId on $side")
        bag.reorderLayer(tokenId, side, layerId, newIndex)
    }

    override fun undo(bag: TokenBag) {
        if (previousIndex < 0) return
        bag.reorderLayer(tokenId, side, layerId, previousIndex)
    }

    override fun mergeWith(next: Command): Command? = null
}
