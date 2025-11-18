package jp.mito.famiemukt.emurator.cartridge.mapper

import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cartridge.Mirroring
import jp.mito.famiemukt.emurator.cartridge.NothingStateObserver
import jp.mito.famiemukt.emurator.cartridge.StateObserver
import jp.mito.famiemukt.emurator.cpu.Interrupter

@OptIn(ExperimentalUnsignedTypes::class)
interface Mapper {
    val information: Cartridge.Information
    var interrupter: Interrupter
    val stateObserver: StateObserver

    fun writePRG(address: Int, value: UByte)
    fun readPRG(address: Int): UByte
    fun writeCHR(address: Int, value: UByte)
    fun readCHR(address: Int): UByte
    fun writeExt(address: Int, value: UByte)
    fun readExt(address: Int): UByte
    fun writeBackup(address: Int, value: UByte)
    fun readBackup(address: Int): UByte

    fun calculateNameTableIndex(address: Int): Int

    companion object {
        val NothingStateObserver: StateObserver = NothingStateObserver()
        fun create(
            cartridge: Cartridge,
            prgRom: UByteArray,
            chrRom: UByteArray,
        ): Mapper {
            return when (cartridge.information.mapperNo) {
                0 -> Mapper000(cartridge, prgRom = prgRom, chrRom = chrRom)
                1 -> Mapper001(cartridge, prgRom = prgRom, chrRom = chrRom)
                2 -> Mapper002(cartridge, prgRom = prgRom, chrRom = chrRom)
                3 -> Mapper003(cartridge, prgRom = prgRom, chrRom = chrRom)
                4 -> Mapper004(cartridge, prgRom = prgRom, chrRom = chrRom)
                5 -> Mapper005(cartridge, prgRom = prgRom, chrRom = chrRom)
                10 -> Mapper010(cartridge, prgRom = prgRom, chrRom = chrRom)
                16 -> Mapper016(cartridge, prgRom = prgRom, chrRom = chrRom)
                19 -> Mapper019(cartridge, prgRom = prgRom, chrRom = chrRom)
                67 -> Mapper067(cartridge, prgRom = prgRom, chrRom = chrRom)
                88 -> Mapper088(cartridge, prgRom = prgRom, chrRom = chrRom)
                93 -> Mapper093(cartridge, prgRom = prgRom, chrRom = chrRom)
                118 -> Mapper118(cartridge, prgRom = prgRom, chrRom = chrRom)
                else -> error("Not support Mapper. no=${cartridge.information.mapperNo}")
            }
        }
    }
}

abstract class MapperBase(protected val cartridge: Cartridge) : Mapper {
    override val information: Cartridge.Information by cartridge::information
    override lateinit var interrupter: Interrupter
    protected open val mirroring: Mirroring = defaultMirroring(cartridge.information)
    override val stateObserver: StateObserver = Mapper.NothingStateObserver

    override fun writePRG(address: Int, value: UByte): Unit = throw UnsupportedOperationException(
        "PRG RAMは標準で未実装 ${address.toString(16).padStart(4, '0')} <= ${value.toString(16).padStart(2, '0')}"
    )

    override fun writeCHR(address: Int, value: UByte): Unit = throw UnsupportedOperationException(
        "CHR RAMは標準で未実装 ${address.toString(16).padStart(4, '0')} <= ${value.toString(16).padStart(2, '0')}"
    )

    override fun writeExt(address: Int, value: UByte): Unit = throw UnsupportedOperationException(
        "拡張RAMは標準で未実装 ${address.toString(16).padStart(4, '0')} <= ${value.toString(16).padStart(2, '0')}"
    )

    override fun readExt(address: Int): UByte = throw UnsupportedOperationException(
        "拡張RAMは標準で未実装 ${address.toString(16).padStart(4, '0')}"
    )

    override fun writeBackup(address: Int, value: UByte) =
        cartridge.backupRAM.write(index = (address and 0x1FFF), value = value)

    override fun readBackup(address: Int): UByte = cartridge.backupRAM.read(index = (address and 0x1FFF))

    // https://www.nesdev.org/wiki/Mirroring
    override fun calculateNameTableIndex(address: Int): Int {
        return when (mirroring) {
            // https://www.nesdev.org/wiki/Mirroring#Horizontal
            Mirroring.Horizontal -> {
                address and 0x2400.inv()
//                when (address) {
//                    in 0x2000U..0x23FFU -> address.toInt() and 0x2000.inv()
//                    in 0x2400U..0x27FFU -> address.toInt() and 0x2400.inv() // 0x2000Uにミラー
//                    in 0x2800U..0x2BFFU -> address.toInt() and 0x2000.inv()
//                    in 0x2C00U..0x2FFFU -> address.toInt() and 0x2400.inv() // 0x2800Uにミラー
//                    else -> error("Illegal address. ${address.toString(radix = 16)}")
//                }
            }
            // https://www.nesdev.org/wiki/Mirroring#Vertical
            Mirroring.Vertical -> {
                address and 0x2800.inv()
//                when (address) {
//                    in 0x2000U..0x23FFU -> address.toInt() and 0x2000.inv()
//                    in 0x2400U..0x27FFU -> address.toInt() and 0x2000.inv()
//                    in 0x2800U..0x2BFFU -> address.toInt() and 0x2800.inv() // 0x2000Uにミラー
//                    in 0x2C00U..0x2FFFU -> address.toInt() and 0x2800.inv() // 0x2400Uにミラー
//                    else -> error("Illegal address. ${address.toString(radix = 16)}")
//                }
            }
            // https://www.nesdev.org/wiki/Mirroring#Single-Screen
            Mirroring.SingleScreen0 -> {
                // TODO: これで実装合ってる？
                address and 0x2C00.inv()
            }
            // https://www.nesdev.org/wiki/Mirroring#Single-Screen
            Mirroring.SingleScreen1 -> {
                // TODO: これで実装合ってる？
                (address and 0x2C00.inv()) or 0x0800
            }
            // https://www.nesdev.org/wiki/Mirroring#4-Screen
            Mirroring.FourScreen -> {
                address and 0x2000.inv()
//                when (address) {
//                    in 0x2000U..0x23FFU -> address.toInt() and 0x2000.inv()
//                    in 0x2400U..0x27FFU -> address.toInt() and 0x2000.inv()
//                    in 0x2800U..0x2BFFU -> address.toInt() and 0x2000.inv()
//                    in 0x2C00U..0x2FFFU -> address.toInt() and 0x2000.inv()
//                    else -> error("Illegal address. ${address.toString(radix = 16)}")
//                }
            }
            // https://www.nesdev.org/wiki/Mirroring#Other
            Mirroring.Other -> calculateNameTableIndexMirroringOther(address = address)
        }
    }

    // マップ毎で独自でミラーリングする場合
    protected open fun calculateNameTableIndexMirroringOther(address: Int): Int = error("Not implement.")

    companion object {
        fun defaultMirroring(information: Cartridge.Information): Mirroring {
            if (information.ignoreMirroring) return Mirroring.FourScreen
            if (information.mirroringVertical) return Mirroring.Vertical
            return Mirroring.Horizontal
        }
    }
}
