package jp.mito.famiemukt.test.cpu

import jp.mito.famiemukt.emurator.NesSystem
import jp.mito.famiemukt.emurator.apu.AudioSampleNotifier
import jp.mito.famiemukt.emurator.cartridge.BackupRAM
import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.util.VisibleForTesting
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail

@OptIn(ExperimentalUnsignedTypes::class, VisibleForTesting::class)
class NesTestRomsTest {
    @Test
    fun testInstrOfficial() = checkRom("nes-test-roms/instr_test-v5/official_only.nes")

    @Test
    fun testInstrOfficial_15() = checkRom("nes-test-roms/instr_test-v5/rom_singles/15-brk.nes")

    @Test
    fun testInstrOfficial_16() = checkRom("nes-test-roms/instr_test-v5/rom_singles/16-special.nes")

    ///////////////////////////////////


    @Test
    fun testInstrMisc_1() = checkRom("nes-test-roms/instr_misc/rom_singles/01-abs_x_wrap.nes")

    @Test
    fun testInstrMisc_2() = checkRom("nes-test-roms/instr_misc/rom_singles/02-branch_wrap.nes")

    @Test
    fun testInstrMisc_3() = checkRom("nes-test-roms/instr_misc/rom_singles/03-dummy_reads.nes")

    @Test
    fun testInstrMisc_4() = checkRom("nes-test-roms/instr_misc/rom_singles/04-dummy_reads_apu.nes")

    ///////////////////////////////////

    @Test
    fun testCPUInterrupt_1() = checkRom("nes-test-roms/cpu_interrupts_v2/rom_singles/1-cli_latency.nes")

    @Test
    fun testCPUInterrupt_2() = checkRom("nes-test-roms/cpu_interrupts_v2/rom_singles/2-nmi_and_brk.nes")

    @Test
    fun testCPUInterrupt_3() = checkRom("nes-test-roms/cpu_interrupts_v2/rom_singles/3-nmi_and_irq.nes")

    @Test
    fun testCPUInterrupt_4() = checkRom("nes-test-roms/cpu_interrupts_v2/rom_singles/4-irq_and_dma.nes")

    @Test
    fun testCPUInterrupt_5() = checkRom("nes-test-roms/cpu_interrupts_v2/rom_singles/5-branch_delays_irq.nes")

    /////////////////////////////////////////////

    @Test
    fun testPPUVBLNMI_01() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/01-vbl_basics.nes")

    @Test
    fun testPPUVBLNMI_02() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/02-vbl_set_time.nes")

    @Test
    fun testPPUVBLNMI_03() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/03-vbl_clear_time.nes")

    @Test
    fun testPPUVBLNMI_04() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/04-nmi_control.nes")

    @Test
    fun testPPUVBLNMI_05() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/05-nmi_timing.nes")

    @Test
    fun testPPUVBLNMI_06() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/06-suppression.nes")

    @Test
    fun testPPUVBLNMI_07() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/07-nmi_on_timing.nes")

    @Test
    fun testPPUVBLNMI_08() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/08-nmi_off_timing.nes")

    @Test
    fun testPPUVBLNMI_09() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/09-even_odd_frames.nes")

    @Test
    fun testPPUVBLNMI_10() = checkRom("nes-test-roms/ppu_vbl_nmi/rom_singles/10-even_odd_timing.nes")

    /////////////////////////////////////////////

    @Test
    fun testAPUTest_1() = checkRom("nes-test-roms/apu_test/rom_singles/1-len_ctr.nes")

    @Test
    fun testAPUTest_2() = checkRom("nes-test-roms/apu_test/rom_singles/2-len_table.nes")

    @Test
    fun testAPUTest_3() = checkRom("nes-test-roms/apu_test/rom_singles/3-irq_flag.nes")

    @Test
    fun testAPUTest_4() = checkRom("nes-test-roms/apu_test/rom_singles/4-jitter.nes")

    @Test
    fun testAPUTest_5() = checkRom("nes-test-roms/apu_test/rom_singles/5-len_timing.nes")

    @Test
    fun testAPUTest_6() = checkRom("nes-test-roms/apu_test/rom_singles/6-irq_flag_timing.nes")

    @Test
    fun testAPUTest_7() = checkRom("nes-test-roms/apu_test/rom_singles/7-dmc_basics.nes")

    @Test
    fun testAPUTest_8() = checkRom("nes-test-roms/apu_test/rom_singles/8-dmc_rates.nes")

    ///////////////////////////////////

    @Test
    fun testOAMStress() = checkRom("nes-test-roms/oam_stress/oam_stress.nes")

    /////////////////////////////////////////////

    @Test
    fun testSpriteHitTests_01() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/01.basics.nes")

    @Test
    fun testSpriteHitTests_02() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/02.alignment.nes")

    @Test
    fun testSpriteHitTests_03() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/03.corners.nes")

    @Test
    fun testSpriteHitTests_04() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/04.flip.nes")

    @Test
    fun testSpriteHitTests_05() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/05.left_clip.nes")

    @Test
    fun testSpriteHitTests_06() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/06.right_edge.nes")

    @Test
    fun testSpriteHitTests_07() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/07.screen_bottom.nes")

    @Test
    fun testSpriteHitTests_08() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/08.double_height.nes")

    @Test
    fun testSpriteHitTests_09() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/09.timing_basics.nes")

    @Test
    fun testSpriteHitTests_10() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/10.timing_order.nes")

    @Test
    fun testSpriteHitTests_11() = checkRomDisplay("nes-test-roms/sprite_hit_tests_2005.10.05/11.edge_timing.nes")

    /////////////////////////////////////////////

    @Test
    fun testSpriteOverflowTests_1() = checkRomDisplay("nes-test-roms/sprite_overflow_tests/1.Basics.nes")

    @Test
    fun testSpriteOverflowTests_2() = checkRomDisplay("nes-test-roms/sprite_overflow_tests/2.Details.nes")

    @Test
    fun testSpriteOverflowTests_3() = checkRomDisplay("nes-test-roms/sprite_overflow_tests/3.Timing.nes")

    @Test
    fun testSpriteOverflowTests_4() = checkRomDisplay("nes-test-roms/sprite_overflow_tests/4.Obscure.nes")

    @Test
    fun testSpriteOverflowTests_5() = checkRomDisplay("nes-test-roms/sprite_overflow_tests/5.Emulator.nes")

    /////////////////////////////////////////////

    @Test
    fun testExecSpace_apu() = checkRom("nes-test-roms/cpu_exec_space/test_cpu_exec_space_apu.nes")

    @Test
    fun testExecSpace_ppuio() = checkRom("nes-test-roms/cpu_exec_space/test_cpu_exec_space_ppuio.nes")

    /////////////////////////////////////////////

    @Test
    fun testMM3Test_1() = checkRom("nes-test-roms/mmc3_test_2/rom_singles/1-clocking.nes")

    @Test
    fun testMM3Test_2() = checkRom("nes-test-roms/mmc3_test_2/rom_singles/2-details.nes")

    @Test
    fun testMM3Test_3() = checkRom("nes-test-roms/mmc3_test_2/rom_singles/3-A12_clocking.nes")

    @Test
    fun testMM3Test_4() = checkRom("nes-test-roms/mmc3_test_2/rom_singles/4-scanline_timing.nes")

    @Test
    fun testMM3Test_5() = checkRom("nes-test-roms/mmc3_test_2/rom_singles/5-MMC3.nes")

    @Test
    fun testMM3Test_6() = checkRom("nes-test-roms/mmc3_test_2/rom_singles/6-MMC3_alt.nes")

    /////////////////////////////////////////////

    private fun checkRom(file: String) {
        val backupRAM = BackupRAM()
        val iNesData = assertNotNull(actual = javaClass.classLoader.getResource(file)).readBytes()
        val cartridge = Cartridge(backupRAM = backupRAM, iNesData = iNesData)
        val audioSampleNotifier = object : AudioSampleNotifier {
            override fun notifySample(value: UByte) = Unit
        }
        var count = 0L
        val system = NesSystem(cartridge, audioSampleNotifier, AUDIO_SAMPLING_RATE)
        system.reset()
        while (true) {
            val drawFrame = system.executeMasterClockStep()
            if (drawFrame.not()) continue
            if (finishCheckResult(backupRAM, system)) break
            if (++count > TIMEOUT_FRAME_COUNT) {
                val data = backupRAM._backup
                val code = data[0].toString(16).padStart(2, '0')
                val key1 = data[1].toString(16).padStart(2, '0')
                val key2 = data[2].toString(16).padStart(2, '0')
                val key3 = data[3].toString(16).padStart(2, '0')
                val message = data.drop(n = 4).takeWhile { it != 0.toUByte() }
                    .toUByteArray().asByteArray().toString(Charsets.UTF_8)
                fail(
                    message = """
                        |タイムアウト ${count / 60} [sec]？
                        |code=$code key=[$key1][$key2][$key3]
                        |$message
                        |${system.debugInfo(nest = 0)}
                        |""".trimMargin().trimEnd()
                )
            }
        }
    }

    private fun finishCheckResult(backupRAM: BackupRAM, system: NesSystem): Boolean {
        val data = backupRAM._backup
        if (data[1] != 0xDE.toUByte() || data[2] != 0xB0.toUByte() || data[3] != 0x61.toUByte()) {
            return false
        }
        return when (data[0]) {
            0x80.toUByte() -> false
            0x81.toUByte() -> {
                println("0x81 : need reset button")
                false
            }

            0x00.toUByte() -> true
            else -> {
                val code = data[0].toString(16).padStart(2, '0')
                val message = data.drop(n = 4).takeWhile { it != 0.toUByte() }
                    .toUByteArray().asByteArray().toString(Charsets.UTF_8)
                fail(
                    message = """
                        |code=$code
                        |$message
                        |${system.debugInfo(nest = 0)}
                        |""".trimMargin().trimEnd()
                )
            }
        }
    }

    /////////////////////////////////////////////

    private fun checkRomDisplay(file: String) {
        val backupRAM = BackupRAM()
        val iNesData = assertNotNull(actual = javaClass.classLoader.getResource(file)).readBytes()
        val cartridge = Cartridge(backupRAM = backupRAM, iNesData = iNesData)
        val audioSampleNotifier = object : AudioSampleNotifier {
            override fun notifySample(value: UByte) = Unit
        }
        var count = 0L
        val system = NesSystem(cartridge, audioSampleNotifier, AUDIO_SAMPLING_RATE)
        system.reset()
        while (true) {
            val drawFrame = system.executeMasterClockStep()
            if (drawFrame.not()) continue
            if (finishCheckResultDisplay(system)) break
            if (++count > TIMEOUT_FRAME_COUNT) {
                val data = backupRAM._backup
                val code = data[0].toString(16).padStart(2, '0')
                val key1 = data[1].toString(16).padStart(2, '0')
                val key2 = data[2].toString(16).padStart(2, '0')
                val key3 = data[3].toString(16).padStart(2, '0')
                val message = data.drop(n = 4).takeWhile { it != 0.toUByte() }
                    .toUByteArray().asByteArray().toString(Charsets.UTF_8)
                fail(
                    message = """
                        |タイムアウト ${count / 60} [sec]？
                        |code=$code key=[$key1][$key2][$key3]
                        |$message
                        |${system.debugInfo(nest = 0)}
                        |""".trimMargin().trimEnd()
                )
            }
        }
    }

    private fun finishCheckResultDisplay(system: NesSystem): Boolean {
        val data = system._videoRAM._nameTable
        val passFirst = data.lastIndexOf('P'.code.toUByte())
        val failFirst = data.lastIndexOf('F'.code.toUByte())
        if (passFirst in 0..<0x400 &&
            data[passFirst + 1] == 'A'.code.toUByte() &&
            data[passFirst + 2] == 'S'.code.toUByte() &&
            data[passFirst + 3] == 'S'.code.toUByte() &&
            data[passFirst + 4] == 'E'.code.toUByte() &&
            data[passFirst + 5] == 'D'.code.toUByte()
        ) {
            return true
        }
        if (failFirst in 0..<0x400 &&
            data[failFirst + 1] == 'A'.code.toUByte() &&
            data[failFirst + 2] == 'I'.code.toUByte() &&
            data[failFirst + 3] == 'L'.code.toUByte() &&
            data[failFirst + 4] == 'E'.code.toUByte() &&
            data[failFirst + 5] == 'D'.code.toUByte()
        ) {
            // 少し実行して番号(#)があるものは、それも表示できるようにする
            repeat(times = 12 * 20) { system.executeMasterClockStep() }
            // 失敗
            val message = data.asSequence().take(n = 0x400).windowed(size = 32, step = 32, partialWindows = true)
                .map { String(it.toUByteArray().asByteArray(), Charsets.US_ASCII).trim { c -> c < ' ' }.trimEnd() }
                .filter { it.isNotEmpty() }.joinToString(separator = "\n").trimEnd()
            fail(
                message = """
                    |$message
                    |${system.debugInfo(nest = 0)}
                    |""".trimMargin().trimEnd()
            )
        }
        return false
    }

    /////////////////////////////////////////////

    companion object {
        private const val AUDIO_SAMPLING_RATE = 11_500
        private const val TIMEOUT_FRAME_COUNT = 60 * 60
    }
}
