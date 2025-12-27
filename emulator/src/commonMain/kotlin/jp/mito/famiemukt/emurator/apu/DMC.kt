package jp.mito.famiemukt.emurator.apu

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.Interrupter
import jp.mito.famiemukt.emurator.dma.DMA

/*
DMC ($4010–$4013)
See APU DMC（https://www.nesdev.org/wiki/APU_DMC）
The delta modulation channel outputs a 7-bit PCM signal from a counter that can be driven by DPCM samples.

$4010	IL-- RRRR	IRQ enable (I), loop (L), frequency (R)
$4011	-DDD DDDD	Load counter (D)
$4012	AAAA AAAA	Sample address (A)
$4013	LLLL LLLL	Sample length (L)

DPCM samples are stored as a stream of 1-bit deltas that control the 7-bit PCM counter that the channel outputs.
A bit of 1 will increment the counter, 0 will decrement, and it will clamp rather than overflow if the 7-bit range is exceeded.
DPCM samples may loop if the loop flag in $4010 is set,
and the DMC may be used to generate an IRQ when the end of the sample is reached if its IRQ flag is set.
The playback rate is controlled by register $4010 with a 4-bit frequency index value (see APU DMC for frequency lookup tables).
DPCM samples must begin in the memory range $C000–$FFFF at an address set by register $4012 (address = %11AAAAAA AA000000).
The length of the sample in bytes is set by register $4013 (length = %LLLL LLLL0001).

Other Uses
The $4011 register can be used to play PCM samples directly by setting the counter value at a high frequency.
Because this requires intensive use of the CPU, when used in games all other gameplay is usually halted to facilitate this.
Because of the APU's nonlinear mixing, a high value in the PCM counter reduces the volume of the triangle and noise channels.
This is sometimes used to apply limited volume control to the triangle channel
(e.g. Super Mario Bros. adjusts the counter between levels to accomplish this).
The DMC's IRQ can be used as an IRQ-based timer when the mapper used does not have one available.
 */
class DMC(private val interrupter: Interrupter, private val dma: DMA) : AudioChannel {
    private lateinit var cpuBus: CPUBus
    private var isEnabledIRQ: Boolean = false
    private var isLoop: Boolean = false
    private var timer: Int = 0
    private var addressLoad: Int = 0
    private var lengthLoad: Int = 0

    // TODO: これはAPUと同じく直結と考えると良くない？
    //  isEnabledIRQと別だから合ってる？
    //  APUとはちょっと違うかも
    private var isDMCInterrupt: Boolean = false
    // TODO: DMC割り込みフラグ内で処理をするのでは無く、フラグを確認して通知するのが正しい？
    //  https://www.nesdev.org/wiki/IRQ
    //  上記のAcknowledgeを見るとそれも違うかも？
//        set(value) {
//            field = value
//            if (value) {
//                interrupter.requestOnIRQ()
//            } else {
//                interrupter.requestOffIRQ()
//            }
//        }

    fun setCPUBus(cpuBus: CPUBus) {
        this.cpuBus = cpuBus
    }

    fun writeILR(value: UByte) {
        // $4010	IL-- RRRR	IRQ enable (I), loop (L), frequency (R)
        isEnabledIRQ = (value.toInt() and 0b1000_0000) != 0
        isLoop = (value.toInt() and 0b0100_0000) != 0
        timer = TIMER_TABLE[value.toInt() and 0b0000_1111]
        // bit 7	I---.----	IRQ enabled flag. If clear, the interrupt flag is cleared.
        if (isEnabledIRQ.not()) {
            isDMCInterrupt = false
            interrupter.requestOffIRQ() // TODO: 合ってる？
        }
//println("writeILR(${value.toHex()}) isLoop=$isLoop")
    }

    fun writeD(value: UByte) {
        // $4011	-DDD DDDD	Load counter (D)
        outputVolume = value.toInt() and 0b0111_1111
    }

    fun writeA(value: UByte) {
        // $4012	AAAA AAAA	Sample address (A)
        // DPCM samples must begin in the memory range $C000–$FFFF at an address set by register $4012 (address = %11AAAAAA AA000000).
        addressLoad = 0xC000 or (value.toInt() shl 6)
    }

    fun writeL(value: UByte) {
        // $4013	LLLL LLLL	Sample length (L)
        // The length of the sample in bytes is set by register $4013 (length = %LLLL LLLL0001).
        lengthLoad = (value.toInt() shl 4) or 1
//println("writeL(${value.toHex()}) timerCounter=$timerCounter, remainCounter=$remainCounter, length=$length, lengthLoad=$lengthLoad")
    }

    private var timerCounter: Int = 0
    private var sampleBuffer: Int = Int.MIN_VALUE // Box化されてIntegerインスタンスが増えるので、nullで管理しない
    private var isSilent: Boolean = false

    // メモリーリーダー
    private var address: Int = 0
    private var length: Int = 0

    // 出力ユニット
    private var remainCounter: Int = 0
    private var shiftRegister: Int = 0

    override fun writeEnableStatus(value: UByte) {
        // $4015 write	---D NT21	Enable DMC (D), noise (N), triangle (T), and pulse channels (2/1)
        // If the DMC bit is clear, the DMC bytes remaining will be set to 0 and the DMC will silence when it empties.
        // If the DMC bit is set, the DMC sample will be restarted only if its bytes remaining is 0.
        // If there are bits remaining in the 1-byte sample buffer, these will finish playing before the next sample is fetched.
        // Writing to this register clears the DMC interrupt flag.
//print("writeEnableStatus(${value.toHex()}) isEnabledIRQ=$isEnabledIRQ, timerCounter=$timerCounter, remainCounter=$remainCounter, length=$length, lengthLoad=$lengthLoad => ")
        val enabled = (value.toInt() and 0b0001_0000) != 0
        if (enabled.not()) {
            length = 0
        } else if (length == 0) {
            address = addressLoad
            length = lengthLoad
        }
        isDMCInterrupt = false
        interrupter.requestOffIRQ()
        // 補充処理
        //  https://www.nesdev.org/wiki/APU_DMC#Memory_reader
        //  Any time the sample buffer is in an empty state and bytes remaining is not zero
        //  (including just after a write to $4015 that enables the channel,
        //   regardless of where that write occurs relative to the bit counter mentioned below),
        //  the following occur:
        fillingBufferIfNeeded(isReload = false)
//println("remainCounter=$remainCounter, length=$length, lengthLoad=$lengthLoad")
    }

    override fun readEnableStatus(): UByte {
        // $4015 read	IF-D NT21	DMC interrupt (I), frame interrupt (F), DMC active (D), length counter > 0 (N/T/2/1)
        // D will read as 1 if the DMC bytes remaining is more than 0.
        var status = 0
        if (length > 0) status = status or 0b0001_0000
        if (isDMCInterrupt) status = status or 0b1000_0000
//println("readEnableStatus() => ${status.toUByte().toHex()}, timerCounter=$timerCounter, remainCounter=$remainCounter, length=$length, lengthLoad=$lengthLoad")
        return status.toUByte()
    }

    override fun clockTimer() {
        if (timerCounter > 0) {
            timerCounter--
        } else {
            timerCounter = timer
            /* https://www.nesdev.org/wiki/APU_DMC#Output_unit
            Output unit
            The output unit continuously outputs a 7-bit value to the mixer.
            It contains an 8-bit right shift register, a bits-remaining counter,
            a 7-bit output level (the same one that can be loaded directly via $4011), and a silence flag.
            The bits-remaining counter is updated whenever the timer outputs a clock,
            regardless of whether a sample is currently playing. When this counter reaches zero,
            we say that the output cycle ends. The DPCM unit can only transition from silent to playing at the end of an output cycle.
            When an output cycle ends, a new cycle is started as follows:
                The bits-remaining counter is loaded with 8.
                If the sample buffer is empty, then the silence flag is set; otherwise,
                the silence flag is cleared and the sample buffer is emptied into the shift register.
            When the timer outputs a clock, the following actions occur in order:
              1.If the silence flag is clear, the output level changes based on bit 0 of the shift register.
                If the bit is 1, add 2; otherwise, subtract 2.
                But if adding or subtracting 2 would cause the output level to leave the 0-127 range,
                leave the output level unchanged. This means subtract 2 only if the current level is at least 2,
                or add 2 only if the current level is at most 125.
              2.The right shift register is clocked.
              3.As stated above, the bits-remaining counter is decremented. If it becomes zero, a new output cycle is started.
                Nothing can interrupt a cycle; every cycle runs to completion before a new cycle is started. */
            if (remainCounter == 0) {
                remainCounter = 8
                val sampleBuffer = sampleBuffer
                if (sampleBuffer == Int.MIN_VALUE) {
                    isSilent = true
                } else {
                    isSilent = false
                    shiftRegister = sampleBuffer
                    this.sampleBuffer = Int.MIN_VALUE
                    fillingBufferIfNeeded(isReload = true)
                }
            }
            if (isSilent.not()) {
                if ((shiftRegister and 0x01) == 0) {
                    if (outputVolume > 1) outputVolume -= 2
                } else {
                    if (outputVolume < 0x7E) outputVolume += 2
                }
            }
            shiftRegister = shiftRegister ushr 1
            remainCounter--
        }
        // TODO: DMC割り込みフラグ内で処理をするのでは無く、フラグを確認して通知するのが正しい？
        // https://www.nesdev.org/wiki/APU_DMC#Memory_reader
        // At any time, if the interrupt flag is set the CPU's IRQ line is continuously asserted until the interrupt flag is cleared.
        // The processor will continue on from where it was stalled.
        if (isDMCInterrupt) {
            interrupter.requestOnIRQ()
        }
    }

    override fun clockQuarterFrame() = Unit
    override fun clockHalfFrame() = Unit

    override var outputVolume: Int = 0
        private set

    private fun fillingBufferIfNeeded(isReload: Boolean) {
        if (sampleBuffer == Int.MIN_VALUE && length > 0) {
            dma.copyDMCSampleBuffer(address, isReload) { sampleBuffer = it.toInt() }
            if (++address > 0xFFFF) address = 0x8000
            if (--length == 0) {
                if (isLoop) {
                    address = addressLoad
                    length = lengthLoad
                } else if (isEnabledIRQ) {
                    isDMCInterrupt = true
                }
            }
        }
    }

    fun debugInfo(nest: Int): String = buildString {
        append(" ".repeat(n = nest)).append("isEnabledIRQ=").appendLine(isEnabledIRQ)
        append(" ".repeat(n = nest)).append("isLoop=").appendLine(isLoop)
        append(" ".repeat(n = nest)).append("timer=").appendLine(timer)
        append(" ".repeat(n = nest)).append("addressLoad=").appendLine(addressLoad)
        append(" ".repeat(n = nest)).append("lengthLoad=").appendLine(lengthLoad)
        append(" ".repeat(n = nest)).append("isDMCInterrupt=").appendLine(isDMCInterrupt)
        append(" ".repeat(n = nest)).append("timerCounter=").appendLine(timerCounter)
        append(" ".repeat(n = nest)).append("address=").appendLine(address)
        append(" ".repeat(n = nest)).append("length=").appendLine(this@DMC.length)
        append(" ".repeat(n = nest)).append("sampleBuffer=").appendLine(sampleBuffer)
        append(" ".repeat(n = nest)).append("remainCounter=").appendLine(remainCounter)
        append(" ".repeat(n = nest)).append("outputVolume=").appendLine(outputVolume)
    }

    companion object {
        /*  Rate   $0   $1   $2   $3   $4   $5   $6   $7   $8   $9   $A   $B   $C   $D   $E   $F
            ------------------------------------------------------------------------------
            NTSC  428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106,  84,  72,  54
            PAL   398, 354, 316, 298, 276, 236, 210, 198, 176, 148, 132, 118,  98,  78,  66,  50 */
        private val TIMER_TABLE: IntArray =
            intArrayOf(428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54)
    }
}
