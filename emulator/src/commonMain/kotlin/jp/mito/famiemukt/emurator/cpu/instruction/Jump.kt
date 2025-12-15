package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction
import jp.mito.famiemukt.emurator.cpu.ProcessorStatus
import jp.mito.famiemukt.emurator.cpu.logic.executeInterruptBRK
import jp.mito.famiemukt.emurator.cpu.logic.pop
import jp.mito.famiemukt.emurator.cpu.logic.printMovePCLog
import jp.mito.famiemukt.emurator.cpu.logic.push

// ジャンプ（＋割り込み）命令
// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html
//  Jumps & Subroutines
//   JMP JSR RTS
//  Interrupts
//   BRK RTI

/* JMP アドレスへジャンプします。[0.0.0.0.0.0.0.0] */
object JMP : OfficialOpCode(name = "JMP", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        registers.PC = operand.operand.toUShort()
        return 0
    }
}

/* JSR サブルーチンを呼び出します。[0.0.0.0.0.0.0.0] */
object JSR : OfficialOpCode(name = "JSR", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val beforePC = registers.PC
        val operand = instruction.addressing.operand(bus, registers)
        // PCを上位バイトからpush（JSRでpushするのは次の命令の手前のアドレス）
        val value = registers.PC - 1U
        val h = (value shr 8).toUByte()
        val l = value.toUByte()
        push(value = h, bus, registers)
        push(value = l, bus, registers)
        registers.PC = operand.operand.toUShort()
        printMovePCLog(command = "JSR", beforePC = beforePC, registers = registers)
        return 0
    }
}

/* RTS サブルーチンから復帰します。[0.0.0.0.0.0.0.0] */
object RTS : OfficialOpCode(name = "RTS", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val beforePC = registers.PC
        // 下位バイトからpopしてPCへ（JSRでpushされているのは次の命令の手前のアドレス）
        val l = pop(bus, registers)
        val h = pop(bus, registers)
        val value = ((h.toUInt() shl 8) or (l.toUInt())) + 1U
        registers.PC = value.toUShort()
        printMovePCLog(command = "RTS", beforePC = beforePC, registers = registers)
        return 0
    }
}

/* BRK ソフトウェア割り込みを起こします。[0.0.0.B.0.0.0.0] */
object BRK : OfficialOpCode(name = "BRK", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        executeInterruptBRK(bus, registers)
        return 0
    }
}

/* RTI 割り込みルーチンから復帰します。[N.V.R.B.D.I.Z.C]
   The status register is pulled with the break flag and bit 5 ignored.
   Then PC is pulled from the stack. */
object RTI : OfficialOpCode(name = "RTI", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val beforePC = registers.PC
        // break flag と bit 5 を無視（実質break flagを変更前で上書き）
        val value = pop(bus, registers)
        val p = ProcessorStatus(value = value).apply { B = registers.P.B }.value
        registers.P.value = p
        // 下位バイトからpopしてPCへ
        val l = pop(bus, registers)
        val h = pop(bus, registers)
        val pc = (h.toUInt() shl 8) or (l.toUInt())
        registers.PC = pc.toUShort()
        printMovePCLog(command = "RTI", beforePC = beforePC, registers = registers)
        return 0
    }
}
