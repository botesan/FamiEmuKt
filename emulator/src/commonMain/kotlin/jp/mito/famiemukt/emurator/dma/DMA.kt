package jp.mito.famiemukt.emurator.dma

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.ppu.PPU
import jp.mito.famiemukt.emurator.ppu.PPURegisters

class DMA(private val ppu: PPU, private val ppuRegisters: PPURegisters) {
    fun executeCPUCycleStepIfNeed(cpuBus: CPUBus, currentCycles: Int): Boolean {
        // コピー
        if (copyObjectAttributeMemoryRemainCount > 256) {
            // first cycle wait
            copyObjectAttributeMemoryRemainCount--
            return true
        }
        if (copyObjectAttributeMemoryRemainCount > 0) {
            if (copyObjectAttributeMemoryReadValue == -1) {
                if (currentCycles and 0x1 == 0) return true
                // read
                copyObjectAttributeMemoryReadValue = cpuBus.readMemIO(copyObjectAttributeMemoryFromAddress).toInt()
                copyObjectAttributeMemoryFromAddress++
            } else {
                if (currentCycles and 0x1 != 0) return true
                // write
                ppu.writeObjectAttributeMemory(copyObjectAttributeMemoryReadValue.toUByte())
                copyObjectAttributeMemoryReadValue = -1
                copyObjectAttributeMemoryRemainCount--
            }
            return true
        }
        // TODO: APUの追加CPU cyclesの待ちの動作修正？
        if (executedAPUDmaCycles > 0) {
            executedAPUDmaCycles--
            return true
        }
        return false
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

    private var executedAPUDmaCycles: Int = 0

    // TODO: APUの追加CPU cyclesの加算方法の確認
    fun addDMCCycles(cycles: Int) {
        executedAPUDmaCycles += cycles
    }
}
