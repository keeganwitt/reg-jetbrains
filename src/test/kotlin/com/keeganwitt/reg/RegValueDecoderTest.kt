package com.keeganwitt.reg

import junit.framework.TestCase
import java.math.BigInteger
import java.nio.charset.StandardCharsets.UTF_16LE

class RegValueDecoderTest : TestCase() {
    private fun decode(type: Int, vararg bytes: Int) = RegValueDecoder.preview(type.toBigInteger(), bytes.map { it.toByte() }.toByteArray(), UTF_16LE)
    private fun text(type: Int, value: String) = RegValueDecoder.preview(type.toBigInteger(), value.toByteArray(UTF_16LE), UTF_16LE)

    fun testUnsignedNumbersAndEndianness() {
        assertEquals("305419896 (0x12345678)", decode(4, 0x78, 0x56, 0x34, 0x12))
        assertEquals("305419896 (0x12345678)", decode(5, 0x12, 0x34, 0x56, 0x78))
        assertEquals("4294967295 (0xffffffff)", decode(4, 255, 255, 255, 255))
        assertEquals("18446744073709551615 (0xffffffffffffffff)", decode(11, 255, 255, 255, 255, 255, 255, 255, 255))
        assertEquals("0 (0x00000000)", RegValueDecoder.number(BigInteger.ZERO, 8))
    }

    fun testStringAndMultiStringDecoding() {
        assertEquals("\"café 例\"", text(1, "café 例\u0000"))
        assertEquals("\"%TEMP%\"", text(2, "%TEMP%\u0000"))
        assertEquals("[\"one\", \"two\"]", text(7, "one\u0000two\u0000\u0000"))
        assertEquals("[]", text(7, "\u0000"))
        assertEquals("[]", text(7, "\u0000\u0000"))
        assertEquals("\"a\\n\\t\\\"b\"", text(1, "a\n\t\"b\u0000"))
    }

    fun testMalformedDataIsExplained() {
        assertTrue(decode(4, 1, 2).contains("expected 4"))
        assertTrue(decode(11, 1, 2).contains("expected 8"))
        assertTrue(decode(1, 65).contains("invalid UTF-16LE"))
        assertTrue(text(1, "hello").contains("missing NUL"))
        assertTrue(text(1, "a\u0000b\u0000").contains("embedded NUL"))
        assertTrue(text(7, "one\u0000").contains("missing double NUL"))
        assertTrue(text(7, "one\u0000\u0000two\u0000\u0000").contains("empty item"))
        assertTrue(decode(1, 0x00, 0xd8, 0, 0).contains("invalid UTF-16LE"))
    }

    fun testLegacyCharsetAndUnknownTypes() {
        val bytes = byteArrayOf(0xe9.toByte(), 0)
        assertTrue(RegValueDecoder.preview(2.toBigInteger(), bytes, null).contains("legacy file encoding"))
        assertEquals("\"é\"", RegValueDecoder.preview(2.toBigInteger(), bytes, java.nio.charset.Charset.forName("windows-1252")))
        assertEquals("hex(100000000000000000000)", RegValueDecoder.typeName(BigInteger("100000000000000000000", 16)))
        assertEquals("2 bytes", RegValueDecoder.preview(BigInteger("abc", 16), bytes, null))
    }

    fun testBoundedPreviewsPreserveUnicodeCodePoints() {
        val long = RegValueDecoder.quoted("😀".repeat(200))
        assertEquals(80, long.codePointCount(0, long.length))
        assertTrue(long.endsWith("…"))
        assertFalse(Character.isHighSurrogate(long[long.lastIndex - 1]))
        assertTrue(RegValueDecoder.preview(1.toBigInteger(), ByteArray(4098), UTF_16LE).contains("preview omitted"))
    }
}
