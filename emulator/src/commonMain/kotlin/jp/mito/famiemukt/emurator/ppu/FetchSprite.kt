package jp.mito.famiemukt.emurator.ppu

@OptIn(ExperimentalUnsignedTypes::class)
class FetchSprite(private val ppuBus: PPUBus, ppuControl: PPUControl, objectAttributeMemory: UByteArray, no: Int) {
    val index: Int = no * 4
    private val isSprite8x16: Boolean = ppuControl.isSprite8x16

    /* Y position of top of sprite.
        Sprite data is delayed by one scanline; you must subtract 1 from the sprite's Y coordinate before writing it here.
        Hide a sprite by moving it down offscreen, by writing any values between #$EF-#$FF here.
        Sprites are never displayed on the first line of the picture, and it is impossible to place a sprite partially off the top of the screen. */
    val offsetY: Int = (objectAttributeMemory[index] + 1U).toInt()

    /* Tile index number
        For 8x8 sprites, this is the tile number of this sprite within the pattern table selected in bit 3 of PPUCTRL ($2000).
        For 8x16 sprites, the PPU ignores the pattern table selection and selects a pattern table from bit 0 of this number.
        76543210
        ||||||||
        |||||||+- Bank ($0000 or $1000) of tiles
        +++++++-- Tile number of top of sprite (0 to 254; bottom half gets the next tile) */
    private val tile: UByte = objectAttributeMemory[index + 1]
    private val bank: UByte = tile and 0x01U  // 8x16用？
    private val tileNo: Int = if (isSprite8x16) tile.toInt() and 0xFE else tile.toInt()
    val spritePatternTableAddress: Int = when {
        isSprite8x16.not() -> ppuControl.spritePatternTableAddress
        bank == 0.toUByte() -> 0x0000
        else -> 0x1000
    }

    /* Attributes
        76543210
        ||||||||
        ||||||++- Palette (4 to 7) of sprite
        |||+++--- Unimplemented (read 0)
        ||+------ Priority (0: in front of background; 1: behind background)
        |+------- Flip sprite horizontally
        +-------- Flip sprite vertically */
    private val attribute: UByte = objectAttributeMemory[index + 2]
    val paletteH: Int = (attribute and 0x03U).toInt() shl 2
    val isFront: Boolean = attribute and 0b0010_0000U == 0.toUByte()
    private val isFlipHorizontal: Boolean = attribute and 0b0100_0000U != 0.toUByte()
    private val isFlipVertical: Boolean = attribute and 0b1000_0000U != 0.toUByte()

    /* X position of left side of sprite.
        X-scroll values of $F9-FF results in parts of the sprite to be past the right edge of the screen, thus invisible.
        It is not possible to have a sprite partially visible on the left edge.
        Instead, left-clipping through PPUMASK ($2001) can be used to simulate this effect. */
    private val offsetX: Int = objectAttributeMemory[index + 3].toInt()

    // その他
    val spriteHeight: Int = if (ppuControl.isSprite8x16) 16 else 8

    private var fetchingRelativeY: Int = 0
    private var fetchedPatternL: UByte = 0U
    private var fetchedPatternH: UByte = 0U

    fun fetchLinePatternL(y: Int) {
        if (y == 261 + 1) return
        val indexY = y - offsetY
        // 多分、スプライト描画の有効無効を切り替えているのが原因
        // もしくは割り込み実装のタイミングが不正確
        // とりあえず強制終了しないようにしておけば良い？
        // https://www.nesdev.org/wiki/PPU_sprite_evaluation#Rendering_disable_or_enable_during_active_scanline
        // Disabling rendering mid-frame, then re-enabling it on the same frame may have additional corruption,
        // at least on the first scanline it is re-enabled.
        //check(value = indexY in 0 until spriteHeight) { "y out of range. y=$y offsetY=$offsetY spriteHeight=$spriteHeight" }
        if (indexY !in 0 until spriteHeight) return
        val fetchingRelativeY = if (isFlipVertical) spriteHeight - 1 - indexY else indexY
        val address = spritePatternTableAddress +
                (tileNo + (fetchingRelativeY ushr 3)) * PPU.PATTERN_TABLE_ELEMENT_SIZE +
                (fetchingRelativeY and 0x07)
        fetchedPatternL = ppuBus.readMemory(address = address)
        this.fetchingRelativeY = fetchingRelativeY
    }

    fun fetchLinePatternH(y: Int) {
        if (y == 261 + 1) return
        val indexY = y - offsetY
        // 多分、スプライト描画の有効無効を切り替えているのが原因
        // もしくは割り込み実装のタイミングが不正確
        // とりあえず強制終了しないようにしておけば良い？
        // https://www.nesdev.org/wiki/PPU_sprite_evaluation#Rendering_disable_or_enable_during_active_scanline
        // Disabling rendering mid-frame, then re-enabling it on the same frame may have additional corruption,
        // at least on the first scanline it is re-enabled.
        //check(value = indexY in 0 until spriteHeight) { "y out of range. y=$y offsetY=$offsetY spriteHeight=$spriteHeight" }
        if (indexY !in 0 until spriteHeight) return
        val fetchingRelativeY = if (isFlipVertical) spriteHeight - 1 - indexY else indexY
        //check(value = fetchingRelativeY == this.fetchingRelativeY) { "fetchLinePatternH must be called after fetchLinePatternL. fetchingRelativeY=$fetchingRelativeY this.fetchingRelativeY=${this.fetchingRelativeY}" }
        if (fetchingRelativeY != this.fetchingRelativeY) return
        val address = spritePatternTableAddress +
                (tileNo + (fetchingRelativeY ushr 3)) * PPU.PATTERN_TABLE_ELEMENT_SIZE +
                (fetchingRelativeY and 0x07) + PPU.PATTERN_TABLE_ELEMENT_SIZE / 2
        fetchedPatternH = ppuBus.readMemory(address = address)
    }

    fun getColorNo(x: Int): Int? {
        val indexX = x - offsetX
        if (indexX !in 0..7) return null
        val relativeX = if (isFlipHorizontal) 7 - indexX else indexX
        val patternBitPos = 7 - relativeX
        val colorNoL = if (fetchedPatternL.toInt() and (1 shl patternBitPos) != 0) 1 else 0
        val colorNoH = if (fetchedPatternH.toInt() and (1 shl patternBitPos) != 0) 2 else 0
        return colorNoH or colorNoL
    }
}
