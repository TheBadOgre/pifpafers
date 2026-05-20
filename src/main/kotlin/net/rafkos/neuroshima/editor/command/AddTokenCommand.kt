package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import java.util.UUID

class AddTokenCommand(
    private val kind: TokenKind,
    private val atIndex: Int,
) : Command {
    override val label: String = "Add ${kind.name.lowercase()} token"
    var createdId: UUID? = null
        private set
    private var assignedMaskId: Int = -1

    override fun execute(bag: TokenBag) {
        val id = createdId ?: UUID.randomUUID().also { createdId = it }
        val maskId = if (assignedMaskId >= 0) assignedMaskId else bag.nextMaskId().also { assignedMaskId = it }
        val token = Token(id, kind, maskId = maskId)
        bag.addToken(token, atIndex.coerceIn(0, bag.tokens.size))
    }

    override fun undo(bag: TokenBag) {
        val id = createdId ?: return
        bag.removeToken(id)
    }

    override fun mergeWith(next: Command): Command? = null
}
