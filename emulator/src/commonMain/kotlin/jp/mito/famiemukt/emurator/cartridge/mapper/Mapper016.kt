package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cartridge.Mirroring
import jp.mito.famiemukt.emurator.cartridge.StateObserver
import jp.mito.famiemukt.emurator.cartridge.StateObserverAdapter

/*
https://www.nesdev.org/wiki/INES_Mapper_016
INES Mapper 016 submapper table
Submapper #	Meaning	Note
0	Unspecified	Emulate both FCG-1/2 and LZ93D50 chips in their respective CPU address ranges.
1	LZ93D50 with 128 byte serial EEPROM (24C01)	Deprecated, use INES Mapper 159 instead.
2	Datach Joint ROM System	Deprecated, use INES Mapper 157 instead.
3	8 KiB of WRAM instead of serial EEPROM	Deprecated, use INES Mapper 153 instead.
4	FCG-1/2	Responds only in the CPU $6000-$7FFF address range; IRQ counter is not latched.
5	LZ93D50 with no or 256-byte serial EEPROM (24C02)	Responds only in the CPU $8000-$FFFF address range; IRQ counter is latched.
 */
// TODO: マッパー16の作成（ちゃんと対応するのは難しそう？）
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper016(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    private val lastPRGBankNo: Int = cartridge.information.prgRom16Units - 1
    private var prgBankNo: Int = 0
    private val chrBankNos: IntArray = IntArray(size = 8)

    private var isIRQCounterEnabled: Boolean = false
    private var irqCounter: Int = 0
    private var irqCounterTemp: Int = 0

    override var mirroring: Mirroring = defaultMirroring(information)
        private set
    override val stateObserver: StateObserver = object : StateObserverAdapter() {
        override fun notifyM2Cycle(cycle: Int) {
            if (isIRQCounterEnabled) {
                if (irqCounter > 0) {
                    irqCounter = (irqCounter - cycle).coerceAtLeast(minimumValue = 0)
                    if (irqCounter == 0) {
                        interrupter.requestOnIRQ() // TODO: 合ってる？
                    }
                }
            }
        }
    }

    override fun writePRG(address: Int, value: UByte) {
        when (address) {
            //CHR-ROM Bank Select ($6000-$6007 write, Submapper 4; $8000-$8007 write, Submapper 5)
            //7  bit  0
            //---- ----
            //CCCC CCCC
            //|||| ||||
            //++++-++++-- 1 KiB CHR-ROM bank number
            //$xxx0: Select 1 KiB CHR-ROM bank at PPU $0000-$03FF
            //$xxx1: Select 1 KiB CHR-ROM bank at PPU $0400-$07FF
            //$xxx2: Select 1 KiB CHR-ROM bank at PPU $0800-$0BFF
            //$xxx3: Select 1 KiB CHR-ROM bank at PPU $0C00-$0FFF
            //$xxx4: Select 1 KiB CHR-ROM bank at PPU $1000-$13FF
            //$xxx5: Select 1 KiB CHR-ROM bank at PPU $1400-$17FF
            //$xxx6: Select 1 KiB CHR-ROM bank at PPU $1800-$1BFF
            //$xxx7: Select 1 KiB CHR-ROM bank at PPU $1C00-$1FFF
            in 0x6000..0x6007, in 0x8000..0x8007 -> {
                val index = address and 0x0007
                chrBankNos[index] = value.toInt()
            }
            //PRG-ROM Bank Select ($6008 write, Submapper 4; $8008 write, Submapper 5)
            //Mask: $E00F (Submapper 4), $800F (Submapper 5)
            //7  bit  0
            //---- ----
            //.... PPPP
            //     ||||
            //     ++++-- Select 16 KiB PRG-ROM bank at CPU $8000-$BFFF
            0x6008, 0x8008 -> {
                prgBankNo = value.toInt() and 0x0F
            }
            //Nametable Mirroring Type Select ($6009 write, Submapper 4; $8009 write, Submapper 5)
            //Mask: $E00F (Submapper 4), $800F (Submapper 5)
            //7  bit  0
            //---- ----
            //.... ..MM
            //       ||
            //       ++-- Select nametable mirroring type
            //            0: Vertical
            //            1: Horizontal
            //            2: One-screen, page 0
            //            3: One-screen, page 1
            0x6009, 0x8009 -> {
                mirroring = when (value.toInt() and 0b0000_0011) {
                    0 -> Mirroring.Vertical
                    1 -> Mirroring.Horizontal
                    2 -> Mirroring.SingleScreen0
                    3 -> Mirroring.SingleScreen1
                    else -> error("Invalid value. $value")
                }
            }
            //IRQ Control ($600A write, Submapper 4; $800A write, Submapper 5)
            //Mask: $E00F (Submapper 4), $800F (Submapper 5)
            //7  bit  0
            //---- ----
            //.... ...C
            //        |
            //        +-- IRQ counter control
            //            0: Counting disabled
            //            1: Counting enabled
            //            Writing to this register acknowledges a pending IRQ.
            //            On the LZ93D50 (Submapper 5), writing to this register also copies the latch to the actual counter.
            //            If a write to this register enables counting while the counter is holding a value of zero, an IRQ is generated immediately.
            0x600A -> {
                isIRQCounterEnabled = (value.toInt() and 0x01) == 0x01
                interrupter.requestOffIRQ() // TODO: 合ってる？
            }

            0x800A -> {
                // TODO: 実装違う？
                isIRQCounterEnabled = (value.toInt() and 0x01) == 0x01
                irqCounter = irqCounterTemp
                interrupter.requestOffIRQ() // TODO: 合ってる？
            }
            //IRQ Latch/Counter ($600B-$600C write, Submapper 4; $800B-$800C write, Submapper 5)
            //Mask: $E00F (Submapper 4), $800F (Submapper 5)
            //   $C         $B
            //7  bit  0  7  bit  0
            //---- ----  ---- ----
            //CCCC CCCC  CCCC CCCC
            //|||| ||||  |||| ||||
            //++++-++++--++++-++++-- Counter value (little-endian)
            //If counting is enabled, the counter decreases on every M2 cycle. When it holds a value of zero, an IRQ is generated.
            //On the FCG-1/2 (Submapper 4), writing to these two registers directly modifies the counter itself; all such games therefore disable counting before changing the counter value.
            0x600B -> irqCounter = (irqCounter and 0b1111_1111_0000_0000) or value.toInt()
            0x600C -> irqCounter = (irqCounter and 0b0000_0000_1111_1111) or (value.toInt() shl 8)
            //   $C         $B
            //7  bit  0  7  bit  0
            //---- ----  ---- ----
            //CCCC CCCC  CCCC CCCC
            //|||| ||||  |||| ||||
            //++++-++++--++++-++++-- Counter value (little-endian)
            //On the LZ93D50 (Submapper 5), these registers instead modify a latch that will only be copied to the actual counter when register $800A is written to.
            // TODO: 実装違う？
            0x800B -> irqCounterTemp = (irqCounterTemp and 0b1111_1111_0000_0000) or value.toInt()
            0x800C -> irqCounterTemp = (irqCounterTemp and 0b0000_0000_1111_1111) or (value.toInt() shl 8)
            //EEPROM Control ($800D write, Submapper 5 only)
            //Mask: $800F
            //7  bit  0
            //---- ----
            //RDC. ....
            //|||
            //||+-------- I²C SCL
            //|+--------- I²C SDA
            //+---------- Direction bit (1=Enable Read)
            0x800D -> {
                //TODO: 未実装
            }
        }
    }

    //Banks
    //CPU $8000-$BFFF: 16 KiB switchable PRG ROM bank
    //CPU $C000-$FFFF: 16 KiB PRG ROM bank, fixed to the last bank
    override fun readPRG(address: Int): UByte {
        return if (address >= 0xC000) {
            val index = address and 0x3FFF
            val offset = lastPRGBankNo shl 14
            prgRom[index + offset]
        } else {
            val index = address and 0x3FFF
            val offset = prgBankNo shl 14
            prgRom[index + offset]
        }
    }

    //Banks
    //PPU $0000-$03FF: 1 KiB switchable CHR ROM bank
    //PPU $0400-$07FF: 1 KiB switchable CHR ROM bank
    //PPU $0800-$0BFF: 1 KiB switchable CHR ROM bank
    //PPU $0C00-$0FFF: 1 KiB switchable CHR ROM bank
    //PPU $1000-$13FF: 1 KiB switchable CHR ROM bank
    //PPU $1400-$17FF: 1 KiB switchable CHR ROM bank
    //PPU $1800-$1BFF: 1 KiB switchable CHR ROM bank
    //PPU $1C00-$1FFF: 1 KiB switchable CHR ROM bank
    override fun readCHR(address: Int): UByte {
        val index = address shr 10
        return chrRom[(chrBankNos[index] shl 10) or (address and 0x03FF)]
    }

    //Read Serial EEPROM ($6000-$7FFF read, Submapper 5 only)
    //7  bit  0
    //---- ----
    //xxxE xxxx
    //|||| ||||
    //+++|-++++- Open bus
    //   +------ Data out from I²C EEPROM
    //TODO: 未実装
    @Suppress("RedundantOverride")
    override fun readBackup(address: Int): UByte {
        return super.readBackup(address)
    }
}
