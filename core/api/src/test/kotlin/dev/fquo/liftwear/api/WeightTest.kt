package dev.fquo.liftwear.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightTest {

    @Test
    fun `parses the unit suffixes the API actually returns`() {
        assertEquals(Weight(100.0, "kg"), Weight.parse("100kg"))
        assertEquals(Weight(180.0, "lb"), Weight.parse("180lb"))
        assertEquals(Weight(37.0, "cm"), Weight.parse("37cm"))
        assertEquals(Weight(18.0, "%"), Weight.parse("18%"))
    }

    @Test
    fun `parses fractional plate weights`() {
        assertEquals(Weight(2.5, "kg"), Weight.parse("2.5kg"))
        assertEquals(Weight(102.5, "lb"), Weight.parse("102.5lb"))
    }

    @Test
    fun `tolerates surrounding and internal whitespace`() {
        assertEquals(Weight(60.0, "kg"), Weight.parse("  60 kg "))
    }

    @Test
    fun `round-trips without gaining a spurious decimal point`() {
        assertEquals("100kg", Weight.parse("100kg").toString())
        assertEquals("2.5kg", Weight.parse("2.5kg").toString())
    }

    @Test
    fun `returns null rather than throwing on junk`() {
        // A watch mid-workout must degrade, never crash, on unexpected input.
        assertNull(Weight.parse(""))
        assertNull(Weight.parse("kg"))
        assertNull(Weight.parse("100"))
        assertNull(Weight.parse("abc"))
    }
}
