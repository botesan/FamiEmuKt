package jp.mito.famiemukt.emurator.util

fun UShort.toHex(): String = toString(radix = 16).padStart(length = 4, padChar = '0')
fun UByte.toHex(): String = toString(radix = 16).padStart(length = 2, padChar = '0')
