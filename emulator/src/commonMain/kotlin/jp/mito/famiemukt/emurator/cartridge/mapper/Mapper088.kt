package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge

// https://www.nesdev.org/wiki/INES_Mapper_088
// https://www.nesdev.org/wiki/INES_Mapper_206
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper088(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    override fun readPRG(address: Int): UByte {
        TODO("未実装：Mapper088")
    }

    override fun readCHR(address: Int): UByte {
        TODO("未実装：Mapper088")
    }
}
