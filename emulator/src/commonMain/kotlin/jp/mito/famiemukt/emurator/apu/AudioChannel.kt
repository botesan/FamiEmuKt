package jp.mito.famiemukt.emurator.apu

interface AudioChannel {
    fun writeEnableStatus(value: UByte)
    fun readEnableStatus(): UByte
    fun clockTimer()
    fun clockQuarterFrame()
    fun clockHalfFrame()
    val outputVolume: Int
}
