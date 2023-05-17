package jp.mito.famiemukt.emurator

// https://www.nesdev.org/wiki/Cycle_reference_chart

const val NTSC_PPU_CYCLES_PER_MASTER_CLOCKS: Int = 4
const val NTSC_CPU_CYCLES_PER_MASTER_CLOCKS: Int = 3 * NTSC_PPU_CYCLES_PER_MASTER_CLOCKS

const val HTSC_MASTER_CLOCKS_HZ: Int = ((236_250_000L * 10 / 11 + 5) / 10).toInt()
const val NTSC_CPU_CLOCKS_HZ: Int = (HTSC_MASTER_CLOCKS_HZ * 10 + 5) / NTSC_CPU_CYCLES_PER_MASTER_CLOCKS / 10
