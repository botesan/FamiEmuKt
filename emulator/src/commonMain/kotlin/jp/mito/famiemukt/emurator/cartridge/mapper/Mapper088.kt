package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge

// https://www.nesdev.org/wiki/INES_Mapper_088
// https://www.nesdev.org/wiki/INES_Mapper_206
@OptIn(ExperimentalUnsignedTypes::class)
class Mapper088(
    cartridge: Cartridge,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray,
) : MapperBase(cartridge = cartridge) {
    // Bank size 8k
    private val lastPRGBankNo: Int = cartridge.information.prgRom16Units * 2 - 1
    private val bankNoR: IntArray = IntArray(size = 8)
    private var selectedR: Int = 0

    override fun writePRG(address: Int, value: UByte) {
        when (address) {
            in 0x8000..0x9FFF -> {
                if (address and 0x01 == 0x00) {
                    /* https://www.nesdev.org/wiki/INES_Mapper_206
                        Bank select ($8000-$9FFE, even)
                        7  bit  0
                        ---- ----
                        xxxx xRRR
                              |||
                              +++- Specify which bank register to update on next write to Bank Data register
                                   0: Select 2 KB CHR bank at PPU $0000-$07FF
                                   1: Select 2 KB CHR bank at PPU $0800-$0FFF
                                   2: Select 1 KB CHR bank at PPU $1000-$13FF
                                   3: Select 1 KB CHR bank at PPU $1400-$17FF
                                   4: Select 1 KB CHR bank at PPU $1800-$1BFF
                                   5: Select 1 KB CHR bank at PPU $1C00-$1FFF
                                   6: Select 8 KB PRG ROM bank at $8000-$9FFF
                                   7: Select 8 KB PRG ROM bank at $A000-$BFFF
                        See MMC3 and note the absence of any control bits in the upper five bits of this register. */
                    selectedR = value.toInt() and 0x07
                } else {
                    /* Bank data ($8001-$9FFF, odd)
                        7  bit  0
                        ---- ----
                        xxdd DDDd
                          || ||||
                          ++-++++- New bank value, based on last value written to bank select register (mentioned above)
                        Only bits 5-1 exist for the two 2 KiB CHR banks, only bits 5-0 exist for the four 1 KiB CHR banks,
                        and only bits 3-0 exist for the two 8 KiB PRG banks.
                        These eight bank registers are identical to those of MMC3,
                        except that only 128 KiB PRG and 64 KiB CHR are supported.
                        ---
                        iNES Mapper 088 is the same as mapper 206 with the following exception:
                        CHR support is increased to 128KB by connecting PPU's A12 line to the CHR ROM's A16 line.
                        Consequently, CHR is split into two halves. $0xxx can only have CHR from the first 64K,
                        $1xxx can only have CHR from the second 64K.
                        A possible way to implement this would be to mask the CHR ROM 1K bank output from the mapper
                        by ANDing with $3F, and then OR it with $40 for N108 registers 2, 3, 4, and 5.
                        If the CHR ROM is 64K or smaller, it is identical to mapper 206. */
                    bankNoR[selectedR] = when (selectedR) {
                        0, 1 -> value.toInt() and 0x3E // 2 KB CHR bank
                        2, 3, 4, 5 -> value.toInt() and 0x3F or 0x40 // 1 KB CHR bank
                        6, 7 -> value.toInt() and 0x0F // 8 KB PRG bank
                        else -> error("Illegal selectedR=$selectedR")
                    }
                }
            }
        }
    }

    override fun readPRG(address: Int): UByte {
        /* PRG Banks
            6: Select 8 KB PRG ROM bank at $8000-$9FFF
            7: Select 8 KB PRG ROM bank at $A000-$BFFF
            ---
            PRG always has the last two 8KiB banks fixed to the end. */
        val bankNo = when (address) {
            in 0x8000..0x9FFF -> bankNoR[6]
            in 0xA000..0xBFFF -> bankNoR[7]
            in 0xC000..0xDFFF -> lastPRGBankNo - 1
            in 0xE000..0xFFFF -> lastPRGBankNo
            else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
        }
        val index = (bankNo shl 13) or (address and 0x1FFF)
        return prgRom[index]
    }

    override fun readCHR(address: Int): UByte {
        /* CHR Banks
            0: Select 2 KB CHR bank at PPU $0000-$07FF
            1: Select 2 KB CHR bank at PPU $0800-$0FFF
            2: Select 1 KB CHR bank at PPU $1000-$13FF
            3: Select 1 KB CHR bank at PPU $1400-$17FF
            4: Select 1 KB CHR bank at PPU $1800-$1BFF
            5: Select 1 KB CHR bank at PPU $1C00-$1FFF  */
        val bankNo = when (address) {
            in 0x0000..0x03FF -> bankNoR[0] and 0xFE
            in 0x0400..0x07FF -> (bankNoR[0] and 0xFE) + 1
            in 0x0800..0x0BFF -> bankNoR[1] and 0xFE
            in 0x0C00..0x0FFF -> (bankNoR[1] and 0xFE) + 1
            in 0x1000..0x13FF -> bankNoR[2]
            in 0x1400..0x17FF -> bankNoR[3]
            in 0x1800..0x1BFF -> bankNoR[4]
            in 0x1C00..0x1FFF -> bankNoR[5]
            else -> error("Illegal address. ${address.toString(radix = 16).padStart(length = 4, padChar = '0')}")
        }
        val index = (bankNo shl 10) or (address and 0x03FF)
        return chrRom[index]
    }
}
