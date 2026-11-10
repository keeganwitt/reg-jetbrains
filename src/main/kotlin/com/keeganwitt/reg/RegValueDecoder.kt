package com.keeganwitt.reg

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object RegValueDecoder {
    const val MAX_DECODE_BYTES = 4096
    private const val MAX_PREVIEW_CHARACTERS = 80
    private val names = mapOf(
        0 to "REG_NONE", 1 to "REG_SZ", 2 to "REG_EXPAND_SZ", 3 to "REG_BINARY",
        4 to "REG_DWORD", 5 to "REG_DWORD_BIG_ENDIAN", 6 to "REG_LINK", 7 to "REG_MULTI_SZ",
        8 to "REG_RESOURCE_LIST", 9 to "REG_FULL_RESOURCE_DESCRIPTOR", 10 to "REG_RESOURCE_REQUIREMENTS_LIST", 11 to "REG_QWORD",
    )

    fun typeName(type: BigInteger): String = names.entries.firstOrNull { type == it.key.toBigInteger() }?.value ?: "hex(${type.toString(16)})"

    fun byteCount(size: Int) = "$size ${if (size == 1) "byte" else "bytes"}"

    fun preview(type: BigInteger, bytes: ByteArray, charset: Charset?): String = decode(type, bytes, charset).preview

    fun numericSizeProblem(type: BigInteger, size: Int): String? {
        val expected = when (type) {
            BigInteger.valueOf(4), BigInteger.valueOf(5) -> 4
            BigInteger.valueOf(11) -> 8
            else -> return null
        }
        return if (size == expected) null else "${byteCount(size)} (expected $expected)"
    }

    fun decode(type: BigInteger, bytes: ByteArray, charset: Charset?): RegDecodedValue {
        fun problem(reason: String) = RegDecodedValue("${byteCount(bytes.size)} ($reason)", reason)
        numericSizeProblem(type, bytes.size)?.let { return RegDecodedValue(it, it) }
        val id = type.takeIf { it.bitLength() < 31 }?.toInt()
        if (id in listOf(4, 5, 11)) {
            val expected = if (id == 11) 8 else 4
            val bigEndian = if (id == 5) bytes else bytes.reversedArray()
            return RegDecodedValue(number(BigInteger(1, bigEndian), expected * 2))
        }
        if (id !in listOf(1, 2, 7)) return RegDecodedValue(byteCount(bytes.size))
        if (bytes.size > MAX_DECODE_BYTES) return RegDecodedValue("${byteCount(bytes.size)} (preview omitted)")
        if (charset == null) return RegDecodedValue("${byteCount(bytes.size)} (select a legacy file encoding to decode)")
        val decoded = try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            return problem("invalid ${charset.name()}")
        }
        if (id != 7) {
            if (!decoded.endsWith('\u0000')) return problem("missing NUL terminator")
            val content = decoded.dropLast(1)
            if ('\u0000' in content) return problem("embedded NUL")
            return RegDecodedValue(quoted(content))
        }
        if (decoded == "\u0000" || decoded == "\u0000\u0000") return RegDecodedValue("[]")
        if (!decoded.endsWith("\u0000\u0000")) return problem("missing double NUL terminator")
        val strings = decoded.dropLast(2).split('\u0000')
        if (strings.any { it.isEmpty() }) return problem("empty item before end of multi-string")
        return RegDecodedValue(bounded(strings.joinToString(", ", "[", "]") { quoted(it) }))
    }

    fun number(value: BigInteger, width: Int) = "$value (0x${value.toString(16).padStart(width, '0')})"

    fun quoted(value: String): String = bounded(buildString {
        append('"')
        value.forEach { c ->
            append(when (c) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> if (c.isISOControl()) "\\u${c.code.toString(16).padStart(4, '0')}" else c.toString()
            })
        }
        append('"')
    })

    fun bounded(text: String): String = if (text.codePointCount(0, text.length) <= MAX_PREVIEW_CHARACTERS) text
        else text.substring(0, text.offsetByCodePoints(0, MAX_PREVIEW_CHARACTERS - 1)) + "…"
}

data class RegDecodedValue(val preview: String, val problem: String? = null)
