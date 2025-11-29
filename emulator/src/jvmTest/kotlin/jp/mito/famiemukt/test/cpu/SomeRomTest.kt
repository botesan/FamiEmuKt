package jp.mito.famiemukt.test.cpu

import jp.mito.famiemukt.emurator.NTSC_CPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.apu.APU
import jp.mito.famiemukt.emurator.apu.AudioSampleNotifier
import jp.mito.famiemukt.emurator.cartridge.BackupRAM
import jp.mito.famiemukt.emurator.cartridge.Cartridge
import jp.mito.famiemukt.emurator.cpu.*
import jp.mito.famiemukt.emurator.dma.DMA
import jp.mito.famiemukt.emurator.pad.Pad
import jp.mito.famiemukt.emurator.ppu.PPU
import jp.mito.famiemukt.emurator.ppu.PPUBus
import jp.mito.famiemukt.emurator.ppu.PPURegisters
import jp.mito.famiemukt.emurator.ppu.VideoRAM
import jp.mito.famiemukt.emurator.util.VisibleForTesting
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalUnsignedTypes::class, VisibleForTesting::class)
class SomeRomTest {
    @Test
    fun testHelloWorld() {
        var cpu: CPU? = null
        val ram = RAM()
        val cpuRegisters = CPURegisters()
        val ppuRegisters = PPURegisters()
        val iNesData = assertNotNull(actual = javaClass.classLoader.getResource("sample1/sample1.nes")).readBytes()
        val backupRAM = BackupRAM()
        val cartridge = Cartridge(backupRAM = backupRAM, iNesData = iNesData)
        val stateObserver = cartridge.mapper.stateObserver
        val videoRAM = VideoRAM()
        val ppuBus = PPUBus(mapper = cartridge.mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            stateObserver = stateObserver,
            interrupter = object : Interrupter {
                override fun requestREST() = cpu?.requestInterruptREST() ?: Unit
                override fun requestNMI(levelLow: Boolean) = cpu?.requestInterruptNMI(levelLow) ?: Unit
                override fun requestOnIRQ() = cpu?.requestInterruptOnIRQ() ?: Unit
                override fun requestOffIRQ() = cpu?.requestInterruptOffIRQ() ?: Unit
                override val isRequestedIRQ: Boolean get() = cpu?.isRequestedIRQ ?: false
            })
        val dma = DMA(ppu = ppu, ppuRegisters = ppuRegisters)
        val apu = APU(
            interrupter = object : Interrupter {
                override fun requestREST() = cpu?.requestInterruptREST() ?: Unit
                override fun requestNMI(levelLow: Boolean) = cpu?.requestInterruptNMI(levelLow) ?: Unit
                override fun requestOnIRQ() = cpu?.requestInterruptOnIRQ() ?: Unit
                override fun requestOffIRQ() = cpu?.requestInterruptOffIRQ() ?: Unit
                override val isRequestedIRQ: Boolean get() = cpu?.isRequestedIRQ ?: false
            }, dma = dma,
            audioSampleNotifier = object : AudioSampleNotifier {
                override fun notifySample(value: UByte) = Unit
            }, audioSamplingRate = 11_500
        )
        val pad = Pad()
        val cpuBus = CPUBus(mapper = cartridge.mapper, dma = dma, ram = ram, apu = apu, ppu = ppu, pad = pad)
        cpu = CPU(cpuRegisters = cpuRegisters, cpuBus = cpuBus, dma = dma)
        cpu.setPowerOnState()
        try {
            do {
                val beforePC = cpuRegisters.PC
                while (true) {
                    ppu.executeMasterClockStep()
                    val result = cpu.executeMasterClockStep()
                    apu.executeMasterClockStep()
                    if (result?.instruction != null) break
                }
                assertContains(range = 0x8000U..0xffffU, value = cpuRegisters.PC.toUInt())
                print("${cpuRegisters.PC.toString(radix = 16).padStart(length = 4, padChar = '0')} : ")
                print(" A:${cpuRegisters.A.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" X:${cpuRegisters.X.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" Y:${cpuRegisters.Y.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" S:${cpuRegisters.S.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" P:${cpuRegisters.P.value.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                println()
            } while (cpuRegisters.PC != beforePC)
        } finally {
            println()
            println("cartridge=${cartridge.information}")
            println("cpuRegisters=$cpuRegisters")
            println("ppuRegisters=$ppuRegisters")
            println("palletTable=${videoRAM._palletTable.contentToString()}")
            println("nameTable")
            println(
                videoRAM._nameTable.chunked(size = 32).joinToString(separator = "\n") { bytes ->
                    bytes.joinToString(separator = " ") { byte ->
                        byte.toString(radix = 16).padStart(length = 2, padChar = '0')
                    }
                }
            )
        }
    }

    @Test
    fun testNesTest() {
        data class Excepted(
            val lineNo: Int,
            val line: String,
            val commandName: String,
            val cpuRegisters: CPURegisters,
            val cpuCycles: Int,
            val ppuY: Int,
            val ppuX: Int,
        )

        val exceptedSequence =
            assertNotNull(actual = javaClass.classLoader.getResource("nes-test-roms/other/nestest.log"))
                .readText()
                .lineSequence()
                .filter { it.isNotEmpty() }
                .mapIndexed { index, line ->
                    @Suppress("ReplaceSubstringWithTake")
                    Excepted(
                        lineNo = index + 1, line = line,
                        commandName = line.substring(startIndex = 16, endIndex = 19),
                        cpuRegisters = CPURegisters(
                            A = line.substring(startIndex = 50, endIndex = 52).toUByte(radix = 16),
                            X = line.substring(startIndex = 55, endIndex = 57).toUByte(radix = 16),
                            Y = line.substring(startIndex = 60, endIndex = 62).toUByte(radix = 16),
                            PC = line.substring(startIndex = 0, endIndex = 4).toUShort(radix = 16),
                            S = line.substring(startIndex = 71, endIndex = 73).toUByte(radix = 16),
                            P = ProcessorStatus(
                                value = line.substring(startIndex = 65, endIndex = 67).toUByte(radix = 16)
                            ),
                        ),
                        cpuCycles = line.substring(startIndex = 90).toInt(),
                        ppuY = line.substring(startIndex = 78, endIndex = 81).trim().toInt(),
                        ppuX = line.substring(startIndex = 82, endIndex = 85).trim().toInt(),
                    )
                }
        var cpu: CPU? = null
        val ram = RAM()
        val cpuRegisters = CPURegisters()
        val ppuRegisters = PPURegisters()
        val backupRAM = BackupRAM()
        val iNesData =
            assertNotNull(actual = javaClass.classLoader.getResource("nes-test-roms/other/nestest.nes")).readBytes()
        val cartridge = Cartridge(backupRAM = backupRAM, iNesData = iNesData)
        val stateObserver = cartridge.mapper.stateObserver
        val videoRAM = VideoRAM()
        val ppuBus = PPUBus(mapper = cartridge.mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            stateObserver = stateObserver,
            interrupter = object : Interrupter {
                override fun requestREST() = cpu?.requestInterruptREST() ?: Unit
                override fun requestNMI(levelLow: Boolean) = cpu?.requestInterruptNMI(levelLow) ?: Unit
                override fun requestOnIRQ() = cpu?.requestInterruptOnIRQ() ?: Unit
                override fun requestOffIRQ() = cpu?.requestInterruptOffIRQ() ?: Unit
                override val isRequestedIRQ: Boolean get() = cpu?.isRequestedIRQ ?: false
            })
        val dma = DMA(ppu = ppu, ppuRegisters = ppuRegisters)
        val apu = APU(
            interrupter = object : Interrupter {
                override fun requestREST() = cpu?.requestInterruptREST() ?: Unit
                override fun requestNMI(levelLow: Boolean) = cpu?.requestInterruptNMI(levelLow) ?: Unit
                override fun requestOnIRQ() = cpu?.requestInterruptOnIRQ() ?: Unit
                override fun requestOffIRQ() = cpu?.requestInterruptOffIRQ() ?: Unit
                override val isRequestedIRQ: Boolean get() = cpu?.isRequestedIRQ ?: false
            }, dma = dma,
            audioSampleNotifier = object : AudioSampleNotifier {
                override fun notifySample(value: UByte) = Unit
            }, audioSamplingRate = 11_500
        )
        val pad = Pad()
        val cpuBus = CPUBus(mapper = cartridge.mapper, dma = dma, ram = ram, apu = apu, ppu = ppu, pad = pad)
        cpu = CPU(cpuRegisters = cpuRegisters, cpuBus = cpuBus, dma = dma)
        cpu.setPowerOnState()
        cpuRegisters.PC = 0xc000U
        var cycles = 7
        repeat(cycles * NTSC_CPU_CYCLES_PER_MASTER_CLOCKS) { ppu.executeMasterClockStep() }
        try {
            exceptedSequence.forEach { expected ->
                println("[${expected.lineNo}]-------------------------------------------------------------------------------")
                println(expected.line)
                print(
                    "expected ${expected.cpuRegisters.PC.toString(radix = 16).padStart(length = 4, padChar = '0')} : "
                )
                print(" A:${expected.cpuRegisters.A.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" X:${expected.cpuRegisters.X.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" Y:${expected.cpuRegisters.Y.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" S:${expected.cpuRegisters.S.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" P:${expected.cpuRegisters.P.value.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" | ")
                print("actual ${cpuRegisters.PC.toString(radix = 16).padStart(length = 4, padChar = '0')} : ")
                print(" A:${cpuRegisters.A.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" X:${cpuRegisters.X.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" Y:${cpuRegisters.Y.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                print(" S:${cpuRegisters.S.toString(radix = 16).padStart(length = 2, padChar = '0')} ")
                println(" P:${cpuRegisters.P.value.toString(radix = 16).padStart(length = 2, padChar = '0')}")
                println("expected ppuX:${expected.ppuX} ${expected.ppuY} | actual ppuX=${ppu._ppuX} ppuY=${ppu._ppuY}")
                //
                assertEquals(expected = expected.cpuRegisters.PC, actual = cpuRegisters.PC)
                assertEquals(expected = expected.cpuRegisters.A, actual = cpuRegisters.A)
                assertEquals(expected = expected.cpuRegisters.X, actual = cpuRegisters.X)
                assertEquals(expected = expected.cpuRegisters.Y, actual = cpuRegisters.Y)
                assertEquals(expected = expected.cpuRegisters.S, actual = cpuRegisters.S)
                assertEquals(expected = expected.cpuRegisters.P.value, actual = cpuRegisters.P.value)
                assertEquals(expected = expected.cpuCycles, actual = cycles)
                assertEquals(expected = expected.ppuY, actual = ppu._ppuY)
                assertEquals(expected = expected.ppuX, actual = ppu._ppuX)
                //
                var masterClockCount = 0
                while (true) {
                    masterClockCount++
                    ppu.executeMasterClockStep()
                    val result = cpu.executeMasterClockStep()
                    apu.executeMasterClockStep()
                    if (result == null) continue
                    cycles += result.executeCycle
                    println("$result")
                    assertEquals(expected = expected.commandName, actual = result.instruction?.opCode?.name)
                    assertEquals(
                        expected = result.executeCycle * NTSC_CPU_CYCLES_PER_MASTER_CLOCKS,
                        actual = masterClockCount,
                    )
                    break
                }
            }
        } finally {
            println()
            println("cartridge=${cartridge.information}")
            println("cpuRegisters=$cpuRegisters")
            println("ppuRegisters=$ppuRegisters")
        }
    }
}
