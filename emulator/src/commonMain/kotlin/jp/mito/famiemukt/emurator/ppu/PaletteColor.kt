package jp.mito.famiemukt.emurator.ppu

data class PaletteColor(val r: UByte, val g: UByte, val b: UByte) {
    val rgb32: Int = (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
}

// https://www.nesdev.org/wiki/PPU_registers#PPUMASK
// Bit 0 controls a greyscale mode, which causes the palette to use only the colors from the grey column: $00, $10, $20, $30.
// This is implemented as a bitwise AND with $30 on any value read from PPU $3F00-$3FFF,
fun convertPaletteToRGB32(palette: UByte, ppuMask: PPUMask): Int =
    when {
        ppuMask.isGrayscale -> palette and 0x30U
        else -> palette
    }.let {
        ((ppuMask.value.toInt() and 0b1110_0000) shl 1) or it.toInt()
    }.let {
        FullPaletteColors[it].rgb32
    }

// BGRs bMmG
// https://www.nesdev.org/wiki/Colour_emphasis
// https://www.nesdev.org/wiki/NTSC_video#Color_Tint_Bits
// The terminated measurements above suggest that resulting attenuated absolute voltage is approximately 0.81 times
// the un-attenuated absolute voltage.
private val FullPaletteColors = listOf(
    // Normal
    paletteColors.toList(),
    // __R
    paletteColors.map { it.copy(g = (it.g * 4U / 5U).toUByte(), b = (it.b * 4U / 5U).toUByte()) },
    // _G_
    paletteColors.map { it.copy(r = (it.r * 4U / 5U).toUByte(), b = (it.b * 4U / 5U).toUByte()) },
    // _GR
    paletteColors.map {
        it.copy(r = (it.r * 4U / 5U).toUByte(), g = (it.g * 4U / 5U).toUByte(), b = (it.b * 16U / 25U).toUByte())
    },
    // B__
    paletteColors.map { it.copy(r = (it.r * 4U / 5U).toUByte(), g = (it.g * 4U / 5U).toUByte()) },
    // B_R
    paletteColors.map {
        it.copy(r = (it.r * 4U / 5U).toUByte(), g = (it.g * 16U / 25U).toUByte(), b = (it.b * 4U / 5U).toUByte())
    },
    // BG_
    paletteColors.map {
        it.copy(r = (it.r * 16U / 25U).toUByte(), g = (it.g * 4U / 5U).toUByte(), b = (it.b * 4U / 5U).toUByte())
    },
    // BGR
    paletteColors.map {
        it.copy(r = (it.r * 16U / 25U).toUByte(), g = (it.g * 16U / 25U).toUByte(), b = (it.b * 16U / 25U).toUByte())
    },
).flatten().toTypedArray()

private val paletteColors: Array<PaletteColor>
    get() = rgbValues.map { (r, g, b) -> PaletteColor(r.toUByte(), g.toUByte(), b.toUByte()) }.toTypedArray()

// https://www.nesdev.org/wiki/PPU_palettes に記載の例とは値が異なる
// https://github.com/ymduu/EmotionalEngine-NES/blob/main/Programs/src/Nes.cpp
// https://qiita.com/bokuweb/items/1575337bef44ae82f4d3#%E3%83%91%E3%83%AC%E3%83%83%E3%83%88
private val rgbValues
    get() = arrayOf(
        intArrayOf(0x80, 0x80, 0x80),
        intArrayOf(0x00, 0x3D, 0xA6),
        intArrayOf(0x00, 0x12, 0xB0),
        intArrayOf(0x44, 0x00, 0x96),
        intArrayOf(0xA1, 0x00, 0x5E),
        intArrayOf(0xC7, 0x00, 0x28),
        intArrayOf(0xBA, 0x06, 0x00),
        intArrayOf(0x8C, 0x17, 0x00),
        intArrayOf(0x5C, 0x2F, 0x00),
        intArrayOf(0x10, 0x45, 0x00),
        intArrayOf(0x05, 0x4A, 0x00),
        intArrayOf(0x00, 0x47, 0x2E),
        intArrayOf(0x00, 0x41, 0x66),
        intArrayOf(0x00, 0x00, 0x00),
        intArrayOf(0x05, 0x05, 0x05),
        intArrayOf(0x05, 0x05, 0x05),
        intArrayOf(0xC7, 0xC7, 0xC7),
        intArrayOf(0x00, 0x77, 0xFF),
        intArrayOf(0x21, 0x55, 0xFF),
        intArrayOf(0x82, 0x37, 0xFA),
        intArrayOf(0xEB, 0x2F, 0xB5),
        intArrayOf(0xFF, 0x29, 0x50),
        intArrayOf(0xFF, 0x22, 0x00),
        intArrayOf(0xD6, 0x32, 0x00),
        intArrayOf(0xC4, 0x62, 0x00),
        intArrayOf(0x35, 0x80, 0x00),
        intArrayOf(0x05, 0x8F, 0x00),
        intArrayOf(0x00, 0x8A, 0x55),
        intArrayOf(0x00, 0x99, 0xCC),
        intArrayOf(0x21, 0x21, 0x21),
        intArrayOf(0x09, 0x09, 0x09),
        intArrayOf(0x09, 0x09, 0x09),
        intArrayOf(0xFF, 0xFF, 0xFF),
        intArrayOf(0x0F, 0xD7, 0xFF),
        intArrayOf(0x69, 0xA2, 0xFF),
        intArrayOf(0xD4, 0x80, 0xFF),
        intArrayOf(0xFF, 0x45, 0xF3),
        intArrayOf(0xFF, 0x61, 0x8B),
        intArrayOf(0xFF, 0x88, 0x33),
        intArrayOf(0xFF, 0x9C, 0x12),
        intArrayOf(0xFA, 0xBC, 0x20),
        intArrayOf(0x9F, 0xE3, 0x0E),
        intArrayOf(0x2B, 0xF0, 0x35),
        intArrayOf(0x0C, 0xF0, 0xA4),
        intArrayOf(0x05, 0xFB, 0xFF),
        intArrayOf(0x5E, 0x5E, 0x5E),
        intArrayOf(0x0D, 0x0D, 0x0D),
        intArrayOf(0x0D, 0x0D, 0x0D),
        intArrayOf(0xFF, 0xFF, 0xFF),
        intArrayOf(0xA6, 0xFC, 0xFF),
        intArrayOf(0xB3, 0xEC, 0xFF),
        intArrayOf(0xDA, 0xAB, 0xEB),
        intArrayOf(0xFF, 0xA8, 0xF9),
        intArrayOf(0xFF, 0xAB, 0xB3),
        intArrayOf(0xFF, 0xD2, 0xB0),
        intArrayOf(0xFF, 0xEF, 0xA6),
        intArrayOf(0xFF, 0xF7, 0x9C),
        intArrayOf(0xD7, 0xE8, 0x95),
        intArrayOf(0xA6, 0xED, 0xAF),
        intArrayOf(0xA2, 0xF2, 0xDA),
        intArrayOf(0x99, 0xFF, 0xFC),
        intArrayOf(0xDD, 0xDD, 0xDD),
        intArrayOf(0x11, 0x11, 0x11),
        intArrayOf(0x11, 0x11, 0x11),
    )
