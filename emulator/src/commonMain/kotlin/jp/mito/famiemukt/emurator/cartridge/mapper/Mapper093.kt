package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge

/*
https://www.nesdev.org/wiki/INES_Mapper_093

 Registers **BUS CONFLICTS**:
 --------------------------
   $8000-FFFF:  [.PPP ...E]
     P = PRG Reg  (16k @ $8000)
     E = CHR RAM enable:
         0 = RAM is disabled; writes are ignored and reads are open bus
             (like iNES Mapper 185 except no games use this)
         1 = normal operation.
 PRG Setup:
 --------------------------
       $8000   $A000   $C000   $E000
     +---------------+---------------+
     |     $8000     |     { -1}     | 中括弧は固定、マイナスは最後の何番目のバンク、-1は一番最後のバンク
     +---------------+---------------+
 https://www.nesdev.org/wiki/INES_Mapper_DischDocs
 */
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper093(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    private val lastBankNo: Int = cartridge.information.prgRom16Units - 1
    private var bankNo: Int = 0
    private var isEnableCHRRam: Boolean = false

    override fun writePRG(address: Int, value: UByte) {
        bankNo = value.toInt() ushr 4 and 0x07
        isEnableCHRRam = (value.toInt() and 0x01 == 0x01)
    }

    override fun readPRG(address: Int): UByte {
        return if (address >= 0xC000) {
            val index = address and 0x3FFF
            val offset = lastBankNo shl 14
            prgRom[index + offset]
        } else {
            val index = address and 0x3FFF
            val offset = bankNo shl 14
            prgRom[index + offset]
        }
    }

    override fun writeCHR(address: Int, value: UByte) {
        if (isEnableCHRRam) {
            chrRom[address] = value
        }
    }

    override fun readCHR(address: Int): UByte = chrRom[address]

    // FantasyZoneで書き込みあり／デバッグとかで使用したいた？
    override fun writeExt(address: Int, value: UByte) = Unit
}
