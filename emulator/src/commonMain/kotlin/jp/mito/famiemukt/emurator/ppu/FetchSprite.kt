package jp.mito.famiemukt.emurator.ppu

import jp.mito.famiemukt.emurator.util.Poolable

@OptIn(ExperimentalUnsignedTypes::class)
interface FetchSprite : Poolable<FetchSprite> {
    val index: Int
    val offsetX: Int
    val offsetY: Int
    val spritePatternTableAddress: Int
    val paletteH: Int
    val isFront: Boolean
    val spriteHeight: Int
    fun fetchLinePatternL(ppuBus: PPUBus, y: Int)
    fun fetchLinePatternH(ppuBus: PPUBus, y: Int)
    fun getColorNo(x: Int): Int

    companion object {
        val poolSize: Int by FetchSpriteImpl::poolSize
        fun obtain(ppuControl: PPUControl, objectAttributeMemory: UByteArray, no: Int): FetchSprite =
            FetchSpriteImpl.obtain(ppuControl, objectAttributeMemory, no)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
private class FetchSpriteImpl(
    ppuControl: PPUControl,
    objectAttributeMemory: UByteArray,
    no: Int,
) : FetchSprite {
    override var index: Int = -1
    override var offsetX: Int = -1
    override var offsetY: Int = -1
    override var spritePatternTableAddress: Int = -1
    override var paletteH: Int = -1
    override var isFront: Boolean = false
    override var spriteHeight: Int = -1
    private var tileNo: Int = -1
    private var isFlipHorizontal: Boolean = false
    private var isFlipVertical: Boolean = false

    private fun setInitialValues(ppuControl: PPUControl, objectAttributeMemory: UByteArray, no: Int) {
        val index = no * 4
        this.index = index
        /*  Y position of top of sprite.
            Sprite data is delayed by one scanline; you must subtract 1 from the sprite's Y coordinate before writing it here.
            Hide a sprite by moving it down offscreen, by writing any values between #$EF-#$FF here.
            Sprites are never displayed on the first line of the picture, and it is impossible to place a sprite partially off
            the top of the screen. */
        offsetY = objectAttributeMemory[index].toInt() + 1
        /* Tile index number
            For 8x8 sprites, this is the tile number of this sprite within the pattern table selected in bit 3 of PPUCTRL ($2000).
            For 8x16 sprites, the PPU ignores the pattern table selection and selects a pattern table from bit 0 of this number.
            76543210
            ||||||||
            |||||||+- Bank ($0000 or $1000) of tiles
            +++++++-- Tile number of top of sprite (0 to 254; bottom half gets the next tile) */
        val tile = objectAttributeMemory[index + 1].toInt()
        val bank = tile and 0x01  // 8x16用？
        val isSprite8x16 = ppuControl.isSprite8x16
        tileNo = if (isSprite8x16) tile and 0xFE else tile
        spritePatternTableAddress = when {
            isSprite8x16.not() -> ppuControl.spritePatternTableAddress
            bank == 0 -> 0x0000
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
        val attribute = objectAttributeMemory.asByteArray()[index + 2].toInt()
        paletteH = (attribute and 0b11) shl 2
        isFront = attribute and 0b0010_0000 == 0
        isFlipHorizontal = attribute and 0b0100_0000 != 0
        isFlipVertical = attribute and 0b1000_0000 != 0
        /* X position of left side of sprite.
            X-scroll values of $F9-FF results in parts of the sprite to be past the right edge of the screen, thus invisible.
            It is not possible to have a sprite partially visible on the left edge.
            Instead, left-clipping through PPUMASK ($2001) can be used to simulate this effect. */
        offsetX = objectAttributeMemory[index + 3].toInt()
        // その他
        spriteHeight = if (isSprite8x16) 16 else 8
    }

    private var fetchingRelativeY: Int = 0
    private var fetchedPatternL: UByte = 0U
    private var fetchedPatternH: UByte = 0U

    override fun fetchLinePatternL(ppuBus: PPUBus, y: Int) {
        if (y == 261 + 1) return
        val indexY = y - offsetY
        // 多分、スプライト描画の有効無効を切り替えているのが原因
        // もしくは割り込み実装のタイミングが不正確
        // とりあえず強制終了しないようにしておけば良い？
        // https://www.nesdev.org/wiki/PPU_sprite_evaluation#Rendering_disable_or_enable_during_active_scanline
        // Disabling rendering mid-frame, then re-enabling it on the same frame may have additional corruption,
        // at least on the first scanline it is re-enabled.
        //check(value = indexY in 0 until spriteHeight) { "y out of range. y=$y offsetY=$offsetY spriteHeight=$spriteHeight" }
        @Suppress("EmptyRange")
        if (indexY !in 0..spriteHeight) return
        val fetchingRelativeY = if (isFlipVertical) spriteHeight - 1 - indexY else indexY
        val address = spritePatternTableAddress +
                (tileNo + (fetchingRelativeY ushr 3)) * PPU.PATTERN_TABLE_ELEMENT_SIZE +
                (fetchingRelativeY and 0x07)
        fetchedPatternL = ppuBus.readMemory(address = address)
        this.fetchingRelativeY = fetchingRelativeY
    }

    override fun fetchLinePatternH(ppuBus: PPUBus, y: Int) {
        if (y == 261 + 1) return
        val indexY = y - offsetY
        // 多分、スプライト描画の有効無効を切り替えているのが原因
        // もしくは割り込み実装のタイミングが不正確
        // とりあえず強制終了しないようにしておけば良い？
        // https://www.nesdev.org/wiki/PPU_sprite_evaluation#Rendering_disable_or_enable_during_active_scanline
        // Disabling rendering mid-frame, then re-enabling it on the same frame may have additional corruption,
        // at least on the first scanline it is re-enabled.
        //check(value = indexY in 0 until spriteHeight) { "y out of range. y=$y offsetY=$offsetY spriteHeight=$spriteHeight" }
        @Suppress("EmptyRange")
        if (indexY !in 0..spriteHeight) return
        val fetchingRelativeY = if (isFlipVertical) spriteHeight - 1 - indexY else indexY
        //check(value = fetchingRelativeY == this.fetchingRelativeY) { "fetchLinePatternH must be called after fetchLinePatternL. fetchingRelativeY=$fetchingRelativeY this.fetchingRelativeY=${this.fetchingRelativeY}" }
        if (fetchingRelativeY != this.fetchingRelativeY) return
        val address = spritePatternTableAddress +
                (tileNo + (fetchingRelativeY ushr 3)) * PPU.PATTERN_TABLE_ELEMENT_SIZE +
                (fetchingRelativeY and 0x07) + PPU.PATTERN_TABLE_ELEMENT_SIZE / 2
        fetchedPatternH = ppuBus.readMemory(address = address)
    }

    override fun getColorNo(x: Int): Int {
        val indexX = x - offsetX
        if (indexX !in 0..7) return Int.MIN_VALUE
        //val relativeX = if (isFlipHorizontal) 7 - indexX else indexX
        //val patternBitPos = 7 - relativeX
        val patternBitPos = if (isFlipHorizontal) indexX else 7 - indexX
        //val colorNoL = if (fetchedPatternL.toInt() and (1 shl patternBitPos) != 0) 1 else 0
        //val colorNoH = if (fetchedPatternH.toInt() and (1 shl patternBitPos) != 0) 2 else 0
        //return colorNoH or colorNoL
        val colorNoL = (fetchedPatternL.toInt() shr patternBitPos) and 1
        val colorNoH = (fetchedPatternH.toInt() shr patternBitPos) and 1
        return (colorNoH shl 1) or colorNoL
    }

    override fun recycle() {
//        index = -1
//        offsetX = -1
//        offsetY = -1
//        spritePatternTableAddress = -1
//        paletteH = -1
//        spriteHeight = -1
//        tileNo = -1
//        fetchingRelativeY = 0
//        fetchedPatternL = 0U
//        fetchedPatternH = 0U
        pool.recycle(obj = this)
    }

    init {
        setInitialValues(
            ppuControl = ppuControl,
            objectAttributeMemory = objectAttributeMemory,
            no = no,
        )
    }

    companion object {
        private val pool: Poolable.ObjectPool<FetchSpriteImpl> = Poolable.ObjectPool(initialCapacity = 64)
        val poolSize: Int get() = pool.size
        fun obtain(ppuControl: PPUControl, objectAttributeMemory: UByteArray, no: Int): FetchSprite =
            pool.obtain()?.also {
                it.setInitialValues(
                    ppuControl = ppuControl,
                    objectAttributeMemory = objectAttributeMemory,
                    no = no,
                )
            } ?: FetchSpriteImpl(
                ppuControl = ppuControl,
                objectAttributeMemory = objectAttributeMemory,
                no = no,
            )
    }
}
