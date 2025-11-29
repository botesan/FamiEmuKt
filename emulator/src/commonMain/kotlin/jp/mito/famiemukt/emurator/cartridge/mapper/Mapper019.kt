package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cartridge.StateObserver
import jp.mito.famiemukt.emurator.cartridge.StateObserverAdapter
import jp.mito.famiemukt.emurator.util.BIT_MASK_6
import jp.mito.famiemukt.emurator.util.BIT_MASK_7

/*
  https://www.nesdev.org/wiki/INES_Mapper_019
  https://www.nesdev.org/wiki/Namco_163_audio
    Banks
    CPU $6000-$7FFF: 8 KB PRG RAM bank, if WRAM is present
    CPU $8000-$9FFF: 8 KB switchable PRG ROM bank
    CPU $A000-$BFFF: 8 KB switchable PRG ROM bank
    CPU $C000-$DFFF: 8 KB switchable PRG ROM bank
    CPU $E000-$FFFF: 8 KB PRG ROM bank, fixed to the last bank
    PPU $0000-$03FF: 1 KB switchable CHR bank
    PPU $0400-$07FF: 1 KB switchable CHR bank
    PPU $0800-$0BFF: 1 KB switchable CHR bank
    PPU $0C00-$0FFF: 1 KB switchable CHR bank
    PPU $1000-$13FF: 1 KB switchable CHR bank
    PPU $1400-$17FF: 1 KB switchable CHR bank
    PPU $1800-$1BFF: 1 KB switchable CHR bank
    PPU $1C00-$1FFF: 1 KB switchable CHR bank
    PPU $2000-$23FF: 1 KB switchable CHR bank
    PPU $2400-$27FF: 1 KB switchable CHR bank
    PPU $2800-$2BFF: 1 KB switchable CHR bank
    PPU $2C00-$2FFF: 1 KB switchable CHR bank
    These ASICs have the unusual ability to select the internal 2 KB nametable RAM as a CHR bank page,
    allowing it to be used as CHR RAM in combination with the existing CHR ROM.
*/
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper019(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    // Bank size 8k
    private val lastPRGBankNo: Int = cartridge.information.prgRom16Units * 2 - 1
    private val prgBankNoMask: Int = (lastPRGBankNo.takeHighestOneBit() shl 1) - 1
    private var selectedPRGBankNo1: Int = 0
    private var selectedPRGBankNo2: Int = 0
    private var selectedPRGBankNo3: Int = 0
    private val selectedCHRBankNos: IntArray = IntArray(size = 12) { 0 }

    private var isEnableIRQ: Boolean = false
    private var irqCounter: Int = 0
    override val stateObserver: StateObserver = object : StateObserverAdapter() {
        override fun notifyM2OneCycle() {
            if (irqCounter >= 0x7FFF) return
            irqCounter++
            if (irqCounter >= 0x7FFF) {
                if (isEnableIRQ) interrupter.requestOnIRQ()
                irqCounter = 0x7FFF
            }
        }
    }

    override fun writeExt(address: Int, value: UByte) {
        when (address) {
            // Chip RAM Data Port ($4800-$4FFF) r/w
            // See Namco 163 audio. https://www.nesdev.org/wiki/Namco_163_audio
            in 0x4800..0x4FFF -> {
                // TODO
            }
            // IRQ Counter (low) ($5000-$57FF) r/w
            // 7  bit  0
            // ---- ----
            // IIII IIII
            // |||| ||||
            // ++++-++++- Low 8 bits of IRQ counter
            in 0x5000..0x57FF -> {
                interrupter.requestOffIRQ()
                irqCounter = (irqCounter and 0xFF00) or value.toInt()
            }
            // IRQ Counter (high) / IRQ Enable ($5800-$5FFF) r/w
            // 7  bit  0
            // ---- ----
            // EIII IIII
            // |||| ||||
            // |+++-++++- High 7 bits of IRQ counter
            // +--------- IRQ Enable: (0: disabled; 1: enabled)
            in 0x5800..0x5FFF -> {
                interrupter.requestOffIRQ()
                irqCounter = (irqCounter and 0x00FF) or ((value.toInt() and 0x7F) shl 8)
                isEnableIRQ = (value.toInt() and BIT_MASK_7.toInt()) != 0
            }
        }
    }

    override fun readExt(address: Int): UByte {
        when (address) {
            // Chip RAM Data Port ($4800-$4FFF) r/w
            // See Namco 163 audio. https://www.nesdev.org/wiki/Namco_163_audio
            in 0x4800..0x4FFF -> {
                return 0U // TODO
            }
            // IRQ Counter (low) ($5000-$57FF) r/w
            // 7  bit  0
            // ---- ----
            // IIII IIII
            // |||| ||||
            // ++++-++++- Low 8 bits of IRQ counter
            in 0x5000..0x57FF -> {
                return irqCounter.toUByte()
            }
            // IRQ Counter (high) / IRQ Enable ($5800-$5FFF) r/w
            // 7  bit  0
            // ---- ----
            // EIII IIII
            // |||| ||||
            // |+++-++++- High 7 bits of IRQ counter
            // +--------- IRQ Enable: (0: disabled; 1: enabled)
            in 0x5800..0x5FFF -> {
                return if (isEnableIRQ) {
                    (irqCounter ushr 8).toUByte() or BIT_MASK_7
                } else {
                    (irqCounter ushr 8).toUByte()
                }
            }
        }
        TODO()
    }

    override fun writePRG(address: Int, value: UByte) {
        when (address) {
            // CHR and NT Select ($8000-$DFFF) w
            // --
            // Value CPU writes	Behavior
            // $00-$DF	Always selects 1KB page of CHR-ROM
            // $E0-$FF	If enabled by bit in $E800, use the NES's internal nametables (even values for A, odd values for B)
            // --
            // Write to CPU address	1KB CHR bank affected	Values ≥ $E0 denote NES NTRAM if
            // $8000-$87FF	$0000-$03FF	$E800.6 = 0
            // $8800-$8FFF	$0400-$07FF	$E800.6 = 0
            // $9000-$97FF	$0800-$0BFF	$E800.6 = 0
            // $9800-$9FFF	$0C00-$0FFF	$E800.6 = 0
            // $A000-$A7FF	$1000-$13FF	$E800.7 = 0
            // $A800-$AFFF	$1400-$17FF	$E800.7 = 0
            // $B000-$B7FF	$1800-$1BFF	$E800.7 = 0
            // $B800-$BFFF	$1C00-$1FFF	$E800.7 = 0
            // $C000-$C7FF	$2000-$23FF	always
            // $C800-$CFFF	$2400-$27FF	always
            // $D000-$D7FF	$2800-$2BFF	always
            // $D800-$DFFF	$2C00-$2FFF	always
            // --
            // It is believed, but untested, that a game could add a normal SRAM and use it in lieu of the nametable RAM;
            // if so, a game would be able to get 4-screen mirroring and many more pages of CHR-RAM.
            in 0x8000..0xDFFF -> {
                selectedCHRBankNos[(address and 0b0111_1000_0000_0000) ushr 11] = value.toInt()
            }
            // PRG Select 1 ($E000-$E7FF) w
            // 7  bit  0
            // ---- ----
            // AMPP PPPP
            // |||| ||||
            // ||++-++++- Select 8KB page of PRG-ROM at $8000
            // |+-------- Disable sound if set
            // +--------- Pin 22 (open collector) reflects the inverse of this value, unchanged by the address bus inputs.
            in 0xE000..0xE7FF -> {
                value and BIT_MASK_7 // TODO: A
                value and BIT_MASK_6 // TODO: M
                selectedPRGBankNo1 = value.toInt() and 0b0011_1111 and prgBankNoMask
            }
            // PRG Select 2 / CHR-RAM Enable ($E800-$EFFF) w
            // 7  bit  0
            // ---- ----
            // HLPP PPPP
            // |||| ||||
            // ||++-++++- Select 8KB page of PRG-ROM at $A000
            // |+-------- Disable CHR-RAM at $0000-$0FFF
            // |            0: Pages $E0-$FF use NT RAM as CHR-RAM
            // |            1: Pages $E0-$FF are the last $20 banks of CHR-ROM
            // +--------- Disable CHR-RAM at $1000-$1FFF
            //              0: Pages $E0-$FF use NT RAM as CHR-RAM
            //              1: Pages $E0-$FF are the last $20 banks of CHR-ROM
            in 0xE800..0xEFFF -> {
                value and BIT_MASK_7 // TODO: H
                value and BIT_MASK_6 // TODO: L
                selectedPRGBankNo2 = value.toInt() and 0b0011_1111 and prgBankNoMask
            }
            // PRG Select 3 ($F000-$F7FF) w
            // 7  bit  0
            // ---- ----
            // CDPP PPPP
            // |||| ||||
            // ||++-++++- Select 8KB page of PRG-ROM at $C000
            // ++-------- PPU A12, A13 and these bits control pin 44
            // Pin 44 is:
            //   if PPU A13 is high, then D
            //   if PPU A13 is low, then C bitwise-or PPU A12
            // Additionally, choosing bank $3F here replaces the CHR bank output with debugging information for the internal audio state
            in 0xF000..0xF7FF -> {
                value and BIT_MASK_7 // TODO: C
                value and BIT_MASK_6 // TODO: D
                selectedPRGBankNo3 = value.toInt() and 0b0011_1111 and prgBankNoMask
            }
            // Write Protect for External RAM AND Chip RAM Address Port ($F800-$FFFF) w
            // 7  bit  0
            // ---- ----
            // KKKK DCBA
            // |||| ||||
            // |||| |||+- 1: Write-protect 2kB window of external RAM from $6000-$67FF (0: write enable)
            // |||| ||+-- 1: Write-protect 2kB window of external RAM from $6800-$6FFF (0: write enable)
            // |||| |+--- 1: Write-protect 2kB window of external RAM from $7000-$77FF (0: write enable)
            // |||| +---- 1: Write-protect 2kB window of external RAM from $7800-$7FFF (0: write enable)
            // ++++------ Additionally the upper nybble must be equal to b0100 to enable writes
            // Any value outside of the range $40-$4E will cause all PRG RAM to be read-only.
            // Also see Namco 163 audio. https://www.nesdev.org/wiki/Namco_163_audio
            in 0xF800..0xFFFF -> {
            }
        }
    }

    override fun readBackup(address: Int): UByte {
        when (address) {
            // CPU $6000-$7FFF: 8 KB PRG RAM bank, if WRAM is present
            in 0x6000..0x7FFF -> {
                TODO()
            }

            else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
        }
    }

    override fun readPRG(address: Int): UByte {
        when (address) {
            // CPU $8000-$9FFF: 8 KB switchable PRG ROM bank
            in 0x8000..0x9FFF -> {
                val bankNo = selectedPRGBankNo1
                val index = (bankNo shl 13) or (address and 0x1FFF)
                return prgRom[index]
            }
            // CPU $A000-$BFFF: 8 KB switchable PRG ROM bank
            in 0xA000..0xBFFF -> {
                val bankNo = selectedPRGBankNo2
                val index = (bankNo shl 13) or (address and 0x1FFF)
                return prgRom[index]
            }
            // CPU $C000-$DFFF: 8 KB switchable PRG ROM bank
            in 0xC000..0xDFFF -> {
                val bankNo = selectedPRGBankNo3
                val index = (bankNo shl 13) or (address and 0x1FFF)
                return prgRom[index]
            }
            // CPU $E000-$FFFF: 8 KB PRG ROM bank, fixed to the last bank
            in 0xE000..0xFFFF -> {
                val bankNo = lastPRGBankNo
                val index = (bankNo shl 13) or (address and 0x1FFF)
                return prgRom[index]
            }

            else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
        }
    }

    override fun readCHR(address: Int): UByte {
        // PPU $0000-$03FF: 1 KB switchable CHR bank
        // PPU $0400-$07FF: 1 KB switchable CHR bank
        // PPU $0800-$0BFF: 1 KB switchable CHR bank
        // PPU $0C00-$0FFF: 1 KB switchable CHR bank
        // PPU $1000-$13FF: 1 KB switchable CHR bank
        // PPU $1400-$17FF: 1 KB switchable CHR bank
        // PPU $1800-$1BFF: 1 KB switchable CHR bank
        // PPU $1C00-$1FFF: 1 KB switchable CHR bank
        // PPU $2000-$23FF: 1 KB switchable CHR bank
        // PPU $2400-$27FF: 1 KB switchable CHR bank
        // PPU $2800-$2BFF: 1 KB switchable CHR bank
        // PPU $2C00-$2FFF: 1 KB switchable CHR bank
        // These ASICs have the unusual ability to select the internal 2 KB nametable RAM as a CHR bank page,
        // allowing it to be used as CHR RAM in combination with the existing CHR ROM.
        val bankNo = selectedCHRBankNos[address ushr 10]
        if (bankNo >= 0xE0) {
            TODO()
        } else {
            val index = (bankNo shl 10) or (address and 0x3FF)
            return chrRom[index]
        }
    }
}
