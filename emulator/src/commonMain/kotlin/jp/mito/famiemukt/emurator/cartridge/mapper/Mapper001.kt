package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cartridge.Mirroring
import jp.mito.famiemukt.emurator.util.BIT_MASK_0
import jp.mito.famiemukt.emurator.util.BIT_MASK_7

/*
https://www.nesdev.org/wiki/MMC1
Banks
CPU $6000-$7FFF: 8 KB PRG RAM bank, (optional)
CPU $8000-$BFFF: 16 KB PRG ROM bank, either switchable or fixed to the first bank
CPU $C000-$FFFF: 16 KB PRG ROM bank, either fixed to the last bank or switchable
PPU $0000-$0FFF: 4 KB switchable CHR bank
PPU $1000-$1FFF: 4 KB switchable CHR bank
Through writes to the MMC1 control register,
it is possible for the program to swap the fixed and switchable PRG ROM banks or to set up 32 KB PRG bankswitching (like BNROM),
but most games use the default setup, which is similar to that of UxROM.
*/
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper001(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    private val lastPRGBankNo: Int = cartridge.information.prgRom16Units - 1

    /* Load register ($8000-$FFFF)
        7  bit  0
        ---- ----
        Rxxx xxxD
        |       |
        |       +- Data bit to be shifted into shift register, LSB first
        +--------- A write with bit set will reset shift register
                    and write Control with (Control OR $0C),
                    locking PRG ROM at $C000-$FFFF to the last bank.
        On consecutive-cycle writes, writes to the shift register (D0) after the first are ignored.
        See Consecutive-cycle writes for more details. */
    private var shift: Int = SHIFT_REGISTER_INIT

    /* Control (internal, $8000-$9FFF)
        4bit0
        -----
        CPPMM
        |||||
        |||++- Mirroring (0: one-screen, lower bank; 1: one-screen, upper bank;
        |||               2: vertical; 3: horizontal)
        |++--- PRG ROM bank mode (0, 1: switch 32 KB at $8000, ignoring low bit of bank number;
        |                         2: fix first bank at $8000 and switch 16 KB bank at $C000;
        |                         3: fix last bank at $C000 and switch 16 KB bank at $8000)
        +----- CHR ROM bank mode (0: switch 8 KB at a time; 1: switch two separate 4 KB banks)
        Although some tests have found that all versions of the MMC1 seems to reliably power on in the last bank
        (by setting the "PRG ROM bank mode" to 3); other tests have found that this is fragile.
        Several commercial games have reset vectors every 32 KiB, but not every 16, so evidently PRG ROM bank mode 2
        doesn't seem to occur randomly on power-up. */
    private var control: Int = 0x0C

    /* CHR bank 0 (internal, $A000-$BFFF)
        4bit0
        -----
        CCCCC
        |||||
        +++++- Select 4 KB or 8 KB CHR bank at PPU $0000 (low bit ignored in 8 KB mode)
        MMC1 can do CHR banking in 4KB chunks. Known carts with CHR RAM have 8 KiB, so that makes 2 banks.
        RAM vs ROM doesn't make any difference for address lines. For carts with 8 KiB of CHR (be it ROM or RAM),
        MMC1 follows the common behavior of using only the low-order bits: the bank number is in effect ANDed with 1. */
    private var chrBank0: Int = 0

    /* CHR bank 1 (internal, $C000-$DFFF)
        4bit0
        -----
        CCCCC
        |||||
        +++++- Select 4 KB CHR bank at PPU $1000 (ignored in 8 KB mode) */
    private var chrBank1: Int = 0

    // https://www.nesdev.org/wiki/MMC1#iNES_Mapper_001
    // $A000 and $C000:
    // 4bit0
    // -----
    // EDCBA
    // |||||
    // ||||+- CHR A12
    // |||+-- CHR A13 if CHR >= 16k
    // ||+--- CHR A14 if CHR >= 32k; and PRG RAM A13 if PRG RAM = 32k
    // |+---- CHR A15 if CHR >= 64k; and PRG RAM A13 if PRG RAM = 16k
    // |                              or PRG RAM A14 if PRG RAM = 32k
    // +----- CHR A16 if CHR = 128k; and PRG ROM A18 if PRG ROM = 512k
    private var lastSetChrBank: Int = 0

    /* PRG bank (internal, $E000-$FFFF)
        4bit0
        -----
        RPPPP
        |||||
        |++++- Select 16 KB PRG ROM bank (low bit ignored in 32 KB mode)
        +----- MMC1B and later: PRG RAM chip enable (0: enabled; 1: disabled; ignored on MMC1A)
               MMC1A: Bit 3 bypasses fixed bank logic in 16K mode (0: affected; 1: bypassed)
        The high bit does not select a PRG ROM bank.
        MMC1 with 512K was supported by re-using a line from the CHR banking controls. (See below.) */
    private var prgBank: Int = 0

    override val mirroring: Mirroring
        get() = when (val value = control and 0b0011) {
            0 -> Mirroring.SingleScreen0
            1 -> Mirroring.SingleScreen1
            2 -> Mirroring.Vertical
            3 -> Mirroring.Horizontal
            else -> error("Illegal value. $value")
        }

    /* Interface
        Unlike almost all other mappers, the MMC1 is configured through a serial port in order to reduce its pin count.
        CPU $8000-$FFFF is connected to a common shift register.
        Writing a value with bit 7 set ($80 through $FF) to any address in $8000-$FFFF clears the shift register to its initial state.
        To change a register's value, the CPU writes five times with bit 7 clear and one bit of the desired value in bit 0
        (starting with the low bit of the value).
        On the first four writes, the MMC1 shifts bit 0 into a shift register.
        On the fifth write, the MMC1 copies bit 0 and the shift register contents into an internal register selected by
        bits 14 and 13 of the address, and then it clears the shift register.
        Only on the fifth write does the address matter,
        and even then, only bits 14 and 13 of the address matter because the mapper doesn't see the lower address bits
        (similar to the mirroring seen with PPU registers).
        After the fifth write, the shift register is cleared automatically, so writing again with bit 7
        set to clear the shift register is not needed. */
    override fun writePRG(address: Int, value: UByte) {
        /* Load register ($8000-$FFFF)
            7  bit  0
            ---- ----
            Rxxx xxxD
            |       |
            |       +- Data bit to be shifted into shift register, LSB first
            +--------- A write with bit set will reset shift register
                        and write Control with (Control OR $0C),
                        locking PRG ROM at $C000-$FFFF to the last bank. */
        if ((value and BIT_MASK_7) != 0.toUByte()) {
            // reset
            shift = SHIFT_REGISTER_INIT
            control = control or 0x0C
            return
        } else if (shift and 0b0000_0001 == 0) {
            // not full
            shift = ((value and BIT_MASK_0).toInt() shl 4) or (shift ushr 1)
            return
        }
        // 各値へ代入（fullのとき）
        val pbValue = ((value and BIT_MASK_0).toInt() shl 4) or (shift ushr 1)
        when (address) {
            /* Control (internal, $8000-$9FFF) */
            in 0x8000..0x9FFF -> {
                control = pbValue
            }
            /* CHR bank 0 (internal, $A000-$BFFF) */
            in 0xA000..0xBFFF -> {
                chrBank0 = pbValue
                lastSetChrBank = pbValue
            }
            /* CHR bank 1 (internal, $C000-$DFFF) */
            in 0xC000..0xDFFF -> {
                chrBank1 = pbValue
                lastSetChrBank = pbValue
            }
            /* PRG bank (internal, $E000-$FFFF) */
            in 0xE000..0xFFFF -> {
                prgBank = pbValue
            }
            // 範囲外
            else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
        }
        // シフトレジスタの初期化
        shift = SHIFT_REGISTER_INIT
    }

    override fun readPRG(address: Int): UByte {
        // https://www.nesdev.org/wiki/MMC1#iNES_Mapper_001
        // $A000 and $C000:
        // 4bit0
        // -----
        // EDCBA
        // |||||
        // ||||+- CHR A12
        // |||+-- CHR A13 if CHR >= 16k
        // ||+--- CHR A14 if CHR >= 32k; and PRG RAM A13 if PRG RAM = 32k
        // |+---- CHR A15 if CHR >= 64k; and PRG RAM A13 if PRG RAM = 16k
        // |                              or PRG RAM A14 if PRG RAM = 32k
        // +----- CHR A16 if CHR = 128k; and PRG ROM A18 if PRG ROM = 512k
        val bankOffsetA18 = if (information.prgRom16Units != 32/*512k*/) 0 else lastSetChrBank and 0b0001_0000
        /*  4bit0
            -----
            CPPMM
             ||
             ++--- PRG ROM bank mode (0, 1: switch 32 KB at $8000, ignoring low bit of bank number;
                                      2: fix first bank at $8000 and switch 16 KB bank at $C000;
                                      3: fix last bank at $C000 and switch 16 KB bank at $8000) */
        val bankNo = when (control and 0b0_11_00) {
            // 0, 1: switch 32 KB at $8000, ignoring low bit of bank number;
            0b0_00_00, 0b0_01_00 -> (prgBank and 0b0000_1110) or bankOffsetA18
            // 2: fix first bank at $8000 and switch 16 KB bank at $C000;
            0b0_10_00 -> when (address) {
                in 0x8000..0xBFFF -> bankOffsetA18
                in 0xC000..0xFFFF -> (prgBank and 0b0000_1111) or bankOffsetA18
                else -> error("Illegal address. ${address.toString(16).padStart(4, '0')}")
            }
            // 3: fix last bank at $C000 and switch 16 KB bank at $8000)
            0b0_11_00 -> when (address) {
                in 0x8000..0xBFFF -> (prgBank and 0b0000_1111) or bankOffsetA18
                in 0xC000..0xFFFF -> (lastPRGBankNo and 0b0000_1111) or bankOffsetA18
                else -> error("Illegal address. ${address.toString(16).padStart(4, '0')}")
            }
            // other
            else -> error("Illegal control value. $control")
        }
        val index = (bankNo shl 14) or (address and 0x3FFF)
        return prgRom[index]
    }

    override fun readCHR(address: Int): UByte {
        // https://www.nesdev.org/wiki/MMC1#iNES_Mapper_001
        // $A000 and $C000:
        // 4bit0
        // -----
        // EDCBA
        // |||||
        // ||||+- CHR A12
        // |||+-- CHR A13 if CHR >= 16k
        // ||+--- CHR A14 if CHR >= 32k; and PRG RAM A13 if PRG RAM = 32k
        // |+---- CHR A15 if CHR >= 64k; and PRG RAM A13 if PRG RAM = 16k
        // |                              or PRG RAM A14 if PRG RAM = 32k
        // +----- CHR A16 if CHR = 128k; and PRG ROM A18 if PRG ROM = 512k
        val bankMask = when {
            information.chrRom8Units == 16/*128k*/ -> 0b1_1111
            information.chrRom8Units >= 8 /* 64k*/ -> 0b0_1111
            information.chrRom8Units >= 4 /* 32k*/ -> 0b0_0111
            information.chrRom8Units >= 2 /* 16k*/ -> 0b0_0011
            else /*                           8k*/ -> 0b0_0001
        }
        // CHR ROM bank mode (0: switch 8 KB at a time; 1: switch two separate 4 KB banks)
        /*  4bit0
            -----
            CPPMM
            |
            +----- CHR ROM bank mode (0: switch 8 KB at a time; 1: switch two separate 4 KB banks) */
        val bankNo = if (control and 0b1_00_00 == 0) {
            // Select 4 KB or 8 KB CHR bank at PPU $0000 (low bit ignored in 8 KB mode)
            chrBank0 and bankMask and 0xFE
        } else when (address) {
            // Select 4 KB or 8 KB CHR bank at PPU $0000 (low bit ignored in 8 KB mode)
            in 0x0000..0x0FFF -> chrBank0 and bankMask
            // Select 4 KB CHR bank at PPU $1000 (ignored in 8 KB mode)
            in 0x1000..0x1FFF -> chrBank1 and bankMask
            // 範囲外
            else -> error("Illegal address. ${address.toString(16).padStart(4, '0')}")
        }
        val index = (bankNo shl 12) or (address and 0x0FFF)
        return chrRom[index]
    }

    // https://www.nesdev.org/wiki/MMC1#iNES_Mapper_001
    // $A000 and $C000:
    // 4bit0
    // -----
    // EDCBA
    // |||||
    // ||||+- CHR A12
    // |||+-- CHR A13 if CHR >= 16k
    // ||+--- CHR A14 if CHR >= 32k; and PRG RAM A13 if PRG RAM = 32k
    // |+---- CHR A15 if CHR >= 64k; and PRG RAM A13 if PRG RAM = 16k
    // |                              or PRG RAM A14 if PRG RAM = 32k
    // +----- CHR A16 if CHR = 128k; and PRG ROM A18 if PRG ROM = 512k
    override fun writeBackup(address: Int, value: UByte) =
        cartridge.backupRAM.write(
            index = (address and 0x1FFF) or ((lastSetChrBank and 0b0_1100) shl 11),
            value = value,
        )

    // https://www.nesdev.org/wiki/MMC1#iNES_Mapper_001
    // $A000 and $C000:
    // 4bit0
    // -----
    // EDCBA
    // |||||
    // ||||+- CHR A12
    // |||+-- CHR A13 if CHR >= 16k
    // ||+--- CHR A14 if CHR >= 32k; and PRG RAM A13 if PRG RAM = 32k
    // |+---- CHR A15 if CHR >= 64k; and PRG RAM A13 if PRG RAM = 16k
    // |                              or PRG RAM A14 if PRG RAM = 32k
    // +----- CHR A16 if CHR = 128k; and PRG ROM A18 if PRG ROM = 512k
    override fun readBackup(address: Int): UByte =
        cartridge.backupRAM.read(index = (address and 0x1FFF) or ((lastSetChrBank and 0b0_1100) shl 11))

    companion object {
        private const val SHIFT_REGISTER_INIT: Int = 0b0001_0000
    }
}
