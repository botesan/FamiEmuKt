package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

// フラグ変更命令
// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html
//  Flag Instructions
//   CLC CLD CLI CLV SEC SED SEI

/* CLC キャリーフラグをクリアします。[0.0.0.0.0.0.0.C] */
object CLC : OfficialOpCode(name = "CLC") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        registers.P.C = false
        return 0
    }
}

/* CLD BCDモードから通常モードに戻ります。ファミコンでは実装されていません。[0.0.0.0.D.0.0.0] */
object CLD : OfficialOpCode(name = "CLD") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        registers.P.D = false
        return 0
    }
}

/* CLI IRQ割り込みを許可します。[0.0.0.0.0.I.0.0] */
object CLI : OfficialOpCode(name = "CLI") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        registers.P.I = false
        return 0
    }
}

/* CLV オーバーフローフラグをクリアします。[0.V.0.0.0.0.0.0] */
object CLV : OfficialOpCode(name = "CLV") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        registers.P.V = false
        return 0
    }
}

/* SEC キャリーフラグをセットします。[0.0.0.0.0.0.0.C] */
object SEC : OfficialOpCode(name = "SEC") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        registers.P.C = true
        return 0
    }
}

/* SED BCDモードに設定します。ファミコンでは実装されていません。[0.0.0.0.D.0.0.0] */
object SED : OfficialOpCode(name = "SED") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        registers.P.D = true
        return 0
    }
}

/* SEI IRQ割り込みを禁止します。[0.0.0.0.0.I.0.0] */
object SEI : OfficialOpCode(name = "SEI") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        registers.P.I = true
        return 0
    }
}
