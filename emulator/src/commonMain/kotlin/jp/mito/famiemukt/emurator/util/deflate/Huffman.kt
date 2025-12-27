package jp.mito.famiemukt.emurator.util.deflate

import co.touchlab.kermit.Logger

interface Huffman {
    fun setBitLengths(bitLengths: List<Int>)
    fun read(from: BitReader): Int
}

fun Huffman(optimized: Boolean = false): Huffman = if (optimized) TODO() else NormalHuffmanImpl()

// https://datatracker.ietf.org/doc/html/rfc1951

private const val DEBUG_LOG = false
private inline fun debugLog(message: () -> String) {
    if (DEBUG_LOG) Logger.d { message() }
}

private class NormalHuffmanImpl : Huffman {
    private data class Tree(
        val value: Int,
        var left: Tree? = null,
        var right: Tree? = null,
    )

    private val root: Tree = Tree(value = -1)

    override fun setBitLengths(bitLengths: List<Int>) {
        val codes = huffmanCodes(bitLengths = bitLengths)
        createTree(codes = codes)
    }

    override fun read(from: BitReader): Int {
        var currentNode = root
        do {
            currentNode = if (from.readBit()) {
                checkNotNull(value = currentNode.right) { "Null node right. $currentNode" }
            } else {
                checkNotNull(value = currentNode.left) { "Null node left. $currentNode" }
            }
        } while (currentNode.left != null || currentNode.right != null)
        return currentNode.value
    }

    private fun huffmanCodes(bitLengths: List<Int>): List<String> {
        debugLog { "bitLengths=$bitLengths" }
        /* 1) Count the number of codes for each code length.
              Let bl_count[N] be the number of codes of length N, N >= 1. */
        val maxBitLength = bitLengths.maxOrNull() ?: 0
        val bitLengthCounts = IntArray(size = maxBitLength + 1)
        for (bitLength in bitLengths) {
            if (bitLength > 0) {
                bitLengthCounts[bitLength]++
            }
        }
        debugLog { "bitLengthCounts=${bitLengthCounts.contentToString()}" }
        /* 2) Find the numerical value of the smallest code for each code length:
               code = 0;
               bl_count[0] = 0;
               for (bits = 1; bits <= MAX_BITS; bits++) {
                   code = (code + bl_count[bits-1]) << 1;
                   next_code[bits] = code;
               } */
        val nextCode = IntArray(size = maxBitLength + 1)
        var codeValue = 0
        for (bits in 1..maxBitLength) {
            codeValue = (codeValue + bitLengthCounts[bits - 1]) shl 1
            nextCode[bits] = codeValue
        }
        debugLog { "nextCode=${nextCode.contentToString()}" }
        /* 3) Assign numerical values to all codes, using consecutive values for all codes of the same length
              with the base values determined at step 2.
              Codes that are never used (which have a bit length of zero) must not be assigned a value.
               for (n = 0;  n <= max_code; n++) {
                   len = tree[n].Len;
                   if (len != 0) {
                       tree[n].Code = next_code[len];
                       next_code[len]++;
                   }
               } */
        val code = IntArray(size = bitLengths.size)
        for (i in bitLengths.indices) {
            val length = bitLengths[i]
            if (length != 0) {
                code[i] = nextCode[length]
                nextCode[length]++
            }
        }
        debugLog { "code=${code.contentToString()}" }
        // コードを0,1の文字列で表現
        val codes = code.mapIndexed { i, c ->
            val length = bitLengths[i]
            if (length == 0) "" else c.toString(radix = 2).padStart(length = length, padChar = '0')
        }
        debugLog { "codes=$codes" }
        debugLog {
            codes.withIndex()
                .filter { it.value.isNotEmpty() }
                .sortedBy { it.value.length }
                .joinToString(separator = "\n") { (index, code) -> "$index: $code (${code.length}/${bitLengths[index]})" }
        }
        // チェック
        codes.forEachIndexed { index, string ->
            check(value = bitLengths[index] == string.length) {
                "Invalid code length: ${bitLengths[index]} != ${string.length}, index=$index, string=$string"
            }
        }
        return codes
    }

    private fun createTree(codes: List<String>) {
        val root = root.also {
            it.left = null
            it.right = null
        }
        codes.forEachIndexed { codeIndex, code ->
            if (code.isNotEmpty()) {
                var currentNode = root
                code.forEach { bit ->
                    currentNode = if (bit == '0') {
                        currentNode.left ?: Tree(value = codeIndex).also { currentNode.left = it }
                    } else {
                        currentNode.right ?: Tree(value = codeIndex).also { currentNode.right = it }
                    }
                }
            }
        }
    }

    override fun toString(): String = "NormalHuffmanImpl[root=$root]"
}
