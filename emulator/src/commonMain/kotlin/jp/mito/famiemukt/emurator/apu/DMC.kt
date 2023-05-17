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
    }

    private var timerCounter: Int = 0
    private var address: Int = 0
    private var length: Int = 0
    private var sampleBuffer: Int = 0
    private var sampleBufferBitRemain: Int = 0

    override fun writeEnableStatus(value: UByte) {
        // $4015 write	---D NT21	Enable DMC (D), noise (N), triangle (T), and pulse channels (2/1)
        // If the DMC bit is clear, the DMC bytes remaining will be set to 0 and the DMC will silence when it empties.
        // If the DMC bit is set, the DMC sample will be restarted only if its bytes remaining is 0.
        // If there are bits remaining in the 1-byte sample buffer, these will finish playing before the next sample is fetched.
        // Writing to this register clears the DMC interrupt flag.
        val enabled = (value.toInt() and 0b0001_0000) != 0
        if (enabled.not()) {
            length = 0
        } else if (length == 0) { // TODO: 合っているか確認
            address = addressLoad
            length = lengthLoad
        } else {
            length = 0 // TODO: 合っているか確認
        }
        isDMCInterrupt = false
        interrupter.requestOffIRQ() // TODO: 合ってる？
    }

    override fun readEnableStatus(): UByte {
        // $4015 read	IF-D NT21	DMC interrupt (I), frame interrupt (F), DMC active (D), length counter > 0 (N/T/2/1)
        // D will read as 1 if the DMC bytes remaining is more than 0.
        var status = 0
        if (length > 0) status = status or 0b0001_0000
        if (isDMCInterrupt) status = status or 0b1000_0000
        return status.toUByte()
    }

    override fun clockTimer() {
        if (timerCounter > 0) {
            timerCounter--
        } else {
            timerCounter = timer
            if (sampleBufferBitRemain == 0) {
                if (length == 0) return
                sampleBuffer = cpuBus.readMemIO(address = address).toInt()
                sampleBufferBitRemain = UByte.SIZE_BITS
                // 厳密には追加 CPU Cycle は 4 以外の場合もある
                // https://www.nesdev.org/wiki/APU_DMC#Memory_reader
                // 4 cycles if it falls on a CPU read cycle.
                // 3 cycles if it falls on a single CPU write cycle (or the second write of a double CPU write).
                // 4 cycles if it falls on the first write of a double CPU write cycle.[4]
                // 2 cycles if it occurs during an OAM DMA, or on the $4014 write cycle that triggers the OAM DMA.
                // 1 cycle if it occurs on the second-last OAM DMA cycle.
                // 3 cycles if it occurs on the last OAM DMA cycle.
                // TODO: APUの追加CPU cyclesの加算方法の確認
                dma.addDMCCycles(cycles = 4)
                if (++address > 0xFFFF) address = 0x8000
                if (--length == 0) {
                    if (isLoop) {
                        address = addressLoad
                        length = lengthLoad
                    } else if (isEnabledIRQ) {
                        isDMCInterrupt = true
                    }
                }
            } else {
                if ((sampleBuffer and 0x01) == 0) {
                    if (outputVolume > 1) outputVolume -= 2
                } else {
                    if (outputVolume < 0x7E) outputVolume += 2
                }
                sampleBuffer = sampleBuffer ushr 1
                sampleBufferBitRemain--
            }
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
        append(" ".repeat(n = nest)).append("sampleBufferBitRemain=").appendLine(sampleBufferBitRemain)
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
