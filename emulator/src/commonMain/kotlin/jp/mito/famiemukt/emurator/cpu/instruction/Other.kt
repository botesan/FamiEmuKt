package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

// その他
// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html
//  Other
//   NOP

/* NOP */
object NOP : OfficialOpCode(name = "NOP") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int = 0
}
