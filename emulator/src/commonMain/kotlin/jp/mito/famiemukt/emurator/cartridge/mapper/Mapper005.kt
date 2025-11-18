package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge

@OptIn(ExperimentalUnsignedTypes::class)
class Mapper005(
    cartridge: Cartridge,
    prgRom: UByteArray,
    chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    override fun readPRG(address: Int): UByte {
        TODO("未実装：Mapper005")
    }

    override fun readCHR(address: Int): UByte {
        TODO("未実装：Mapper005")
    }
}
