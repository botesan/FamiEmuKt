package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

// （広い意味での）算術命令
// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html
//  Arithmetic Operations
//   ADC SBC
//  Decrements & Increments
//   DEC DEX DEY INC INX INY
//  Logical Operations
//   AND EOR ORA
//  Shift & Rotate Instructions
//   ASL LSR ROL ROR
//  Comparisons
//   CMP CPX CPY
//  Bit Test
//   BIT

/* ADC (A + メモリ + キャリーフラグ) を演算して結果をAへ返します。[N.V.0.0.0.0.Z.C] */
object ADC : OfficialOpCode(name = "ADC", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val a = registers.A
        val memory = instruction.addressing.read(operand, bus)
        val carry = if (registers.P.C) 1 else 0
        val resultS = a.toByte() + memory.toByte() + carry
        val resultU = a + memory + carry.toUInt()
        registers.A = resultU.toUByte()
        registers.P.N = resultU.toByte() < 0
        registers.P.V = (resultS < Byte.MIN_VALUE || resultS > Byte.MAX_VALUE)
        registers.P.Z = (resultU.toByte() == 0.toByte())
        registers.P.C = ((resultU and 0x100U) != 0U)
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* SBC (A - メモリ - キャリーフラグの反転) を演算して結果をAへ返します。[N.V.0.0.0.0.Z.C] */
object SBC : OfficialOpCode(name = "SBC", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val a = registers.A
        val memory = instruction.addressing.read(operand, bus)
        val carry = if (registers.P.C.not()) 1 else 0
        val resultS = a.toByte() - memory.toByte() - carry
        val resultU = a - memory - carry.toUInt()
        registers.A = resultU.toUByte()
        registers.P.N = resultU.toByte() < 0
        registers.P.V = (resultS < Byte.MIN_VALUE || resultS > Byte.MAX_VALUE)
        registers.P.Z = (resultU.toByte() == 0.toByte())
        registers.P.C = ((resultU and 0x100U) == 0U)
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* DEC メモリをデクリメントします。[N.0.0.0.0.0.Z.0] */
// https://www.nesdev.org/wiki/Instruction_reference#DEC
object DEC : OfficialOpCode(name = "DEC", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = bus.readMemIO(address = operand.operand)
        val result = (memory - 1U).toUByte()
        bus.writeMemIO(address = operand.operand, value = result)
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        // ページまたぎでも追加無し
        operand.recycle()
        return 0 // operand.addCycle
    }
}

/* DEX Xをデクリメントします。[N.0.0.0.0.0.Z.0] */
object DEX : OfficialOpCode(name = "DEX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = (registers.X - 1U).toUByte()
        registers.X = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* DEY Yをデクリメントします。[N.0.0.0.0.0.Z.0] */
object DEY : OfficialOpCode(name = "DEY", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = (registers.Y - 1U).toUByte()
        registers.Y = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* INC メモリをインクリメントします。[N.0.0.0.0.0.Z.0] */
// https://www.nesdev.org/wiki/Instruction_reference#INC
object INC : OfficialOpCode(name = "INC", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = bus.readMemIO(address = operand.operand)
        val result = (memory + 1U).toUByte()
        bus.writeMemIO(address = operand.operand, value = result)
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        // ページまたぎでも追加無し
        operand.recycle()
        return 0 // operand.addCycle
    }
}

/* INX Xをインクリメントします。[N.0.0.0.0.0.Z.0] */
object INX : OfficialOpCode(name = "INX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = (registers.X + 1U).toUByte()
        registers.X = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* INY Yをインクリメントします。[N.0.0.0.0.0.Z.0] */
object INY : OfficialOpCode(name = "INY", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val result = (registers.Y + 1U).toUByte()
        registers.Y = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        return 0
    }
}

/* AND Aとメモリを論理AND演算して結果をAへ返します。[N.0.0.0.0.0.Z.0] */
object AND : OfficialOpCode(name = "AND", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = registers.A and memory
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* EOR Aとメモリを論理XOR演算して結果をAへ返します。[N.0.0.0.0.0.Z.0] */
object EOR : OfficialOpCode(name = "EOR", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = registers.A xor memory
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* ORA Aとメモリを論理OR演算して結果をAへ返します。[N.0.0.0.0.0.Z.0] */
object ORA : OfficialOpCode(name = "ORA", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = registers.A or memory
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* ASL Aまたはメモリを左へシフトします。[N.0.0.0.0.0.Z.C] */
// https://www.nesdev.org/wiki/Instruction_reference#ASL
object ASL : OfficialOpCode(name = "ASL", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val aOrMemory = instruction.addressing.read(operand, bus)
        val result = aOrMemory.toUInt() shl 1
        instruction.addressing.write(result.toUByte(), operand, bus, registers)
        registers.P.N = result.toUByte().toByte() < 0
        registers.P.Z = (result.toUByte() == 0.toUByte())
        registers.P.C = ((result and 0x100U) != 0U)
        // ページまたぎでも追加無し
        operand.recycle()
        return 0 // operand.addCycle
    }
}

/* LSR Aまたはメモリを右へシフトします。[N.0.0.0.0.0.Z.C]
   Shift One Bit Right (Memory or Accumulator) / 0 -> [76543210] -> C */
// https://www.nesdev.org/wiki/Instruction_reference#LSR
object LSR : OfficialOpCode(name = "LSR", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val aOrMemory = instruction.addressing.read(operand, bus)
        val result = aOrMemory.toUInt() shr 1
        instruction.addressing.write(result.toUByte(), operand, bus, registers)
        registers.P.N = result.toUByte().toByte() < 0
        registers.P.Z = (result == 0U)
        registers.P.C = ((aOrMemory.toUInt() and 0x01U) != 0U)
        // ページまたぎでも追加無し
        operand.recycle()
        return 0 // operand.addCycle
    }
}

/* ROL Aまたはメモリを左へローテートします。[N.0.0.0.0.0.Z.C]
   Rotate One Bit Left (Memory or Accumulator) / C <- [76543210] <- C */
// https://www.nesdev.org/wiki/Instruction_reference#ROL
object ROL : OfficialOpCode(name = "ROL", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val aOrMemory = instruction.addressing.read(operand, bus)
        val result = if (registers.P.C) {
            (aOrMemory.toUInt() shl 1) or 0x01U
        } else {
            (aOrMemory.toUInt() shl 1)
        }
        instruction.addressing.write(result.toUByte(), operand, bus, registers)
        registers.P.N = result.toUByte().toByte() < 0
        registers.P.Z = (result.toUByte() == 0.toUByte())
        registers.P.C = ((result and 0x100U) != 0U)
        // ページまたぎでも追加無し
        operand.recycle()
        return 0 // operand.addCycle
    }
}

/* ROR Aまたはメモリを右へローテートします。[N.0.0.0.0.0.Z.C]
   Rotate One Bit Right (Memory or Accumulator) / C -> [76543210] -> C */
// https://www.nesdev.org/wiki/Instruction_reference#ROR
object ROR : OfficialOpCode(name = "ROR", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val aOrMemory = instruction.addressing.read(operand, bus)
        val result = if (registers.P.C) {
            (aOrMemory.toUInt() shr 1) or 0b1000_0000U
        } else {
            (aOrMemory.toUInt() shr 1)
        }
        instruction.addressing.write(result.toUByte(), operand, bus, registers)
        registers.P.N = result.toUByte().toByte() < 0
        registers.P.Z = (result == 0U)
        registers.P.C = ((aOrMemory.toUInt() and 0x001U) != 0U)
        // ページまたぎでも追加無し
        operand.recycle()
        return 0 // operand.addCycle
    }
}

/* CMP Aとメモリを比較演算します。[N.0.0.0.0.0.Z.C]
   Compare Memory with Accumulator A - M */
object CMP : OfficialOpCode(name = "CMP", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = registers.A - memory
        registers.P.N = result.toUByte().toByte() < 0
        registers.P.Z = (result == 0U)
        registers.P.C = ((result and 0x100U) == 0U)
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* CPX Xとメモリを比較演算します。[N.0.0.0.0.0.Z.C]
   Compare Memory and Index X. X - M */
object CPX : OfficialOpCode(name = "CPX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = registers.X - memory
        registers.P.N = result.toUByte().toByte() < 0
        registers.P.Z = (result == 0U)
        registers.P.C = ((result and 0x100U) == 0U)
        operand.recycle()
        return 0
    }
}

/* CPY Yとメモリを比較演算します。[N.0.0.0.0.0.Z.C]
   Compare Memory and Index Y. Y - M */
object CPY : OfficialOpCode(name = "CPY", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = registers.Y - memory
        registers.P.N = result.toUByte().toByte() < 0
        registers.P.Z = (result == 0U)
        registers.P.C = ((result and 0x100U) == 0U)
        operand.recycle()
        return 0
    }
}

/* BIT Aとメモリをビット比較演算します。[N.V.0.0.0.0.Z.0]
   bits 7 and 6 of operand are transfered to bit 7 and 6 of SR (N,V);
   the zero-flag is set to the result of operand AND accumulator.  */
object BIT : OfficialOpCode(name = "BIT", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus).toUInt()
        val result = registers.A.toUInt() and memory
        registers.P.N = ((memory and 0b1000_0000U) != 0U)
        registers.P.Z = (result == 0U)
        registers.P.V = ((memory and 0b0100_0000U) != 0U)
        operand.recycle()
        return 0
    }
}
