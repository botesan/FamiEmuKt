package jp.mito.famiemukt.emurator.cartridge

import jp.mito.famiemukt.emurator.cartridge.mapper.Mapper

/*
https://www.tekepen.com/nes/adrmap.html
アドレス	内容	ミラーアドレス
$4020-$5FFF	拡張RAM(特殊なマッパー使用時)
$6000-$7FFF	バッテリーバックアップRAM
$8000-$BFFF	プログラムROM LOW
$C000-$FFFF	プログラムROM HIGH

https://www.nesdev.org/wiki/CPU_memory_map
Address range	Size	Device
$4020–$FFFF	$BFE0	Cartridge space: PRG ROM, PRG RAM, and mapper registers
 */
@OptIn(ExperimentalUnsignedTypes::class)
class Cartridge private constructor(
    val information: Information,
    val backupRAM: BackupRAM,
    private val prgRom: UByteArray,
    private val chrRom: UByteArray
) {
    val mapper: Mapper by lazy { Mapper.create(cartridge = this, prgRom = prgRom, chrRom = chrRom) }

    data class Information(
        val prgRom16Units: Int,
        val chrRom8Units: Int,
        val mirroringVertical: Boolean,
        val batteryBackup: Boolean,
        val trainer: Boolean,
        val ignoreMirroring: Boolean,
        val vsUniSystem: Boolean,
        val playChoice10: Boolean,
        val isNES20: Boolean,
        val mapperNo: Int,
        val subMapperNo: Int?,
        val rpgRam8Units: Int,
        val isNTSC: Boolean,
        val isPrgRamPresent: Boolean,
        val isHasBusConflicts: Boolean,
    )

    companion object {
        operator fun invoke(backupRAM: BackupRAM, iNesData: ByteArray): Cartridge {
            // https://www.nesdev.org/wiki/INES
            // 0-3	Constant $4E $45 $53 $1A (ASCII "NES" followed by MS-DOS end-of-file)
            // 4	Size of PRG ROM in 16 KB units
            // 5	Size of CHR ROM in 8 KB units (value 0 means the board uses CHR RAM)
            // 6	Flags 6 – Mapper, mirroring, battery, trainer
            // 7	Flags 7 – Mapper, VS/Playchoice, NES 2.0
            // 8	Flags 8 – PRG-RAM size (rarely used extension)
            // 9	Flags 9 – TV system (rarely used extension)
            // 10	Flags 10 – TV system, PRG-RAM presence (unofficial, rarely used extension)
            // 11-15	Unused padding (should be filled with zero, but some rippers put their name across bytes 7-15)
            // ヘッダーチェック
            check(value = iNesData[0] == 'N'.code.toByte())
            check(value = iNesData[1] == 'E'.code.toByte())
            check(value = iNesData[2] == 'S'.code.toByte())
            check(value = iNesData[3] == 0x1a.toByte())
            // サイズ取得
            val prgRom16Units = iNesData[4].toUByte().toInt()
            val chrRom8Units = iNesData[5].toUByte().toInt()
            // Flags 6
            // 76543210
            // ||||||||
            // |||||||+- Mirroring: 0: horizontal (vertical arrangement) (CIRAM A10 = PPU A11)
            // |||||||              1: vertical (horizontal arrangement) (CIRAM A10 = PPU A10)
            // ||||||+-- 1: Cartridge contains battery-backed PRG RAM ($6000-7FFF) or other persistent memory
            // |||||+--- 1: 512-byte trainer at $7000-$71FF (stored before PRG data)
            // ||||+---- 1: Ignore mirroring control or above mirroring bit; instead provide four-screen VRAM
            // ++++----- Lower nybble of mapper number
            val mirroringVertical: Boolean = (iNesData[6].toInt() and 0b0000_0001) != 0
            val batteryBackup: Boolean = (iNesData[6].toInt() and 0b0000_0010) != 0
            val trainer: Boolean = (iNesData[6].toInt() and 0b0000_0100) != 0
            val ignoreMirroring: Boolean = (iNesData[6].toInt() and 0b0000_1000) != 0
            // Flags 7
            // 76543210
            // ||||||||
            // |||||||+- VS Unisystem
            // ||||||+-- PlayChoice-10 (8 KB of Hint Screen data stored after CHR data)
            // ||||++--- If equal to 2, flags 8-15 are in NES 2.0 format
            // ++++----- Upper nybble of mapper number
            val vsUniSystem: Boolean = (iNesData[7].toInt() and 0b0000_00_0_1) != 0
            val playChoice10: Boolean = (iNesData[7].toInt() and 0b0000_00_1_0) != 0
            val isNES20: Boolean = (iNesData[7].toInt() and 0b0000_11_0_0) == 0b0000_10_0_0
            // TODO: その他のデータ（iNESとNES2.0のフォーマット毎に切り替え）
            val mapperNo: Int
            val subMapperNo: Int?
            val rpgRam8Units: Int
            val isNTSC: Boolean
            val isPrgRamPresent: Boolean
            val isHasBusConflicts: Boolean
            if (isNES20.not()) {
                mapperNo = ((iNesData[7].toUByte().toInt() and 0b1111_0000) or (iNesData[6].toUByte().toInt() ushr 4))
                subMapperNo = null
                // Flags 8
                // 76543210
                // ||||||||
                // ++++++++- PRG RAM size
                // Size of PRG RAM in 8 KB units (Value 0 infers 8 KB for compatibility; see PRG RAM circuit)
                // This was a later extension to the iNES format and not widely used.
                // NES 2.0 is recommended for specifying PRG RAM size instead.
                rpgRam8Units = iNesData[8].toInt()
                // Flags 9
                // 76543210
                // ||||||||
                // |||||||+- TV system (0: NTSC; 1: PAL)
                // +++++++-- Reserved, set to zero
                // Though in the official specification,
                // very few emulators honor this bit as virtually no ROM images in circulation make use of it.
                // Flags 10
                // 76543210
                //   ||  ||
                //   ||  ++- TV system (0: NTSC; 2: PAL; 1/3: dual compatible)
                //   |+----- PRG RAM ($6000-$7FFF) (0: present; 1: not present)
                //   +------ 0: Board has no bus conflicts; 1: Board has bus conflicts
                isNTSC = (iNesData[10].toInt() and 0b0000_0011) == 0
                isPrgRamPresent = (iNesData[10].toInt() and 0b0001_0000) == 0
                isHasBusConflicts = (iNesData[10].toInt() and 0b0010_0000) == 0
            } else {
                TODO(reason = "とりあえず持ってないので作るのは保留")
                // https://www.nesdev.org/wiki/NES_2.0
                // 8     Mapper MSB/Submapper
                //       D~7654 3210
                //         ---------
                //         SSSS NNNN
                //         |||| ++++- Mapper number D8..D11
                //         ++++------ Submapper number
//                mapperNo = (((iNesData[8].toUByte().toInt() and 0b0000_1111) shl 12) or
//                        (iNesData[7].toUByte().toInt() and 0b1111_0000) or
//                        (iNesData[6].toUByte().toInt() ushr 4))
//                subMapperNo = iNesData[8].toUByte().toInt() ushr 4
                // 9     PRG-ROM/CHR-ROM size MSB
                //       D~7654 3210
                //         ---------
                //         CCCC PPPP
                //         |||| ++++- PRG-ROM size MSB
                //         ++++------ CHR-ROM size MSB
                // 10    PRG-RAM/EEPROM size
                //       D~7654 3210
                //         ---------
                //         pppp PPPP
                //         |||| ++++- PRG-RAM (volatile) shift count
                //         ++++------ PRG-NVRAM/EEPROM (non-volatile) shift count
                //       If the shift count is zero, there is no PRG-(NV)RAM.
                //       If the shift count is non-zero, the actual size is
                //       "64 << shift count" bytes, i.e. 8192 bytes for a shift count of 7.
                // 11    CHR-RAM size
                //       D~7654 3210
                //         ---------
                //         cccc CCCC
                //         |||| ++++- CHR-RAM size (volatile) shift count
                //         ++++------ CHR-NVRAM size (non-volatile) shift count
                //       If the shift count is zero, there is no CHR-(NV)RAM.
                //       If the shift count is non-zero, the actual size is
                //       "64 << shift count" bytes, i.e. 8192 bytes for a shift count of 7.
                // 12    CPU/PPU Timing
                //       D~7654 3210
                //         ---------
                //         .... ..VV
                //                ++- CPU/PPU timing mode
                //                     0: RP2C02 ("NTSC NES")
                //                     1: RP2C07 ("Licensed PAL NES")
                //                     2: Multiple-region
                //                     3: UA6538 ("Dendy")
//                isNTSC = (iNesData[12].toInt() and 0b0000_0011) == 0
                // 13    When Byte 7 AND 3 =1: Vs. System Type
                //       D~7654 3210
                //         ---------
                //         MMMM PPPP
                //         |||| ++++- Vs. PPU Type
                //         ++++------ Vs. Hardware Type
                //       When Byte 7 AND 3 =3: Extended Console Type
                //       D~7654 3210
                //         ---------
                //         .... CCCC
                //              ++++- Extended Console Type
                // 14    Miscellaneous ROMs
                //       D~7654 3210
                //         ---------
                //         .... ..RR
                //                ++- Number of miscellaneous ROMs present
                // 15    Default Expansion Device
                //       D~7654 3210
                //         ---------
                //         ..DD DDDD
                //           ++-++++- Default Expansion Device
            }
            // ROMデータ取得
            val prgRom = iNesData.copyOfRange(
                fromIndex = 0x10,
                toIndex = 0x10 + prgRom16Units * 16 * 1024,
            ).asUByteArray()
            val chrRom = iNesData.copyOfRange(
                fromIndex = 0x10 + prgRom16Units * 16 * 1024,
                toIndex = 0x10 + prgRom16Units * 16 * 1024 + chrRom8Units * 8 * 1024,
            ).asUByteArray()
            val information = Information(
                prgRom16Units = prgRom16Units,
                chrRom8Units = chrRom8Units,
                mirroringVertical = mirroringVertical,
                batteryBackup = batteryBackup,
                trainer = trainer,
                ignoreMirroring = ignoreMirroring,
                vsUniSystem = vsUniSystem,
                playChoice10 = playChoice10,
                isNES20 = isNES20,
                mapperNo = mapperNo,
                subMapperNo = subMapperNo,
                rpgRam8Units = rpgRam8Units,
                isNTSC = isNTSC,
                isPrgRamPresent = isPrgRamPresent,
                isHasBusConflicts = isHasBusConflicts,
            )
            println(
                """
                |[cartridge]
                |${
                    iNesData.asUByteArray().take(16).withIndex()
                        .joinToString { (i, v) -> "$i:" + v.toString(16).padStart(2, '0') }
                }
                |${information}
                |""".trimMargin().trim()
            )
            check(value = information.trainer.not()) { "Not support trainer." }
            return Cartridge(
                information = information,
                backupRAM = backupRAM,
                prgRom = prgRom,
                chrRom = chrRom,
            )
        }
    }
}
