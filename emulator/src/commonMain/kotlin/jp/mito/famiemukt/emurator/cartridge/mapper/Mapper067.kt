package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cartridge.Mirroring
import jp.mito.famiemukt.emurator.cartridge.StateObserver
import jp.mito.famiemukt.emurator.cartridge.StateObserverAdapter

/*
https://www.nesdev.org/wiki/INES_Mapper_067
    Overview
        PRG ROM size: Hardware supports at most 256 KiB
        PRG ROM bank size: 16 KiB
        PRG RAM: Unused, but mapper IC provides RAM enables.
        CHR bank size: 2 KiB
        CHR ROM size: Hardware supports at most 128 KiB
        Nametable mirroring: Controlled by mapper (Horizontal, Vertical, 1-screen)
        Subject to bus conflicts: No
    Banks
        CPU $8000-$BFFF: 16 KiB switchable PRG ROM bank
        CPU $C000-$FFFF: 16 KiB PRG ROM bank, fixed to the last bank
        PPU $0000-$07FF: 2 KiB switchable CHR bank
        PPU $0800-$0FFF: 2 KiB switchable CHR bank
        PPU $1000-$17FF: 2 KiB switchable CHR bank
        PPU $1800-$1FFF: 2 KiB switchable CHR bank
 */
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper067(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    private val lastPRGBankNo: Int = cartridge.information.prgRom16Units - 1
    private val prgBankNoMask: Int = (lastPRGBankNo.takeHighestOneBit() shl 1) - 1
    private var prgBankNo: Int = 0
    private var chrBankNo0: Int = 0
    private var chrBankNo1: Int = 0
    private var chrBankNo2: Int = 0
    private var chrBankNo3: Int = 0

    private var isEnableIRQCounter: Boolean = false
    private var isIRQCounterHighLatch: Boolean = true
    private var irqCounter: Int = 0

    override var mirroring: Mirroring = defaultMirroring(cartridge.information)
        private set
    override val stateObserver: StateObserver = object : StateObserverAdapter() {
        /* IRQ Operation
            $C800 is a write-twice register (similar to $2005 and $2006).
            The first write sets the high 8 bits of the IRQ counter, and the second write sets the low 8 bits.
            This directly changes the actual IRQ counter – not a reload value.
            In addition to enabling/disabling counting, any write to $D800
            will reset the toggle so that the next write to $C800 will be the first write.
            The IRQ counter, when enabled, counts down every CPU cycle.
            When it wraps ($0000→FFFF), it disables itself and triggers an IRQ. */
        override fun notifyM2Cycle(cycle: Int) {
            if (isEnableIRQCounter) {
                irqCounter -= cycle
                if (irqCounter < 0) {
                    irqCounter += 0x1_00_00
                    isEnableIRQCounter = false
                    interrupter.requestOnIRQ() // TODO: 合ってるっぽい？
                }
            }
        }
    }

    override fun writePRG(address: Int, value: UByte) {
        // Mask $8800
        when (address and 0x8800) {
            /* Interrupt Acknowledge ($8000)
                Mask: $8800
                Any write to this address or any of its mirrors acknowledges a pending IRQ. */
            0x8000 -> {
                // TODO: 合ってるっぽい？
                interrupter.requestOffIRQ()
            }
        }
        // Mask $F800
        when (address and 0xF800) {
            /* CHR bank 0…3 ($8800..$BFFF)
                Mask: $F800
                Note that the hardware only has six pins for CHR banking, for a limit of 128KB of CHR.
                Write to CPU address	2KB CHR bank affected
                $8800	                $0000-$07FF
                $9800	                $0800-$07FF
                $A800	                $1000-$07FF
                $B800	                $1800-$07FF */
            0x8800 -> chrBankNo0 = value.toInt()
            0x9800 -> chrBankNo1 = value.toInt()
            0xA800 -> chrBankNo2 = value.toInt()
            0xB800 -> chrBankNo3 = value.toInt()
            /* IRQ load ($C800, write twice)
                Mask: $F800
                Write the high then low byte of a 16-bit CPU cycle count, much like PPUADDR.
                This directly affects the current count, not a reload value.
                The write state is reset by writing to the register at $D800. */
            0xC800 -> {
                irqCounter = when (isIRQCounterHighLatch) {
                    true -> (irqCounter and 0x00_FF) or (value.toInt() shl 8)
                    else -> (irqCounter and 0xFF_00) or value.toInt()
                }
                isIRQCounterHighLatch = isIRQCounterHighLatch.not()
            }
            /*  IRQ enable ($D800)
                    Mask: $F800
                    7  bit  0
                    ...P ....
                       |
                       +------ 0: Pause counter; 1: Count
                    While bit 4 is true, the 16-bit count decreases by 1 every CPU cycle. Whenever the count wraps
                    from $0000 to $FFFF, the mapper asserts an IRQ and pauses itself.
                    Writes reset a latch such that the next $C800 write goes to the high byte of the count.
                    Despite previous notes, writes to this register do not acknowledge the IRQ.
                    If counting is enabled, the External IRQ pin is also capable of asserting an IRQ.
                    No existing hardware uses this functionality. */
            0xD800 -> {
                isEnableIRQCounter = value.toInt() and 0b0001_0000 != 0
                isIRQCounterHighLatch = true
            }
            /* Mirroring ($E800)
                Mask: $F800
                7  bit  0
                .... ..MM
                       ||
                       ++- Nametable mirroring (0=vertical, 1=horizontal, 2=1scA, 3=1scB)
                            aka connect VRAM A10 to (0=PPU A10, 1=PPU A11, 2=Gnd, 3=Vcc) */
            0xE800 -> {
                when (value.toInt() and 0b0000_0011) {
                    0 -> mirroring = Mirroring.Vertical
                    1 -> mirroring = Mirroring.Horizontal
                    2 -> mirroring = Mirroring.SingleScreen0
                    3 -> mirroring = Mirroring.SingleScreen1
                }
            }
            /* PRG bank ($F800)
                Mask: $F800
                7  bit  0
                ...X PPPP
                   | ||||
                   | ++++- select a 16 KiB CHR ROM bank at CPU $8000-$BFFF. $C000-$FFFF is fixed to the last bank of PRG ROM.
                   +------ 1 bit latch, present but unused. Could be combined with an external OR gate to increase PRG capacity to 512KB. */
            0xF800 -> {
                prgBankNo = value.toInt() and 0b0000_1111 and prgBankNoMask
            }
        }
    }

    // CPU $8000-$BFFF: 16 KiB switchable PRG ROM bank
    // CPU $C000-$FFFF: 16 KiB PRG ROM bank, fixed to the last bank
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

    // FantasyZone2で書き込みあり／デバッグとかで使用したいた？
    override fun writeCHR(address: Int, value: UByte) = Unit

    // PPU $0000-$07FF: 2 KiB switchable CHR bank
    // PPU $0800-$0FFF: 2 KiB switchable CHR bank
    // PPU $1000-$17FF: 2 KiB switchable CHR bank
    // PPU $1800-$1FFF: 2 KiB switchable CHR bank
    override fun readCHR(address: Int): UByte {
        val bankNo = when (address) {
            in 0x0000..0x07FF -> chrBankNo0
            in 0x0800..0x0FFF -> chrBankNo1
            in 0x1000..0x17FF -> chrBankNo2
            in 0x1800..0x1FFF -> chrBankNo3
            else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
        }
        val index = (bankNo shl 11) or (address and 0x07FF)
        return chrRom[index]
    }
}
