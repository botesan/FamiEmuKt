package jp.mito.famiemukt.emurator.apu

/*
    https://www.nesdev.org/wiki/APU_Triangle
    Triangle ($4008–$400B)
    See APU Triangle
    The triangle channel produces a quantized triangle wave. It has no volume control,
    but it has a length counter as well as a higher resolution linear counter control
    (called "linear" since it uses the 7-bit value written to $4008 directly instead of a lookup table like the length counter).
    $4008	CRRR RRRR	Length counter halt / linear counter control (C), linear counter load (R)
    $4009	---- ----	Unused
    $400A	TTTT TTTT	Timer low (T)
    $400B	LLLL LTTT	Length counter load (L), timer high (T), set linear counter reload flag
    The triangle wave has 32 steps that output a 4-bit value.
    C: This bit controls both the length counter and linear counter at the same time.
    When set this will stop the length counter in the same way as for the pulse/noise channels.
    When set it prevents the linear counter's internal reload flag from clearing,
    which effectively halts it if $400B is written after setting C.
    The linear counter silences the channel after a specified time with a resolution of 240Hz in NTSC (see frame counter below).
    Because both the length and linear counters are be enabled at the same time, whichever has a longer setting is redundant.
    See APU Triangle for more linear counter details.
    R: This reload value will be applied to the linear counter on the next frame counter tick, but only if its reload flag is set.
    A write to $400B is needed to raise the reload flag.
    After a frame counter tick applies the load value R, the reload flag will only be cleared if C is also clear,
    otherwise it will continually reload (i.e. halt).
    The pitch of the triangle channel is one octave below the pulse channels with an equivalent timer value
    (i.e. use the formula above but divide the resulting frequency by two).
    Silencing the triangle channel merely halts it. It will continue to output its last value rather than 0.
    There is no way to reset the triangle channel's phase.
*/
class Triangle : AudioChannel {
    private var timer: Int = 0
    private var lengthCounter: Int = 0
    private var isLengthCounter: Boolean = true
    private var linearCounterLoad: Int = 0
    private var isLinearCounterReload: Boolean = false
    private var isChannelEnabled: Boolean = false

    fun writeCR(value: UByte) {
        // $4008	CRRR RRRR	Length counter halt / linear counter control (C), linear counter load (R)
        isLengthCounter = (value.toInt() and 0b1000_0000) == 0
        linearCounterLoad = (value.toInt() and 0b0111_1111)
    }

    fun writeTLow(value: UByte) {
        // $400A	TTTT TTTT	Timer low (T)
        timer = (timer and 0x0700) or value.toInt()
    }

    fun writeLT(value: UByte) {
        // $400B	LLLL LTTT	Length counter load (L), timer high (T), set linear counter reload flag */
        timer = (timer and 0x00FF) or ((value.toInt() and 0x07) shl 8)
        // TODO: isChannelEnabledの要不要を確認する
        // https://www.nesdev.org/wiki/APU_Length_Counter
        if (isChannelEnabled) {
            lengthCounter = LengthCounter[value.toInt() ushr 3]
        }
        timerCounter = timer
        isLinearCounterReload = true
    }

    override fun writeEnableStatus(value: UByte) {
        // $4015 write	---D NT21	Enable DMC (D), noise (N), triangle (T), and pulse channels (2/1)
        val enabled = (value.toInt() and 0b0000_0100) != 0
        isChannelEnabled = enabled
        if (enabled.not()) {
            lengthCounter = 0
        }
    }

    // $4015 read	IF-D NT21	DMC interrupt (I), frame interrupt (F), DMC active (D), length counter > 0 (N/T/2/1)
    override fun readEnableStatus(): UByte = if (lengthCounter == 0) 0U else 0b0000_0100U

    private var timerCounter: Int = 0
    private var linearCounter: Int = 0

    // F E D C B A 9 8 7 6 5 4 3 2 1 0 0 1 2 3 4 5 6 7 8 9 A B C D E F のシーケンスを生成 するためのインデックスが m_TriStep
    private var triangleStepIndex: Int = 0x10

    override fun clockTimer() {
        // タイマをクロック、その値によって三角波チャンネルをクロック
        val ultraSonic = timer < 2
        if (lengthCounter != 0 && linearCounter != 0 && ultraSonic.not()) {
            if (timerCounter > 0) {
                timerCounter--
            } else {
                timerCounter = timer
                triangleStepIndex = (triangleStepIndex + 1) and 0x1F
            }
        }
        // TORIAEZU: ClockTimer の責務からは外れるが、三角波ユニットをクロックした直後の値で出力値を更新する
        outputVolume = when {
            // Disch の疑似コードでは 7.5 って言ってるけど[0, F]の中心で止める、という意味なので7でもいいはず
            // https://www.nesdev.org/wiki/APU_Triangle
            // Write a period value of 0 or 1 to $400A/$400B, causing a very high frequency. Due to the averaging effect of the lowpass filter,
            // the resulting value is halfway between 7 and 8.
            // This sudden jump to "7.5" causes a harder popping noise than other triangle silencing methods,
            // which will instead halt it in whatever its current output position is.
            // Mega Man 1 and 2 use this technique.
            ultraSonic -> 7
            // 0x10 のビットが立ってたら、そのビットを0にして、その下の4bitを反転することで
            // F E D C B A 9 8 7 6 5 4 3 2 1 0 0 1 2 3 4 5 6 7 8 9 A B C D E F のシーケンスを生成
            // cf. http://pgate1.at-ninja.jp/NES_on_FPGA/nes_apu.htm の 三角波 のとこ
            triangleStepIndex and 0x10 != 0 -> triangleStepIndex xor 0x1F
            else -> triangleStepIndex
        }
    }

    override fun clockQuarterFrame() {
        // 線形カウンタの処理
        if (isLinearCounterReload) {
            // レジスタ$400Bへの書き込みによって、線形カウンタを停止し、カウンタへ音の長さをロードします(NES on FPGA)
            linearCounter = linearCounterLoad
        } else if (linearCounter > 0) {
            // (線形カウンタのコントロールフラグ(http://pgate1.at-ninja.jp/NES_on_FPGA/nes_apu.htm)がクリアされてたら？)
            // && カウンタが0でなければデクリメント
            linearCounter--
        }
        if (isLengthCounter) {
            // https://www.nesdev.org/wiki/APU_Triangle
            // If the control flag is clear, the linear counter reload flag is cleared.
            // Note that the reload flag is not cleared unless the control flag is also clear,
            // so when both are already set a value written to $4008 will be reloaded at the next linear counter clock.
            isLinearCounterReload = false
        }
    }

    override fun clockHalfFrame() {
        // 長さカウンタのクロック生成
        if (isLengthCounter && lengthCounter > 0) {
            lengthCounter--
        }
    }

    override var outputVolume: Int = 0
        private set
}
