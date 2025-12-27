package jp.mito.famiemukt.test

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import jp.mito.famiemukt.emurator.NTSC_CPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.NTSC_PPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.cartridge.A12
import jp.mito.famiemukt.emurator.cartridge.NothingStateObserver
import jp.mito.famiemukt.emurator.cartridge.mapper.Mapper
import jp.mito.famiemukt.emurator.cartridge.mapper.Mapper000
import jp.mito.famiemukt.emurator.cpu.Interrupter
import jp.mito.famiemukt.emurator.ppu.PPU
import jp.mito.famiemukt.emurator.ppu.PPUBus
import jp.mito.famiemukt.emurator.ppu.PPURegisters
import jp.mito.famiemukt.emurator.ppu.VideoRAM
import jp.mito.famiemukt.emurator.util.VisibleForTesting
import jp.mito.famiemukt.emurator.util.toHex
import kotlin.test.*

@OptIn(ExperimentalUnsignedTypes::class, VisibleForTesting::class)
class PPUTest {
    @BeforeTest
    fun setup() {
        Logger.setLogWriters(CommonWriter())
        Logger.setMinSeverity(Severity.Info)
    }

    @Test
    fun testConstructor() {
        val mapper = mock<Mapper>()
        val interrupter = mock<Interrupter>()
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        assertNotNull(ppu)
    }

    // https://www.nesdev.org/wiki/PPU_scrolling#Summary
    @Test
    fun testWrite2InternalRegister() {
        val mapper = mock<Mapper>()
        val interrupter = mock<Interrupter>()
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // write Control -> t
        ppu.writePPUControl(value = 0b0000_0011U)
        assertEquals(expected = 0b0_000_11_00_000_00000U, actual = ppuRegisters.internal.t)
        // read Control -> w
        ppu.readPPUStatus()
        assertEquals(expected = false, actual = ppuRegisters.internal.w)
        // write Scroll X -> t,x,w
        ppu.writePPUScroll(value = 0b00001_010U) // 10
        assertEquals(expected = 0b0_000_11_00_000_00001U, actual = ppuRegisters.internal.t)
        assertEquals(expected = 0b00000_010U, actual = ppuRegisters.internal.x)
        assertEquals(expected = true, actual = ppuRegisters.internal.w)
        // write Scroll Y -> t,x,w
        ppu.writePPUScroll(value = 0b00_010_100U) // 20
        assertEquals(expected = 0b0_100_11_00_010_00001U, actual = ppuRegisters.internal.t)
        assertEquals(expected = 0b00000_010U, actual = ppuRegisters.internal.x)
        assertEquals(expected = false, actual = ppuRegisters.internal.w)
        // write Address High -> t
        ppu.writePPUAddress(value = 0b00_000011U) // 1000 -> [0000 0011] 1110 1000
        assertEquals(expected = 0b0_0_000011_01000001U, actual = ppuRegisters.internal.t)
        // write Address Low -> t,v
        ppu.writePPUAddress(value = 0b11101000U) // 1000 -> 0000 0011 [1110 1000]
        assertEquals(expected = 0b0_0_000011_11101000U, actual = ppuRegisters.internal.t)
        assertEquals(expected = 0b0_0_000011_11101000U, actual = ppuRegisters.internal.v)
    }

    @Test
    fun testReadBufferMirrorHorizontal() {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // データ
        val data = (0x2000u..0x3effu).map { ((it + (it shr 8)) * 7u + 11u).toUByte() }
        // 書き込み
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x20u)
        ppu.writePPUAddress(value = 0x00u)
        data.forEach { ppu.writeVideoRAM(value = it) }
        // VRAMチェック（書き込み時のPPU BusのミラーリングやNameTableのミラーリングHorizontalを考慮）
        //// 0x3400 -> 0x2400 -> 0x2000 のデータ
        val exceptScreenA = data.drop(n = 0x1400).take(n = 0x0400)
        //// 0x3C00 -> 0x2C00 -> 0x2800 のデータ（0x0300バイト） + 0x3800 -> 0x2800 のラストデータ（0x0100バイト）
        val exceptScreenB = data.drop(n = 0x1C00).take(n = 0x0300) + data.drop(n = 0x1800 + 0x0300).take(n = 0x0100)
        val exceptAllZero = UByteArray(size = 0x0400)
        assertContentEquals(expected = exceptScreenA, actual = videoRAM._nameTable.drop(n = 0x0000).take(n = 0x0400))
        assertContentEquals(expected = exceptScreenB, actual = videoRAM._nameTable.drop(n = 0x0800).take(n = 0x0400))
        assertContentEquals(expected = exceptAllZero, actual = videoRAM._nameTable.drop(n = 0x0400).take(n = 0x0400))
        assertContentEquals(expected = exceptAllZero, actual = videoRAM._nameTable.drop(n = 0x0C00).take(n = 0x0400))
        // 読み込みチェック：一回ずつ遅れて値が返ってくる
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x20u)
        ppu.writePPUAddress(value = 0x00u)
        assertEquals(expected = 0u, actual = ppu.readVideoRAM())
        // チェック（書き込み時のPPU BusのミラーリングやNameTableのミラーリングHorizontalを考慮）
        exceptScreenA.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "A1:" + index.toString(radix = 16))
        }
        exceptScreenA.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "A2:" + index.toString(radix = 16))
        }
        exceptScreenB.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "B1:" + index.toString(radix = 16))
        }
        exceptScreenB.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "B2:" + index.toString(radix = 16))
        }
    }

    @Test
    fun testReadBufferMirrorVertical() {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns true
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // データ
        val data = (0x2000u..0x3effu).map { ((it + (it shr 8)) * 7u + 11u).toUByte() }
        // 書き込み
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x20u)
        ppu.writePPUAddress(value = 0x00u)
        data.forEach { ppu.writeVideoRAM(value = it) }
        // VRAMチェック（書き込み時のPPU BusのミラーリングやNameTableのミラーリングHorizontalを考慮）
        //// 0x3800 -> 0x2800 -> 0x2000 のデータ
        val exceptScreenA = data.drop(n = 0x1800).take(n = 0x0400)
        //// 0x3C00 -> 0x2C00 -> 0x2400 のデータ（0x0300バイト） + 0x3400 -> 0x2400 のラストデータ（0x0100バイト）
        val exceptScreenB = data.drop(n = 0x1C00).take(n = 0x0300) + data.drop(n = 0x1400 + 0x0300).take(n = 0x0100)
        val exceptAllZero = UByteArray(size = 0x0400)
        assertContentEquals(expected = exceptScreenA, actual = videoRAM._nameTable.drop(n = 0x0000).take(n = 0x0400))
        assertContentEquals(expected = exceptScreenB, actual = videoRAM._nameTable.drop(n = 0x0400).take(n = 0x0400))
        assertContentEquals(expected = exceptAllZero, actual = videoRAM._nameTable.drop(n = 0x0800).take(n = 0x0400))
        assertContentEquals(expected = exceptAllZero, actual = videoRAM._nameTable.drop(n = 0x0C00).take(n = 0x0400))
        // 読み込みチェック：一回ずつ遅れて値が返ってくる
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x20u)
        ppu.writePPUAddress(value = 0x00u)
        assertEquals(expected = 0u, actual = ppu.readVideoRAM())
        // チェック（書き込み時のPPU BusのミラーリングやNameTableのミラーリングHorizontalを考慮）
        exceptScreenA.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "A1:" + index.toString(radix = 16))
        }
        exceptScreenB.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "B1:" + index.toString(radix = 16))
        }
        exceptScreenA.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "A2:" + index.toString(radix = 16))
        }
        exceptScreenB.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "B2:" + index.toString(radix = 16))
        }
    }

    @Test
    fun testReadBufferMirrorFourScreen() {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns true
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // データ
        val data = (0x2000u..0x3effu).map { ((it + (it shr 8)) * 7u + 11u).toUByte() }
        // 書き込み
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x20u)
        ppu.writePPUAddress(value = 0x00u)
        data.forEach { ppu.writeVideoRAM(value = it) }
        // VRAMチェック（書き込み時のPPU BusのミラーリングやNameTableのミラーリングHorizontalを考慮）
        //// 0x3000 -> 0x2000 のデータ
        val exceptScreenA = data.drop(n = 0x1000).take(n = 0x0400)
        //// 0x3400 -> 0x2400 のデータ
        val exceptScreenB = data.drop(n = 0x1400).take(n = 0x0400)
        //// 0x3800 -> 0x2800 のデータ
        val exceptScreenC = data.drop(n = 0x1800).take(n = 0x0400)
        //// 0x3C00 -> 0x2C00 のデータ（0x0300バイト） + 0x2C00 のラストデータ（0x0100バイト）
        val exceptScreenD = data.drop(n = 0x1C00).take(n = 0x0300) + data.drop(n = 0x0C00 + 0x0300).take(n = 0x0100)
        assertContentEquals(expected = exceptScreenA, actual = videoRAM._nameTable.drop(n = 0x0000).take(n = 0x0400))
        assertContentEquals(expected = exceptScreenB, actual = videoRAM._nameTable.drop(n = 0x0400).take(n = 0x0400))
        assertContentEquals(expected = exceptScreenC, actual = videoRAM._nameTable.drop(n = 0x0800).take(n = 0x0400))
        assertContentEquals(expected = exceptScreenD, actual = videoRAM._nameTable.drop(n = 0x0C00).take(n = 0x0400))
        // 読み込みチェック：一回ずつ遅れて値が返ってくる
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x20u)
        ppu.writePPUAddress(value = 0x00u)
        assertEquals(expected = 0u, actual = ppu.readVideoRAM())
        // チェック（書き込み時のPPU BusのミラーリングやNameTableのミラーリングHorizontalを考慮）
        exceptScreenA.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "A:" + index.toString(radix = 16))
        }
        exceptScreenB.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "B:" + index.toString(radix = 16))
        }
        exceptScreenC.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "C:" + index.toString(radix = 16))
        }
        exceptScreenD.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "D:" + index.toString(radix = 16))
        }
    }

    @Test
    fun testWritePalletTable() {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // ミラーリングもチェック
        ubyteArrayOf(0x00u, 0x20u, 0x40u, 0x60u, 0x80u, 0xa0u, 0xc0u, 0xe0u)
            .forEach { low ->
                // クリア
                videoRAM._palletTable.fill(element = 0xffu)
                // 書き込み
                ppu.readPPUStatus()
                ppu.writePPUAddress(value = 0x3fu)
                ppu.writePPUAddress(value = low)
                (0..31).forEach { ppu.writeVideoRAM(value = (it + 1).toUByte()) }
                // チェック
                assertEquals(expected = 17u, actual = videoRAM._palletTable[0])   // 0x3f00 <- 0x3f10
                assertEquals(expected = 2u, actual = videoRAM._palletTable[1])    // 0x3f01
                assertEquals(expected = 3u, actual = videoRAM._palletTable[2])    // 0x3f02
                assertEquals(expected = 4u, actual = videoRAM._palletTable[3])    // 0x3f03
                assertEquals(expected = 21u, actual = videoRAM._palletTable[4])   // 0x3f04 <- 0x3f14
                assertEquals(expected = 6u, actual = videoRAM._palletTable[5])    // 0x3f05
                assertEquals(expected = 7u, actual = videoRAM._palletTable[6])    // 0x3f06
                assertEquals(expected = 8u, actual = videoRAM._palletTable[7])    // 0x3f07
                assertEquals(expected = 25u, actual = videoRAM._palletTable[8])   // 0x3f08 <- 0x3f18
                assertEquals(expected = 10u, actual = videoRAM._palletTable[9])   // 0x3f09
                assertEquals(expected = 11u, actual = videoRAM._palletTable[10])  // 0x3f0a
                assertEquals(expected = 12u, actual = videoRAM._palletTable[11])  // 0x3f0b
                assertEquals(expected = 29u, actual = videoRAM._palletTable[12])  // 0x3f0c <- 0x3f1c
                assertEquals(expected = 14u, actual = videoRAM._palletTable[13])  // 0x3f0d
                assertEquals(expected = 15u, actual = videoRAM._palletTable[14])  // 0x3f0e
                assertEquals(expected = 16u, actual = videoRAM._palletTable[15])  // 0x3f0f
                assertEquals(expected = 255u, actual = videoRAM._palletTable[16]) // 0x3f10 -> 0x3f00
                assertEquals(expected = 18u, actual = videoRAM._palletTable[17])  // 0x3f11
                assertEquals(expected = 19u, actual = videoRAM._palletTable[18])  // 0x3f12
                assertEquals(expected = 20u, actual = videoRAM._palletTable[19])  // 0x3f13
                assertEquals(expected = 255u, actual = videoRAM._palletTable[20]) // 0x3f14 -> 0x3f04
                assertEquals(expected = 22u, actual = videoRAM._palletTable[21])  // 0x3f15
                assertEquals(expected = 23u, actual = videoRAM._palletTable[22])  // 0x3f16
                assertEquals(expected = 24u, actual = videoRAM._palletTable[23])  // 0x3f17
                assertEquals(expected = 255u, actual = videoRAM._palletTable[24]) // 0x3f18 -> 0x3f08
                assertEquals(expected = 26u, actual = videoRAM._palletTable[25])  // 0x3f19
                assertEquals(expected = 27u, actual = videoRAM._palletTable[26])  // 0x3f1a
                assertEquals(expected = 28u, actual = videoRAM._palletTable[27])  // 0x3f1b
                assertEquals(expected = 255u, actual = videoRAM._palletTable[28]) // 0x3f1c -> 0x3f0c
                assertEquals(expected = 30u, actual = videoRAM._palletTable[29])  // 0x3f1d
                assertEquals(expected = 31u, actual = videoRAM._palletTable[30])  // 0x3f1e
                assertEquals(expected = 32u, actual = videoRAM._palletTable[31])  // 0x3f1f
            }
    }

    @Test
    fun testReadChrRom() {
        val chrRom = UByteArray(size = 0x2000) { (it + (it shr 8)).toUByte() }
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns chrRom.size / 8 / 1024
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = chrRom,
        )
        val interrupter = mock<Interrupter>()
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // 読み込み準備
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x00u)
        ppu.writePPUAddress(value = 0x00u)
        ppu.readVideoRAM() // １つ空読み
        // チェック
        chrRom.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "index=$index")
        }
    }

    @Test
    fun testAccessChrRam() {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // 書き込み
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x00u)
        ppu.writePPUAddress(value = 0x00u)
        val data = UByteArray(size = 0x2000) { (it + (it shr 8)).toUByte() }
        data.forEach { ppu.writeVideoRAM(value = it) }
        // VRAMチェック
        assertContentEquals(expected = data, actual = videoRAM._patternTable)
        // 読み込みチェック
        ppu.readPPUStatus()
        ppu.writePPUAddress(value = 0x00u)
        ppu.writePPUAddress(value = 0x00u)
        ppu.readVideoRAM() // １つ空読み
        data.forEachIndexed { index, value ->
            assertEquals(expected = value, actual = ppu.readVideoRAM(), message = "index=$index")
        }
    }

    @Test
    fun testInterruptNMI() {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        every { interrupter.requestNMI() } returns Unit
        every { interrupter.requestNMI(levelLow = false) } returns Unit
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // NMIコントロール設定
        ppu.writePPUControl(value = 0x80u)
        // NMI割り込み直前まで実行してチェック
        repeat(times = 341 * (240 + 1) + 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        assertFalse(actual = ppuRegisters.ppuStatus.isVerticalBlankHasStarted)
        // NMI割り込みまで実行してチェック
        repeat(times = 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 1)) { interrupter.requestNMI() }
        assertTrue(actual = ppuRegisters.ppuStatus.isVerticalBlankHasStarted)
        // VBlankクリア直前まで実行してチェック
        repeat(times = 341 * 20 - 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        assertTrue(actual = ppuRegisters.ppuStatus.isVerticalBlankHasStarted)
        // VBlankクリアまで実行してチェック
        repeat(times = 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        assertFalse(actual = ppuRegisters.ppuStatus.isVerticalBlankHasStarted)
        // NMIコントロールクリア
        ppu.writePPUControl(value = 0x00u)
        // NMI割り込み直前まで実行してチェック
        repeat(times = 341 - 2 + 341 * (240 + 1) + 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        assertFalse(actual = ppuRegisters.ppuStatus.isVerticalBlankHasStarted)
        // NMI割り込み（フラグのみ）まで実行してチェック
        repeat(times = 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        assertTrue(actual = ppuRegisters.ppuStatus.isVerticalBlankHasStarted)
        // NMIコントロール設定してNMI割り込み実行チェック
        ppu.writePPUControl(value = 0x80u)
        verify(mode = exactly(n = 1)) { interrupter.requestNMI() }
        assertTrue(actual = ppuRegisters.ppuStatus.isVerticalBlankHasStarted)
        // ステータス読み込んでフラグ解除チェック
        ppu.readPPUStatus()
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        assertFalse(actual = ppuRegisters.ppuStatus.isVerticalBlankHasStarted)
    }

    // https://www.nesdev.org/wiki/PPU_frame_timing#Even/Odd_Frames
    @Test
    fun testEvenOddFrameCycle1() {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        every { interrupter.requestNMI() } returns Unit
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // 偶数フレーム／スプライト表示無し／便宜上最初のフレームが偶数とする
        ppu.writePPUControl(value = 0x80u)
        ppu.writePPUMask(value = 0x00u)
        repeat(times = 341 * (240 + 1) + 1) { ppu._executePPUCycleStep() }
        assertFalse(actual = ppu._isOddFrame)
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        repeat(times = 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 1)) { interrupter.requestNMI() }
        // 奇数フレーム／スプライト表示無し
        repeat(times = 341 * 262 - 1) { ppu._executePPUCycleStep() }
        assertTrue(actual = ppu._isOddFrame)
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        repeat(times = 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 1)) { interrupter.requestNMI() }
        // 偶数フレーム／スプライト表示あり／BG表示はmockkで時間が掛かってしまうためやめる
        ppu.writePPUMask(value = 0x10u)
        repeat(times = 341 * 262 - 2) { ppu._executePPUCycleStep() }
        assertFalse(actual = ppu._isOddFrame)
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        repeat(times = 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 1)) { interrupter.requestNMI() }
        // 奇数フレーム／スプライト表示あり（１サイクル分少なくなる）
        repeat(times = 341 * 262 - 1) { ppu._executePPUCycleStep() }
        assertTrue(actual = ppu._isOddFrame)
        verify(mode = exactly(n = 0)) { interrupter.requestNMI() }
        repeat(times = 1) { ppu._executePPUCycleStep() }
        verify(mode = exactly(n = 1)) { interrupter.requestNMI() }
    }

    // 主に 10-even_odd_timing のための確認
    @Test
    fun testEvenOddFrameCycle2() {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        every { interrupter.requestNMI() } returns Unit
        every { interrupter.requestNMI(levelLow = false) } returns Unit
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // スプライト表示無し
        ppu.writePPUControl(value = 0x80u)
        ppu.writePPUMask(value = 0x00u)
        // 偶数フレーム／最後の手前でスプライトON／便宜上最初のフレームが偶数とする
        repeat(times = 341 * 262 - 3) { ppu._executePPUCycleStep() }
        assertEquals(expected = 338, actual = ppu._ppuX)
        assertEquals(expected = 261, actual = ppu._ppuY)
        assertFalse(actual = ppu._isOddFrame, message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}")
        ppu.writePPUMask(value = 0x10u)
        repeat(times = 2) { ppu._executePPUCycleStep() }
        assertEquals(expected = 340, actual = ppu._ppuX)
        assertEquals(expected = 261, actual = ppu._ppuY)
        repeat(times = 1) { ppu._executePPUCycleStep() }
        // 奇数フレーム／スプライト表示中／VBlankフラグチェック
        assertEquals(expected = 0, actual = ppu._ppuX)
        assertEquals(expected = 0, actual = ppu._ppuY)
        assertTrue(actual = ppu._isOddFrame, message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}")
        repeat(times = 341 * (240 + 1)) { ppu._executePPUCycleStep() }
        assertEquals(expected = 0, actual = ppu._ppuX)
        assertEquals(expected = 241, actual = ppu._ppuY)
        assertEquals(
            expected = 0x00.toUByte(),
            actual = ppu.readPPUStatus() and 0x80u,
            message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}",
        )
        repeat(times = 2) { ppu._executePPUCycleStep() }
        assertEquals(expected = 2, actual = ppu._ppuX)
        assertEquals(expected = 241, actual = ppu._ppuY)
        assertEquals(
            expected = 0x80.toUByte(),
            actual = ppu.readPPUStatus() and 0x80u,
            message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}",
        )
        // 奇数フレーム／スプライトOFF
        ppu.writePPUMask(value = 0x00u)
        // 奇数フレーム／最後の後でスプライトON
        repeat(times = 341 * 20 + (341 - 2) - 1) { ppu._executePPUCycleStep() }
        assertEquals(expected = 340, actual = ppu._ppuX)
        assertEquals(expected = 261, actual = ppu._ppuY)
        ppu.writePPUMask(value = 0x10u)
        repeat(times = 1) { ppu._executePPUCycleStep() }
        // 偶数フレーム／スプライト表示中／VBlankフラグチェック
        assertEquals(expected = 0, actual = ppu._ppuX)
        assertEquals(expected = 0, actual = ppu._ppuY)
        assertFalse(actual = ppu._isOddFrame, message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}")
        repeat(times = 341 * (240 + 1)) { ppu._executePPUCycleStep() }
        assertEquals(expected = 0, actual = ppu._ppuX)
        assertEquals(expected = 241, actual = ppu._ppuY)
        assertEquals(
            expected = 0x00.toUByte(),
            actual = ppu.readPPUStatus() and 0x80u,
            message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}",
        )
        repeat(times = 2) { ppu._executePPUCycleStep() }
        assertEquals(expected = 2, actual = ppu._ppuX)
        assertEquals(expected = 241, actual = ppu._ppuY)
        assertEquals(
            expected = 0x80.toUByte(),
            actual = ppu.readPPUStatus() and 0x80u,
            message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}",
        )
    }

    // 主に 10-even_odd_timing のための確認
    // ロジックはざっくりコピー＆BGの代わりにスプライト
    // set_test 2,"Clock is skipped too soon, relative to enabling BG" の代わりのつもり
    @Test
    fun testFor10EvenOddTiming_2() {
        assertEquals(expected = 8, actual = checkFor10EvenOddTiming_2_test(a = 4, x = 0x00u, y = 0x10u))
    }

    // 主に 10-even_odd_timing のための確認
    // ロジックはざっくりコピー＆BGの代わりにスプライト
    // set_test 3,"Clock is skipped too late, relative to enabling BG" の代わりのつもり
    @Test
    fun testFor10EvenOddTiming_3() {
        // TODO: NG・何となくPPU側の動作としてtoo lateになっていない気がする
        assertEquals(expected = 8/*7になる*/, actual = checkFor10EvenOddTiming_2_test(a = 5, x = 0x00u, y = 0x10u))
    }

    // 主に 10-even_odd_timing のための確認
    // ロジックはざっくりコピー＆BGの代わりにスプライト
    // set_test 4,"Clock is skipped too soon, relative to disabling BG" の代わりのつもり
    @Test
    fun testFor10EvenOddTiming_4() {
        assertEquals(expected = 9, actual = checkFor10EvenOddTiming_2_test(a = 4, x = 0x10u, y = 0x00u))
    }

    // 主に 10-even_odd_timing のための確認
    // ロジックはざっくりコピー＆BGの代わりにスプライト
    // set_test 5,"Clock is skipped too late, relative to disabling BG" の代わりのつもり
    @Test
    fun testFor10EvenOddTiming_5() {
        // TODO: NG・何となくPPU側の動作としてtoo lateになっていない気がする
        assertEquals(expected = 7/*8になる*/, actual = checkFor10EvenOddTiming_2_test(a = 5, x = 0x10u, y = 0x00u))
    }

    private fun checkFor10EvenOddTiming_2_test(a: Int, x: UByte, y: UByte): Int {
        val mapper = Mapper000(
            cartridge = mock {
                every { information } returns mock {
                    every { chrRom8Units } returns 0
                    every { ignoreMirroring } returns false
                    every { mirroringVertical } returns false
                }
            },
            prgRom = UByteArray(size = 0),
            chrRom = UByteArray(size = 0),
        )
        val interrupter = mock<Interrupter>()
        every { interrupter.requestNMI() } returns Unit
        every { interrupter.requestNMI(levelLow = false) } returns Unit
        val stateObserver = NothingStateObserver
        val a12 = A12(stateObserver = stateObserver)
        val videoRAM = VideoRAM()
        val ppuRegisters = PPURegisters(a12 = a12)
        val ppuBus = PPUBus(mapper = mapper, videoRAM = videoRAM)
        val ppu = PPU(
            ppuRegisters = ppuRegisters,
            ppuBus = ppuBus,
            interrupter = interrupter,
            a12 = a12,
        )
        // スプライト表示無し
        ppu.writePPUControl(value = 0x80u)
        ppu.writePPUMask(value = 0x00u)
        //repeat(times = 341 * 262) { ppu._executePPUCycleStep() }
        //assertTrue(actual = ppu._isOddFrame)
        assertEquals(expected = 0, actual = ppu._ppuX, message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}")
        assertEquals(expected = 0, actual = ppu._ppuY, message = "ppuX=${ppu._ppuX},ppuY=${ppu._ppuY}")
        //
        val ppuParCpu = NTSC_CPU_CYCLES_PER_MASTER_CLOCKS / NTSC_PPU_CYCLES_PER_MASTER_CLOCKS
        val adjust = 2359
        //    test:   jsr disable_rendering0
        //    jsr sync_vbl_delay
        repeat(times = 341 * 241 + 2 + a + 48/*?*/) { ppu._executePPUCycleStep() }
        Logger.d { "\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame},a=$a,x=${x.toHex()},y=${y.toHex()}" }
        //            delay 13
        repeat(times = (13) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay 13:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    ; $2001=X for most of VBL, Y for part of frame, then 0
        //    stx $2001
        ppu.writePPUMask(value = x)
        Logger.d { "\twritePPUMask(value = ${x.toHex()})" }
        //    delay adjust-4-4
        repeat(times = (adjust - 4 - 4 + 4/*stx $2001*/) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay adjust-4-4:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    sty $2001
        ppu.writePPUMask(value = y)
        Logger.d { "\twritePPUMask(value = ${y.toHex()})" }
        //    delay 20000
        //    lda #0
        repeat(times = (20000 + 4/*sty $2001*/ + 2/*lda #0*/) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay 20000:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    sta $2001
        ppu.writePPUMask(value = 0x00u)
        Logger.d { "\twritePPUMask(value = 0x00u)" }
        //    delay 29781-adjust-4-20000-6
        repeat(times = (29781 - adjust - 4 - 20000 - 6 + 4/*sta $2001*/) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay 29781-adjust-4-20000-6:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    ; Two frames with BG off
        //    delay 29781
        //    delay 29781-1
        repeat(times = (29781) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay 29781:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        repeat(times = (29781 - 1) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay 29781-1:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    ; Third frame same as first. Since clock is skipped every
        //    ; other frame, only one of these two will have the skipped
        //    ; clock, so its effect on later frame timing won't be a
        //    ; problem.
        //    stx $2001
        ppu.writePPUMask(value = x)
        Logger.d { "\twritePPUMask(value = ${x.toHex()})" }
        //    delay adjust-4
        repeat(times = (adjust - 4 + 4/*stx $2001*/) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay adjust-4:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    sty $2001
        ppu.writePPUMask(value = y)
        Logger.d { "\twritePPUMask(value = ${y.toHex()})" }
        //    delay 20000
        //    lda #0
        repeat(times = (20000 + 4/*sty $2001*/ + 2/*lda #0*/) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay 20000:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    sta $2001
        ppu.writePPUMask(value = 0x00u)
        Logger.d { "\twritePPUMask(value = 0x00u)" }
        //    delay 29781-adjust-4-20000-6
        repeat(times = (29781 - adjust - 4 - 20000 - 6 + 4/*sta $2001*/) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay 29781-adjust-4-20000-6:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    ; Find number of PPU clocks until VBL
        //    delay 29781-3-22
        repeat(times = (29781 - 3 - 22) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "delay 29781-3-22:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame}" }
        //    ldx #0
        var n = 0
        repeat(times = (2/*ldx #0*/) * ppuParCpu) { ppu._executePPUCycleStep() }
        Logger.d { "ldx #0:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame},n=$n" }
        Logger.d { "" }
        do {
            //    :    delay 29781-2-4-3
            //    inx
            if (++n >= 256) fail("ppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame},n=$n")
            repeat(times = (29781 - 2 - 4 - 3) * ppuParCpu) { ppu._executePPUCycleStep() }
            repeat(times = (2/*inx*/) * ppuParCpu) { ppu._executePPUCycleStep() }
            Logger.d { "delay 29781-2-4-3:\n\tppuX=${ppu._ppuX},ppuY=${ppu._ppuY},isOddFrame=${ppu._isOddFrame},n=$n" }
            //    bit PPUSTATUS
            val status = ppu.readPPUStatus()
            Logger.d { "\tppu.readPPUStatus() => ${status.toHex()},n=$n" }
            //    bpl :-
            repeat(times = (4/*bit PPUSTATUS*/ + 3/*bpl*/) * ppuParCpu) { ppu._executePPUCycleStep() }
        } while (status and 0x80u == 0.toUByte())
        //
        return n
    }
}
