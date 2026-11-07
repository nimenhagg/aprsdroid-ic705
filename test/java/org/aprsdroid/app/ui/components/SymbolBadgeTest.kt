package org.aprsdroid.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolBadgeTest {

    @Test
    fun validSymbolIndexRange() {
        // Printable APRS range from '!' (33) to '~' (126)
        assertEquals(0, symbolIndex('!'))
        assertTrue(isValidSymbolIndex(symbolIndex('!')))

        assertEquals(93, symbolIndex('~'))
        assertTrue(isValidSymbolIndex(symbolIndex('~')))

        assertEquals(14, symbolIndex('/'))
        assertTrue(isValidSymbolIndex(symbolIndex('/')))
    }

    @Test
    fun outOfBoundsSymbolIndicesAreRejected() {
        // Characters < 33 (e.g. space, control chars)
        val spaceIndex = symbolIndex(' ')
        assertEquals(-1, spaceIndex)
        assertFalse(isValidSymbolIndex(spaceIndex))

        val nullIndex = symbolIndex('\u0000')
        assertFalse(isValidSymbolIndex(nullIndex))

        // Characters beyond 6 * 16 (>= 129, DEL (127), etc.)
        val outOfBounds = symbolIndex('\u0081')
        assertFalse(isValidSymbolIndex(outOfBounds))
    }
}