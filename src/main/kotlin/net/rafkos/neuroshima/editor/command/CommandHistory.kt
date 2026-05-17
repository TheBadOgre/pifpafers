package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag

class CommandHistory(
    private val mergeWindowMs: Long = 500L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val command: Command, val timestampMs: Long)

    private val done: ArrayDeque<Entry> = ArrayDeque()
    private val undone: ArrayDeque<Entry> = ArrayDeque()

    fun execute(bag: TokenBag, command: Command) {
        command.execute(bag)
        val now = clock()
        val top = done.lastOrNull()
        if (top != null && now - top.timestampMs < mergeWindowMs) {
            val merged = top.command.mergeWith(command)
            if (merged != null) {
                done.removeLast()
                done.addLast(Entry(merged, now))
                undone.clear()
                return
            }
        }
        done.addLast(Entry(command, now))
        undone.clear()
    }

    fun undo(bag: TokenBag) {
        val e = done.removeLastOrNull() ?: return
        e.command.undo(bag)
        undone.addLast(e)
    }

    fun redo(bag: TokenBag) {
        val e = undone.removeLastOrNull() ?: return
        e.command.execute(bag)
        done.addLast(e)
    }

    fun canUndo(): Boolean = done.isNotEmpty()
    fun canRedo(): Boolean = undone.isNotEmpty()

    fun clear() {
        done.clear()
        undone.clear()
    }
}
