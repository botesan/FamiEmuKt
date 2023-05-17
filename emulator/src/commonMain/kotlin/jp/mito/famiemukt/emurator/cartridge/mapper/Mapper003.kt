package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge

/*
https://www.nesdev.org/wiki/INES_Mapper_003
Bank select ($8000-$FFFF)
7  bit  0
---- ----
cccc ccCC
|||| ||||
++++-++++- Select 8 KB CHR ROM bank for PPU $0000-$1FFF
 */
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper003(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    private var bankNo: Int = 0

    override fun writePRG(address: Int, value: UByte) {
        bankNo = value.toInt() and 0x03
    }

    override fun readPRG(address: Int): UByte {
        // 必要によってミラー
        val index = (address - 0x8000).let { if (it < prgRom.size) it else it - 0x4000 }
        return prgRom[index]
    }

    override fun readCHR(address: Int): UByte = chrRom[address or (bankNo shl 13)]
}
