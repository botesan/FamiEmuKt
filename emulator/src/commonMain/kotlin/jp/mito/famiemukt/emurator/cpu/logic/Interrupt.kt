package jp.mito.famiemukt.emurator.cpu.logic

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.InterruptType
import jp.mito.famiemukt.emurator.util.toHex

const val INTERRUPT_ADDRESS_RESET: Int = 0xFFFC
const val INTERRUPT_ADDRESS_NMI: Int = 0xFFFA
const val INTERRUPT_ADDRESS_IRQ: Int = 0xFFFE
const val INTERRUPT_ADDRESS_BRK: Int = 0xFFFE

fun executeInterruptBRK(bus: CPUBus, registers: CPURegisters) =
    executeInterrupt(type = InterruptType.BRK, bus, registers)

fun executeInterrupt(type: InterruptType, bus: CPUBus, registers: CPURegisters) {
    val beforePC = registers.PC
    // タイプ毎に実行
    when (type) {
        InterruptType.RESET -> {
            // スタック強制移動（厳密にはBusが読み込み専用のままpushが動作するためらしい）
            // 下記サイトを見ると電源ONはその通りで単純なリセット時は違うっぽい（S -= 3）
            // https://www.pagetable.com/?p=410
            // 割り込み（リセット状態の反映）
            // https://www.nesdev.org/wiki/CPU_power_up_state
            // Initial CPU Register Values
            // Register | At Power      | After Reset
            // A, X, Y  | 0	            | unchanged
            // PC       | ($FFFC)       | ($FFFC)
            registers.PC = bus.readWordMemIO(address = INTERRUPT_ADDRESS_RESET)
            // S[1]     | $00 - 3 = $FD | S -= 3
            registers.S = 0xFDU // (cpuRegisters.S.toInt() - 3).toUByte() // 0xFDU
            // C        | 0             | unchanged
            // Z        | 0             | unchanged
            // I        | 1             | 1
            registers.P.I = true
            // D        | 0             | unchanged
            // V        | 0             | unchanged
            // N        | 0             | unchanged
            printMovePCLog(command = ">RESET", beforePC = beforePC, registers = registers)
        }

        InterruptType.NMI -> {
            // PCを上位バイトからpush
            val pc = registers.PC
            val h = (pc.toUInt() shr 8).toUByte()
            val l = pc.toUByte()
            push(value = h, bus, registers)
            push(value = l, bus, registers)
            // break flagをクリア、bit 5 は予約で 1 セット済み（のはず）
            val p = registers.P.copy().apply { B = false }.value
            push(value = p, bus, registers)
            // 割り込み
            registers.PC = bus.readWordMemIO(address = INTERRUPT_ADDRESS_NMI)
            registers.P.I = true
            printMovePCLog(command = ">NMI", beforePC = beforePC, registers = registers)
        }

        InterruptType.IRQ -> {
            // PCを上位バイトからpush
            val pc = registers.PC
            val h = (pc.toUInt() shr 8).toUByte()
            val l = pc.toUByte()
            push(value = h, bus, registers)
            push(value = l, bus, registers)
            // break flagをクリア、bit 5 は予約で 1 セット済み（のはず）
            val p = registers.P.copy().apply { B = false }.value
            push(value = p, bus, registers)
            // 割り込み
            registers.PC = bus.readWordMemIO(address = INTERRUPT_ADDRESS_IRQ)
            registers.P.I = true
            printMovePCLog(command = ">IRQ", beforePC = beforePC, registers = registers)
        }
        /*
        https://www.masswerk.at/6502/6502_instruction_set.html#BRK
        BRK initiates a software interrupt similar to a hardware interrupt (IRQ).
        The return address pushed to the stack is PC+2, providing an extra byte of spacing for a break mark
        (identifying a reason for the break.)
        The status register will be pushed to the stack with the break flag set to 1.
        However, when retrieved during RTI or by a PLP instruction, the break flag will be ignored.
        The interrupt disable flag is not set automatically. // TODO: 読み間違い？
        interrupt, push PC+2, push SR
         */
        InterruptType.BRK -> {
            // PCを上位バイトからpush
            val pc = registers.PC + (2U - 1U)
            val h = (pc shr 8).toUByte()
            val l = pc.toUByte()
            push(value = h, bus, registers)
            push(value = l, bus, registers)
            // break flagをセット、bit 5 は予約で 1 セット済み（のはず）
            val p = registers.P.copy().apply { B = true }.value
            push(value = p, bus, registers)
            // 割り込み
            registers.PC = bus.readWordMemIO(address = INTERRUPT_ADDRESS_BRK)
            // TODO: 上記読み間違い？必要？
            //  Wiki側の記載では、どの割り込みでもセットされると書いてある
            // https://www.nesdev.org/wiki/Status_flags#I:_Interrupt_Disable
            // Automatically set by the CPU after pushing flags to the stack
            // when any interrupt is triggered (NMI, IRQ/BRK, or reset).
            // Restored to its previous state from the stack when leaving an interrupt handler with RTI.
            registers.P.I = true
            printMovePCLog(command = ">BRK", beforePC = beforePC, registers = registers)
        }
    }
}

fun printMovePCLog(
    command: String,
    beforePC: UShort,
    registers: CPURegisters,
) {
    @Suppress("ConstantConditionIf")
    if (false) {
        val r = registers
        println("${beforePC.toHex()} : ${command.padEnd(length = 6)} ${r.PC.toHex()}  / S:${r.S.toHex()} / ${r.P.value.toHex()}")
//        println("${beforePC.toHex()} : ${command.padEnd(length = 6)} ${r.PC.toHex()}  / S:${r.S.toHex()} / ${r.P.value.toHex()} / $beforeExecutedOpCode")
    }
}
