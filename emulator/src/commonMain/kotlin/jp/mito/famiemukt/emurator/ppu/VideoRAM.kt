package jp.mito.famiemukt.emurator.ppu

import jp.mito.famiemukt.emurator.util.VisibleForTesting

/*
  https://www.nesdev.org/wiki/PPU_memory_map
  Address range	Size	Description
  $2000-$23FF	$0400	Nametable 0
  $2400-$27FF	$0400	Nametable 1
  $2800-$2BFF	$0400	Nametable 2
  $2C00-$2FFF	$0400	Nametable 3
  $3000-$3EFF	$0F00	Mirrors of $2000-$2EFF
  $3F00-$3F1F	$0020	Palette RAM indexes
  $3F20-$3FFF	$00E0	Mirrors of $3F00-$3F1F
*/
@OptIn(ExperimentalUnsignedTypes::class)
class VideoRAM {
    @Suppress("PropertyName")
    @VisibleForTesting
    val _patternTable: UByteArray get() = patternTable
    private val patternTable: UByteArray = UByteArray(size = 0x2000)

    @Suppress("PropertyName")
    @VisibleForTesting
    val _nameTable: UByteArray get() = nameTable
    private val nameTable: UByteArray = UByteArray(size = 4 * 0x0400)

    @Suppress("PropertyName")
    @VisibleForTesting
    val _palletTable: UByteArray get() = palletTable
    private val palletTable: UByteArray = UByteArray(size = 32)

    fun readPatternTable(index: Int): UByte = patternTable[index]
    fun writePatternTable(index: Int, value: UByte) {
        patternTable[index] = value
    }

    fun readNameTable(index: Int): UByte = nameTable[index]
    fun writeNameTable(index: Int, value: UByte) {
        nameTable[index] = value
    }

    fun readPalletTable(index: Int): UByte = palletTable[index]
    fun writePalletTable(index: Int, value: UByte) {
        palletTable[index] = value
    }

    fun debugInfo(nest: Int): String = buildString {
        append(" ".repeat(n = nest)).appendLine("nameTable=").appendLine(
            nameTable.windowed(size = 0x0400, step = 0x0400, partialWindows = true)
                .joinToString(separator = "\n---\n") { namespace ->
                    namespace.windowed(size = 0x20, step = 0x20, partialWindows = true)
                        .joinToString(separator = "\n") { line ->
                            line.joinToString(separator = ",") {
                                it.toString(radix = 16).padStart(length = 2, padChar = '0')
                            }
                        }
                }
        )
        append(" ".repeat(n = nest)).appendLine(
            palletTable.joinToString(prefix = "palletTable=", separator = ",") {
                it.toString(radix = 16).padStart(length = 2, padChar = '0')
            }
        )
    }
}
