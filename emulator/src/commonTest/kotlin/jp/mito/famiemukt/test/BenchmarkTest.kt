package jp.mito.famiemukt.test

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.goncalossilva.resources.Resource
import jp.mito.famiemukt.emurator.NesSystem
import jp.mito.famiemukt.emurator.apu.AudioSampleNotifier
import jp.mito.famiemukt.emurator.cartridge.BackupRAM
import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.ppu.FetchSprite
import jp.mito.famiemukt.emurator.util.VisibleForTesting
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@OptIn(ExperimentalUnsignedTypes::class, VisibleForTesting::class)
class BenchmarkTest {
    @BeforeTest
    fun setup() {
        Logger.setLogWriters(CommonWriter())
        Logger.setMinSeverity(Severity.Info)
    }

    @Test
    fun benchmarkPerformance() {
        val backupRAM = BackupRAM()
        val iNesData = Resource(path = "nes-test-roms/other/RasterDemo.NES").readBytes()
        val cartridge = Cartridge(backupRAM = backupRAM, iNesData = iNesData)
        val audioSampleNotifier = object : AudioSampleNotifier {
            override fun notifySample(value: UByte) = Unit
        }
        val system = NesSystem(cartridge, audioSampleNotifier, AUDIO_SAMPLING_RATE)
        system.reset()
        // ウォームアップ
        system.executeFrames(frameCount = WARMUP_FRAME_COUNT)
        // 計測
        val testTime = 5.seconds
        val frameCount = testTime.inWholeSeconds.toInt() * ONE_SECOND_FRAME_COUNT
        val time = measureTime { system.executeFrames(frameCount = frameCount) }
        Logger.i { "Test finished: $time / ${testTime / time} / FetchSprite pool size=${FetchSprite.poolSize}" }
        // 実行時間チェック
        assertTrue(actual = time < testTime, message = "$time")
        // TODO: 改善前後で比較
        //  参考JVM：1.022631300s - 1.087982600s ぐらい
    }

    private fun NesSystem.executeFrames(frameCount: Int) {
        var count = 0L
        while (true) {
            val drawFrame = executeMasterClockStep()
            if (drawFrame.not()) continue
            if (++count > frameCount) break
        }
    }

    companion object {
        private const val AUDIO_SAMPLING_RATE = 48_000
        private const val ONE_SECOND_FRAME_COUNT = 60 // 1秒あたりのフレーム数
        private const val WARMUP_FRAME_COUNT = 5 * ONE_SECOND_FRAME_COUNT
    }
}
