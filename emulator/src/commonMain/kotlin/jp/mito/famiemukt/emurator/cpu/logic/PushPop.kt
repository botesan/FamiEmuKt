package jp.mito.famiemukt.emurator.cpu.logic

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters

// スタック操作用関数

fun push(value: UByte, bus: CPUBus, registers: CPURegisters) {
    bus.writeMemIO(address = 0x0100 + registers.S.toInt(), value = value)
    registers.S--
}

fun pop(bus: CPUBus, registers: CPURegisters): UByte {
    registers.S++
    return bus.readMemIO(address = 0x0100 + registers.S.toInt())
}
