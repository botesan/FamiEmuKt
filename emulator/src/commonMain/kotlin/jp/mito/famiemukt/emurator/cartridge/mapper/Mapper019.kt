package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge

@OptIn(ExperimentalUnsignedTypes::class)
class Mapper019(
    cartridge: Cartridge,
    prgRom: UByteArray,
    chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    override fun readPRG(address: Int): UByte {
        TODO("未実装：Mapper019")
    }

    override fun readCHR(address: Int): UByte {
        TODO("未実装：Mapper019")
    }
}
