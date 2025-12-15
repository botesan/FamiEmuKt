package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction

sealed interface OpCode {
    val name: String
    val isAddCyclePageCrossed: Boolean

    /**
     * @return 下位16bit:追加サイクル数、上位16bit:0!=分岐命令が成功したかどうか
     */
    fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int
}

sealed class BaseOpCode(override val name: String, override val isAddCyclePageCrossed: Boolean) : OpCode {
    override fun toString(): String = this::class.simpleName + "(" + name + ")"
}

sealed class OfficialOpCode(name: String, isAddCyclePageCrossed: Boolean) : BaseOpCode(name, isAddCyclePageCrossed)

sealed class UnofficialOpCode(name: String, isAddCyclePageCrossed: Boolean) : BaseOpCode(name, isAddCyclePageCrossed)
