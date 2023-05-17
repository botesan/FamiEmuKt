package jp.mito.famiemukt.emurator.ppu

import jp.mito.famiemukt.emurator.NTSC_PPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.cartridge.StateObserver
import jp.mito.famiemukt.emurator.cpu.Interrupter
import jp.mito.famiemukt.emurator.util.VisibleForTesting

/*
https://www.nesdev.org/wiki/CPU_memory_map
$2000–$2007	$0008	NES PPU registers

https://www.nesdev.org/wiki/PPU_registers
https://www.nesdev.org/wiki/2A03
$4014	OAMDMA	OAM DMA: Copy 256 bytes from $xx00-$xxFF into OAM via OAMDATA ($2004)

https://www.nesdev.org/wiki/PPU_memory_map

https://www.nesdev.org/wiki/PPU_nametables
A nametable is a 1024 byte area of memory used by the PPU to lay out backgrounds.
Each byte in the nametable controls one 8x8 pixel character cell, and each nametable has 30 rows of 32 tiles each, for 960 ($3C0) bytes;
the rest is used by each nametable's attribute table.
With each tile being 8x8 pixels, this makes a total of 256x240 pixels in one map, the same size as one full screen.

https://www.nesdev.org/wiki/PPU_nametables
PPU attribute tables
Jump to navigationJump to search
An attribute table is a 64-byte array at the end of each nametable that controls which palette is assigned to each part of the background.
Each attribute table, starting at $23C0, $27C0, $2BC0, or $2FC0, is arranged as an 8x8 byte array:
 */
@OptIn(ExperimentalUnsignedTypes::class)
class PPU(
    private val ppuRegisters: PPURegisters,
    private val ppuBus: PPUBus,
    private val interrupter: Interrupter,
    private val stateObserver: StateObserver,
) {
    val pixelsRGB32: IntArray =
        IntArray(size = NTSC_PPU_VISIBLE_LINE_X_COUNT * NTSC_PPU_VISIBLE_FRAME_LINE_COUNT)

    private val objectAttributeMemory: UByteArray = UByteArray(size = 256)

    @Suppress("PropertyName")
    @VisibleForTesting
    val _ppuX: Int get() = ppuX
    private var ppuX: Int = NTSC_PPU_VISIBLE_LINE_FIRST_X

    @Suppress("PropertyName")
    @VisibleForTesting
    val _ppuY: Int get() = ppuY
    private var ppuY: Int = NTSC_PPU_VISIBLE_FRAME_FIRST_LINE

    /** 奇数フレーム／便宜上最初は偶数フレームとする */
    @Suppress("PropertyName")
    @VisibleForTesting
    val _isOddFrame: Boolean get() = isOddFrame
    private var isOddFrame: Boolean = false

    // 可能な範囲でリセット状態を設定
    // https://www.nesdev.org/wiki/PPU_power_up_state
    // Initial Register Values
    // Register								At Power		After Reset
    // PPUCTRL($2000)						0000 0000		0000 0000
    // PPUMASK($2001)						0000 0000		0000 0000
    // PPUSTATUS($2002)						+0+x xxxx		U??x xxxx
    // OAMADDR($2003)						$00				unchanged
    // $2005/$2006 latch					cleared			cleared
    // PPUSCROLL($2005)						$0000			$0000
    // PPUADDR($2006)						$0000			unchanged
    // PPUDATA($2007) read buffer			$00				$00
    // odd frame							no				no
    // OAM									unspecified		unspecified
    // Palette								unspecified		unchanged
    // NT RAM(external, in Control Deck)	unspecified		unchanged
    // CHR RAM(external, in Game Pak)		unspecified		unchanged
    fun reset() {
        // PPUCTRL($2000)						0000 0000		0000 0000
        ppuRegisters.ppuControl.value = 0u
        // PPUMASK($2001)						0000 0000		0000 0000
        ppuRegisters.ppuMask.value = 0u
        // PPUSTATUS($2002)						+0+x xxxx		U??x xxxx
        // unchanged ppuRegisters.ppuStatus.isVerticalBlankHasStarted
        // unknown ppuRegisters.ppuStatus.isSprite0Hit
        // unknown ppuRegisters.ppuStatus.isSpriteOverflow
        // OAMADDR($2003)						$00				unchanged
        // $2005/$2006 latch					cleared			cleared
        ppuRegisters.internal.w = false
        // PPUSCROLL($2005)						$0000			$0000
        // PPUADDR($2006)						$0000			unchanged
        ppuRegisters.internal.t = 0u
        ppuRegisters.internal.v = 0u
        ppuRegisters.internal.x = 0u
        // PPUDATA($2007) read buffer			$00				$00
        readBufferVideoRAM = 0u
        // odd frame							no				no
        isOddFrame = false
        // OAM									unspecified		unspecified
        // Palette								unspecified		unchanged
        // NT RAM(external, in Control Deck)	unspecified		unchanged
        // CHR RAM(external, in Game Pak)		unspecified		unchanged
    }

    fun writePPUControl(value: UByte) {
        // NMIがONになったときステータスでVBLANKだったらすぐにNMI割り込みする？
        // https://www.nesdev.org/wiki/PPU_registers#Vblank_NMI
        // Changing NMI enable from 0 to 1 while the vblank flag in PPUSTATUS is 1 will immediately trigger an NMI.
        // This happens during vblank if the PPUSTATUS register has not yet been read.
        // It can result in graphical glitches by making the NMI routine execute too late in vblank to finish on time,
        // or cause the game to handle more frames than have actually occurred.
        // To avoid this problem, it is prudent to read PPUSTATUS first to clear the vblank flag before enabling NMI in PPUCTRL.
        val before = ppuRegisters.ppuControl.isVerticalBlankingInterval
        ppuRegisters.ppuControl.value = value
        val after = ppuRegisters.ppuControl.isVerticalBlankingInterval
        if (before.not() && after && ppuRegisters.ppuStatus.isVerticalBlankHasStarted) {
            interrupter.requestNMI()
        }
    }

    fun writePPUMask(value: UByte) {
        ppuRegisters.ppuMask.value = value
    }

    fun readPPUStatus(): UByte {
        val result = ppuRegisters.ppuStatus.value
        ppuRegisters.ppuStatus.isVerticalBlankHasStarted = false
        ppuRegisters.internal.w = false
        return result
    }

    fun writeObjectAttributeMemoryAddress(value: UByte) {
        ppuRegisters.oamAddress = value
    }

    fun readObjectAttributeMemory(): UByte {
        return objectAttributeMemory[ppuRegisters.oamAddress.toInt()]
    }

    fun writeObjectAttributeMemory(value: UByte) {
        objectAttributeMemory[ppuRegisters.oamAddress.toInt()] = value
        ppuRegisters.oamAddress = (ppuRegisters.oamAddress + 1U).toUByte()
    }

    fun dmaCopyObjectAttributeMemory(copyTo: (UByteArray) -> Unit) {
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
        // とりあえず0以外は実行できないようにする
        check(value = ppuRegisters.oamAddress == 0.toUByte())
        copyTo(objectAttributeMemory)
    }

    fun writePPUScroll(value: UByte) {
        ppuRegisters.ppuScroll.writeIO(value = value)
    }

    fun writePPUAddress(value: UByte) {
        ppuRegisters.ppuAddress.writeIO(value = value)
    }

    // パイプラインが必要
    // https://www.nesdev.org/wiki/PPU_registers#PPUDATA
    // The PPUDATA read buffer (post-fetch)
    private var readBufferVideoRAM: UByte = 0U

    // パイプラインが必要（情報が更新されている？／パレットデータのアクセスは後のPPUの機能？）
    // https://www.nesdev.org/wiki/PPU_registers#PPUDATA
    // The PPUDATA read buffer (post-fetch)
    // When reading while the VRAM address is in the range 0–$3EFF (i.e., before the palettes),
    // the read will return the contents of an internal read buffer.
    // This internal buffer is updated only when reading PPUDATA, and so is preserved across frames.
    // After the CPU reads and gets the contents of the internal buffer,
    // the PPU will immediately update the internal buffer with the byte at the current VRAM address.
    // Thus, after setting the VRAM address, one should first read this register to prime the pipeline and discard the result.
    fun readVideoRAM(): UByte {
        val ppuAddress = ppuRegisters.ppuAddress.address.toInt() and 0x3FFF
        val result = ppuBus.readMemory(address = ppuAddress)
        val beforeResult = readBufferVideoRAM
        readBufferVideoRAM = result
        incrementPPUAddress()
        return if (ppuRegisters.ppuAddress.address <= 0x3EFFU) beforeResult else result
    }

    fun writeVideoRAM(value: UByte) {
        val ppuAddress = ppuRegisters.ppuAddress.address.toInt() and 0x3FFF
        ppuBus.writeMemory(address = ppuAddress, value = value)
        incrementPPUAddress()
    }

    private fun incrementPPUAddress() {
        ppuRegisters.ppuAddress.address =
            (ppuRegisters.ppuAddress.address + ppuRegisters.ppuControl.incrementSizePPUDATA).toUShort()
    }

    /** マスタークロックカウンター */
    private var masterClockCount: Int = 0

    /**
     * マスタークロックを１つ進める
     * @return フレーム描画済み
     */
    fun executeMasterClockStep(): Boolean {
        if (++masterClockCount >= NTSC_PPU_CYCLES_PER_MASTER_CLOCKS) {
            val drawFrame = execute(ppuCycle = 1)
            masterClockCount = 0
            return drawFrame
        }
        return false
    }

    /**
     * @param ppuCycle 進めるPPUクロック
     */
    fun execute(ppuCycle: Int): Boolean {
        var isFinishFrame = false
        var remainCycle = ppuCycle
        while (remainCycle > 0) {
            // 位置保持
            val x = ppuX
            val y = ppuY
            val pixelIndex = (NTSC_PPU_VISIBLE_LINE_X_COUNT) * y + x
            // バックグラウンド描画
            val isTranslucentBackground = buildBackground(x = x, y = y, pixelIndex = pixelIndex)
            // スプライトの描画とフェッチ（次のライン）
            buildSprite(x = x, y = y, pixelIndex = pixelIndex, isTranslucentBackground = isTranslucentBackground)
            // A12の通知
            notifyRisingA12IfNeeded(x = x, y = y)
            // 各種フラグ＆割り込み処理
            applyFlagsAndInterrupt(x = x, y = y)
            // インクリメント（デクリメント）
            //// 奇数フレームの対応 https://www.nesdev.org/wiki/PPU_frame_timing#Even/Odd_Frames
            if ((ppuRegisters.ppuMask.isShowBackground || ppuRegisters.ppuMask.isShowSprite) &&
                isOddFrame && x == NTSC_PPU_VISIBLE_LINE_FIRST_X && y == NTSC_PPU_VISIBLE_FRAME_FIRST_LINE
            ) {
                // BGかスプライトが表示中の場合、奇数フレームの先頭は１サイクルスキップする
                // TODO: 実装は最後のサイクルをスキップするみたい
                //  https://www.nesdev.org/w/images/default/4/4f/Ppu.svg の右下
            } else {
                remainCycle--
            }
            val ppuX = ++ppuX
            if (ppuX > NTSC_PPU_LINE_LAST_X) {
                this.ppuX = NTSC_PPU_VISIBLE_LINE_FIRST_X
                val ppuY = ++ppuY
                if (ppuY == NTSC_PPU_POST_RENDER_LINE) {
                    // フレーム終了
                    // TODO: ==NTSC_PPU_POST_RENDER_LINE or >NTSC_PPU_PRE_RENDER_LINE どちらが良い？
                    isFinishFrame = true
                } else if (ppuY > NTSC_PPU_PRE_RENDER_LINE) {
                    this.ppuY = NTSC_PPU_VISIBLE_FRAME_FIRST_LINE
                    // 奇数／偶数フレーム反転
                    isOddFrame = isOddFrame.not()
                }
            }
        }
        return isFinishFrame
    }

    private fun buildBackground(x: Int, y: Int, pixelIndex: Int): Boolean {
        var isTranslucentBackgroundPixel = true
        if (ppuRegisters.ppuMask.isShowBackground.not()) {
            if (y <= NTSC_PPU_VISIBLE_FRAME_LAST_LINE && x <= NTSC_PPU_VISIBLE_LINE_LAST_X) {
                // TODO: パレット0で良い？
                pixelsRGB32[pixelIndex] = /* 0 */convertPaletteToRGB32(
                    palette = ppuBus.readMemory(address = 0x3F00),
                    ppuMask = ppuRegisters.ppuMask,
                )
            }
        } else {
            // 位置計算１
            val relativeX = (ppuRegisters.ppuScroll.fineX.toInt() + x) and 0x07
            // 範囲チェック
            if (y <= NTSC_PPU_VISIBLE_FRAME_LAST_LINE && x <= NTSC_PPU_VISIBLE_LINE_LAST_X) {
                // 位置計算２
                val coarseX = ppuRegisters.ppuScroll.coarseX
                val coarseY = ppuRegisters.ppuScroll.coarseY
                val relativeY = ppuRegisters.ppuScroll.fineY.toInt()
                // 属性取得
                val attribute = ppuBus.readMemory(address = ppuRegisters.ppuScroll.attributeAddress)
                // パターン取得
                val patternNo = ppuBus.readMemory(address = ppuRegisters.ppuScroll.tileAddress).toInt()
                val colorNo = getColorNo(
                    patternTableAddress = ppuRegisters.ppuControl.backgroundPatternTableAddress,
                    tileNo = patternNo,
                    relativeX = relativeX,
                    relativeY = relativeY,
                )
                // パレット取得
                val paletteIndexX2 = ((coarseY.toInt() and 0b10) shl 1) or ((coarseX.toInt()) and 0b10)
                val paletteH = ((attribute.toInt() shr paletteIndexX2) and 0b11) shl 2
                val palette = getPalette(isSprite = false, paletteH = paletteH, colorNo = colorNo)
                // ２タイル分、先行フェッチしている前提でのドット反映？
                if (colorNo != 0) {
                    pixelsRGB32[pixelIndex] = convertPaletteToRGB32(
                        palette = palette,
                        ppuMask = ppuRegisters.ppuMask,
                    )
                    isTranslucentBackgroundPixel = false
                } else {
                    pixelsRGB32[pixelIndex] = convertPaletteToRGB32(
                        palette = ppuBus.readMemory(address = 0x3F00),
                        ppuMask = ppuRegisters.ppuMask,
                    )
                }
            }
            // 内部レジスタvの更新
            // これ以降のvの更新はVBlank中は行わない？
            // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
            if (y !in NTSC_PPU_POST_RENDER_LINE..<NTSC_PPU_PRE_RENDER_LINE) {
                // At dot 256 of each scanline
                //  If rendering is enabled, the PPU increments the vertical position in v.
                //  The effective Y scroll coordinate is incremented, which is a complex operation that
                //  will correctly skip the attribute table memory regions, and wrap to the next nametable appropriately.
                if (x == NTSC_PPU_VISIBLE_LINE_LAST_X + 1) {
                    ppuRegisters.ppuScroll.internal.incrementY()
                }
                // At dot 257 of each scanline
                if (x == NTSC_PPU_VISIBLE_LINE_LAST_X + 2) {
                    ppuRegisters.ppuScroll.internal.updateVForDot257OfEachScanline()
                }
                // During dots 280 to 304 of the pre-render scanline (end of vblank)
                if (y == NTSC_PPU_PRE_RENDER_LINE && x in 280..304) {
                    ppuRegisters.ppuScroll.internal.updateVForDot280To304OfPreRenderScanline()
                }
                // Between dot 328 of a scanline, and 256 of the next scanline
                //  If rendering is enabled, the PPU increments the horizontal position in v many times across the scanline,
                //  it begins at dots 328 and 336, and will continue through the next scanline at 8, 16, 24... 240, 248, 256
                //  (every 8 dots across the scanline until 256).
                //  Across the scanline the effective coarse X scroll coordinate is incremented repeatedly,
                //  which will also wrap to the next nametable appropriately.
                // TODO: 下記の修正できるなら行う？
                // ハードウェア的な動作としては２タイル分を先行フェッチするためにレジスターの値が先行してしまう？
                // そのための調整をここで行ってみる（先行フェッチしている前提とすれば、relativeXで判断するのはOKだと思われる）
                //  https://www.nesdev.org/wiki/PPU_rendering
                if (x <= NTSC_PPU_VISIBLE_LINE_LAST_X + 1) {
                    if (/*x*/relativeX and 0x07 == 0x07) { // relativeX判定用
                        ppuRegisters.ppuScroll.internal.incrementCoarseX()
                    }
                }
            }
        }
        return isTranslucentBackgroundPixel
    }

    private fun buildSprite(x: Int, y: Int, pixelIndex: Int, isTranslucentBackground: Boolean) {
        // スプライトの描画とフェッチ（次のライン）（正しくはないかもしれないが…)
        // https://www.nesdev.org/wiki/PPU_OAM
        // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
        // https://www.nesdev.org/wiki/PPU_sprite_evaluation
        if (ppuRegisters.ppuMask.isShowSprite) {
            if (y <= NTSC_PPU_VISIBLE_FRAME_LAST_LINE) {
                if (x <= NTSC_PPU_VISIBLE_LINE_LAST_X) {
                    val lineSprites = lineSprites
                    // 該当する中で最初のものを描画
                    for (indexLineSprite in lineSprites.indices) {
                        val sprite = lineSprites[indexLineSprite]
                        val colorNo = sprite.getColorNo(x = x, y = y) ?: continue
                        if (colorNo == 0) continue
                        // 以降、描画処理など
                        val palette = getPalette(isSprite = true, paletteH = sprite.paletteH, colorNo = colorNo)
                        if (sprite.isFront) {
                            pixelsRGB32[pixelIndex] = convertPaletteToRGB32(
                                palette = palette,
                                ppuMask = ppuRegisters.ppuMask,
                            )
                        } else if (isTranslucentBackground) {
                            pixelsRGB32[pixelIndex] = convertPaletteToRGB32(
                                palette = palette,
                                ppuMask = ppuRegisters.ppuMask,
                            )
                        }
                        /* スプライト0チェック
Sprite 0 hits
Sprites are conventionally numbered 0 to 63. Sprite 0 is the sprite controlled by OAM addresses $00-$03,
sprite 1 is controlled by $04-$07, ..., and sprite 63 is controlled by $FC-$FF.
While the PPU is drawing the picture, when an opaque pixel of sprite 0 overlaps an opaque pixel of the background, this is a sprite 0 hit.
The PPU detects this condition and sets bit 6 of PPUSTATUS ($2002) to 1 starting at this pixel,
letting the CPU know how far along the PPU is in drawing the picture.

Sprite 0 hit does not happen:
If background or sprite rendering is disabled in PPUMASK ($2001)
At x=0 to x=7 if the left-side clipping window is enabled (if bit 2 or bit 1 of PPUMASK is 0).
At x=255, for an obscure reason related to the pixel pipeline.
At any pixel where the background or sprite pixel is transparent (2-bit color index from the CHR pattern is %00).
If sprite 0 hit has already occurred this frame. Bit 6 of PPUSTATUS ($2002) is cleared to 0 at dot 1 of the pre-render line.
This means only the first sprite 0 hit in a frame can be detected.

Sprite 0 hit happens regardless of the following:
Sprite priority. Sprite 0 can still hit the background from behind.
The pixel colors. Only the CHR pattern bits are relevant, not the actual rendered colors,
and any CHR color index except %00 is considered opaque.
The palette. The contents of the palette are irrelevant to sprite 0 hits.
For example: a black ($0F) sprite pixel can hit a black ($0F) background as long as neither is the transparent color index %00.
The PAL PPU blanking on the left and right edges at x=0, x=1, and x=254 (see Overscan). */
                        if (sprite.index == 0 &&
                            isTranslucentBackground.not() &&
                            //ppuRegisters.ppuMask.isShowBackground && // isTranslucentBackground.not() でチェック済みとなる
                            ppuRegisters.ppuStatus.isSprite0Hit.not() &&
                            (x >= 8 || (ppuRegisters.ppuMask.isShowBackgroundLeft8Pixels
                                    && ppuRegisters.ppuMask.isShowSpriteLeft8Pixels)) &&
                            x != NTSC_PPU_VISIBLE_LINE_LAST_X
                        ) {
                            ppuRegisters.ppuStatus.isSprite0Hit = true
                        }
                        // １個でも描画したら終了
                        break
                    }
                }
            }
        }
        // スプライトが表示無効でも次の行のフェッチは行う
        if (x == NTSC_PPU_VISIBLE_LINE_LAST_X + 1) {
            // 次のラインのフェッチ（実際は64～256で実行されるみたい⇒違うかも／左記はバックグラウンドの話？）
            val fetched = fetchSprites(line = y).take(n = 9).toList()
            lineSprites = fetched.take(n = 8)
            // 実機はバグがあるため実際の動作とは違うが、一応スプライトオーバーフローを設定
            if (fetched.size > 8) {
                ppuRegisters.ppuStatus.isSpriteOverflow = true
            }
        }
        // https://www.nesdev.org/wiki/PPU_registers#Values_during_rendering
        // OAMADDR is set to 0 during each of ticks 257–320 (the sprite tile loading interval) of the pre-render and visible scanlines.
        // This also means that at the end of a normal complete rendered frame, OAMADDR will always have returned to 0.
        if (x > NTSC_PPU_VISIBLE_LINE_LAST_X + 2 && (y <= NTSC_PPU_VISIBLE_FRAME_LAST_LINE || y == NTSC_PPU_PRE_RENDER_LINE)) {
            ppuRegisters.oamAddress = 0u
        }
    }

    private fun notifyRisingA12IfNeeded(x: Int, y: Int) {
        if (ppuRegisters.ppuMask.isShowBackground || ppuRegisters.ppuMask.isShowSprite) {
            /* Regarding PPU A12:
                When using 8x8 sprites,
                 if the BG uses $0000, and the sprites use $1000, the IRQ counter should decrement on PPU cycle 260,
                 right after the visible part of the target scanline has ended.
                When using 8x8 sprites,
                 if the BG uses $1000, and the sprites use $0000, the IRQ counter should decrement on PPU cycle 324
                 of the previous scanline (as in, right before the target scanline is about to be drawn).
                 However, the 2C02's pre-render scanline will decrement the counter twice every other vertical redraw,
                 so the IRQ will shake one scanline.
                 This is visible in Wario's Woods:
                  with some PPU-CPU reset alignments the bottom line of the green grass of the play area may flicker black
                  on the rightmost ~48 pixels, due to an extra count firing the IRQ one line earlier than expected.
                When using 8x16 sprites PPU A12 must be explicitly tracked.
                 The exact time and number of times the counter is clocked will depend on the specific set of sprites
                 present on every scanline.
                 Specific combinations of sprites could cause the counter to decrement up to four times,
                 or the IRQ to be delayed or early by some multiple of 8 pixels.
                 If there are fewer than 8 sprites on a scanline, the PPU fetches tile $FF ($1FF0-$1FFF)
                 for each leftover sprite and discards its value.
                 Thus if a game uses 8x16 sprites with its background and sprites from PPU $0000,
                 then the MMC3 ends up counting each scanline that doesn't use all eight sprites. */
            if (y <= NTSC_PPU_VISIBLE_FRAME_LAST_LINE || y == NTSC_PPU_PRE_RENDER_LINE) {
                // A12が立ち上がるのはパターンテーブルアクセスのアドレスの12ビット目(A12:マスク0x1000)が0から1になるときみたい
                if (ppuRegisters.ppuControl.isSprite8x16) {
                    // アドレスは各スプライトの設定を使用する
                    // 上記説明文にスキャンライン毎に詳細を追わないとダメと書いてある
                    // FCEUXは、基本は全スキャンラインで１回呼んでいるっぽい
                    // https://github.com/TASEmulators/fceux/blob/f980ec2bc7dc962f6cd76b9ae3131f2eb902c9e7/src/ppu.cpp#L1378
                    // https://www.nesdev.org/wiki/MMC3#IRQ_Specifics
                    // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                    when (x) {
                        260, 268, 276, 284, 292, 300, 308, 316, 324 -> {
                            val lineSprites = lineSprites
                            val backgroundPatternTableAddress =
                                ppuRegisters.ppuControl.backgroundPatternTableAddress
                            val addresses = listOf(
                                backgroundPatternTableAddress,
                                lineSprites.getOrNull(index = 0)?.spritePatternTableAddress ?: 0x1000,
                                lineSprites.getOrNull(index = 1)?.spritePatternTableAddress ?: 0x1000,
                                lineSprites.getOrNull(index = 2)?.spritePatternTableAddress ?: 0x1000,
                                lineSprites.getOrNull(index = 3)?.spritePatternTableAddress ?: 0x1000,
                                lineSprites.getOrNull(index = 4)?.spritePatternTableAddress ?: 0x1000,
                                lineSprites.getOrNull(index = 5)?.spritePatternTableAddress ?: 0x1000,
                                lineSprites.getOrNull(index = 6)?.spritePatternTableAddress ?: 0x1000,
                                lineSprites.getOrNull(index = 7)?.spritePatternTableAddress ?: 0x1000,
                                backgroundPatternTableAddress,
                            ).take(n = (x - 260) / 8 + 2).takeLast(n = 2)
                            // A12が立ち上がった時（0⇒1）に通知
                            if (addresses.first() == 0x0000 && addresses.last() != 0x0000) {
                                stateObserver.notifyRisingA12PPU()
                            }
                        }
                    }
                } else if (x == 260) {
                    // PPU cycle 260
                    val backgroundPatternTableAddress = ppuRegisters.ppuControl.backgroundPatternTableAddress
                    if (backgroundPatternTableAddress != ppuRegisters.ppuControl.spritePatternTableAddress) {
                        if (backgroundPatternTableAddress == 0x0000) {
                            stateObserver.notifyRisingA12PPU()
                        }
                    }
                } else if (x == 324) {
                    // PPU cycle 324 of the previous scanline
                    val backgroundPatternTableAddress = ppuRegisters.ppuControl.backgroundPatternTableAddress
                    if (backgroundPatternTableAddress != ppuRegisters.ppuControl.spritePatternTableAddress) {
                        if (backgroundPatternTableAddress != 0x0000) {
                            stateObserver.notifyRisingA12PPU()
                        }
                    }
                }
            }
        }
    }

    private fun applyFlagsAndInterrupt(x: Int, y: Int) {
        // 各種フラグ設定解除
        if (x == 1) {
            if (y == NTSC_PPU_POST_RENDER_LINE + 1) {
                // Line 241 の１ドット目（２番目のドット）にきてたら VBLANK フラグを立てて NMI 割り込みする
                // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                ppuRegisters.ppuStatus.isVerticalBlankHasStarted = true
                if (ppuRegisters.ppuControl.isVerticalBlankingInterval) {
                    // https://www.nesdev.org/wiki/NMI
                    //  Two 1-bit registers inside the PPU control the generation of NMI signals.
                    //  Frame timing and accesses to the PPU's PPUCTRL and PPUSTATUS registers change these registers as follows,
                    //  regardless of whether rendering is enabled:
                    //  1.Start of vertical blanking: Set NMI_occurred in PPU to true.
                    //  2.End of vertical blanking, sometime in pre-render scanline: Set NMI_occurred to false.
                    //  3.Read PPUSTATUS: Return old status of NMI_occurred in bit 7, then set NMI_occurred to false.
                    //  4.Write to PPUCTRL: Set NMI_output to bit 7.
                    //  The PPU pulls /NMI low if and only if both NMI_occurred and NMI_output are true.
                    //  By toggling NMI_output (PPUCTRL.7) during vertical blank without reading PPUSTATUS,
                    //  a program can cause /NMI to be pulled low multiple times, causing multiple NMIs to be generated.
                    interrupter.requestNMI()
                }
            } else if (y == NTSC_PPU_PRE_RENDER_LINE) {
                // line 261の先頭１ドット目（２番目のドット）で各フラグのクリア
                // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                ppuRegisters.ppuStatus.isVerticalBlankHasStarted = false
                ppuRegisters.ppuStatus.isSprite0Hit = false
                ppuRegisters.ppuStatus.isSpriteOverflow = false
            }
        }
    }

    private var lineSprites: List<Sprite> = emptyList()

    private fun fetchSprites(line: Int): Sequence<Sprite> {
        // スプライト番号が若い方が前面／前面順で取得
        val spriteHeight = if (ppuRegisters.ppuControl.isSprite8x16) 16 else 8
        return (0..<objectAttributeMemory.size / 4)
            .asSequence()
            .map { no -> Sprite(ppuBus, ppuRegisters.ppuControl, objectAttributeMemory, no) }
            .filter { sprite -> (line - (sprite.offsetY - 1) in 0..<spriteHeight) }
    }

    private fun getColorNo(patternTableAddress: Int, tileNo: Int, relativeX: Int, relativeY: Int): Int {
        val patternAddress = patternTableAddress + tileNo * PATTERN_TABLE_ELEMENT_SIZE + relativeY
        val patternL = ppuBus.readMemory(address = patternAddress)
        val patternH = ppuBus.readMemory(address = patternAddress + PATTERN_TABLE_ELEMENT_SIZE / 2)
        val patternBitPos = 7 - relativeX
        val colorNoL = (patternL.toInt() shr patternBitPos) and 0x01
        val colorNoH = ((patternH.toInt() shl 1) shr patternBitPos) and 0x02
        return colorNoH or colorNoL
    }

    private fun getPalette(isSprite: Boolean, paletteH: Int, colorNo: Int): UByte {
        val baseAddress = if (isSprite) 0x3F10 else 0x3F00
        val paletteAddress = baseAddress + paletteH + colorNo
        return ppuBus.readMemory(address = paletteAddress)
    }

    fun debugInfo(nest: Int): String = buildString {
        append(" ".repeat(n = nest)).append("ppuRegisters=").appendLine(ppuRegisters)
        append(ppuBus.debugInfo(nest = nest + 1))
    }

    companion object {
        private const val NTSC_PPU_VISIBLE_LINE_FIRST_X: Int = 0
        private const val NTSC_PPU_VISIBLE_LINE_LAST_X: Int = 255
        private const val NTSC_PPU_VISIBLE_LINE_X_COUNT: Int = NTSC_PPU_VISIBLE_LINE_LAST_X + 1
        private const val NTSC_PPU_LINE_LAST_X: Int = 340

        private const val NTSC_PPU_VISIBLE_FRAME_FIRST_LINE: Int = 0
        private const val NTSC_PPU_VISIBLE_FRAME_LAST_LINE: Int = 239
        private const val NTSC_PPU_VISIBLE_FRAME_LINE_COUNT: Int = NTSC_PPU_VISIBLE_FRAME_LAST_LINE + 1
        private const val NTSC_PPU_POST_RENDER_LINE: Int = 240
        private const val NTSC_PPU_PRE_RENDER_LINE: Int = 261

        const val PATTERN_TABLE_ELEMENT_SIZE = 16
    }
}
