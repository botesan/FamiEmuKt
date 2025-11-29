package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

sealed interface OpCode {
    val name: String

    /**
     * @return 下位16bit:追加サイクル数、上位16bit:0!=分岐命令が成功したかどうか
     */
    fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int
}

sealed class BaseOpCode(override val name: String) : OpCode {
    override fun toString(): String = this::class.simpleName + "(" + name + ")"
}

sealed class OfficialOpCode(name: String) : BaseOpCode(name)

sealed class UnofficialOpCode(name: String) : BaseOpCode(name)
