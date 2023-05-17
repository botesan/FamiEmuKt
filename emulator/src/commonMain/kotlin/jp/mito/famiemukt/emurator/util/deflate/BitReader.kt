package jp.mito.famiemukt.emurator.util.deflate

import kotlinx.io.Source

interface BitReader {
    fun readBit(): Boolean
    fun readBits(count: Int): Int
    fun clearBitBuffer()
}

fun BitReader(source: Source, optimize: Boolean = true): BitReader =
    if (optimize) OptimizedBitReaderImpl(source) else SimpleBitReaderImpl(source)

// https://datatracker.ietf.org/doc/html/rfc1951

private class SimpleBitReaderImpl(private val source: Source) : BitReader {
    private var currentByte: Byte = 0
    private var currentBitIndex: Int = 0

    override fun readBit(): Boolean {
        if (currentBitIndex == 0) currentByte = source.readByte()
        val bit = (currentByte.toInt() ushr currentBitIndex) and 1
        currentBitIndex = (currentBitIndex + 1) % Byte.SIZE_BITS
        return bit == 1
    }

    override fun readBits(count: Int): Int {
        var result = 0
        repeat(times = count) {
            result = result or if (readBit()) (1 shl it) else 0
        }
        return result
    }

    override fun clearBitBuffer() {
        currentBitIndex = 0
    }
}

private class OptimizedBitReaderImpl(private val source: Source) : BitReader {
    private var currentByte: Int = 0
    private var currentBitMask: Int = 0

    override fun readBit(): Boolean {
        if (currentBitMask == 0) {
            currentByte = source.readByte().toInt()
            currentBitMask = 0b0000_0001
        }
        val bit = currentByte and currentBitMask
        currentBitMask = (currentBitMask shl 1) and 0b1111_1111
        return bit != 0
    }

    override fun readBits(count: Int): Int {
        var result = 0
        var readCount = 0
        if (count >= 5) {
            if (count >= Byte.SIZE_BITS) {
                while (readCount < Byte.SIZE_BITS && currentBitMask != 0) {
                    result = result or if (readBit()) (1 shl readCount) else 0
                    readCount++
                }
                while (readCount < count - Byte.SIZE_BITS) {
                    result = result or (source.readByte().toUByte().toInt() shl readCount)
                    readCount += Byte.SIZE_BITS
                }
            }
            val remain = count - readCount
            if (remain < 5) {
                if (remain == 4) {
                    val currentByte = source.readByte().toInt()
                    result = result or ((currentByte and 0b0000_1111) shl readCount)
                    this.currentByte = currentByte
                    currentBitMask = 0b0001_0000
                    readCount += 4
                }
            } else {
                when (currentBitMask) {
                    0b0000_0000 -> {
                        val currentByte = source.readByte().toInt()
                        result = result or ((currentByte and 0b0001_1111) shl readCount)
                        this.currentByte = currentByte
                        currentBitMask = 0b0010_0000
                        readCount += 5
                    }

                    0b0000_0010 -> {
                        result = result or (((currentByte ushr 1) and 0b0001_1111) shl readCount)
                        currentBitMask = 0b0100_0000
                        readCount += 5
                    }

                    0b0000_0100 -> {
                        result = result or (((currentByte ushr 2) and 0b0001_1111) shl readCount)
                        currentBitMask = 0b1000_0000
                        readCount += 5
                    }

                    0b0000_1000 -> {
                        result = result or (((currentByte ushr 3) and 0b0001_1111) shl readCount)
                        currentBitMask = 0b0000_0000
                        readCount += 5
                    }
                }
            }
        }
        while (readCount < count) {
            result = result or if (readBit()) (1 shl readCount) else 0
            readCount++
        }
        return result
    }

    override fun clearBitBuffer() {
        currentBitMask = 0
    }
}
