package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

// 分岐命令
// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html
//  Conditional Branch Instructions
//   BCC BCS BEQ BMI BNE BPL BVC BVS

/* BCC キャリーフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BCC : OfficialOpCode(name = "BCC") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.C) {
            0
        } else {
            registers.PC = operand.operand.toUShort()
            operand.addCycle + 1
        }
    }
}

/* BCS キャリーフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BCS : OfficialOpCode(name = "BCS") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.C.not()) {
            0
        } else {
            registers.PC = operand.operand.toUShort()
            operand.addCycle + 1
        }
    }
}

/* BEQ ゼロフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BEQ : OfficialOpCode(name = "BEQ") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.Z.not()) {
            0
        } else {
            registers.PC = operand.operand.toUShort()
            operand.addCycle + 1
        }
    }
}

/* BMI ネガティブフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BMI : OfficialOpCode(name = "BMI") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.N.not()) {
            0
        } else {
            registers.PC = operand.operand.toUShort()
            operand.addCycle + 1
        }
    }
}

/* BNE ゼロフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BNE : OfficialOpCode(name = "BNE") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.Z) {
            0
        } else {
            registers.PC = operand.operand.toUShort()
            operand.addCycle + 1
        }
    }
}

/* BPL ネガティブフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BPL : OfficialOpCode(name = "BPL") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.N) {
            0
        } else {
            registers.PC = operand.operand.toUShort()
            operand.addCycle + 1
        }
    }
}

/* BVC オーバーフローフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BVC : OfficialOpCode(name = "BVC") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.V) {
            0
        } else {
            registers.PC = operand.operand.toUShort()
            operand.addCycle + 1
        }
    }
}

/* BVS オーバーフローフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BVS : OfficialOpCode(name = "BVS") {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.V.not()) {
            0
        } else {
            registers.PC = operand.operand.toUShort()
            operand.addCycle + 1
        }
    }
}
