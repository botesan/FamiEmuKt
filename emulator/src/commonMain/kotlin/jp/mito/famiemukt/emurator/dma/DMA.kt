package jp.mito.famiemukt.emurator.dma

import jp.mito.famiemukt.emurator.cpu.RAM
import jp.mito.famiemukt.emurator.ppu.PPU

class DMA(
    private val ram: RAM,
    private val ppu: PPU,
) {
    private var executedAPUDmaCycles: Int = 0
    private var isExecutedOAMDma: Boolean = false

    fun getAndClearLastDMACycles(currentCycles: Int): Int {
        // TODO: APUの追加CPU cyclesも含めて取得していいのか確認
        val executedAPUDmaCycles = executedAPUDmaCycles
        this.executedAPUDmaCycles = 0
        val executedOAMDmaCycles = if (isExecutedOAMDma.not()) 0 else 513 + (currentCycles and 1)
        isExecutedOAMDma = false
        return executedAPUDmaCycles + executedOAMDmaCycles
    }

    // TODO: APUの追加CPU cyclesの加算方法の確認
    fun addDMCCycles(cycles: Int) {
        executedAPUDmaCycles += cycles
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun copyObjectAttributeMemory(fromAddress: UShort) {
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
        ppu.dmaCopyObjectAttributeMemory { to -> ram.read(address = fromAddress.toInt(), data = to) }
        isExecutedOAMDma = true
    }
}
