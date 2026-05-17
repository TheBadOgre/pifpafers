package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class TrackingCommand(
    override val label: String,
    val onDo: () -> Unit,
    val onUndo: () -> Unit,
) : Command {
    override fun execute(bag: TokenBag) { onDo() }
    override fun undo(bag: TokenBag) { onUndo() }
    override fun mergeWith(next: Command): Command? = null
}

class CommandHistoryTest {

    @Test
    fun `execute pushes to done stack and runs command`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var did = 0
        history.execute(bag, TrackingCommand("x", { did++ }, { }))
        assertEquals(1, did)
        assertTrue(history.canUndo())
        assertFalse(history.canRedo())
    }

    @Test
    fun `undo runs command undo and moves to undone stack`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var undid = 0
        history.execute(bag, TrackingCommand("x", { }, { undid++ }))
        history.undo(bag)
        assertEquals(1, undid)
        assertFalse(history.canUndo())
        assertTrue(history.canRedo())
    }

    @Test
    fun `redo re-executes`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var redid = 0
        history.execute(bag, TrackingCommand("x", { redid++ }, { }))
        history.undo(bag)
        history.redo(bag)
        assertEquals(2, redid)
        assertTrue(history.canUndo())
        assertFalse(history.canRedo())
    }

    @Test
    fun `new execute clears undone stack`() {
        val bag = TokenBag()
        val history = CommandHistory()
        history.execute(bag, TrackingCommand("a", { }, { }))
        history.undo(bag)
        history.execute(bag, TrackingCommand("b", { }, { }))
        assertFalse(history.canRedo())
    }

    @Test
    fun `clear empties both stacks`() {
        val bag = TokenBag()
        val history = CommandHistory()
        history.execute(bag, TrackingCommand("a", { }, { }))
        history.undo(bag)
        history.clear()
        assertFalse(history.canUndo())
        assertFalse(history.canRedo())
    }

    private class Mergeable(
        override val label: String,
        val tag: String,
        var value: Int,
    ) : Command {
        override fun execute(bag: TokenBag) {}
        override fun undo(bag: TokenBag) {}
        override fun mergeWith(next: Command): Command? =
            if (next is Mergeable && next.tag == tag) Mergeable(label, tag, next.value) else null
    }

    @Test
    fun `consecutive mergeable commands within window collapse into one entry`() {
        val bag = TokenBag()
        var t = 0L
        val history = CommandHistory(mergeWindowMs = 500L, clock = { t })
        history.execute(bag, Mergeable("set", "opacity", 50))
        t = 100L
        history.execute(bag, Mergeable("set", "opacity", 60))
        t = 200L
        history.execute(bag, Mergeable("set", "opacity", 70))
        history.undo(bag)
        assertFalse(history.canUndo())
    }

    @Test
    fun `commands outside merge window do not collapse`() {
        val bag = TokenBag()
        var t = 0L
        val history = CommandHistory(mergeWindowMs = 500L, clock = { t })
        history.execute(bag, Mergeable("set", "opacity", 50))
        t = 1_000L
        history.execute(bag, Mergeable("set", "opacity", 60))
        history.undo(bag)
        assertTrue(history.canUndo())
    }

    @Test
    fun `non-mergeable next does not collapse`() {
        val bag = TokenBag()
        val history = CommandHistory()
        history.execute(bag, Mergeable("set", "opacity", 50))
        history.execute(bag, TrackingCommand("other", { }, { }))
        history.undo(bag)
        assertTrue(history.canUndo())
    }
}
