package jp.mito.famiemukt.emurator.ppu

import jp.mito.famiemukt.emurator.util.*

/*
https://www.nesdev.org/wiki/PPU_registers
 */
data class PPURegisters(
    val internal: PPUInternalRegister = PPUInternalRegister(),
    val ppuControl: PPUControl = PPUControl(internal),
    val ppuMask: PPUMask = PPUMask(),
    val ppuStatus: PPUStatus = PPUStatus(),
    var oamAddress: UByte = 0U,
    val ppuScroll: PPUScroll = PPUScroll(internal),
    val ppuAddress: PPUAddress = PPUAddress(internal),
)

/*
7  bit  0
---- ----
VPHB SINN
|||| ||||
|||| ||++- Base nametable address
|||| ||    (0 = $2000; 1 = $2400; 2 = $2800; 3 = $2C00)
|||| |+--- VRAM address increment per CPU read/write of PPUDATA
|||| |     (0: add 1, going across; 1: add 32, going down)
|||| +---- Sprite pattern table address for 8x8 sprites
||||       (0: $0000; 1: $1000; ignored in 8x16 mode)
|||+------ Background pattern table address (0: $0000; 1: $1000)
||+------- Sprite size (0: 8x8 pixels; 1: 8x16 pixels – see PPU OAM#Byte 1)
|+-------- PPU master/slave select
|          (0: read backdrop from EXT pins; 1: output color on EXT pins)
+--------- Generate an NMI at the start of the
           vertical blanking interval (0: off; 1: on)

Equivalently, bits 1 and 0 are the most significant bit of the scrolling coordinates (see Nametables and PPUSCROLL):
7  bit  0
---- ----
.... ..YX
       ||
       |+- 1: Add 256 to the X scroll position
       +-- 1: Add 240 to the Y scroll position
 */
class PPUControl(private val internal: PPUInternalRegister) {
    var value: UByte = 0U
        set(value) {
            field = value
            internal.setBaseNameTableAddress(value = value and (BIT_MASK_0 or BIT_MASK_1))
        }
    val incrementSizePPUDATA: UByte get() = if ((value and BIT_MASK_2) == 0.toUByte()) 1U else 32U
    val spritePatternTableAddress: Int get() = if ((value and BIT_MASK_3) == 0.toUByte()) 0x0000 else 0x1000
    val backgroundPatternTableAddress: Int get() = if ((value and BIT_MASK_4) == 0.toUByte()) 0x0000 else 0x1000
    val isSprite8x16: Boolean get() = ((value and BIT_MASK_5) != 0.toUByte())
    private val isOutputColorOnEXTPins: Boolean get() = ((value and BIT_MASK_6) != 0.toUByte())
    val isVerticalBlankingInterval: Boolean get() = ((value and BIT_MASK_7) != 0.toUByte())
    override fun toString(): String =
        """
        |PPUControl(0x${value.toString(16).padStart(2, '0')})
        |${"\t"}tileAddress=0x${internal.tileAddress.toString(16).padStart(4, '0')}
        |${"\t"}incrementSizePPUDATA=$incrementSizePPUDATA
        |${"\t"}spritePatternTableAddress=0x${spritePatternTableAddress.toString(16).padStart(4, '0')}
        |${"\t"}backgroundPatternTableAddress=0x${backgroundPatternTableAddress.toString(16).padStart(4, '0')}
        |${"\t"}isSprite8x16=$isSprite8x16
        |${"\t"}isOutputColorOnEXTPins=$isOutputColorOnEXTPins
        |${"\t"}isVerticalBlankingInterval=$isVerticalBlankingInterval
        |""".trimMargin()
}

/*
7  bit  0
---- ----
BGRs bMmG
|||| ||||
|||| |||+- Greyscale (0: normal color, 1: produce a greyscale display)
|||| ||+-- 1: Show background in leftmost 8 pixels of screen, 0: Hide
|||| |+--- 1: Show sprites in leftmost 8 pixels of screen, 0: Hide
|||| +---- 1: Show background
|||+------ 1: Show sprites
||+------- Emphasize red (green on PAL/Dendy)
|+-------- Emphasize green (red on PAL/Dendy)
+--------- Emphasize blue
 */
data class PPUMask(var value: UByte = 0U) {
    val isGrayscale: Boolean get() = ((value and BIT_MASK_0) != 0.toUByte())
    val isShowBackgroundLeft8Pixels: Boolean get() = ((value and BIT_MASK_1) != 0.toUByte())
    val isShowSpriteLeft8Pixels: Boolean get() = ((value and BIT_MASK_2) != 0.toUByte())
    val isShowBackground: Boolean get() = ((value and BIT_MASK_3) != 0.toUByte())
    val isShowSprite: Boolean get() = ((value and BIT_MASK_4) != 0.toUByte())
    private val isEmphasizeRed: Boolean get() = ((value and BIT_MASK_5) != 0.toUByte())
    private val isEmphasizeGreen: Boolean get() = ((value and BIT_MASK_6) != 0.toUByte())
    private val isEmphasizeBlue: Boolean get() = ((value and BIT_MASK_7) != 0.toUByte())
    override fun toString(): String =
        """
        |PPUMask(0x${value.toString(16).padStart(2, '0')})
        |${"\t"}isGrayscale=$isGrayscale
        |${"\t"}isShowBackgroundLeft8Pixels=$isShowBackgroundLeft8Pixels
        |${"\t"}isShowSpriteLeft8Pixels=$isShowSpriteLeft8Pixels
        |${"\t"}isShowBackground=$isShowBackground
        |${"\t"}isShowSprite=$isShowSprite
        |${"\t"}isEmphasizeRed=$isEmphasizeRed
        |${"\t"}isEmphasizeGreen=$isEmphasizeGreen
        |${"\t"}isEmphasizeBlue=$isEmphasizeBlue
        |""".trimMargin()
}

/*
7  bit  0
---- ----
VSO. ....
|||| ||||
|||+-++++- PPU open bus. Returns stale PPU bus contents.
||+------- Sprite overflow. The intent was for this flag to be set
||         whenever more than eight sprites appear on a scanline, but a
||         hardware bug causes the actual behavior to be more complicated
||         and generate false positives as well as false negatives; see
||         PPU sprite evaluation. This flag is set during sprite
||         evaluation and cleared at dot 1 (the second dot) of the
||         pre-render line.
|+-------- Sprite 0 Hit.  Set when a nonzero pixel of sprite 0 overlaps
|          a nonzero background pixel; cleared at dot 1 of the pre-render
|          line.  Used for raster timing.
+--------- Vertical blank has started (0: not in vblank; 1: in vblank).
           Set at dot 1 of line 241 (the line *after* the post-render
           line); cleared after reading $2002 and at dot 1 of the
           pre-render line.
 */
data class PPUStatus(
    var isSpriteOverflow: Boolean = false,
    var isSprite0Hit: Boolean = false,
    var isVerticalBlankHasStarted: Boolean = false,
) {
    val value: UByte
        get() = (if (isSpriteOverflow) BIT_MASK_5 else 0U) or
                (if (isSprite0Hit) BIT_MASK_6 else 0U) or
                (if (isVerticalBlankHasStarted) BIT_MASK_7 else 0U)
}

data class PPUScroll(val internal: PPUInternalRegister) {
    val tileAddress: Int by internal::tileAddress
    val attributeAddress: Int by internal::attributeAddress
    val coarseX: UByte by internal::coarseX
    val coarseY: UByte by internal::coarseY
    val fineX: UByte by internal::fineX
    val fineY: UByte by internal::fineY
    fun writeIO(value: UByte) = internal.setPPUScroll(value = value)
}

data class PPUAddress(val internal: PPUInternalRegister) {
    var address: UShort by internal::v
    fun writeIO(value: UByte) = internal.setPPUAddress(value = value)
}

/*
https://www.nesdev.org/wiki/PPU_scrolling
PPUInternalRegister
v   Current VRAM address (15 bits)
t   Temporary VRAM address (15 bits); can also be thought of as the address of the top left onscreen tile.
x   Fine X scroll (3 bits)
w   First or second write toggle (1 bit)

The 15 bit registers t and v are composed this way during rendering:
yyy NN YYYYY XXXXX
||| || ||||| +++++-- coarse X scroll
||| || +++++-------- coarse Y scroll
||| ++-------------- nametable select
+++----------------- fine Y scroll
 */
data class PPUInternalRegister(
    var t: UShort = 0U,
    var v: UShort = 0U,
    var x: UByte = 0U,
    var w: Boolean = false,
) {
    // https://www.nesdev.org/wiki/PPU_scrolling
    // tile address      = 0x2000 | (v & 0x0FFF)
    val tileAddress: Int get() = 0x2000 or (v.toInt() and 0b000_11_11111_11111)

    // https://www.nesdev.org/wiki/PPU_scrolling
    // attribute address = 0x23C0 | (v & 0x0C00) | ((v >> 4) & 0x38) | ((v >> 2) & 0x07)
    val attributeAddress: Int
        get() = 0x23C0 or
                (v.toInt() and 0b000_11_00000_00000) or
                ((v.toInt() and 0b000_00_11100_00000) shr 4) or
                ((v.toInt() and 0b000_00_00000_11100) shr 2)

    val coarseX: UByte
        //get() = (v and 0b000_00_00000_11111U).toUByte()
        get() = (v.toInt() and 0b000_00_00000_11111).toUByte()
    val coarseY: UByte
        //get() = ((v and 0b000_00_11111_00000U).toUInt() shr 5).toUByte()
        get() = ((v.toInt() and 0b000_00_11111_00000) shr 5).toUByte()
    val fineX: UByte by ::x
    val fineY: UByte
        //get() = ((v and 0b111_00_00000_00000U).toUInt() shr 12).toUByte()
        get() = ((v.toInt() and 0b111_00_00000_00000) shr 12).toUByte()

    /* https://www.nesdev.org/wiki/PPU_scrolling
       t: ...GH.. ........ <- d: ......GH
          <used elsewhere> <- d: ABCDEF..
       t: ... GH ..... ..... <- d: ......GH
            <used elsewhere> <- d: ABCDEF.. */
    fun setBaseNameTableAddress(value: UByte) {
        t = (t and 0b111_00_11111_11111U) or (value.toUInt() shl 10).toUShort()
    }

    /*
    yyy NN YYYYY XXXXX
    ||| || ||||| +++++-- coarse X scroll
    ||| || +++++-------- coarse Y scroll
    ||| ++-------------- nametable select
    +++----------------- fine Y scroll
     */
    fun setPPUScroll(value: UByte) {
        if (w) {
            // Y
            t = ((t.toUInt() and 0b000_11_00000_11111U) or
                    ((value.toUInt() and 0b0000_0111U) shl 12) or
                    ((value.toUInt() and 0b1111_1000U) shl 2)).toUShort()
            w = false
        } else {
            // X
            x = value and 0b0111U
            t = ((t.toUInt() and 0b111_11_11111_00000U) or (value.toUInt() shr 3)).toUShort()
            w = true
        }
    }

    /* $2006 first write (w is 0)
            t: .CDEFGH ........ <- d: ..CDEFGH
                   <unused>     <- d: AB......
            t: Z...... ........ <- 0 (bit Z is cleared)
            w:                  <- 1
       $2006 second write (w is 1)
            t: ....... ABCDEFGH <- d: ABCDEFGH
            v: <...all bits...> <- t: <...all bits...>
            w:                  <- 0  */
    fun setPPUAddress(value: UByte) {
        if (w) {
            // Low
            t = (t and 0xFF_00U) or value.toUShort()
            v = t
            w = false
        } else {
            // High
            t = ((t and 0x00_FFU) or (value.toUInt() shl 8).toUShort()) and 0x3F_FFU
            w = true
        }
    }

    /*
      https://www.nesdev.org/wiki/PPU_scrolling
      At dot 257 of each scanline
      If rendering is enabled, the PPU copies all bits related to horizontal position from t to v:
      v: ....A.. ...BCDEF <- t: ....A.. ...BCDEF
      v: ... .A ..... BCDEF <- t: ... .A ..... BCDEF
         yyy NN YYYYY XXXXX       yyy NN YYYYY XXXXX
     */
    fun updateVForDot257OfEachScanline() {
        v = /**/(v and 0b111_10_11111_00000U) or
                (t and 0b000_01_00000_11111U)
    }

    /*
      https://www.nesdev.org/wiki/PPU_scrolling
      During dots 280 to 304 of the pre-render scanline (end of vblank)
      If rendering is enabled, at the end of vblank, shortly after the horizontal bits are copied from t to v at dot 257,
      the PPU will repeatedly copy the vertical bits from t to v from dots 280 to 304, completing the full initialization of v from t:
      v: GHIA.BC DEF..... <- t: GHIA.BC DEF.....
      v: GHI A. BCDEF ..... <- t: GHI A. BCDEF .....
         yyy NN YYYYY XXXXX       yyy NN YYYYY XXXXX
     */
    fun updateVForDot280To304OfPreRenderScanline() {
        v = /**/(v and 0b000_01_00000_11111U) or
                (t and 0b111_10_11111_00000U)
    }

    /*
      https://www.nesdev.org/wiki/PPU_scrolling#Wrapping_around
        Coarse X increment
        The coarse X component of v needs to be incremented when the next tile is reached.
        Bits 0-4 are incremented, with overflow toggling bit 10.
        This means that bits 0-4 count from 0 to 31 across a single nametable, and bit 10 selects the current nametable horizontally.
        if ((v & 0x001F) == 31) // if coarse X == 31
          v &= ~0x001F          // coarse X = 0
          v ^= 0x0400           // switch horizontal nametable
        else
          v += 1                // increment coarse X
     */
    fun incrementCoarseX() {
        if ((v and 0x001FU) == 31.toUShort()) {
            v = v and 0x001F.toUShort().inv()
            v = v xor 0x0400.toUShort()
        } else {
            v++
        }
    }

    /*
      https://www.nesdev.org/wiki/PPU_scrolling#Wrapping_around
      Y increment
      If rendering is enabled, fine Y is incremented at dot 256 of each scanline, overflowing to coarse Y,
      and finally adjusted to wrap among the nametables vertically.
      Bits 12-14 are fine Y. Bits 5-9 are coarse Y. Bit 11 selects the vertical nametable.
      if ((v & 0x7000) != 0x7000)        // if fine Y < 7
          v += 0x1000                      // increment fine Y
      else
          v &= ~0x7000                     // fine Y = 0
          int y = (v & 0x03E0) >> 5        // let y = coarse Y
          if (y == 29)
              y = 0                          // coarse Y = 0
              v ^= 0x0800                    // switch vertical nametable
          else if (y == 31)
              y = 0                          // coarse Y = 0, nametable not switched
          else
              y += 1                         // increment coarse Y
          v = (v & ~0x03E0) | (y << 5)     // put coarse Y back into v
     */
    fun incrementY() {
        if (v and 0x7000U != 0x7000.toUShort()) {
            v = (v + 0x1000U).toUShort()
        } else {
            v = v and 0x7000.toUShort().inv()
            val y = (v.toUInt() and 0x03E0U) shr 5
            v = when (y) {
                29U -> ((v xor 0x0800U) and 0x03E0.toUShort().inv())
                31U -> (v and 0x03E0.toUShort().inv())
                else -> (v and 0x03E0.toUShort().inv()) or ((y + 1U) shl 5).toUShort()
            }
        }
    }
}
