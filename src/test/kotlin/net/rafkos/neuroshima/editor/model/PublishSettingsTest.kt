package net.rafkos.neuroshima.editor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublishSettingsTest {
    @Test
    fun `defaults match spec`() {
        val s = PublishSettings()
        assertEquals(300, s.dpi)
        assertEquals(PageFormat.A4, s.pageFormat)
        assertTrue(!s.invertBackSide)
        assertTrue(s.renderOverlay)
    }

    @Test
    fun `copy produces equal instance`() {
        val a = PublishSettings(dpi = 600, pageFormat = PageFormat.A3, invertBackSide = true, renderOverlay = false)
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `page format dimensions in mm`() {
        assertEquals(297.0, PageFormat.A3.widthMm)
        assertEquals(420.0, PageFormat.A3.heightMm)
        assertEquals(210.0, PageFormat.A4.widthMm)
        assertEquals(297.0, PageFormat.A4.heightMm)
        assertEquals(148.0, PageFormat.A5.widthMm)
        assertEquals(210.0, PageFormat.A5.heightMm)
    }
}
