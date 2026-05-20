package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class SetSameSidesCommand(
    private val tokenId: UUID,
    private val newValue: Boolean,
) : Command {
    override val label: String = if (newValue) "Enable same sides" else "Disable same sides"
    private var previousValue: Boolean? = null

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        if (previousValue == null) previousValue = token.sameSides
        bag.updateSameSides(tokenId, newValue)
    }

    override fun undo(bag: TokenBag) {
        val prev = previousValue ?: return
        bag.updateSameSides(tokenId, prev)
    }

    override fun mergeWith(next: Command): Command? = null
}
