package jp.mito.famiemukt.emurator.util.deflate

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readUShortLe

interface Deflate {
    fun uncompressDeflate(from: Source, to: Sink, maxWriteSize: Int = Int.MAX_VALUE)
}

fun Deflate(optimized: Boolean = true): Deflate = if (optimized) OptimizedDeflateImpl() else NormalDeflateImpl()

// https://datatracker.ietf.org/doc/html/rfc1951

private const val DEBUG_LOG = !false
private inline fun debugLog(message: () -> String) {
    if (DEBUG_LOG) println(message())
}

// (HCLEN + 4) x 3 bits: code lengths for the code length alphabet given just above, in the order:
// 16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
private val HCLEN_CODE_LENGTH = listOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
private val FIXED_LITERAL_BIT_LENGTH = List(size = 288) {
    when (it) {
        in 0..143 -> 8
        in 144..255 -> 9
        in 256..279 -> 7
        in 280..287 -> 8
        else -> error("Invalid index: $it")
    }
}
private val FIXED_DISTANCE_BIT_LENGTH = List(size = 32) { 5 }

private class NormalDeflateImpl : Deflate {
    class SlidingWindow(private val sink: Sink, windowSize: Int = 32_768, private val maxWriteSize: Int) {
        private val window = ByteArray(size = windowSize)
        private var written = 0
        private var current = 0
        private var writeCount: Long = 0

        private fun sinkWriteIfNeeded(requireWriteSize: Int, written: Int, current: Int) {
            when {
                written < current -> {
                    if (current - written + requireWriteSize >= window.size) {
                        writeCount += current - written
                        check(value = writeCount <= maxWriteSize) { "writeCount=$writeCount > maxWriteSize=$maxWriteSize" }
                        sink.write(source = window, startIndex = written, endIndex = current)
                        this.written = current
                    }
                }

                written > current -> {
                    if (current - written + window.size + requireWriteSize >= window.size) {
                        writeCount += current - written + window.size
                        check(value = writeCount <= maxWriteSize) { "writeCount=$writeCount > maxWriteSize=$maxWriteSize" }
                        sink.write(source = window, startIndex = written)
                        sink.write(source = window, startIndex = 0, endIndex = current)
                        this.written = current
                    }
                }
            }
        }

        fun write(byte: Byte) {
            val current = current
            sinkWriteIfNeeded(requireWriteSize = 1, written = written, current = current)
            window[current] = byte
            this.current = (current + 1) % window.size
        }

        fun write(source: ByteArray) {
            val current = current
            sinkWriteIfNeeded(requireWriteSize = source.size, written = written, current = current)
            if (source.size > window.size) {
                sink.write(source = source)
                source.copyInto(
                    destination = window,
                    destinationOffset = 0,
                    startIndex = source.size - window.size,
                    endIndex = source.size,
                )
                this.current = 0
                this.written = 0
            } else {
                val sliceIndex = source.size.coerceAtMost(maximumValue = window.size - current)
                source.copyInto(
                    destination = window,
                    destinationOffset = current,
                    startIndex = 0,
                    endIndex = sliceIndex,
                )
                source.copyInto(
                    destination = window,
                    destinationOffset = 0,
                    startIndex = sliceIndex,
                    endIndex = source.size,
                )
                this.current = (current + source.size) % window.size
            }
        }

        fun slidingCopy(length: Int, distance: Int) {
            sinkWriteIfNeeded(requireWriteSize = length, written = written, current = current)
            val startSrcIndex = (current - distance).let { base -> if (base < 0) base + window.size else base }
            val endSrcIndex = (startSrcIndex + length) % window.size
            var srcIndex = startSrcIndex
            while (srcIndex != endSrcIndex) {
                window[current] = window[srcIndex]
                current = (current + 1) % window.size
                srcIndex = (srcIndex + 1) % window.size
            }
        }

        fun flush() {
            sinkWriteIfNeeded(requireWriteSize = window.size, written = written, current = current)
        }
    }

    override fun uncompressDeflate(from: Source, to: Sink, maxWriteSize: Int) {
        val bitReader = BitReader(source = from)
        val huffmanForLength = Huffman()
        val huffmanLiteral = Huffman()
        val huffmanDistance = Huffman()
        val slidingWindow = SlidingWindow(sink = to, maxWriteSize = maxWriteSize)
        do {
            val bfinal = bitReader.readBits(count = 1)
            debugLog { "BFINAL:$bfinal" }
            val btype = bitReader.readBits(count = 2)
            debugLog { "BTYPE:$btype" }
            if (btype == 0b00) {
                bitReader.clearBitBuffer()
                val len = from.readUShortLe()
                val nlen = from.readUShortLe()
                check(value = len == nlen.inv()) { "Illegal value. LEN=$len, NLEN=$nlen" }
                slidingWindow.write(source = from.readByteArray(byteCount = len.toInt()))
            } else {
                when (btype) {
                    // Fixed Huffman codes
                    0b01 -> {
                        huffmanLiteral.setBitLengths(bitLengths = FIXED_LITERAL_BIT_LENGTH)
                        huffmanDistance.setBitLengths(bitLengths = FIXED_DISTANCE_BIT_LENGTH)
                    }
                    // Dynamic Huffman codes
                    0b10 -> {
                        // 5 Bits : HLIT, # of Literal/Length codes-257 (257-286) + 257
                        val hlit = bitReader.readBits(count = 5) + 257
                        // 5 Bits : HDIST, # of Distance codes-1 (1-32)
                        val hdist = bitReader.readBits(count = 5) + 1
                        // 4 Bits : HCLEN, # of Code Length codes-4 (4-19)
                        val hclen = bitReader.readBits(count = 4) + 4
                        debugLog { "hlit=$hlit, hdist=$hdist, hclen=$hclen" }
                        //
                        val lengthForLength = HCLEN_CODE_LENGTH.take(n = hclen)
                            .associateWith { bitReader.readBits(count = 3) }
                            .let { map -> HCLEN_CODE_LENGTH.indices.map { index -> map.getOrElse(key = index) { 0 } } }
                        huffmanForLength.setBitLengths(bitLengths = lengthForLength)
                        debugLog { huffmanForLength.toString() }
                        val hlithdist = hlit + hdist
                        val lengthList = mutableListOf<Int>()
                        while (lengthList.size < hlithdist) {
                            when (val l = huffmanForLength.read(from = bitReader)) {
                                // 0 - 15: Represent code lengths of 0 - 15
                                in 0..15 -> lengthList += l
                                //     16: Copy the previous code length 3 - 6 times.
                                //         The next 2 bits indicate repeat length
                                //               (0 = 3, ... , 3 = 6)
                                //            Example:  Codes 8, 16 (+2 bits 11),
                                //                      16 (+2 bits 10) will expand to
                                //                      12 code lengths of 8 (1 + 6 + 5)
                                16 -> {
                                    val repeatValue = lengthList.last()
                                    val repeatCount = bitReader.readBits(count = 2) + 3
                                    repeat(times = repeatCount) { lengthList += repeatValue }
                                }
                                //     17: Repeat a code length of 0 for 3 - 10 times.
                                //         (3 bits of length)
                                17 -> {
                                    val repeatCount = bitReader.readBits(count = 3) + 3
                                    repeat(times = repeatCount) { lengthList += 0 }
                                }
                                //     18: Repeat a code length of 0 for 11 - 138 times
                                //         (7 bits of length)
                                18 -> {
                                    val repeatCount = bitReader.readBits(count = 7) + 11
                                    repeat(times = repeatCount) { lengthList += 0 }
                                }
                                //
                                else -> error("Not $l in 0..18")
                            }
                        }
                        check(value = lengthList.size == hlithdist) { "size=${lengthList.size} != HLIT+HDIST=$hlithdist" }
                        huffmanLiteral.setBitLengths(bitLengths = lengthList.subList(0, hlit))
                        huffmanDistance.setBitLengths(bitLengths = lengthList.subList(hlit, hlithdist))
                    }
                    // Reserved
                    else -> error("Illegal BTYPE=${btype.toString(radix = 2).padStart(length = 2, padChar = '0')}")
                }
                while (true) {
                    val valueLiteral = huffmanLiteral.read(from = bitReader)
                    debugLog { "valueLiteral=$valueLiteral" }
                    if (valueLiteral == 256) {
                        break
                    } else if (valueLiteral < 256) {
                        slidingWindow.write(byte = valueLiteral.toByte())
                    } else {
                        val length = when (valueLiteral) {
                            in 257..264 -> (valueLiteral - 257) + 3
                            in 265..268 -> ((valueLiteral - 265) shl 1) + 11 + bitReader.readBits(count = 1)
                            in 269..272 -> ((valueLiteral - 269) shl 2) + 19 + bitReader.readBits(count = 2)
                            in 273..276 -> ((valueLiteral - 273) shl 3) + 35 + bitReader.readBits(count = 3)
                            in 277..280 -> ((valueLiteral - 277) shl 4) + 67 + bitReader.readBits(count = 4)
                            in 281..284 -> ((valueLiteral - 281) shl 5) + 131 + bitReader.readBits(count = 5)
                            285 -> 258
                            else -> error("Illegal valueLiteral=$valueLiteral")
                        }
                        val valueDistance = huffmanDistance.read(from = bitReader)
                        debugLog { "valueDistance=$valueDistance" }
                        val distance = when (valueDistance) {
                            in 0..3 -> valueDistance + 1
                            4, 5 -> ((valueDistance - 4) shl 1) + 5 + bitReader.readBits(count = 1)
                            6, 7 -> ((valueDistance - 6) shl 2) + 9 + bitReader.readBits(count = 2)
                            8, 9 -> ((valueDistance - 8) shl 3) + 17 + bitReader.readBits(count = 3)
                            10, 11 -> ((valueDistance - 10) shl 4) + 33 + bitReader.readBits(count = 4)
                            12, 13 -> ((valueDistance - 12) shl 5) + 65 + bitReader.readBits(count = 5)
                            14, 15 -> ((valueDistance - 14) shl 6) + 129 + bitReader.readBits(count = 6)
                            16, 17 -> ((valueDistance - 16) shl 7) + 257 + bitReader.readBits(count = 7)
                            18, 19 -> ((valueDistance - 18) shl 8) + 513 + bitReader.readBits(count = 8)
                            20, 21 -> ((valueDistance - 20) shl 9) + 1025 + bitReader.readBits(count = 9)
                            22, 23 -> ((valueDistance - 22) shl 10) + 2049 + bitReader.readBits(count = 10)
                            24, 25 -> ((valueDistance - 24) shl 11) + 4097 + bitReader.readBits(count = 11)
                            26, 27 -> ((valueDistance - 26) shl 12) + 8193 + bitReader.readBits(count = 12)
                            28, 29 -> ((valueDistance - 28) shl 13) + 16385 + bitReader.readBits(count = 13)
                            else -> error("Illegal valueDistance=$valueDistance")
                        }
                        debugLog { "length=$length, distance=$distance" }
                        slidingWindow.slidingCopy(length = length, distance = distance)
                    }
                }
            }
        } while (bfinal == 0b0)
        slidingWindow.flush()
    }
}

private class OptimizedDeflateImpl : Deflate {
    companion object {
        private val LENGTH_BASE = intArrayOf(
            3, 4, 5, 6, 7, 8, 9, 10, 11, 13,
            15, 17, 19, 23, 27, 31, 35, 43, 51, 59,
            67, 83, 99, 115, 131, 163, 195, 227, 258,
        )
        private val LENGTH_EXTRA_BITS = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1,
            1, 1, 2, 2, 2, 2, 3, 3, 3, 3,
            4, 4, 4, 4, 5, 5, 5, 5, 0,
        )
        private val DISTANCE_BASE = intArrayOf(
            1, 2, 3, 4, 5, 7, 9, 13, 17, 25,
            33, 49, 65, 97, 129, 193, 257, 385, 513, 769,
            1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
        )
        private val DISTANCE_EXTRA_BITS = intArrayOf(
            0, 0, 0, 0, 1, 1, 2, 2, 3, 3,
            4, 4, 5, 5, 6, 6, 7, 7, 8, 8,
            9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
        )
    }

    class SlidingWindow(private val sink: Sink, bitSize: Int = 15, private val maxWriteSize: Int) {
        private val windowSize = 1 shl bitSize
        private val window = ByteArray(size = windowSize)
        private val indexMask = windowSize - 1
        private var written = 0
        private var current = 0
        private var writeCount: Long = 0

        private fun sinkWriteIfNeeded(requireWriteSize: Int, written: Int, current: Int) {
            when {
                written < current -> {
                    if (current - written + requireWriteSize >= windowSize) {
                        writeCount += current - written
                        check(value = writeCount <= maxWriteSize) { "writeCount=$writeCount > maxWriteSize=$maxWriteSize" }
                        sink.write(source = window, startIndex = written, endIndex = current)
                        this.written = current
                    }
                }

                written > current -> {
                    if (current - written + windowSize + requireWriteSize >= windowSize) {
                        writeCount += current - written + windowSize
                        check(value = writeCount <= maxWriteSize) { "writeCount=$writeCount > maxWriteSize=$maxWriteSize" }
                        sink.write(source = window, startIndex = written)
                        sink.write(source = window, startIndex = 0, endIndex = current)
                        this.written = current
                    }
                }
            }
        }

        fun write(byte: Byte) {
            val current = current
            sinkWriteIfNeeded(requireWriteSize = 1, written = written, current = current)
            window[current] = byte
            this.current = (current + 1) and indexMask
        }

        fun write(source: ByteArray) {
            val current = current
            sinkWriteIfNeeded(requireWriteSize = source.size, written = written, current = current)
            if (source.size > windowSize) {
                sink.write(source = source)
                source.copyInto(
                    destination = window,
                    destinationOffset = 0,
                    startIndex = source.size - windowSize,
                    endIndex = source.size,
                )
                this.current = 0
                this.written = 0
            } else {
                val sliceIndex = source.size.coerceAtMost(maximumValue = windowSize - current)
                source.copyInto(
                    destination = window,
                    destinationOffset = current,
                    startIndex = 0,
                    endIndex = sliceIndex,
                )
                source.copyInto(
                    destination = window,
                    destinationOffset = 0,
                    startIndex = sliceIndex,
                    endIndex = source.size,
                )
                this.current = (current + source.size) and indexMask
            }
        }

        fun slidingCopy(length: Int, distance: Int) {
            val current = current
            sinkWriteIfNeeded(requireWriteSize = length, written = written, current = current)
            val startSrcIndex = (current - distance).let { base -> if (base < 0) base + windowSize else base }
            val endSrcIndex = (startSrcIndex + length) and indexMask
            if (length <= 128 || startSrcIndex > endSrcIndex || current + length > windowSize || length > distance) {
                var srcIndex = startSrcIndex
                while (srcIndex != endSrcIndex) {
                    window[this.current] = window[srcIndex]
                    this.current = (this.current + 1) and indexMask
                    srcIndex = (srcIndex + 1) and indexMask
                }
            } else {
                window.copyInto(
                    destination = window,
                    destinationOffset = current,
                    startIndex = startSrcIndex,
                    endIndex = endSrcIndex,
                )
                this.current = (current + length) and indexMask
            }
        }

        fun flush() {
            sinkWriteIfNeeded(requireWriteSize = windowSize, written = written, current = current)
        }
    }

    override fun uncompressDeflate(from: Source, to: Sink, maxWriteSize: Int) {
        val bitReader = BitReader(source = from)
        val huffmanForLength = Huffman()
        val huffmanLiteral = Huffman()
        val huffmanDistance = Huffman()
        val slidingWindow = SlidingWindow(sink = to, maxWriteSize = maxWriteSize)
        do {
            val bfinal = bitReader.readBits(count = 1)
            debugLog { "BFINAL:$bfinal" }
            val btype = bitReader.readBits(count = 2)
            debugLog { "BTYPE:$btype" }
            if (btype == 0b00) {
                bitReader.clearBitBuffer()
                val len = from.readUShortLe()
                val nlen = from.readUShortLe()
                check(value = len == nlen.inv()) { "Illegal value. LEN=$len, NLEN=$nlen" }
                slidingWindow.write(source = from.readByteArray(byteCount = len.toInt()))
            } else {
                when (btype) {
                    // Fixed Huffman codes
                    0b01 -> {
                        huffmanLiteral.setBitLengths(bitLengths = FIXED_LITERAL_BIT_LENGTH)
                        huffmanDistance.setBitLengths(bitLengths = FIXED_DISTANCE_BIT_LENGTH)
                    }
                    // Dynamic Huffman codes
                    0b10 -> {
                        // 5 Bits : HLIT, # of Literal/Length codes-257 (257-286) + 257
                        val hlit = bitReader.readBits(count = 5) + 257
                        // 5 Bits : HDIST, # of Distance codes-1 (1-32)
                        val hdist = bitReader.readBits(count = 5) + 1
                        // 4 Bits : HCLEN, # of Code Length codes-4 (4-19)
                        val hclen = bitReader.readBits(count = 4) + 4
                        debugLog { "hlit=$hlit, hdist=$hdist, hclen=$hclen" }
                        //
                        val lengthForLength = HCLEN_CODE_LENGTH.take(n = hclen)
                            .associateWith { bitReader.readBits(count = 3) }
                            .let { map -> HCLEN_CODE_LENGTH.indices.map { index -> map.getOrElse(key = index) { 0 } } }
                        huffmanForLength.setBitLengths(bitLengths = lengthForLength)
                        debugLog { huffmanForLength.toString() }
                        val hlithdist = hlit + hdist
                        val lengthList = mutableListOf<Int>()
                        while (lengthList.size < hlithdist) {
                            when (val l = huffmanForLength.read(from = bitReader)) {
                                // 0 - 15: Represent code lengths of 0 - 15
                                in 0..15 -> lengthList += l
                                //     16: Copy the previous code length 3 - 6 times.
                                //         The next 2 bits indicate repeat length
                                //               (0 = 3, ... , 3 = 6)
                                //            Example:  Codes 8, 16 (+2 bits 11),
                                //                      16 (+2 bits 10) will expand to
                                //                      12 code lengths of 8 (1 + 6 + 5)
                                16 -> {
                                    val repeatValue = lengthList.last()
                                    val repeatCount = bitReader.readBits(count = 2) + 3
                                    repeat(times = repeatCount) { lengthList += repeatValue }
                                }
                                //     17: Repeat a code length of 0 for 3 - 10 times.
                                //         (3 bits of length)
                                17 -> {
                                    val repeatCount = bitReader.readBits(count = 3) + 3
                                    repeat(times = repeatCount) { lengthList += 0 }
                                }
                                //     18: Repeat a code length of 0 for 11 - 138 times
                                //         (7 bits of length)
                                18 -> {
                                    val repeatCount = bitReader.readBits(count = 7) + 11
                                    repeat(times = repeatCount) { lengthList += 0 }
                                }
                                //
                                else -> error("Not $l in 0..18")
                            }
                        }
                        check(value = lengthList.size == hlithdist) { "size=${lengthList.size} != HLIT+HDIST=$hlithdist" }
                        huffmanLiteral.setBitLengths(bitLengths = lengthList.subList(0, hlit))
                        huffmanDistance.setBitLengths(bitLengths = lengthList.subList(hlit, hlithdist))
                    }
                    // Reserved
                    else -> error("Illegal BTYPE=${btype.toString(radix = 2).padStart(length = 2, padChar = '0')}")
                }
                while (true) {
                    val valueLiteral = huffmanLiteral.read(from = bitReader)
                    debugLog { "valueLiteral=$valueLiteral" }
                    if (valueLiteral < 256) {
                        slidingWindow.write(byte = valueLiteral.toByte())
                    } else if (valueLiteral == 256) {
                        break
                    } else {
                        val lengthIndex = valueLiteral - 257
                        val length = LENGTH_BASE[lengthIndex] +
                                bitReader.readBits(count = LENGTH_EXTRA_BITS[lengthIndex])
                        val valueDistance = huffmanDistance.read(from = bitReader)
                        debugLog { "valueDistance=$valueDistance" }
                        val distance = DISTANCE_BASE[valueDistance] +
                                bitReader.readBits(count = DISTANCE_EXTRA_BITS[valueDistance])
                        debugLog { "length=$length, distance=$distance" }
                        slidingWindow.slidingCopy(length = length, distance = distance)
                    }
                }
            }
        } while (bfinal == 0b0)
        slidingWindow.flush()
    }
}
