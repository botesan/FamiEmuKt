package jp.mito.famiemukt.emurator.cpu

enum class InterruptType(val executeCycle: Int) {
    RESET(executeCycle = 7), NMI(executeCycle = 7), IRQ(executeCycle = 7), BRK(executeCycle = 7),
}

interface Interrupter {
    fun requestREST()
    fun requestNMI(levelLow: Boolean = true)
    fun requestOnIRQ()
    fun requestOffIRQ()
    val isRequestedIRQ: Boolean
}
