package jp.mito.famiemukt.emurator.apu

class Pulse(private val isChannel1: Boolean) : AudioChannel {
    private var timer: Int = 0
    private var lengthCounter: Int = 0
    private var dutyIndex: Int = 0
    private var isEnvelopeLoop: Boolean = false
    private var isConstantVolume: Boolean = false
    private var volumeOrEnvelope: Int = 0
    private var isSweepUnitEnabled: Boolean = false
    private var period: Int = 0
    private var isNegate: Boolean = false
    private var shift: Int = 0
    private var isChannelEnabled: Boolean = false

    /* https://www.nesdev.org/wiki/APU_Pulse
     Pulse ($4000–$4007)
     See APU Pulse
     The pulse channels produce a variable-width pulse signal, controlled by volume, envelope, length, and sweep units. */
    fun writeDLCV(value: UByte) {
        // $4000 / $4004	DDLC VVVV	Duty (D), envelope loop / length counter halt (L), constant volume (C), volume/envelope (V)
        dutyIndex = (value.toInt() and 0b1100_0000) ushr 6
        isEnvelopeLoop = (value.toInt() and 0b0010_0000) != 0
        isConstantVolume = (value.toInt() and 0b0001_0000) != 0
        volumeOrEnvelope = value.toInt() and 0b0000_1111
    }

    fun writeEPNS(value: UByte) {
        // $4001 / $4005	EPPP NSSS	Sweep unit: enabled (E), period (P), negate (N), shift (S)
        // https://www.nesdev.org/wiki/APU_Sweep#Registers
        // $4001	EPPP.NSSS	Pulse channel 1 sweep setup (write)
        // $4005	EPPP.NSSS	Pulse channel 2 sweep setup (write)
        // bit 7	E--- ----	Enabled flag
        // bits 6-4	-PPP ----	The divider's period is P + 1 half-frames
        // bit 3	---- N---	Negate flag
        // 0: add to period, sweeping toward lower frequencies
        // 1: subtract from period, sweeping toward higher frequencies
        // bits 2-0	---- -SSS	Shift count (number of bits)
        // Side effects	Sets the reload flag
        isSweepUnitEnabled = (value.toInt() and 0b1000_0000) != 0
        period = (value.toInt() and 0b0111_0000) ushr 4
        isNegate = (value.toInt() and 0b0000_1000) != 0
        shift = value.toInt() and 0b0000_0111
        isSweepReload = true
    }

    fun writeTLow(value: UByte) {
        // $4002 / $4006	TTTT TTTT	Timer low (T)
        timer = (timer and 0x0700) or value.toInt()
    }

    fun writeLT(value: UByte) {
        // $4003 / $4007	LLLL LTTT	Length counter load (L), timer high (T)
        timer = (timer and 0x00FF) or ((value.toInt() and 0x07) shl 8)
        // TODO: isChannelEnabledの要不要を確認する
        // https://www.nesdev.org/wiki/APU_Length_Counter
        if (isChannelEnabled) {
            lengthCounter = LengthCounter[value.toInt() ushr 3]
        }
        timerCounter = timer
        dutyCounter = 0
        isDecayReset = true
    }

    override fun writeEnableStatus(value: UByte) {
        // $4015 write	---D NT21	Enable DMC (D), noise (N), triangle (T), and pulse channels (2/1)
        val mask = if (isChannel1) 0b0000_0001 else 0b0000_0010
        val enabled = (value.toInt() and mask) != 0
        isChannelEnabled = enabled
        if (enabled.not()) {
            lengthCounter = 0
        }
    }

    // $4015 read	IF-D NT21	DMC interrupt (I), frame interrupt (F), DMC active (D), length counter > 0 (N/T/2/1)
    override fun readEnableStatus(): UByte =
        if (lengthCounter == 0) 0U else if (isChannel1) 0b0000_0001U else 0b0000_0010U

    private var timerCounter: Int = 0
    private var dutyCounter: Int = 0
    private var isDecayReset: Boolean = false
    private var decayCounter: Int = 0
    private var decayHiddenVolume = 0
    private var isSweepReload: Boolean = false
    private var sweepCounter: Int = 0

    override fun clockTimer() {
        if (timerCounter > 0) {
            timerCounter--
        } else {
            timerCounter = timer
            dutyCounter = (dutyCounter + 1) and 0x07
        }
    }

    override fun clockQuarterFrame() {
        if (isDecayReset) {
            isDecayReset = false
            decayHiddenVolume = 0x0f
            // decay_counter == エンベロープ周期(分周器でつかうもの)
            // この if にはいるときの1回 + else の時が dacay_V 回なので、周期は decay_v+1になるよね(NES on FPGA)
            decayCounter = volumeOrEnvelope
        } else if (decayCounter > 0) {
            // カウンタ = decay_hidden_vol であってる？(たぶんあってると思う)
            // 特定条件でカウンタの値が volume になるからこの名前なのかも。
            decayCounter--
        } else {
            decayCounter = volumeOrEnvelope
            // 分周器が励起されるとき、カウンタがゼロでなければデクリメントします
            if (decayHiddenVolume > 0) {
                decayHiddenVolume--
            } else if (isEnvelopeLoop) {
                // カウンタが0で、ループフラグがセットされているならカウンタへ$Fをセットします。
                decayHiddenVolume = 0x0f
            }
        }
    }

    override fun clockHalfFrame() {
// Wikiを元にソースをLayerWalkerの資料ベースから修正した
// https://www.nesdev.org/wiki/APU_Sweep#Updating_the_period
// When the frame counter sends a half-frame clock (at 120 or 96 Hz), two things happen:
//
// 1. If the divider's counter is zero, the sweep is enabled, the shift count is nonzero,
//     1. and the sweep unit is not muting the channel: The pulse's period is set to the target period.
//     2. and the sweep unit is muting the channel: the pulse's period remains unchanged,
//        but the sweep unit's divider continues to count down and reload the divider's period as normal.
// 2. If the divider's counter is zero or the reload flag is true: The divider counter is set to P and the reload flag is cleared.
//    Otherwise, the divider counter is decremented.
//
// If the sweep unit is disabled including because the shift count is zero, the pulse channel's period is never updated,
// but muting logic still applies.
        // 1. If the divider's counter is zero, the sweep is enabled, the shift count is nonzero,
        if (sweepCounter == 0 && isSweepUnitEnabled && shift > 0) {
            if (isSweepForcingSilence.not()) {
                // 1. and the sweep unit is not muting the channel: The pulse's period is set to the target period.
                // https://www.nesdev.org/wiki/APU_Sweep#Calculating_the_target_period
                // 1. A barrel shifter shifts the pulse channel's 11-bit raw timer period right by the shift count,
                //    producing the change amount.
                // 2. If the negate flag is true, the change amount is made negative.
                // 3. The target period is the sum of the current period and the change amount,
                //    clamped to zero if this sum is negative.
                if (isNegate) {
                    // Pulse 1 adds the ones' complement (−c − 1). Making 20 negative produces a change amount of −21.
                    // Pulse 2 adds the two's complement (−c). Making 20 negative produces a change amount of −20.
                    timer -= (timer ushr shift)
                    if (isChannel1) timer--
                    if (timer < 0) timer = 0
                } else {
                    timer += (timer ushr shift)
                }
            } else {
                // 2. and the sweep unit is muting the channel: the pulse's period remains unchanged,
                //    but the sweep unit's divider continues to count down and reload the divider's period as normal.
                // 上記内容は下記のリロードやカウントダウンをするということで、ここでは何もしなくて良さそう
            }
        }
        // 2. If the divider's counter is zero or the reload flag is true: The divider counter is set to P and the reload flag is cleared.
        //    Otherwise, the divider counter is decremented.
        if (isSweepReload || sweepCounter == 0) {
            isSweepReload = false
            sweepCounter = period
        } else {
            sweepCounter--
        }
        // 長さカウンタのクロック生成(NES on FPGA の l)
        if (isEnvelopeLoop.not() && lengthCounter > 0) {
            lengthCounter--
        }
    }

    private val isSweepForcingSilence: Boolean
        // https://www.nesdev.org/wiki/APU_Sweep#Muting
        // 1.If the current period is less than 8, the sweep unit mutes the channel.
        // 2.If at any time the target period is greater than $7FF, the sweep unit mutes the channel.
        get() = (timer < 8) || (isNegate.not() && (timer + (timer ushr shift) > 0x7FF))

    override val outputVolume: Int
        get() =
            when {
                // TODO: isChannelEnabled.not()の必要有無の確認
                /*isChannelEnabled.not() || */DUTY_VALUES[dutyIndex][dutyCounter] || lengthCounter == 0 || isSweepForcingSilence -> 0
                isConstantVolume.not() -> decayHiddenVolume
                // decay_V は $4000 の下位4bit(0123bit目)できまる4bitのあたい
                // NES on FPGA  エンベロープジェネレータ の
                // チャンネルのボリューム出力として、 エンベロープ無効フラグがセットされているなら、
                // エンベロープ周期のnをそのまま出力します。 クリアされているならカウンタの値を出力します相当
                // 結局、エンベロープ無効なら $4000 の下位 4 bit がボリュームになって、
                // 有効ならカウンタの値 = decay_hidden_vol がボリュームになるとのこと
                else -> volumeOrEnvelope
            }

    fun debugInfo(nest: Int): String = buildString {
        append(" ".repeat(n = nest)).append("isChannel1=").appendLine(isChannel1)
        append(" ".repeat(n = nest)).append("timer=").appendLine(timer)
        append(" ".repeat(n = nest)).append("lengthCounter=").appendLine(lengthCounter)
        append(" ".repeat(n = nest)).append("dutyIndex=").appendLine(dutyIndex)
        append(" ".repeat(n = nest)).append("isEnvelopeLoop=").appendLine(isEnvelopeLoop)
        append(" ".repeat(n = nest)).append("isConstantVolume=").appendLine(isConstantVolume)
        append(" ".repeat(n = nest)).append("volumeOrEnvelope=").appendLine(volumeOrEnvelope)
        append(" ".repeat(n = nest)).append("isSweepUnitEnabled=").appendLine(isSweepUnitEnabled)
        append(" ".repeat(n = nest)).append("period=").appendLine(period)
        append(" ".repeat(n = nest)).append("isNegate=").appendLine(isNegate)
        append(" ".repeat(n = nest)).append("shift=").appendLine(shift)
        append(" ".repeat(n = nest)).append("isChannelEnabled=").appendLine(isChannelEnabled)
        append(" ".repeat(n = nest)).append("timerCounter=").appendLine(timerCounter)
        append(" ".repeat(n = nest)).append("dutyCounter=").appendLine(dutyCounter)
        append(" ".repeat(n = nest)).append("isDecayReset=").appendLine(isDecayReset)
        append(" ".repeat(n = nest)).append("decayCounter=").appendLine(decayCounter)
        append(" ".repeat(n = nest)).append("decayHiddenVolume=").appendLine(decayHiddenVolume)
        append(" ".repeat(n = nest)).append("isSweepReload=").appendLine(isSweepReload)
        append(" ".repeat(n = nest)).append("sweepCounter=").appendLine(sweepCounter)
    }

    companion object {
        private val DUTY_VALUES: Array<BooleanArray> = arrayOf(
            intArrayOf(0, 1, 0, 0, 0, 0, 0, 0).map { it != 0 }.toBooleanArray(),
            intArrayOf(0, 1, 1, 0, 0, 0, 0, 0).map { it != 0 }.toBooleanArray(),
            intArrayOf(0, 1, 1, 1, 1, 0, 0, 0).map { it != 0 }.toBooleanArray(),
            intArrayOf(1, 0, 0, 1, 1, 1, 1, 1).map { it != 0 }.toBooleanArray(),
        )
    }
}
