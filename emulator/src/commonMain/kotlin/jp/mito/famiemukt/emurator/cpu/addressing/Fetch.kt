package jp.mito.famiemukt.emurator.cpu.addressing

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters

fun fetch(bus: CPUBus, registers: CPURegisters): UByte =
    bus.readMemIO(address = (registers.PC++).toInt())

fun fetchWord(bus: CPUBus, registers: CPURegisters): UShort =
    (fetch(bus, registers).toInt() or (fetch(bus, registers).toInt() shl 8)).toUShort()
