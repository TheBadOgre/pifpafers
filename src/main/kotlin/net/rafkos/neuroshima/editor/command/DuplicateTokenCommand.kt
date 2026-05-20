package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import java.util.UUID

class DuplicateTokenCommand(private val sourceTokenId: UUID) : Command {
    override val label: String = "Duplicate token"
    var newTokenId: UUID? = null
        private set
    private var snapshotKind: TokenKind? = null
    private var snapshotFront: List<Layer>? = null
    private var snapshotBack: List<Layer>? = null
    private var snapshotSameSides: Boolean = false
    private var assignedMaskId: Int = -1

    override fun execute(bag: TokenBag) {
        val source = bag.findToken(sourceTokenId) ?: throw NoSuchElementException("Token $sourceTokenId")
        val id = newTokenId ?: UUID.randomUUID().also { newTokenId = it }
        val kind = source.kind.also { snapshotKind = it }
        val front = snapshotFront
            ?: source.layers(TokenSide.FRONT).map { l -> Layer(UUID.randomUUID(), l.assetPath, l.props) }
                .also { snapshotFront = it }
        val back = snapshotBack
            ?: source.layers(TokenSide.BACK).map { l -> Layer(UUID.randomUUID(), l.assetPath, l.props) }
                .also { snapshotBack = it }
        val sameSides = if (snapshotFront != null) snapshotSameSides else source.sameSides.also { snapshotSameSides = it }
        val maskId = if (assignedMaskId >= 0) assignedMaskId else bag.nextMaskId().also { assignedMaskId = it }
        val copy = Token(id, kind, maskId = maskId, sameSides = sameSides)
        for (l in front) copy.addLayer(TokenSide.FRONT, l)
        for (l in back) copy.addLayer(TokenSide.BACK, l)
        val idx = bag.tokens.indexOfFirst { it.id == sourceTokenId }
        bag.addToken(copy, idx + 1)
    }

    override fun undo(bag: TokenBag) {
        val id = newTokenId ?: return
        bag.removeToken(id)
    }

    override fun mergeWith(next: Command): Command? = null
}
