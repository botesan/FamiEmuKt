package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cartridge.Mirroring
import jp.mito.famiemukt.emurator.cartridge.StateObserver
import jp.mito.famiemukt.emurator.cartridge.StateObserverAdapter
import jp.mito.famiemukt.emurator.util.BIT_MASK_6
import jp.mito.famiemukt.emurator.util.BIT_MASK_7

/*
https://www.nesdev.org/wiki/INES_Mapper_118
Registers
The behavior of these boards differs from that of a typical MMC3 board in the use of the upper CHR address line.

This board relies on the fact that the MMC3's CHR bank circuit ignores A13 when calculating CHR A10-A17,
responding to nametable fetches from $2000-$2FFF the same way as fetches from the first pattern table at $0000-$0FFF.
This means that the 1KB/2KB banking scheme used for CHR bankswitching is active even during nametable fetches
while the CHR ROM/RAM is disabled.

However, on these particular boards, the CHR bankswitching directly affects the mirroring mapping the nametable RAM.
This allows programs to select which nametable is mapped to each slot, much like CHR banks are mapped to pattern table slots,
in either two 2KB banks (allowing only single-screen or horizontal mirroring) or four 1KB banks
(allowing all mirroring modes one can think of, because this is equal to the size of a nametable) at the price of
mapping the 1KB CHR banks to the first pattern table by setting bit 7 of $8000.
If the IRQ counter is being used in a standard way, this involves having sprites bankswitched in 2KB banks and backgrounds in 1KB banks.

Bank data ($8001-$9FFF, odd)
7  bit  0
---- ----
MDDD DDDD
|||| ||||
|+++-++++- New bank value, based on last value written to Bank select register
|          0: Select 2 KB CHR bank at PPU $0000-$07FF (or $1000-$17FF);
|          1: Select 2 KB CHR bank at PPU $0800-$0FFF (or $1800-$1FFF);
|          2: Select 1 KB CHR bank at PPU $1000-$13FF (or $0000-$03FF);
|          3: Select 1 KB CHR bank at PPU $1400-$17FF (or $0400-$07FF);
|          4: Select 1 KB CHR bank at PPU $1800-$1BFF (or $0800-$0BFF);
|          5: Select 1 KB CHR bank at PPU $1C00-$1FFF (or $0C00-$0FFF);
|          6, 7: as standard MMC3
|
+--------- Mirroring configuration, based on the last value
           written to Bank select register
           0: Select Nametable at PPU $2000-$27FF
           1: Select Nametable at PPU $2800-$2FFF
           Note : Those bits are ignored if corresponding CHR banks
           are mapped at $1000-$1FFF via $8000.

           2 : Select Nametable at PPU $2000-$23FF
           3 : Select Nametable at PPU $2400-$27FF
           4 : Select Nametable at PPU $2800-$2BFF
           5 : Select Nametable at PPU $2C00-$2FFF
           Note : Those bits are ignored if corresponding CHR banks
           are mapped at $1000-$1FFF via $8000.
Mirroring ($A000-$BFFE, even)
7  bit  0
---- ----
xxxx xxxM
        |
        +- Mirroring
           This bit is bypassed by the configuration described above, so writing here has no effect.
Note: In theory, the CHR limitation is 256 KB like all MMC3 boards. But because CHR A17 has another usage,
having a CHR greater than 128 KB would require very careful CHR ROM layout because CHR bankswitching and mirroring
will be linked through the same selection bits.
Probably for this reason, official Nintendo TLSROM boards doesn't allow for 256 KB CHR-ROMs.
However, a mapper 118 game that uses a third party MMC3/board, using 1-screen mirroring could draw the playfield with
the lower 128 KB of CHR ROM and the lower nametable, and draw the status bar and menus with the upper 128 KB of CHR ROM
and the upper nametable. Sprite tile banks could go in whatever space remains in either or both halves.
*/
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper118(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    // Bank size 8k
    private val lastPRGBankNo: Int = cartridge.information.prgRom16Units * 2 - 1
    private val prgBankNoR: IntArray = IntArray(size = 8)
    private val nametableNos: IntArray = IntArray(size = 8)
    private var selectedR: Int = 0

    private var isPRGMode0: Boolean = false
    private var isCHRMode0: Boolean = false
    private var isIRQEnabled: Boolean = false
    private var irqCounter: Int = 0
    private var irqCounterTemp: Int = 0
    private var isReloadIRQCounter: Boolean = false

    override val mirroring: Mirroring = Mirroring.Other
    override val stateObserver: StateObserver = object : StateObserverAdapter() {
        override fun notifyRisingA12PPU() {
            /* Counter operation:
                When the IRQ is clocked (filtered A12 0→1), the counter value is checked - if zero or the reload flag is true,
                it's reloaded with the IRQ latched value at $C000; otherwise, it decrements.
                If the IRQ counter is zero and IRQs are enabled ($E001), an IRQ is triggered.
                The "alternate revision" checks the IRQ counter transition 1→0, whether from decrementing or reloading. */
            if (irqCounter == 0 || isReloadIRQCounter) {
                irqCounter = irqCounterTemp
                isReloadIRQCounter = false
            } else {
                irqCounter--
            }
            if (irqCounter == 0 && isIRQEnabled) {
                interrupter.requestOnIRQ() // TODO: 合ってる？
            }
        }
    }

    override fun writePRG(address: Int, value: UByte) {
        when (address) {
            in 0x8000..0x9FFF -> {
                if (address and 0x01 == 0x00) {
                    /* https://www.nesdev.org/wiki/MMC3
                       Bank select ($8000-$9FFE, even)
                        7  bit  0
                        ---- ----
                        CPMx xRRR
                        |||   |||
                        |||   +++- Specify which bank register to update on next write to Bank Data register
                        |||          110: R6: Select 8 KB PRG ROM bank at $8000-$9FFF (or $C000-$DFFF)
                        |||          111: R7: Select 8 KB PRG ROM bank at $A000-$BFFF
                        ||+------- Nothing on the MMC3, see MMC6
                        |+-------- PRG ROM bank mode (0: $8000-$9FFF swappable,
                        |                                $C000-$DFFF fixed to second-last bank;
                        |                             1: $C000-$DFFF swappable,
                        |                                $8000-$9FFF fixed to second-last bank)
                        +--------- CHR A12 inversion (0: two 2 KB banks at $0000-$0FFF,
                                                         four 1 KB banks at $1000-$1FFF;
                                                      1: two 2 KB banks at $1000-$1FFF,
                                                         four 1 KB banks at $0000-$0FFF)
                       https://www.nesdev.org/wiki/MMC6
                       Bank select ($8000-$9FFE, even)
                        7  bit  0
                        ---- ----
                        CPMx xRRR
                        |||   |||
                        |||   +++- Specify which bank register to update on next write to Bank Data register
                        ||+------- PRG RAM enable
                        |+-------- PRG ROM bank configuration (0: $8000-$9FFF swappable, $C000-$DFFF fixed to second-last bank;
                        |                                      1: $C000-$DFFF swappable, $8000-$9FFF fixed to second-last bank)
                        +--------- CHR ROM bank configuration (0: two 2 KB banks at $0000-$0FFF, four 1 KB banks at $1000-$1FFF;
                                                               1: four 1 KB banks at $0000-$0FFF, two 2 KB banks at $1000-$1FFF) */
                    selectedR = value.toInt() and 0x07
                    isPRGMode0 = (value.toInt() and BIT_MASK_6.toInt() == 0)
                    isCHRMode0 = (value.toInt() and BIT_MASK_7.toInt() == 0)
                } else {
                    /* Bank data ($8001-$9FFF, odd)
                        7  bit  0
                        ---- ----
                        DDDD DDDD
                        |||| ||||
                        ++++-++++- New bank value, based on last value written to Bank select register (mentioned above)
                        R6 and R7 will ignore the top two bits, as the MMC3 has only 6 PRG ROM address lines.
                        Some romhacks rely on an 8-bit extension of R6/7 for oversized PRG-ROM,
                        but this is deliberately not supported by many emulators.
                        See iNES Mapper 004 below.
                        R0 and R1 ignore the bottom bit, as the value written still counts banks in 1KB units
                        but odd numbered banks can't be selected. */
                    /* https://www.nesdev.org/wiki/INES_Mapper_118
                        Bank data ($8001-$9FFF, odd)
                        7  bit  0
                        ---- ----
                        MDDD DDDD
                        |||| ||||
                        |+++-++++- New bank value, based on last value written to Bank select register
                        |          0: Select 2 KB CHR bank at PPU $0000-$07FF (or $1000-$17FF);
                        |          1: Select 2 KB CHR bank at PPU $0800-$0FFF (or $1800-$1FFF);
                        |          2: Select 1 KB CHR bank at PPU $1000-$13FF (or $0000-$03FF);
                        |          3: Select 1 KB CHR bank at PPU $1400-$17FF (or $0400-$07FF);
                        |          4: Select 1 KB CHR bank at PPU $1800-$1BFF (or $0800-$0BFF);
                        |          5: Select 1 KB CHR bank at PPU $1C00-$1FFF (or $0C00-$0FFF);
                        |          6, 7: as standard MMC3
                        +--------- Mirroring configuration, based on the last value written to Bank select register
                                   0: Select Nametable at PPU $2000-$27FF
                                   1: Select Nametable at PPU $2800-$2FFF
                                   Note : Those bits are ignored if corresponding CHR banks are mapped at $1000-$1FFF via $8000.
                                   2 : Select Nametable at PPU $2000-$23FF
                                   3 : Select Nametable at PPU $2400-$27FF
                                   4 : Select Nametable at PPU $2800-$2BFF
                                   5 : Select Nametable at PPU $2C00-$2FFF
                                   Note : Those bits are ignored if corresponding CHR banks are mapped at $1000-$1FFF via $8000. */
                    nametableNos[selectedR] = value.toInt() ushr 7
                    prgBankNoR[selectedR] = value.toInt() and 0b0111_1111
                }
            }

            in 0xA000..0xBFFF -> {
                @Suppress("ControlFlowWithEmptyBody")
                if (address and 0x01 == 0x00) {
                    /* Mirroring ($A000-$BFFE, even)
                        7  bit  0
                        ---- ----
                        xxxx xxxM
                                |
                                +- Mirroring
                                   This bit is bypassed by the configuration described above, so writing here has no effect. */
                    // なにもしない
                } else {
                    /* https://www.nesdev.org/wiki/MMC3
                       PRG RAM protect ($A001-$BFFF, odd)
                        7  bit  0
                        ---- ----
                        RWXX xxxx
                        ||||
                        ||++------ Nothing on the MMC3, see MMC6
                        |+-------- Write protection (0: allow writes; 1: deny writes)
                        +--------- PRG RAM chip enable (0: disable; 1: enable)
                        Disabling PRG RAM through bit 7 causes reads from the PRG RAM region to return open bus.
                        Though these bits are functional on the MMC3, their main purpose is to write-protect save RAM during power-off.
                        Many emulators choose not to implement them as part of iNES Mapper 4 to avoid an incompatibility with the MMC6.
                        See iNES Mapper 004 and MMC6 below.
                       https://www.nesdev.org/wiki/MMC6
                       PRG RAM protect ($A001-$BFFF, odd)
                        7  bit  0
                        ---- ----
                        HhLl xxxx
                        ||||
                        |||+------ Enable writing to RAM at $7000-$71FF
                        ||+------- Enable reading RAM at $7000-$71FF
                        |+-------- Enable writing to RAM at $7200-$73FF
                        +--------- Enable reading RAM at $7200-$73FF */
                    // なにもしない
                }
            }

            in 0xC000..0xDFFF -> {
                if (address and 0x01 == 0x00) {
                    /* IRQ latch ($C000-$DFFE, even)
                        7  bit  0
                        ---- ----
                        DDDD DDDD
                        |||| ||||
                        ++++-++++- IRQ latch value
                        This register specifies the IRQ counter reload value.
                        When the IRQ counter is zero (or a reload is requested through $C001),
                        this value will be copied to the IRQ counter at the NEXT rising edge of the PPU address,
                        presumably at PPU cycle 260 of the current scanline. */
                    irqCounterTemp = value.toInt()
                } else {
                    /* IRQ reload ($C001-$DFFF, odd)
                        7  bit  0
                        ---- ----
                        xxxx xxxx
                        Writing any value to this register clears the MMC3 IRQ counter immediately,
                        and then reloads it at the NEXT rising edge of the PPU address, presumably at PPU cycle 260 of the current scanline. */
                    isReloadIRQCounter = true
                }
            }

            in 0xE000..0xFFFF -> {
                if (address and 0x01 == 0x00) {
                    /* IRQ disable ($E000-$FFFE, even)
                        7  bit  0
                        ---- ----
                        xxxx xxxx
                        Writing any value to this register will disable MMC3 interrupts AND acknowledge any pending interrupts. */
                    isIRQEnabled = false
                    interrupter.requestOffIRQ() // TODO: 合ってる？
                } else {
                    /* IRQ enable ($E001-$FFFF, odd)
                        7  bit  0
                        ---- ----
                        xxxx xxxx
                        Writing any value to this register will enable MMC3 interrupts. */
                    isIRQEnabled = true
                }
            }
        }
    }

    /*
        Banks
        CPU $8000-$9FFF (or $C000-$DFFF): 8 KB switchable PRG ROM bank
        CPU $A000-$BFFF: 8 KB switchable PRG ROM bank
        CPU $C000-$DFFF (or $8000-$9FFF): 8 KB PRG ROM bank, fixed to the second-last bank
        CPU $E000-$FFFF: 8 KB PRG ROM bank, fixed to the last bank
     */
    override fun readPRG(address: Int): UByte {
        /* PRG Banks
            Bit 6 of the last value written to $8000 swaps the PRG windows at $8000 and $C000.
            The MMC3 uses one map if bit 6 was cleared to 0 (value & $40 == $00) and another if set to 1 (value & $40 == $40).
            PRG map mode →	$8000.D6 = 0	$8000.D6 = 1
            CPU Bank	Value of MMC3 register
            $8000-$9FFF      R6             (-2)
            $A000-$BFFF      R7              R7
            $C000-$DFFF     (-2)             R6
            $E000-$FFFF     (-1)            (-1)
            (-1) : the last bank
            (-2) : the second last bank
            Because the values in R6, R7, and $8000 are unspecified at power on, the reset vector must point into $E000-$FFFF,
            and code must initialize these before jumping out of $E000-$FFFF. */
        val bankNo = if (isPRGMode0) {
            when (address) {
                in 0x8000..0x9FFF -> prgBankNoR[6]
                in 0xA000..0xBFFF -> prgBankNoR[7]
                in 0xC000..0xDFFF -> lastPRGBankNo - 1
                in 0xE000..0xFFFF -> lastPRGBankNo
                else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
            }
        } else {
            when (address) {
                in 0x8000..0x9FFF -> lastPRGBankNo - 1
                in 0xA000..0xBFFF -> prgBankNoR[7]
                in 0xC000..0xDFFF -> prgBankNoR[6]
                in 0xE000..0xFFFF -> lastPRGBankNo
                else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
            }
        }
        val index = (bankNo shl 13) or (address and 0x1FFF)
        return prgRom[index]
    }

    /*
        Banks
        PPU $0000-$07FF (or $1000-$17FF): 2 KB switchable CHR bank
        PPU $0800-$0FFF (or $1800-$1FFF): 2 KB switchable CHR bank
        PPU $1000-$13FF (or $0000-$03FF): 1 KB switchable CHR bank
        PPU $1400-$17FF (or $0400-$07FF): 1 KB switchable CHR bank
        PPU $1800-$1BFF (or $0800-$0BFF): 1 KB switchable CHR bank
        PPU $1C00-$1FFF (or $0C00-$0FFF): 1 KB switchable CHR bank

        0: Select 2 KB CHR bank at PPU $0000-$07FF (or $1000-$17FF);
        1: Select 2 KB CHR bank at PPU $0800-$0FFF (or $1800-$1FFF);
        2: Select 1 KB CHR bank at PPU $1000-$13FF (or $0000-$03FF);
        3: Select 1 KB CHR bank at PPU $1400-$17FF (or $0400-$07FF);
        4: Select 1 KB CHR bank at PPU $1800-$1BFF (or $0800-$0BFF);
        5: Select 1 KB CHR bank at PPU $1C00-$1FFF (or $0C00-$0FFF);
        6, 7: as standard MMC3
    */
    override fun readCHR(address: Int): UByte {
        /* CHR Banks
            CHR map mode →	$8000.D7 = 0	$8000.D7 = 1
            PPU Bank	Value of MMC3 register
            $0000-$03FF 	R0          	R2
            $0400-$07FF 	〃              R3
            $0800-$0BFF 	R1              R4
            $0C00-$0FFF 	〃              R5
            $1000-$13FF 	R2              R0
            $1400-$17FF 	R3              〃
            $1800-$1BFF 	R4              R1
            $1C00-$1FFF 	R5              〃
            2KB banks may only select even numbered CHR banks. (The lowest bit is ignored.) */
        val bankNo = if (isCHRMode0) {
            when (address) {
                in 0x0000..0x03FF -> prgBankNoR[0] and 0xFE
                in 0x0400..0x07FF -> (prgBankNoR[0] and 0xFE) + 1
                in 0x0800..0x0BFF -> prgBankNoR[1] and 0xFE
                in 0x0C00..0x0FFF -> (prgBankNoR[1] and 0xFE) + 1
                in 0x1000..0x13FF -> prgBankNoR[2]
                in 0x1400..0x17FF -> prgBankNoR[3]
                in 0x1800..0x1BFF -> prgBankNoR[4]
                in 0x1C00..0x1FFF -> prgBankNoR[5]
                else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
            }
        } else {
            when (address) {
                in 0x0000..0x03FF -> prgBankNoR[2]
                in 0x0400..0x07FF -> prgBankNoR[3]
                in 0x0800..0x0BFF -> prgBankNoR[4]
                in 0x0C00..0x0FFF -> prgBankNoR[5]
                in 0x1000..0x13FF -> prgBankNoR[0] and 0xFE
                in 0x1400..0x17FF -> (prgBankNoR[0] and 0xFE) + 1
                in 0x1800..0x1BFF -> prgBankNoR[1] and 0xFE
                in 0x1C00..0x1FFF -> (prgBankNoR[1] and 0xFE) + 1
                else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
            }
        }
        val index = (bankNo shl 10) or (address and 0x03FF)
        return chrRom[index]
    }

    /*
      Mirroring configuration, based on the last value written to Bank select register
        0: Select Nametable at PPU $2000-$27FF
        1: Select Nametable at PPU $2800-$2FFF
        Note : Those bits are ignored if corresponding CHR banks are mapped at $1000-$1FFF via $8000.
        2 : Select Nametable at PPU $2000-$23FF
        3 : Select Nametable at PPU $2400-$27FF
        4 : Select Nametable at PPU $2800-$2BFF
        5 : Select Nametable at PPU $2C00-$2FFF
        Note : Those bits are ignored if corresponding CHR banks are mapped at $1000-$1FFF via $8000.
     */
    override fun calculateNameTableIndexMirroringOther(address: Int): Int {
        val mirroredAddress = if (isCHRMode0) {
            when (address) {
                in 0x2000..0x27FF -> (address and 0x2400.inv()) or (nametableNos[0] shl 11)
                in 0x2800..0x2FFF -> (address and 0x2C00.inv()) or (nametableNos[1] shl 11)
                else -> error("Illegal address=${address.toString(radix = 16)}")
            }
        } else {
            // TODO: 動作確認未チェック／実装合ってる？
            when (address) {
                in 0x2000..0x23FF -> (address and 0x2000.inv()) or (nametableNos[2] shl 11)
                in 0x2400..0x27FF -> (address and 0x2000.inv()) or (nametableNos[3] shl 11)
                in 0x2800..0x2BFF -> (address and 0x2800.inv()) or (nametableNos[4] shl 11)
                in 0x2C00..0x2FFF -> (address and 0x2800.inv()) or (nametableNos[5] shl 11)
                else -> error("Illegal address=${address.toString(radix = 16)}")
            }
        }
        return mirroredAddress
    }
}
