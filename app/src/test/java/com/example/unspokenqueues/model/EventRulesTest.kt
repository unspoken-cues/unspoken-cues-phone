package com.example.unspokenqueues.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EventRulesTest {

    // ---------- isValidJoinCode ----------

    @Test
    fun joinCode_acceptsLettersAndDigitsWithinLength() {
        assertTrue(isValidJoinCode("MIXER1"))
        assertTrue(isValidJoinCode("ABCD"))
        assertTrue(isValidJoinCode("ABCD1234"))
        assertTrue(isValidJoinCode("1234"))
    }

    @Test
    fun joinCode_isCaseInsensitive_andIgnoresSurroundingWhitespace() {
        assertTrue(isValidJoinCode("mixer1"))
        assertTrue(isValidJoinCode("  MIXER1\n"))
    }

    @Test
    fun joinCode_rejectsWrongLength() {
        assertFalse(isValidJoinCode(""))
        assertFalse(isValidJoinCode("   "))
        assertFalse(isValidJoinCode("ABC"))
        assertFalse(isValidJoinCode("ABCD12345"))
    }

    @Test
    fun joinCode_rejectsOtherCharacters() {
        assertFalse(isValidJoinCode("MIX-ER"))
        assertFalse(isValidJoinCode("MIX ER"))
        assertFalse(isValidJoinCode("MIXER!"))
        assertFalse(isValidJoinCode("MIXÉR1"))
        assertFalse(isValidJoinCode("١٢٣٤"))
    }

    // ---------- generateJoinCode ----------

    @Test
    fun generatedCode_hasTheConfiguredLength() {
        repeat(200) { assertEquals(JOIN_CODE_LENGTH, generateJoinCode().length) }
    }

    @Test
    fun generatedCode_usesOnlyTheUnambiguousAlphabet() {
        repeat(200) {
            val code = generateJoinCode()
            assertTrue(code, code.all { it in JOIN_CODE_ALPHABET })
        }
    }

    @Test
    fun alphabet_isUppercaseLettersAndDigits_withoutLookAlikes() {
        assertTrue(JOIN_CODE_ALPHABET.all { it in 'A'..'Z' || it in '0'..'9' })
        assertTrue("01OIL".none { it in JOIN_CODE_ALPHABET })
        assertEquals(JOIN_CODE_ALPHABET.length, JOIN_CODE_ALPHABET.toSet().size)
    }

    @Test
    fun generatedCode_alwaysPassesTheValidator() {
        repeat(200) {
            val code = generateJoinCode()
            assertTrue(code, isValidJoinCode(code))
        }
    }

    @Test
    fun generatedCode_isRepeatableForASeed_andVariesAcrossSeeds() {
        assertEquals(generateJoinCode(Random(42)), generateJoinCode(Random(42)))
        val codes = (1..50).map { generateJoinCode(Random(it)) }.toSet()
        assertTrue("expected mostly distinct codes, got ${codes.size}", codes.size > 45)
    }
}
