package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge

/*
https://www.nesdev.org/wiki/UxROM
Banks
CPU $8000-$BFFF: 16 KB switchable PRG ROM bank
CPU $C000-$FFFF: 16 KB PRG ROM bank, fixed to the last bank

ミラーリングはハンダ付けで固定
Solder pad config
Horizontal mirroring : 'H' disconnected, 'V' connected.
Vertical mirroring : 'H' connected, 'V' disconnected.
 */
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper002(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    private val lastPRGBankNo: Int = cartridge.information.prgRom16Units - 1
    private var bankNo: Int = 0

    /* Bank select ($8000-$FFFF)
        7  bit  0
        ---- ----
        xxxx pPPP
             ||||
             ++++- Select 16 KB PRG ROM bank for CPU $8000-$BFFF
                  (UNROM uses bits 2-0; UOROM uses bits 3-0)
        Emulator implementations of iNES mapper 2 treat this as a full 8-bit bank select register, without bus conflicts.
        This allows the mapper to be used for similar boards that are compatible.
        To make use of all 8-bits for a 4 MB PRG ROM, an NES 2.0 header must be used (iNES can only effectively go to 2 MB).
        The original UxROM boards used by Nintendo were subject to bus conflicts, and the relevant games all work around this in software.
        Some emulators (notably FCEUX) will have bus conflicts by default,
        but others have none. NES 2.0 submappers were assigned to accurately specify whether the game should be emulated with bus conflicts. */
    override fun writePRG(address: Int, value: UByte) {
        bankNo = value.toInt() and 0x0F
    }

    override fun readPRG(address: Int): UByte {
        val bankNo = when (address) {
            in 0x8000..0xBFFF -> bankNo
            in 0xC000..0xFFFF -> lastPRGBankNo
            else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
        }
        val index = (bankNo shl 14) or (address and 0x3FFF)
        return prgRom[index]
    }

    override fun readCHR(address: Int): UByte = chrRom[address]
}
