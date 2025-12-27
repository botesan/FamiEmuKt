package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

// 転送命令
// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html
//  Transfer Instructions
//   LDA LDX LDY STA STX STY TAX TAY TSX TXA TXS TYA

/* LDA メモリからAにロードします。[N.0.0.0.0.0.Z.0] */
object LDA : OfficialOpCode(name = "LDA", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val result = instruction.addressing.read(operand, bus)
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* LDX メモリからXにロードします。[N.0.0.0.0.0.Z.0] */
object LDX : OfficialOpCode(name = "LDX", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val result = instruction.addressing.read(operand, bus)
        registers.X = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* LDY メモリからYにロードします。[N.0.0.0.0.0.Z.0] */
object LDY : OfficialOpCode(name = "LDY", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val result = instruction.addressing.read(operand, bus)
        registers.Y = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* STA Aからメモリにストアします。[0.0.0.0.0.0.0.0] */
object STA : OfficialOpCode(name = "STA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        bus.writeMemIO(address = operand.operand, value = registers.A)
        operand.recycle()
        return 0
    }
}

/* STX Xからメモリにストアします。[0.0.0.0.0.0.0.0] */
object STX : OfficialOpCode(name = "STX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        bus.writeMemIO(address = operand.operand, value = registers.X)
        operand.recycle()
        return 0
    }
}

/* STY Yからメモリにストアします。[0.0.0.0.0.0.0.0] */
object STY : OfficialOpCode(name = "STY", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        bus.writeMemIO(address = operand.operand, value = registers.Y)
        operand.recycle()
        return 0
    }
}

/* TAX AをXへコピーします。[N.0.0.0.0.0.Z.0] */
object TAX : OfficialOpCode(name = "TAX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = registers.A
        registers.X = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* TAY AをYへコピーします。[N.0.0.0.0.0.Z.0] */
object TAY : OfficialOpCode(name = "TAY", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = registers.A
        registers.Y = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* TSX SをXへコピーします。[N.0.0.0.0.0.Z.0] */
object TSX : OfficialOpCode(name = "TSX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = registers.S
        registers.X = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* TXA XをAへコピーします。[N.0.0.0.0.0.Z.0] */
object TXA : OfficialOpCode(name = "TXA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = registers.X
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* TXS XをSへコピーします。[N.0.0.0.0.0.Z.0] → フラグを設定しない下記の記述が正しい？
   X -> SP  N Z C I D V
            - - - - - - */
object TXS : OfficialOpCode(name = "TXS", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        registers.S = registers.X
        return 0
    }
}

/* TYA YをAへコピーします。[N.0.0.0.0.0.Z.0] */
object TYA : OfficialOpCode(name = "TYA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = registers.Y
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}
