package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

sealed interface OpCode {
    val name: String
    fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int
}

sealed class BaseOpCode(override val name: String) : OpCode {
    override fun toString(): String = this::class.simpleName + "(" + name + ")"
}

sealed class OfficialOpCode(name: String) : BaseOpCode(name)

sealed class UnofficialOpCode(name: String) : BaseOpCode(name)
