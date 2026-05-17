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
    private var createdId: UUID? = null

    override fun execute(bag: TokenBag) {
        val token = when (kind) {
            TokenKind.UNIT -> Token.createUnit()
            TokenKind.MODIFIER -> Token.createModifier()
        }.let { existing ->
            createdId?.let { Token(it, kind) } ?: existing
        }
        createdId = token.id
        bag.addToken(token, atIndex.coerceIn(0, bag.tokens.size))
    }

    override fun undo(bag: TokenBag) {
        val id = createdId ?: return
        bag.removeToken(id)
    }

    override fun mergeWith(next: Command): Command? = null
}
