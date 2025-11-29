package jp.mito.famiemukt.emurator

import jp.mito.famiemukt.emurator.apu.APU
import jp.mito.famiemukt.emurator.apu.AudioSampleNotifier
import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cpu.*
import jp.mito.famiemukt.emurator.dma.DMA
import jp.mito.famiemukt.emurator.pad.Pad
import jp.mito.famiemukt.emurator.ppu.PPU
import jp.mito.famiemukt.emurator.ppu.PPUBus
import jp.mito.famiemukt.emurator.ppu.PPURegisters
import jp.mito.famiemukt.emurator.ppu.VideoRAM
import jp.mito.famiemukt.emurator.util.VisibleForTesting

class NesSystem(cartridge: Cartridge, audioSampleNotifier: AudioSampleNotifier, audioSamplingRate: Int) {
    private val interrupter = object : Interrupter {
        override fun requestREST() = cpu.requestInterruptREST()
        override fun requestNMI(levelLow: Boolean) = cpu.requestInterruptNMI(levelLow)
        override fun requestOnIRQ() = cpu.requestInterruptOnIRQ()
        override fun requestOffIRQ() = cpu.requestInterruptOffIRQ()
        override val isRequestedIRQ: Boolean get() = cpu.isRequestedIRQ
    }

    @Suppress("PropertyName")
    @VisibleForTesting // TODO: 使わずに済むようにする
    val _videoRAM: VideoRAM get() = videoRAM

    private val videoRAM = VideoRAM()
    private val ppuBus = PPUBus(mapper = cartridge.mapper, videoRAM = videoRAM)
    private val ppuRegisters = PPURegisters()
    private val ppu = PPU(
        ppuRegisters = ppuRegisters,
        ppuBus = ppuBus,
        interrupter = interrupter,
        stateObserver = cartridge.mapper.stateObserver,
    )

    private val ram = RAM()
    private val dma = DMA(ppu = ppu, ppuRegisters = ppuRegisters)
    private val apu = APU(
        interrupter = interrupter,
        dma = dma,
        audioSampleNotifier = audioSampleNotifier,
        audioSamplingRate = audioSamplingRate,
    )

    private val pad = Pad()

    private val cpuBus = CPUBus(mapper = cartridge.mapper, dma = dma, ram = ram, apu = apu, ppu = ppu, pad = pad)
    private val cpuRegisters = CPURegisters()

    private val cpu: CPU by lazy {
        CPU(cpuRegisters = cpuRegisters, cpuBus = cpuBus, dma = dma, stateObserver = cartridge.mapper.stateObserver)
    }

    val pixelsRGB32: IntArray by ppu::pixelsRGB32

    var isPad1Up: Boolean by pad::isPad1Up
    var isPad1Down: Boolean by pad::isPad1Down
    var isPad1Left: Boolean by pad::isPad1Left
    var isPad1Right: Boolean by pad::isPad1Right
    var isPad1Select: Boolean by pad::isPad1Select
    var isPad1Start: Boolean by pad::isPad1Start
    var isPad1A: Boolean by pad::isPad1A
    var isPad1B: Boolean by pad::isPad1B

    init {
        cartridge.mapper.interrupter = interrupter
    }

    private var isResetRequested: Boolean = false

    fun powerOn() {
        cpu.setPowerOnState()
        apu.reset()
        ppu.reset()
    }

    fun reset() {
        isResetRequested = true
    }

    private fun executeResetIfNeeded(): Boolean {
        if (isResetRequested.not()) return false
        isResetRequested = false
        cpu.setPowerOnState()
        apu.reset()
        ppu.reset()
        return true
    }

    /**
     * マスタークロックを１つ進める
     * @return フレーム描画済み
     */
    fun executeMasterClockStep(): Boolean {
        val cpuResult = cpu.executeMasterClockStep()
        apu.executeMasterClockStep()
        val drawFrame = ppu.executeMasterClockStep()
        if (cpuResult != null) {
            // リセット要求を処理
            if (cpuResult.instruction != null && executeResetIfNeeded()) return false
        }
        return drawFrame
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun debugInfo(nest: Int): String = buildString {
        append(" ".repeat(n = nest)).appendLine("=== cpu ===")
        append(cpu.debugInfo(nest = nest + 1))
        append(" ".repeat(n = nest)).appendLine("=== apu ===")
        append(apu.debugInfo(nest = nest + 1))
        append(" ".repeat(n = nest)).appendLine("=== ppu ===")
        append(ppu.debugInfo(nest = nest + 1))
        append(" ".repeat(n = nest)).appendLine("=== ram (zero + stack) ===")
        val data = UByteArray(size = 0x200)
        ram.read(address = 0, data = data)
        data.asSequence()
            .windowed(size = 16, step = 16)
            .mapIndexed { index, line ->
                line.joinToString(
                    separator = " ",
                    prefix = (16 * index).toString(radix = 16).padStart(length = 4, padChar = '0') + " : ",
                ) { it.toString(radix = 16).padStart(length = 2, padChar = '0') }
            }
            .forEach { appendLine(it) }
    }
}
