package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cartridge.Mirroring

/*
https://www.nesdev.org/wiki/MMC4
The Nintendo MMC4 is an ASIC mapper, used on the FxROM board set.
The iNES format assigns mapper 10 to these boards.
The chip first appeared in August 1988.
Nintendo's MMC2, used in PxROM boards, is a similar mapper with 8 KB switchable PRG ROM banks, a 24 KB fixed PRG ROM bank,
no PRG RAM, and a slightly different behaviour in auto-switching on the left (low) pattern table.
This page only explains the differences, see MMC2 for full details on the rest of the mapper.

Banks
CPU $6000-$7FFF: 8 KB fixed PRG RAM bank
CPU $8000-$BFFF: 16 KB switchable PRG ROM bank
CPU $C000-$FFFF: 16 KB PRG ROM bank, fixed to the last bank
PPU $0000-$0FFF: Two 4 KB switchable CHR ROM banks
PPU $1000-$1FFF: Two 4 KB switchable CHR ROM banks
When the PPU reads from specific tiles in the pattern table during rendering,
the MMC4 sets a latch that tells it to use a different 4 KB bank number.
On the background layer, this has the effect of setting a different bank for all tiles to the right of a given tile,
virtually increasing the tile count limit from 256 to 512 without monopolising the CPU.

PPU reads $0FD8 through $0FDF: latch 0 is set to $FD
PPU reads $0FE8 through $0FEF: latch 0 is set to $FE
PPU reads $1FD8 through $1FDF: latch 1 is set to $FD
PPU reads $1FE8 through $1FEF: latch 1 is set to $FE

https://www.nesdev.org/wiki/MMC2
MMC2
Banks
CPU $6000-$7FFF: 8 KB PRG RAM bank (PlayChoice version only; contains a 6264 and 74139)
CPU $8000-$9FFF: 8 KB switchable PRG ROM bank
CPU $A000-$FFFF: Three 8 KB PRG ROM banks, fixed to the last three banks
PPU $0000-$0FFF: Two 4 KB switchable CHR ROM banks
PPU $1000-$1FFF: Two 4 KB switchable CHR ROM banks
The two 4 KB PPU banks each have two 4 KB banks,
which can be switched during rendering by using the special tiles $FD or $FE in either a sprite or the background.
See CHR banking below.
 */
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper010(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    private val lastPRGBankNo: Int = cartridge.information.prgRom16Units - 1
    private var prgBank: Int = 0
    private var chrBank0FD: Int = 0
    private var chrBank0FE: Int = 0
    private var chrBank1FD: Int = 0
    private var chrBank1FE: Int = 0
    private var latch0FD: Boolean = true
    private var latch1FD: Boolean = true
    override var mirroring: Mirroring = defaultMirroring(cartridge.information)
        private set

    /*  The MMC4 has 6 registers at $A000-$AFFF, $B000-$BFFF, $C000-$CFFF, $D000-$DFFF, $E000-$EFFF and $F000-$FFFF.
        Only $A000-$AFFF is covered here. For the rest of the registers, see MMC2.
        PRG ROM bank select ($A000-$AFFF)
        7  bit  0
        ---- ----
        xxxx PPPP
             ||||
             ++++- Select 16 KB PRG ROM bank for CPU $8000-$BFFF */
    override fun writePRG(address: Int, value: UByte) {
        when (address) {
            /*  PRG ROM bank select ($A000-$AFFF)
                7  bit  0
                ---- ----
                xxxx PPPP
                     ||||
                     ++++- Select 16 KB PRG ROM bank for CPU $8000-$BFFF */
            in 0xA000..0xAFFF -> prgBank = value.toInt() and 0b0000_1111
            /*  CHR ROM $FD/0000 bank select ($B000-$BFFF)
                7  bit  0
                ---- ----
                xxxC CCCC
                   | ||||
                   +-++++- Select 4 KB CHR ROM bank for PPU $0000-$0FFF
                           used when latch 0 = $FD */
            in 0xB000..0xBFFF -> chrBank0FD = value.toInt() and 0b0001_1111
            /*  CHR ROM $FE/0000 bank select ($C000-$CFFF)
                7  bit  0
                ---- ----
                xxxC CCCC
                   | ||||
                   +-++++- Select 4 KB CHR ROM bank for PPU $0000-$0FFF
                           used when latch 0 = $FE */
            in 0xC000..0xCFFF -> chrBank0FE = value.toInt() and 0b0001_1111
            /*  CHR ROM $FD/1000 bank select ($D000-$DFFF)
                7  bit  0
                ---- ----
                xxxC CCCC
                   | ||||
                   +-++++- Select 4 KB CHR ROM bank for PPU $1000-$1FFF
                           used when latch 1 = $FD */
            in 0xD000..0xDFFF -> chrBank1FD = value.toInt() and 0b0001_1111
            /*  CHR ROM $FE/1000 bank select ($E000-$EFFF)
                7  bit  0
                ---- ----
                xxxC CCCC
                   | ||||
                   +-++++- Select 4 KB CHR ROM bank for PPU $1000-$1FFF
                           used when latch 1 = $FE */
            in 0xE000..0xEFFF -> chrBank1FE = value.toInt() and 0b0001_1111
            /*  Mirroring ($F000-$FFFF)
                7  bit  0
                ---- ----
                xxxx xxxM
                        |
                        +- Select nametable mirroring (0: vertical; 1: horizontal) */
            in 0xF000..0xFFFF ->
                mirroring = if (value.toInt() and 0x01 == 0) Mirroring.Vertical else Mirroring.Horizontal
        }
    }

    override fun readPRG(address: Int): UByte {
        // CPU $8000-$BFFF: 16 KB switchable PRG ROM bank
        // CPU $C000-$FFFF: 16 KB PRG ROM bank, fixed to the last bank
        val bankNo = when (address) {
            in 0x8000..0xBFFF -> prgBank
            in 0xC000..0xFFFF -> lastPRGBankNo
            else -> error("Illegal address=${address.toString(radix = 16)}")
        }
        val index = (bankNo shl 14) or (address and 0x3FFF)
        return prgRom[index]
    }

    override fun readCHR(address: Int): UByte {
        // MMC4
        // PPU $0000-$0FFF: Two 4 KB switchable CHR ROM banks
        // PPU $1000-$1FFF: Two 4 KB switchable CHR ROM banks
        // When the PPU reads from specific tiles in the pattern table during rendering,
        // the MMC4 sets a latch that tells it to use a different 4 KB bank number.
        // On the background layer, this has the effect of setting a different bank for all tiles to the right of a given tile,
        // virtually increasing the tile count limit from 256 to 512 without monopolising the CPU.
        // PPU reads $0FD8 through $0FDF: latch 0 is set to $FD
        // PPU reads $0FE8 through $0FEF: latch 0 is set to $FE
        // PPU reads $1FD8 through $1FDF: latch 1 is set to $FD
        // PPU reads $1FE8 through $1FEF: latch 1 is set to $FE
        val bankNo = when (address) {
            in 0x0000..0x0FFF -> if (latch0FD) chrBank0FD else chrBank0FE
            in 0x1000..0x1FFF -> if (latch1FD) chrBank1FD else chrBank1FE
            else -> error("Illegal address=${address.toString(radix = 16)}")
        }
        when (address) {
            in 0x0FD8..0x0FDF -> latch0FD = true
            in 0x0FE8..0x0FEF -> latch0FD = false
            in 0x1FD8..0x1FDF -> latch1FD = true
            in 0x1FE8..0x1FEF -> latch1FD = false
            else -> Unit
        }
        val index = (bankNo shl 12) or (address and 0x0FFF)
        return chrRom[index]
    }

    // ファイアーエムブレム外伝で書き込みあり／デバッグとかで使用したいた？
    override fun writeExt(address: Int, value: UByte) = Unit
}
