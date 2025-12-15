package jp.mito.famiemukt.emurator.dma

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.ppu.PPU
import jp.mito.famiemukt.emurator.ppu.PPURegisters
import kotlin.properties.Delegates

class DMA(private val ppu: PPU, private val ppuRegisters: PPURegisters) {
    fun executeCPUCycleStepIfNeed(cpuBus: CPUBus, currentCPUCycles: Int): Boolean {
        val copyToDMC = copyToDMC
        when {
            // https://www.nesdev.org/wiki/APU_DMC#Memory_reader
            // 以下は記述が古い（もう書き換えられている）
            // 4 cycles if it falls on a CPU read cycle.
            // 3 cycles if it falls on a single CPU write cycle (or the second write of a double CPU write).
            // 4 cycles if it falls on the first write of a double CPU write cycle.[4]
            // 2 cycles if it occurs during an OAM DMA, or on the $4014 write cycle that triggers the OAM DMA.
            // 1 cycle if it occurs on the second-last OAM DMA cycle.
            // 3 cycles if it occurs on the last OAM DMA cycle.
            // https://www.nesdev.org/wiki/DMA#DMC_DMA
            // https://www.nesdev.org/wiki/DMA#DMC_DMA_during_OAM_DMA
            // DMC 処理（OAM より優先）
            copyToDMC != null -> {
                if (copyDMCRemainCount < 0) {
                    // TODO: 完全ではない（開始の遅延関連とか？）
                    copyDMCRemainCount = when (copyObjectAttributeMemoryRemainCount) {
                        0 -> if (isCopyDMCReload) {
                            // 偶数が put cycle / 奇数が read cycle とする
                            if (currentCPUCycles and 0x1 == 0x00) 4 else 3
                        } else {
                            // 偶数が put cycle / 奇数が read cycle とする
                            if (currentCPUCycles and 0x1 == 0x00) 3 else 4
                        }
                        // OAMと競合した場合
                        1 -> 3
                        2 -> 1
                        else -> 2
                    }
                }
                if (--copyDMCRemainCount <= 0) {
                    val value = cpuBus.readMemIO(copyDMCAddress)
                    copyToDMC(value)
                    this.copyToDMC = null
                }
                return true
            }
            // OAM 待ち
            copyObjectAttributeMemoryRemainCount > 256 -> {
                // 1 wait state cycle while waiting for writes to complete,
                // +1 if on a put cycle,
                // then 256 alternating get/put cycles.
                // See DMA for more information.
                // 偶数が put cycle / 奇数が read cycle とする
                if (currentCPUCycles and 0x1 == 0x00) {
                    copyObjectAttributeMemoryRemainCount--
                }
                return true
            }
            // OAM コピー
            copyObjectAttributeMemoryRemainCount > 0 -> {
                if (copyObjectAttributeMemoryReadValue == -1) {
                    // 偶数が put cycle / 奇数が read cycle とする
                    if (currentCPUCycles and 0x1 != 0x01) return true
                    // read
                    copyObjectAttributeMemoryReadValue = cpuBus.readMemIO(copyObjectAttributeMemoryFromAddress).toInt()
                    copyObjectAttributeMemoryFromAddress++
                } else {
                    // 偶数が put cycle / 奇数が read cycle とする
                    if (currentCPUCycles and 0x1 != 0x00) return true
                    // write
                    ppu.writeObjectAttributeMemory(copyObjectAttributeMemoryReadValue.toUByte())
                    copyObjectAttributeMemoryReadValue = -1
                    copyObjectAttributeMemoryRemainCount--
                }
                return true
            }
            // 処理無し
            else -> {
                return false
            }
        }
    }

    /*
      OAM DMA ($4014) > write
      Common name: OAMDMA
      Description: OAM DMA register (high byte)
      Access: write
      This port is located on the CPU. Writing $XX will upload 256 bytes of data from CPU page $XX00–$XXFF to the internal PPU OAM.
      This page is typically located in internal RAM, commonly $0200–$02FF, but cartridge RAM or ROM can be used as well.

      The CPU is suspended during the transfer, which will take 513 or 514 cycles after the $4014 write tick.
      (1 wait state cycle while waiting for writes to complete, +1 if on a put cycle, then 256 alternating get/put cycles.
      See DMA for more information.)
      The OAM DMA is the only effective method for initializing all 256 bytes of OAM.
      Because of the decay of OAM's dynamic RAM when rendering is disabled,
      the initialization should take place within vblank. Writes through OAMDATA are generally too slow for this task.
      The DMA transfer will begin at the current OAM write address.
      It is common practice to initialize it to 0 with a write to OAMADDR before the DMA transfer.
      Different starting addresses can be used for a simple OAM cycling technique, to alleviate sprite priority conflicts by flickering.
      If using this technique, after the DMA OAMADDR should be set to 0 before the end of vblank to prevent potential OAM corruption (see errata).
      However, due to OAMADDR writes also having a "corruption" effect,[4] this technique is not recommended.
     */
    private var copyObjectAttributeMemoryFromAddress: Int = 0
    private var copyObjectAttributeMemoryRemainCount: Int = 0
    private var copyObjectAttributeMemoryReadValue: Int = -1

    @OptIn(ExperimentalUnsignedTypes::class)
    fun copyObjectAttributeMemory(fromAddress: UShort) {
        // とりあえず0以外は実行できないようにする
        check(value = ppuRegisters.oamAddress == 0.toUByte())
        copyObjectAttributeMemoryFromAddress = fromAddress.toInt()
        copyObjectAttributeMemoryRemainCount = 257
    }

    // https://www.nesdev.org/wiki/APU_DMC#Memory_reader
    // https://www.nesdev.org/wiki/DMA#DMC_DMA
    // https://www.nesdev.org/wiki/DMA#DMC_DMA_during_OAM_DMA
    private var copyToDMC: ((UByte) -> Unit)? by Delegates.observable(initialValue = null) { _, oldValue, newValue ->
        if (oldValue != null && newValue != null) {
            error("copyToDMC: Illegal set new value. Can not duplicate request.")
        }
    }
    private var copyDMCAddress: Int = 0
    private var isCopyDMCReload: Boolean = false
    private var copyDMCRemainCount: Int = -1

    fun copyDMCSampleBuffer(address: Int, isReload: Boolean, copyTo: (UByte) -> Unit) {
        copyToDMC = copyTo
        copyDMCAddress = address
        isCopyDMCReload = isReload
        copyDMCRemainCount = -1
    }
}
