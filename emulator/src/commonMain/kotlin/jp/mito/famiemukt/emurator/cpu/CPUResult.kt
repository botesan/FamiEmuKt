package jp.mito.famiemukt.emurator.cpu

import jp.mito.famiemukt.emurator.util.Poolable

interface CPUResult : Poolable<CPUResult> {
    val addCycle: Int
    val branched: Boolean
    val instruction: Instruction?
    val interrupt: InterruptType?
    val executeCycle: Int get() = addCycle + (instruction?.cycle ?: 0) + (interrupt?.executeCycle ?: 0)

    companion object {
        fun obtain(
            addCycle: Int = 0,
            branched: Boolean = false,
            instruction: Instruction? = null,
            interrupt: InterruptType? = null,
        ): CPUResult = CPUResultImpl.obtain(addCycle, branched, instruction, interrupt)
    }

    private data class CPUResultImpl(
        override var addCycle: Int = 0,
        override var branched: Boolean = false,
        override var instruction: Instruction? = null,
        override var interrupt: InterruptType? = null,
    ) : CPUResult {
        override fun recycle() {
//            addCycle = 0
//            branched = false
//            instruction = null
//            interrupt = null
            pool.recycle(obj = this)
        }

        companion object {
            private val pool: Poolable.ObjectPool<CPUResultImpl> = Poolable.ObjectPool(initialCapacity = 1)
            fun obtain(
                addCycle: Int = 0,
                branched: Boolean = false,
                instruction: Instruction? = null,
                interrupt: InterruptType? = null,
            ): CPUResultImpl = pool.obtain()?.also {
                it.addCycle = addCycle
                it.branched = branched
                it.instruction = instruction
                it.interrupt = interrupt
            } ?: CPUResultImpl(
                addCycle = addCycle,
                branched = branched,
                instruction = instruction,
                interrupt = interrupt,
            )
        }
    }
}
