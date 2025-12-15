package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction
import jp.mito.famiemukt.emurator.cpu.ProcessorStatus
import jp.mito.famiemukt.emurator.cpu.logic.pop
import jp.mito.famiemukt.emurator.cpu.logic.push

// スタック命令
// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html
//  Stack Instructions
//   PHA PHP PLA PLP

/* PHA Aをスタックにプッシュダウンします。[0.0.0.0.0.0.0.0] */
object PHA : OfficialOpCode(name = "PHA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        push(value = registers.A, bus, registers)
        return 0
    }
}

/* PHP Pをスタックにプッシュダウンします。[0.0.0.0.0.0.0.0]
   The status register will be pushed with the break flag and bit 5 set to 1. */
object PHP : OfficialOpCode(name = "PHP", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        // break flagをセット、bit 5 は予約で 1 セット済み（のはず）
        val value = registers.P.copy().apply { B = true }.value
        push(value = value, bus, registers)
        return 0
    }
}

/* PLA スタックからAにポップアップします。[N.0.0.0.0.0.Z.0] */
object PLA : OfficialOpCode(name = "PLA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = pop(bus, registers)
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* PLP スタックからPにポップアップします。[N.V.R.B.D.I.Z.C]
   The status register will be pulled with the break flag and bit 5 ignored. */
object PLP : OfficialOpCode(name = "PLP", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        // break flag と bit 5 を無視（実質break flagを変更前で上書き）
        val value = pop(bus, registers)
        val result = ProcessorStatus(value = value).apply { B = registers.P.B }.value
        registers.P.value = result
        return 0
    }
}
