package jp.mito.famiemukt.emurator.apu

import kotlin.math.roundToInt

class AudioMixer(
    private val pulse1: Pulse,
    private val pulse2: Pulse,
    private val triangle: Triangle,
    private val noise: Noise,
    private val dmc: DMC,
) {
    private var pulse1Volume: Int = 0
    private var pulse2Volume: Int = 0
    private var triangleVolume: Int = 0
    private var noiseVolume: Int = 0
    private var dmcVolume: Int = 0
    fun syncChannelVolume() {
        pulse1Volume = pulse1.outputVolume
        pulse2Volume = pulse2.outputVolume
        triangleVolume = triangle.outputVolume
        noiseVolume = noise.outputVolume
        dmcVolume = dmc.outputVolume
    }

    val outputVolume: UByte
        get() = (PULSE_TABLE[pulse1Volume + pulse2Volume] +
                TND_TABLE[3 * triangleVolume + 2 * noiseVolume + dmcVolume]).toUByte()

    companion object {
        // https://www.nesdev.org/wiki/APU_Mixer
        // Lookup Table
        //  The APU mixer formulas can be efficiently implemented using two lookup tables:
        //  a 31-entry table for the two pulse channels and
        //  a 203-entry table for the remaining channels (due to the approximation of tnd_out,
        //  the numerators are adjusted slightly to preserve the normalized output range).
        //    output = pulse_out + tnd_out
        //    pulse_table [n] = 95.52 / (8128.0 / n + 100)
        //    pulse_out = pulse_table [pulse1 + pulse2]
        private val PULSE_TABLE: IntArray = DoubleArray(size = 31) { 95.52 / (8128.0 / it + 100) }
            .map { (it * UByte.MAX_VALUE.toInt()).roundToInt() }
            .toIntArray()

        //  The tnd_out table is approximated (within 4%) by using a base unit close to the DMC's DAC.
        //    tnd_table [n] = 163.67 / (24329.0 / n + 100)
        //    tnd_out = tnd_table [3 * triangle + 2 * noise + dmc]
        private val TND_TABLE: IntArray = DoubleArray(size = 203) { 163.67 / (24329.0 / it + 100) }
            .map { (it * UByte.MAX_VALUE.toInt()).roundToInt() }
            .toIntArray()
    }
}
