package jp.mito.famiemukt.emurator.ppu

import jp.mito.famiemukt.emurator.cartridge.A12
import jp.mito.famiemukt.emurator.util.*

/*
https://www.nesdev.org/wiki/PPU_registers
 */
data class PPURegisters(
    val internal: PPUInternalRegister,
    val ppuControl: PPUControl,
    val ppuMask: PPUMask,
    val ppuStatus: PPUStatus,
    var oamAddress: UByte,
    val ppuScroll: PPUScroll,
    val ppuAddress: PPUAddress,
) {
    companion object {
        operator fun invoke(
            a12: A12,
            internal: PPUInternalRegister = PPUInternalRegister(a12 = a12),
            ppuControl: PPUControl = PPUControl(internal),
            ppuMask: PPUMask = PPUMask(),
            ppuStatus: PPUStatus = PPUStatus(),
            oamAddress: UByte = 0U,
            ppuScroll: PPUScroll = PPUScroll(internal),
            ppuAddress: PPUAddress = PPUAddress(internal),
        ): PPURegisters = PPURegisters(
            internal = internal,
            ppuControl = ppuControl,
            ppuMask = ppuMask,
            ppuStatus = ppuStatus,
            oamAddress = oamAddress,
            ppuScroll = ppuScroll,
            ppuAddress = ppuAddress,
        )
    }
}

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
            internal.setBaseNameTableAddress(value = (value.toByte().toInt() and (BIT_MASK_0 or BIT_MASK_1)).toUByte())
        }
    val incrementSizePPUDATA: UByte get() = if (value.isBit(bitMask = BIT_MASK_2).not()) 1U else 32U
    val spritePatternTableAddress: Int get() = if (value.isBit(bitMask = BIT_MASK_3).not()) 0x0000 else 0x1000
    val backgroundPatternTableAddress: Int get() = if (value.isBit(bitMask = BIT_MASK_4).not()) 0x0000 else 0x1000
    val isSprite8x16: Boolean get() = value.isBit(bitMask = BIT_MASK_5)
    private val isOutputColorOnEXTPins: Boolean get() = value.isBit(bitMask = BIT_MASK_6)
    val isVerticalBlankingInterval: Boolean get() = value.isBit(bitMask = BIT_MASK_7)
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
    val isGrayscale: Boolean get() = value.isBit(bitMask = BIT_MASK_0)
    val isShowBackgroundLeft8Pixels: Boolean get() = value.isBit(bitMask = BIT_MASK_1)
    val isShowSpriteLeft8Pixels: Boolean get() = value.isBit(bitMask = BIT_MASK_2)
    val isShowBackground: Boolean get() = value.isBit(bitMask = BIT_MASK_3)
    val isShowSprite: Boolean get() = value.isBit(bitMask = BIT_MASK_4)
    val isEmphasizeRed: Boolean get() = value.isBit(bitMask = BIT_MASK_5)
    val isEmphasizeGreen: Boolean get() = value.isBit(bitMask = BIT_MASK_6)
    val isEmphasizeBlue: Boolean get() = value.isBit(bitMask = BIT_MASK_7)
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
        get() = ((if (isSpriteOverflow) BIT_MASK_5 else 0) or
                (if (isSprite0Hit) BIT_MASK_6 else 0) or
                (if (isVerticalBlankHasStarted) BIT_MASK_7 else 0)).toUByte()
}

data class PPUScroll(val internal: PPUInternalRegister) {
    val tileAddress: Int by internal::tileAddress
    val attributeAddress: Int by internal::attributeAddress
    val coarseX: Int by internal::coarseX
    val coarseY: Int by internal::coarseY
    val fineX: Int by internal::fineX
    val fineY: Int by internal::fineY
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
class PPUInternalRegister(
    var v: UShort = 0U,
    var t: UShort = 0u,
    var x: UByte = 0U,
    var w: Boolean = false,
    val a12: A12,
) {
    override fun toString(): String =
        "PPUInternalRegister(t=${t.toString(radix = 2)},v=${v.toString(radix = 2)},x=${x.toString(radix = 2)},w=$w)"

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

    val coarseX: Int
        //get() = (v and 0b000_00_00000_11111U).toUByte()
        get() = v.toInt() and 0b000_00_00000_11111
    val coarseY: Int
        //get() = ((v and 0b000_00_11111_00000U).toUInt() shr 5).toUByte()
        get() = (v.toInt() and 0b000_00_11111_00000) shr 5
    val fineX: Int
        get() = x.toInt()
    val fineY: Int
        //get() = ((v and 0b111_00_00000_00000U).toUInt() shr 12).toUByte()
        get() = (v.toInt() and 0b111_00_00000_00000) shr 12

    /* https://www.nesdev.org/wiki/PPU_scrolling
       t: ...GH.. ........ <- d: ......GH
          <used elsewhere> <- d: ABCDEF..
       t: ... GH ..... ..... <- d: ......GH
            <used elsewhere> <- d: ABCDEF.. */
    fun setBaseNameTableAddress(value: UByte) {
        t = ((t.toInt() and 0b111_00_11111_11111) or (value.toInt() shl 10)).toUShort()
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
            t = ((t.toInt() and 0b000_11_00000_11111) or
                    ((value.toInt() and 0b0000_0111) shl 12) or
                    ((value.toInt() and 0b1111_1000) shl 2)).toUShort()
            w = false
        } else {
            // X
            x = value and 0b0111U
            t = ((t.toInt() and 0b111_11_11111_00000) or (value.toInt() shr 3)).toUShort()
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
            t = ((t.toInt() and 0xFF_00) or value.toInt()).toUShort()
            v = t
            w = false
        } else {
            // High
            t = (((t.toInt() and 0x00_FF) or (value.toInt() shl 8)) and 0x3F_FF).toUShort()
            w = true
            // a12 // TODO: 動作確認
            a12.address = t.toInt()
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
        v =/**/((v.toInt() and 0b111_10_11111_00000) or
                (t.toInt() and 0b000_01_00000_11111)).toUShort()
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
        v =/**/((v.toInt() and 0b000_01_00000_11111) or
                (t.toInt() and 0b111_10_11111_00000)).toUShort()
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
        v = if ((v.toInt() and 0x001F) == 31) {
            ((v.toInt() and 0x001F.inv()) xor 0x0400).toUShort()
        } else {
            // 代入無しでv++のみだとif文の式の値としてUShortがBox化されてインスタンスが生成されるため手動で計算する
            (v.toInt() + 1).toUShort()
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
        if (v.toInt() and 0x7000 != 0x7000) {
            v = (v.toInt() + 0x1000).toUShort()
        } else {
            v = (v.toInt() and 0x7000.inv()).toUShort()
            val y = (v.toInt() and 0x03E0) shr 5
            v = when (y) {
                29 -> ((v.toInt() xor 0x0800) and 0x03E0.inv()).toUShort()
                31 -> (v.toInt() and 0x03E0.inv()).toUShort()
                else -> ((v.toInt() and 0x03E0.inv()) or ((y + 1) shl 5)).toUShort()
            }
        }
    }
}
