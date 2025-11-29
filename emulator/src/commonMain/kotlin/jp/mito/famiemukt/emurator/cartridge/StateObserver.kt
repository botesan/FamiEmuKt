package jp.mito.famiemukt.emurator.cartridge

interface StateObserver {
    fun notifyRisingA12PPU()
    fun notifyM2OneCycle()
}

abstract class StateObserverAdapter : StateObserver {
    override fun notifyRisingA12PPU() = Unit
    override fun notifyM2OneCycle() = Unit
}

class NothingStateObserver : StateObserverAdapter()
