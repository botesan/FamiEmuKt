package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge

@OptIn(ExperimentalUnsignedTypes::class)
class Mapper000(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    override fun readPRG(address: Int): UByte {
        // 必要によってミラー
        val index = (address - 0x8000).let { if (it < prgRom.size) it else it - 0x4000 }
        return prgRom[index]
    }

    override fun readCHR(address: Int): UByte = chrRom[address]
}
