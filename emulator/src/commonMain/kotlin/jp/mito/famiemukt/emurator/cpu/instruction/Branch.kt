package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

// 分岐命令
// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html
//  Conditional Branch Instructions
//   BCC BCS BEQ BMI BNE BPL BVC BVS

sealed class BranchOpCode(name: String, isAddCyclePageCrossed: Boolean) : OfficialOpCode(name, isAddCyclePageCrossed)

/* BCC キャリーフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BCC : BranchOpCode(name = "BCC", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.C) {
            operand.recycle()
            0
        } else {
            registers.PC = operand.operand.toUShort()
            val addCycle = operand.addCycle
            operand.recycle()
            addCycle + 1 or 0x0001_0000
        }
    }
}

/* BCS キャリーフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BCS : BranchOpCode(name = "BCS", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.C.not()) {
            operand.recycle()
            0
        } else {
            registers.PC = operand.operand.toUShort()
            val addCycle = operand.addCycle
            operand.recycle()
            addCycle + 1 or 0x0001_0000
        }
    }
}

/* BEQ ゼロフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BEQ : BranchOpCode(name = "BEQ", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.Z.not()) {
            operand.recycle()
            0
        } else {
            registers.PC = operand.operand.toUShort()
            val addCycle = operand.addCycle
            operand.recycle()
            addCycle + 1 or 0x0001_0000
        }
    }
}

/* BMI ネガティブフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BMI : BranchOpCode(name = "BMI", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.N.not()) {
            operand.recycle()
            0
        } else {
            registers.PC = operand.operand.toUShort()
            val addCycle = operand.addCycle
            operand.recycle()
            addCycle + 1 or 0x0001_0000
        }
    }
}

/* BNE ゼロフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BNE : BranchOpCode(name = "BNE", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.Z) {
            operand.recycle()
            0
        } else {
            registers.PC = operand.operand.toUShort()
            val addCycle = operand.addCycle
            operand.recycle()
            addCycle + 1 or 0x0001_0000
        }
    }
}

/* BPL ネガティブフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BPL : BranchOpCode(name = "BPL", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.N) {
            operand.recycle()
            0
        } else {
            registers.PC = operand.operand.toUShort()
            val addCycle = operand.addCycle
            operand.recycle()
            addCycle + 1 or 0x0001_0000
        }
    }
}

/* BVC オーバーフローフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BVC : BranchOpCode(name = "BVC", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.V) {
            operand.recycle()
            0
        } else {
            registers.PC = operand.operand.toUShort()
            val addCycle = operand.addCycle
            operand.recycle()
            addCycle + 1 or 0x0001_0000
        }
    }
}

/* BVS オーバーフローフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
object BVS : BranchOpCode(name = "BVS", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        return if (registers.P.V.not()) {
            operand.recycle()
            0
        } else {
            registers.PC = operand.operand.toUShort()
            val addCycle = operand.addCycle
            operand.recycle()
            addCycle + 1 or 0x0001_0000
        }
    }
}
