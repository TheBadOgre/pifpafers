package net.rafkos.neuroshima.editor.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class LocaleServiceTest {

    @Test
    fun `english locale loads default bundle`() {
        val svc = LocaleService(Locale.ENGLISH)
        assertEquals("Pifpafers - Neuroshima HEX Army Painter", svc.t("app.title"))
    }

    @Test
    fun `polish locale loads pl bundle`() {
        val svc = LocaleService(Locale.of("pl"))
        assertEquals("Pifpafers - Malarz Armii do Neuroshima Hex", svc.t("app.title"))
    }

    @Test
    fun `unknown locale falls back to english`() {
        val svc = LocaleService(Locale.of("xx"))
        assertEquals("Pifpafers - Neuroshima HEX Army Painter", svc.t("app.title"))
    }

    @Test
    fun `format substitutes positional arguments`() {
        val svc = LocaleService(Locale.ENGLISH)
        assertEquals("Unsupported file version: 7", svc.t("error.schema.version", 7))
    }

    @Test
    fun `unknown key returns the key`() {
        val svc = LocaleService(Locale.ENGLISH)
        assertEquals("missing.key", svc.t("missing.key"))
    }
}
