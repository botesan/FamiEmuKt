package jp.mito.famiemukt.emurator.ppu

import jp.mito.famiemukt.emurator.cartridge.mapper.Mapper

/*
  https://www.nesdev.org/wiki/PPU_memory_map
  Address range	Size	Description
  $0000-$0FFF	$1000	Pattern table 0
  $1000-$1FFF	$1000	Pattern table 1
  $2000-$23FF	$0400	Nametable 0
  $2400-$27FF	$0400	Nametable 1
  $2800-$2BFF	$0400	Nametable 2
  $2C00-$2FFF	$0400	Nametable 3
  $3000-$3EFF	$0F00	Mirrors of $2000-$2EFF
  $3F00-$3F1F	$0020	Palette RAM indexes
  $3F20-$3FFF	$00E0	Mirrors of $3F00-$3F1F
*/
class PPUBus(private val mapper: Mapper, private val videoRAM: VideoRAM) {
    fun readMemory(address: Int): UByte {
        return when (address) {
            // $0000-$0FFF	$1000	Pattern table 0
            // $1000-$1FFF	$1000	Pattern table 1
            in 0x0000..0x1FFF -> readPatternTable(address = address)
            // $2000-$23FF	$0400	Nametable 0
            // $2400-$27FF	$0400	Nametable 1
            // $2800-$2BFF	$0400	Nametable 2
            // $2C00-$2FFF	$0400	Nametable 3
            in 0x2000..0x2FFF -> readNameTable(address = address)
            // $3000-$3EFF	$0F00	Mirrors of $2000-$2EFF
            in 0x3000..0x3EFF -> readMemory(address = address and 0x2FFF)
            // $3F00-$3F1F	$0020	Palette RAM indexes
            //   Addresses $3F10/$3F14/$3F18/$3F1C are mirrors of $3F00/$3F04/$3F08/$3F0C.
            //   Note that this goes for writing as well as reading.
            //   A symptom of not having implemented this correctly in an emulator is the sky being black in Super Mario Bros.,
            //   which writes the backdrop color through $3F10.
            0x3F10 -> readPalletTable(index = 0x00)
            0x3F14 -> readPalletTable(index = 0x04)
            0x3F18 -> readPalletTable(index = 0x08)
            0x3F1C -> readPalletTable(index = 0x0C)
            in 0x3F00..0x3F1F -> readPalletTable(index = address and 0x1F)
            // $3F20-$3FFF	$00E0	Mirrors of $3F00-$3F1F
            in 0x3F20..0x3FFF -> readMemory(address = address and 0x3F1F)
            // 範囲外
            else -> error("Illegal address. ${address.toString(radix = 16)}")
        }
    }

    fun writeMemory(address: Int, value: UByte) {
        when (address) {
            // $0000-$0FFF	$1000	Pattern table 0
            // $1000-$1FFF	$1000	Pattern table 1
            in 0x0000..0x1FFF -> writePatternTable(address = address, value = value)
            // $2000-$23FF	$0400	Nametable 0
            // $2400-$27FF	$0400	Nametable 1
            // $2800-$2BFF	$0400	Nametable 2
            // $2C00-$2FFF	$0400	Nametable 3
            in 0x2000..0x2FFF -> writeNameTable(address = address, value = value)
            // $3000-$3EFF	$0F00	Mirrors of $2000-$2EFF
            in 0x3000..0x3EFF -> writeMemory(address = address and 0x2FFF, value = value)
            // $3F00-$3F1F	$0020	Palette RAM indexes
            //   Addresses $3F10/$3F14/$3F18/$3F1C are mirrors of $3F00/$3F04/$3F08/$3F0C.
            //   Note that this goes for writing as well as reading.
            //   A symptom of not having implemented this correctly in an emulator is the sky being black in Super Mario Bros.,
            //   which writes the backdrop color through $3F10.
            0x3F10 -> writePalletTable(index = 0x00, value = value)
            0x3F14 -> writePalletTable(index = 0x04, value = value)
            0x3F18 -> writePalletTable(index = 0x08, value = value)
            0x3F1C -> writePalletTable(index = 0x0C, value = value)
            in 0x3F00..0x3F1F -> writePalletTable(index = address and 0x1F, value = value)
            // $3F20-$3FFF	$00E0	Mirrors of $3F00-$3F1F
            in 0x3F20..0x3FFF -> writeMemory(address = address and 0x3F1F, value = value)
            // 範囲外
            else -> error("Illegal address. ${address.toString(radix = 16)}")
        }
    }

    private fun readPatternTable(address: Int): UByte {
        // カートリッジにCHRがあればカートリッジから読む
        return if (mapper.information.chrRom8Units > 0) {
            mapper.readCHR(address = address)
        } else {
            videoRAM.readPatternTable(index = address)
        }
    }

    private fun writePatternTable(address: Int, value: UByte) {
        // カートリッジにCHRがあればカートリッジへ書き込む
        if (mapper.information.chrRom8Units > 0) {
            mapper.writeCHR(address = address, value = value)
        } else {
            videoRAM.writePatternTable(index = address, value = value)
        }
    }

    private fun readNameTable(address: Int): UByte =
        videoRAM.readNameTable(mapper.calculateNameTableIndex(address))

    private fun writeNameTable(address: Int, value: UByte) =
        videoRAM.writeNameTable(mapper.calculateNameTableIndex(address), value)

    private fun readPalletTable(index: Int): UByte = videoRAM.readPalletTable(index)
    private fun writePalletTable(index: Int, value: UByte) = videoRAM.writePalletTable(index, value)

    fun debugInfo(nest: Int): String = buildString {
        append(videoRAM.debugInfo(nest = nest + 1))
    }
}
