package jp.mito.famiemukt.emurator.cartridge

interface StateObserver {
    fun notifyRisingA12PPU()
    fun notifyM2Cycle(cycle: Int)
}

abstract class StateObserverAdapter : StateObserver {
    override fun notifyRisingA12PPU() = Unit
    override fun notifyM2Cycle(cycle: Int) = Unit
}

class NothingStateObserver : StateObserverAdapter()
