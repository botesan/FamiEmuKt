package jp.mito.famiemukt.emurator.cartridge

class A12(private val stateObserver: StateA12PPUObserver) {
    var address: Int = 0x0000
        set(value) {
            val old = field and 0x1000
            val new = value and 0x1000
            field = value
            if (old == 0x0000 && new == 0x1000) {
                /* Regarding PPU A12:
                   When using 8x8 sprites,
                    if the BG uses $0000, and the sprites use $1000, the IRQ counter should decrement on PPU cycle 260,
                    right after the visible part of the target scanline has ended.
                   When using 8x8 sprites,
                    if the BG uses $1000, and the sprites use $0000, the IRQ counter should decrement on PPU cycle 324
                    of the previous scanline (as in, right before the target scanline is about to be drawn).
                    However, the 2C02's pre-render scanline will decrement the counter twice every other vertical redraw,
                    so the IRQ will shake one scanline.
                    This is visible in Wario's Woods:
                     with some PPU-CPU reset alignments the bottom line of the green grass of the play area may flicker black
                     on the rightmost ~48 pixels, due to an extra count firing the IRQ one line earlier than expected.
                   When using 8x16 sprites PPU A12 must be explicitly tracked.
                    The exact time and number of times the counter is clocked will depend on the specific set of sprites
                    present on every scanline.
                    Specific combinations of sprites could cause the counter to decrement up to four times,
                    or the IRQ to be delayed or early by some multiple of 8 pixels.
                    If there are fewer than 8 sprites on a scanline, the PPU fetches tile $FF ($1FF0-$1FFF)
                    for each leftover sprite and discards its value.
                    Thus if a game uses 8x16 sprites with its background and sprites from PPU $0000,
                    then the MMC3 ends up counting each scanline that doesn't use all eight sprites. */
                // A12が立ち上がるのはパターンテーブルアクセスのアドレスの12ビット目(A12:マスク0x1000)が0から1になるとき
                // アドレスは各スプライトの設定を使用する
                // 上記説明文にスキャンライン毎に詳細を追わないとダメと書いてある
                // FCEUXは、基本は全スキャンラインで１回呼んでいるっぽい
                // https://github.com/TASEmulators/fceux/blob/f980ec2bc7dc962f6cd76b9ae3131f2eb902c9e7/src/ppu.cpp#L1378
                // https://www.nesdev.org/wiki/MMC3#IRQ_Specifics
                // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                stateObserver.notifyRisingA12PPU()
            }
        }
}

interface StateA12PPUObserver {
    fun notifyRisingA12PPU()
}

object NothingStateA12PPUObserver : StateA12PPUObserver {
    override fun notifyRisingA12PPU() = Unit
}

interface StateM2CycleObserver {
    fun notifyM2OneCycle()
}

object NothingStateM2CycleObserver : StateM2CycleObserver {
    override fun notifyM2OneCycle() = Unit
}

interface StateObserver : StateA12PPUObserver, StateM2CycleObserver

object NothingStateObserver : StateObserver,
    StateA12PPUObserver by NothingStateA12PPUObserver,
    StateM2CycleObserver by NothingStateM2CycleObserver

abstract class StateObserverAdapter : StateObserver by NothingStateObserver
