package jp.mito.famiemukt.emurator.apu

import jp.mito.famiemukt.emurator.NTSC_CPU_CLOCKS_HZ
import jp.mito.famiemukt.emurator.NTSC_CPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.Interrupter
import jp.mito.famiemukt.emurator.dma.DMA

/*
https://www.nesdev.org/wiki/APU
Registers
See APU Registers for a complete APU register diagram.
Registers	Channel	Units
$4000–$4003	Pulse 1	Timer, length counter, envelope, sweep
$4004–$4007	Pulse 2	Timer, length counter, envelope, sweep
$4008–$400B	Triangle	Timer, length counter, linear counter
$400C–$400F	Noise	Timer, length counter, envelope, linear feedback shift register
$4010–$4013	DMC	Timer, memory reader, sample buffer, output unit
$4015	All	Channel enable and length counter status
$4017	All	Frame counter
 */
class APU(
    private val interrupter: Interrupter,
    dma: DMA,
    private val audioSampleNotifier: AudioSampleNotifier,
    private val audioSamplingRate: Int,
) {
    private val pulse1: Pulse = Pulse(isChannel1 = true)
    private val pulse2: Pulse = Pulse(isChannel1 = false)
    private val triangle: Triangle = Triangle()
    private val noise: Noise = Noise()
    private val dmc: DMC = DMC(interrupter = interrupter, dma = dma)
    private val audioMixer: AudioMixer = AudioMixer(
        pulse1 = pulse1,
        pulse2 = pulse2,
        triangle = triangle,
        noise = noise,
        dmc = dmc,
    )
    private var sequencerMode: SequencerMode = SequencerMode.Step4
        set(value) {
            if (field !== value) frameSequencers = getFrameSequencers(value)
            field = value
        }
    private var isDisableInterrupt: Boolean = false
    private var isEvenCPUCycle: Boolean = true
    private var frameSequencers: List<Pair<Int, List<FrameCounter>>> = getFrameSequencers(sequencerMode)
    private var frameSequencerCounter: Int = 0
    private var frameSequencerIndex: Int = 0
    private var resetFrameSequencerCount: Int = -1

    // TODO: このフラグはIRQに直結している？
    //  直結なら取得も直結しないといけない？
    // https://www.nesdev.org/wiki/APU_Frame_Counter
    // The frame interrupt flag is connected to the CPU's IRQ line.
    // It is set at a particular point in the 4-step sequence (see below) provided the interrupt inhibit flag in $4017 is clear,
    // and can be cleared either by reading $4015 (which also returns its old status) or by setting the interrupt inhibit flag.
    private var isFrameInterrupt: Boolean
        get() = interrupter.isRequestedIRQ
        set(value) {
            if (value) {
                interrupter.requestOnIRQ()
            } else {
                interrupter.requestOffIRQ()
            }
        }

    fun setCPUBus(cpuBus: CPUBus) = dmc.setCPUBus(cpuBus = cpuBus)

    // https://www.nesdev.org/wiki/CPU_power_up_state#APU
    fun reset() {
        // TODO: リセット状態の反映
        // Initial APU Register Values
        // Register                          | At Power                                  | After Reset
        // Pulses ($4000-$4007)              | 0                                         | unchanged?
        // Triangle ($4008-$400B)            | 0                                         | unchanged?
        // TODO: Triangle#triangleStepIndexの値を0にする必要あり？
        // Triangle phase                    | ?                                         | 0 (output = 15)
        // Noise ($400C-$400F)               | 0                                         | unchanged?
        // Noise 15-bit LFSR                 | $0000 (all 0s, first clock shifts in a 1) | unchanged?
        // DMC flags and rate ($4010)        | 0                                         | unchanged
        // DMC direct load ($4011)           | 0                                         | [$4011] &= 1
        writeDMCD(value = (dmc.outputVolume and 0x01).toUByte())
        // DMC sample address ($4012)        | 0                                         | unchanged
        // DMC sample length ($4013)         | 0                                         | unchanged
        // DMC LFSR	0? (revision-dependent?) | ?                                         | (revision-dependent?)
        // Status ($4015)                    | 0 (all channels disabled)                 | 0 (all channels disabled)
        writeEnableStatus(value = 0u)
        // Frame Counter ($4017)             | 0 (frame IRQ enabled)                     | unchanged
        // Frame Counter LFSR                | $7FFF (all 1s)                            | revision-dependent
    }

    /* https://www.nesdev.org/wiki/APU_Pulse
       Pulse ($4000–$4007)
        See APU Pulse
        The pulse channels produce a variable-width pulse signal, controlled by volume, envelope, length, and sweep units.
        $4000 / $4004	DDLC VVVV	Duty (D), envelope loop / length counter halt (L), constant volume (C), volume/envelope (V)
        $4001 / $4005	EPPP NSSS	Sweep unit: enabled (E), period (P), negate (N), shift (S)
        $4002 / $4006	TTTT TTTT	Timer low (T)
        $4003 / $4007	LLLL LTTT	Length counter load (L), timer high (T) */
    fun writePulse1DLCV(value: UByte) = pulse1.writeDLCV(value = value)
    fun writePulse1EPNS(value: UByte) = pulse1.writeEPNS(value = value)
    fun writePulse1TLow(value: UByte) = pulse1.writeTLow(value = value)
    fun writePulse1LT(value: UByte) = pulse1.writeLT(value = value)
    fun writePulse2DLCV(value: UByte) = pulse2.writeDLCV(value = value)
    fun writePulse2EPNS(value: UByte) = pulse2.writeEPNS(value = value)
    fun writePulse2TLow(value: UByte) = pulse2.writeTLow(value = value)
    fun writePulse2LT(value: UByte) = pulse2.writeLT(value = value)

    /* The status register is used to enable and disable individual channels,
       control the DMC, and can read the status of length counters and APU interrupts.
        $4015 write	---D NT21	Enable DMC (D), noise (N), triangle (T), and pulse channels (2/1)
        Writing a zero to any of the channel enable bits will silence that channel and immediately set its length counter to 0.
        If the DMC bit is clear, the DMC bytes remaining will be set to 0 and the DMC will silence when it empties.
        If the DMC bit is set, the DMC sample will be restarted only if its bytes remaining is 0.
        If there are bits remaining in the 1-byte sample buffer, these will finish playing before the next sample is fetched.
        Writing to this register clears the DMC interrupt flag.
        Power-up and reset have the effect of writing $00, silencing all channels. */
    fun writeEnableStatus(value: UByte) {
        pulse1.writeEnableStatus(value)
        pulse2.writeEnableStatus(value)
        triangle.writeEnableStatus(value)
        noise.writeEnableStatus(value)
        dmc.writeEnableStatus(value)
    }

    /* Triangle ($4008–$400B)
        See APU Triangle（https://www.nesdev.org/wiki/APU_Triangle）
        The triangle channel produces a quantized triangle wave.
        It has no volume control, but it has a length counter as well as a higher resolution linear counter control
        (called "linear" since it uses the 7-bit value written to $4008 directly instead of a lookup table like the length counter).
        $4008	CRRR RRRR	Length counter halt / linear counter control (C), linear counter load (R)
        $4009	---- ----	Unused
        $400A	TTTT TTTT	Timer low (T)
        $400B	LLLL LTTT	Length counter load (L), timer high (T), set linear counter reload flag */
    fun writeTriangleCR(value: UByte) = triangle.writeCR(value = value)
    fun writeTriangleTLow(value: UByte) = triangle.writeTLow(value = value)
    fun writeTriangleLT(value: UByte) = triangle.writeLT(value = value)

    /* Noise ($400C–$400F)
        See APU Noise（https://www.nesdev.org/wiki/APU_Noise）
        The noise channel produces noise with a pseudo-random bit generator.
        It has a volume, envelope, and length counter like the pulse channels.
        $400C	--LC VVVV	Envelope loop / length counter halt (L), constant volume (C), volume/envelope (V)
        $400D	---- ----	Unused
        $400E	L--- PPPP	Loop noise (L), noise period (P)
        $400F	LLLL L---	Length counter load (L) */
    fun writeNoiseLCV(value: UByte) = noise.writeLCV(value = value)
    fun writeNoiseMP(value: UByte) = noise.writeMP(value = value)
    fun writeNoiseLR(value: UByte) = noise.writeLR(value = value)

    /* DMC ($4010–$4013)
        See APU DMC（https://www.nesdev.org/wiki/APU_DMC）
        The delta modulation channel outputs a 7-bit PCM signal from a counter that can be driven by DPCM samples.
        $4010	IL-- RRRR	IRQ enable (I), loop (L), frequency (R)
        $4011	-DDD DDDD	Load counter (D)
        $4012	AAAA AAAA	Sample address (A)
        $4013	LLLL LLLL	Sample length (L) */
    fun writeDMCILR(value: UByte) = dmc.writeILR(value = value)
    fun writeDMCD(value: UByte) = dmc.writeD(value = value)
    fun writeDMCA(value: UByte) = dmc.writeA(value = value)
    fun writeDMCL(value: UByte) = dmc.writeL(value = value)

    /* The status register is used to enable and disable individual channels,
       control the DMC, and can read the status of length counters and APU interrupts.
        $4015 read	IF-D NT21	DMC interrupt (I), frame interrupt (F), DMC active (D), length counter > 0 (N/T/2/1)
        N/T/2/1 will read as 1 if the corresponding length counter is greater than 0. For the triangle channel,
        the status of the linear counter is irrelevant.
        D will read as 1 if the DMC bytes remaining is more than 0.
        Reading this register clears the frame interrupt flag (but not the DMC interrupt flag).
        If an interrupt flag was set at the same moment of the read, it will read back as 1 but it will not be cleared.
        This register is internal to the CPU and so the external CPU data bus is disconnected when reading it.
        Therefore the returned value cannot be seen by external devices and the value does not affect open bus.
        Bit 5 is open bus. Because the external bus is disconnected when reading $4015,
        the open bus value comes from the last cycle that did not read $4015. */
    fun readEnableStatus(): UByte {
        var value = pulse1.readEnableStatus() or pulse2.readEnableStatus() or
                triangle.readEnableStatus() or noise.readEnableStatus() or dmc.readEnableStatus()
        // TODO: フレーム割り込みフラグクリア条件確認
        //  Reading this register clears the frame interrupt flag (but not the DMC interrupt flag).
        //  If an interrupt flag was set at the same moment of the read, it will read back as 1 but it will not be cleared.
        //  https://www.nesdev.org/wiki/APU#Status_($4015)
        //  https://www.nesdev.org/wiki/APU_Frame_Counter
        if (isFrameInterrupt) {
            value = value or 0b0100_0000.toUByte()
            isFrameInterrupt = false
        }
        return value
    }

    /* https://www.nesdev.org/wiki/APU_Frame_Counter
        Address	Bitfield	Description
        $4017	MI--.----	Set mode and interrupt (write)
        Bit 7	M--- ----	Sequencer mode: 0 selects 4-step sequence, 1 selects 5-step sequence
        Bit 6	-I-- ----	Interrupt inhibit flag. If set, the frame interrupt flag is cleared, otherwise it is unaffected.
        Side effects	After 3 or 4 CPU clock cycles*, the timer is reset.
                        If the mode flag is set, then both "quarter frame" and "half frame" signals are also generated. */
    fun writeFrameCounter(value: UByte) {
        sequencerMode = if (value.toInt() and 0b1000_0000 == 0) SequencerMode.Step4 else SequencerMode.Step5
        isDisableInterrupt = (value.toInt() and 0b0100_0000 != 0)
        // Interrupt inhibit flag. If set, the frame interrupt flag is cleared, otherwise it is unaffected.
        if (isDisableInterrupt) {
            isFrameInterrupt = false
        }
        // After 3 or 4 CPU clock cycles*, the timer is reset.
        resetFrameSequencerCount = frameSequencerCounter + if (isEvenCPUCycle) 4 else 3
        // If the mode flag is set, then both "quarter frame" and "half frame" signals are also generated.
        if (sequencerMode == SequencerMode.Step5) {
            executeFrameCounter(FrameCounter.Quarter)
            executeFrameCounter(FrameCounter.Half)
        }
    }

    private var cpuCycleCountForSampleNotify: Int = 0
    private var cpuCycleRemainForSampleNotify: Int = 0

    /** マスタークロックカウンター */
    private var masterClockCount: Int = 0

    /** マスタークロックを１つ進める */
    fun executeMasterClockStep() {
        if (++masterClockCount >= NTSC_CPU_CYCLES_PER_MASTER_CLOCKS) {
            executeCpuCycleStep()
            masterClockCount = 0
        }
    }

    fun executeCpuCycleStep() {
        // APU cycle毎の処理
        if (isEvenCPUCycle) {
            pulse1.clockTimer()
            pulse2.clockTimer()
            noise.clockTimer()
        }
        // CPU cycle毎の処理
        triangle.clockTimer()
        dmc.clockTimer()
        // フレームシーケンサーのカウント
        val frameSequence = frameSequencers.getOrElse(frameSequencerIndex) { frameSequencers[0] }
        if (frameSequence.first == ++frameSequencerCounter) {
            frameSequence.second.forEach(::executeFrameCounter)
            if (frameSequencerIndex < frameSequencers.lastIndex) {
                frameSequencerIndex++
            } else {
                frameSequencerIndex = 0
                frameSequencerCounter = 0
            }
        }
        // フレームカウンターのリセット
        if (resetFrameSequencerCount == frameSequencerCounter) {
            frameSequencerIndex = 0
            frameSequencerCounter = 0
            resetFrameSequencerCount = -1
        }
        // 出力値の決定 (1 APU クロックごと)
        if (isEvenCPUCycle) {
            audioMixer.syncChannelVolume()
        }
        // サンプリング値通知
        val checkCycleCount = audioSamplingRate * cpuCycleCountForSampleNotify + cpuCycleRemainForSampleNotify
        val checkCycleRemain = checkCycleCount - NTSC_CPU_CLOCKS_HZ
        if (checkCycleRemain >= 0) {
            audioSampleNotifier.notifySample(audioMixer.outputVolume)
            cpuCycleRemainForSampleNotify = checkCycleRemain
            cpuCycleCountForSampleNotify = 0
        }
        cpuCycleCountForSampleNotify++
        // フラグ変更
        isEvenCPUCycle = isEvenCPUCycle.not()
    }

    private fun executeFrameCounter(frameCounter: FrameCounter) {
        when (frameCounter) {
            FrameCounter.Quarter -> {
                pulse1.clockQuarterFrame()
                pulse2.clockQuarterFrame()
                triangle.clockQuarterFrame()
                noise.clockQuarterFrame()
                dmc.clockQuarterFrame()
            }

            FrameCounter.Half -> {
                pulse1.clockHalfFrame()
                pulse2.clockHalfFrame()
                triangle.clockHalfFrame()
                noise.clockHalfFrame()
                dmc.clockHalfFrame()
            }

            FrameCounter.Interrupt -> {
                if (isDisableInterrupt.not()) {
                    isFrameInterrupt = true
                }
            }
        }
    }

    fun debugInfo(nest: Int): String = buildString {
        append(" ".repeat(n = nest)).append("isDisableInterrupt=").appendLine(isDisableInterrupt)
        append(" ".repeat(n = nest)).append("isEvenCPUCycle=").appendLine(isEvenCPUCycle)
        append(" ".repeat(n = nest)).append("frameSequencerCounter=").appendLine(frameSequencerCounter)
        append(" ".repeat(n = nest)).append("isFrameInterrupt=").appendLine(isFrameInterrupt)
        append(" ".repeat(n = nest)).append("cpuCycleCountForSampleNotify=").appendLine(cpuCycleCountForSampleNotify)
        append(" ".repeat(n = nest)).append("cpuCycleRemainForSampleNotify=").appendLine(cpuCycleRemainForSampleNotify)
        append(" ".repeat(n = nest)).appendLine("=== pulse1 ===")
        append(pulse1.debugInfo(nest = nest + 1))
        append(" ".repeat(n = nest)).appendLine("=== pulse2 ===")
        append(pulse2.debugInfo(nest = nest + 1))
        append(" ".repeat(n = nest)).appendLine("=== dmc ===")
        append(dmc.debugInfo(nest = nest + 1))
    }
}
