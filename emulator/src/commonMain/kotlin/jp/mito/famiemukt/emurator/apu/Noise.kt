package jp.mito.famiemukt.emurator.apu

/*
https://www.nesdev.org/wiki/APU_Noise
$400C	--lc.vvvv	Length counter halt, constant volume/envelope flag, and volume/envelope divider period (write)
$400E	M---.PPPP	Mode and period (write)
        bit  7		M--- ----	Mode flag
        bits 3-0	---- PPPP	The timer period is set to entry P of the following:
        Rate  $0 $1  $2  $3  $4  $5   $6   $7   $8   $9   $A   $B   $C    $D    $E    $F
              --------------------------------------------------------------------------
        NTSC   4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
        PAL    4, 8, 14, 30, 60, 88, 118, 148, 188, 236, 354, 472, 708,  944, 1890, 3778
$400F	llll.l---	Length counter load and envelope restart (write)
 */
// TODO: 各種合っているか確認
class Noise : AudioChannel {
    private var timer: Int = 0
    private var lengthCounter: Int = 0
    private var isEnvelopeLoop: Boolean = false
    private var isConstantVolume: Boolean = false
    private var volumeOrEnvelope: Int = 0
    private var isShiftMode: Boolean = false
    private var isChannelEnabled: Boolean = false

    fun writeLCV(value: UByte) {
        // $400C	--lc.vvvv	Length counter halt, constant volume/envelope flag, and volume/envelope divider period (write)
        isEnvelopeLoop = (value.toInt() and 0b0010_0000) != 0
        isConstantVolume = (value.toInt() and 0b0001_0000) != 0
        volumeOrEnvelope = value.toInt() and 0b0000_1111
    }

    fun writeMP(value: UByte) {
        // $400E	M---.PPPP	Mode and period (write
        isShiftMode = (value.toInt() and 0b1000_0000) != 0
        timer = TIMER_PERIOD_TABLE[value.toInt() and 0b0000_1111]
    }

    fun writeLR(value: UByte) {
        // $400F	llll.l---	Length counter load and envelope restart (write)
        // TODO: isChannelEnabledの要不要を確認する
        // https://www.nesdev.org/wiki/APU_Length_Counter
        if (isChannelEnabled) {
            lengthCounter = LengthCounter[value.toInt() ushr 3]
        }
        isDecayReset = true
    }

    override fun writeEnableStatus(value: UByte) {
        // $4015 write	---D NT21	Enable DMC (D), noise (N), triangle (T), and pulse channels (2/1)
        val enabled = (value.toInt() and 0b0000_1000) != 0
        isChannelEnabled = enabled
        if (enabled.not()) {
            lengthCounter = 0
        }
    }

    // $4015 read	IF-D NT21	DMC interrupt (I), frame interrupt (F), DMC active (D), length counter > 0 (N/T/2/1)
    override fun readEnableStatus(): UByte = if (lengthCounter == 0) 0U else 0b0000_1000U

    private var timerCounter: Int = 0
    private var noiseShift: Int = 1 // TODO: リセット時に1をセット
    private var isDecayReset: Boolean = false
    private var decayCounter: Int = 0
    private var decayHiddenVolume = 0

    override fun clockTimer() {
        if (timerCounter > 0) {
            timerCounter--
        } else {
            timerCounter = timer
            // NES on FPGA:
            // 15ビットシフトレジスタにはリセット時に1をセットしておく必要があります。
            // タイマによってシフトレジスタが励起されるたびに1ビット右シフトし、
            // ビット14には、ショートモード時にはビット0とビット6のEORを、
            // ロングモード時にはビット0とビット1のEORを入れます。
            val noiseShift = noiseShift
            val topBit = if (isShiftMode) {
                val shift6 = (noiseShift shr 6) and 0x1
                val shift0 = noiseShift and 0x1
                shift6 xor shift0
            } else {
                val shift1 = (noiseShift shr 1) and 0x1
                val shift0 = noiseShift and 0x1
                shift1 xor shift0
            }
            // topBit を 15 bit目(0-indexed) にいれる
            this.noiseShift = ((noiseShift shr 1) and 0b0011_1111_1111_1111) or (topBit shl 14)
        }
        // ClockTimer は 1 APU クロックごとに呼び出されるので出力値の決定もここでやる
        // シフトレジスタのビット0が1なら、チャンネルの出力は0となります。(NES on FPGA)
        // 長さカウンタが0でない ⇔ channel active
        outputVolume = when {
            (noiseShift and 0x1) != 0 || lengthCounter == 0 -> 0
            isConstantVolume.not() -> decayHiddenVolume
            else -> volumeOrEnvelope
        }
    }

    override fun clockQuarterFrame() {
        // 矩形波のコピペだけど、共通化するのも違う気がするのでコピペのまま……
        // フレームシーケンサによって励起されるとき、 最後のクロック以降チャンネルの4番目のレジスタへの書き込みがあった場合、
        // カウンタへ$Fをセットし、分周器へエンベロープ周期をセットします
        if (isDecayReset) {
            isDecayReset = false
            decayHiddenVolume = 0x0f
            // decay_counter == エンベロープ周期(分周器でつかうもの)
            // この if にはいるときの1回 + else の時が dacay_V 回なので、周期は decay_v+1になるよね(NES on FPGA)
            decayCounter = volumeOrEnvelope
        } else {
            // そうでなければ、分周器を励起します。
            // カウンタ = decay_hidden_vol であってる？(たぶんあってると思う)
            // 特定条件でカウンタの値が volume になるからこの名前なのかも。
            if (decayCounter > 0) {
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
    }

    override fun clockHalfFrame() {
        // 矩形波とちがってスイープユニットはない
        // 長さカウンタのクロック生成(NES on FPGA の l)
        if (isEnvelopeLoop.not() && lengthCounter > 0) {
            lengthCounter--
        }
    }

    override var outputVolume: Int = 0
        private set

    companion object {
        // NTSC
        private val TIMER_PERIOD_TABLE: IntArray = intArrayOf(
            4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068,
        )
    }
}