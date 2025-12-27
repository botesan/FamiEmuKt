package jp.mito.famiemukt.emurator.ppu

import jp.mito.famiemukt.emurator.NTSC_PPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.cartridge.A12
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
    private val a12: A12
) {
    val pixelsRGB32: IntArray = IntArray(size = NTSC_PPU_VISIBLE_LINE_X_COUNT * NTSC_PPU_VISIBLE_FRAME_LINE_COUNT)

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

    /** VBLANKが設定されたときのフラグ */
    private var isWrittenVerticalBlankFlag: Boolean = false

    /** VBLANKがクリアされたときのフラグ */
    private var isClearedVerticalBlankHasStartedFlag: Boolean = false

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
//println("writePPUControl(${value.toHex()}) masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY")// TODO: デバッグ
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
        // TODO: レジスターの変更を通知するようにしたい
        if (ppuRegisters.ppuStatus.isVerticalBlankHasStarted) {
            if (before.not() && after) {
                isWrittenVerticalBlankFlag = true
//println("interrupter.requestNMI() from write ppu control. masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY")// TODO: デバッグ
                interrupter.requestNMI()
            } else if (before && after.not() && ppuX in 2..3 && ppuY == NTSC_PPU_POST_RENDER_LINE + 1) {
//println("interrupter.requestNMI(levelLow = false) from write ppu control. masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY")// TODO: デバッグ
                interrupter.requestNMI(levelLow = false)
            }
        }
    }

    fun writePPUMask(value: UByte) {
//println("writePPUMask(${value.toHex()}) masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY, isOddFrame=$isOddFrame")// TODO: デバッグ
        ppuRegisters.ppuMask.value = value
    }

    fun readPPUStatus(): UByte {
        val result = ppuRegisters.ppuStatus.value
        ppuRegisters.ppuStatus.isVerticalBlankHasStarted = false
        ppuRegisters.internal.w = false
        isClearedVerticalBlankHasStartedFlag = true
        // https://www.nesdev.org/wiki/PPU_frame_timing#VBL_Flag_Timing
        if (ppuRegisters.ppuControl.isVerticalBlankingInterval) {
//println("readPPUStatus()=>${result.toHex()} masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY")// TODO: デバッグ
            // リクエスト済みNMIをクリア（無理やり？）
            if (ppuX in 1..3 && ppuY == NTSC_PPU_POST_RENDER_LINE + 1) {
                interrupter.requestNMI(levelLow = false)
            }
        }
//// TODO: PPU の 10-even_odd_timing #3 テストが通らない／右下最後のピクセル付近で BG 描画切り替えのテスト？
//if (result != 0.toUByte() || ppuY in 240..241) println("readPPUStatus()=>${result.toHex()} masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY, isOddFrame=$isOddFrame")// TODO: デバッグ
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
        // A12対応
        a12.address = ppuRegisters.ppuAddress.address.toInt()
    }

    /** マスタークロックカウンター */
    private var masterClockCount: Int = 0

    /**
     * マスタークロックを１つ進める
     * @return フレーム描画済み
     */
    fun executeMasterClockStep(): Boolean {
        if (++masterClockCount >= NTSC_PPU_CYCLES_PER_MASTER_CLOCKS) {
            val drawFrame = executePPUCycleStep()
            masterClockCount = 0
            return drawFrame
        }
        return false
    }

    // BG フェッチ・シフトレジスタ状態
    private data class BGFetchState(
        var attributeS: Int = 0,
        var patternHS: Int = 0,
        var patternLS: Int = 0,
        var attributeF: UByte = 0u,
        var patternNoF: UByte = 0u,
        var patternAddress: Int = 0,
        var patternHF: UByte = 0u,
        var patternLF: UByte = 0u,
    ) {
        fun shift() {
            attributeS = ((attributeS shl 8) or attributeF.toInt())
            patternLS = ((patternLS shl 8) or patternLF.toInt())
            patternHS = ((patternHS shl 8) or patternHF.toInt())
        }
    }

    private val bgFetchState: BGFetchState = BGFetchState()
    private val fetchingSprites: MutableList<FetchSprite> = mutableListOf()
    private val drawingSprites: MutableList<FetchSprite> = mutableListOf()
    private val drawingSpriteX: BooleanArray = BooleanArray(size = NTSC_PPU_VISIBLE_LINE_LAST_X + 1)

    @Suppress("FunctionName")
    @VisibleForTesting
    fun _executePPUCycleStep(): Boolean = executePPUCycleStep()

    private fun executePPUCycleStep(): Boolean {
        val ppuX = ppuX
        val ppuY = ppuY
        val ppuMask = ppuRegisters.ppuMask
        val isShowBackground = ppuMask.isShowBackground
        val isShowSprite = ppuMask.isShowSprite
        // 各ラインの処理
        when (ppuY) {
            in NTSC_PPU_VISIBLE_FRAME_FIRST_LINE..NTSC_PPU_VISIBLE_FRAME_LAST_LINE -> executeLine0to239(
                isShowBackground = isShowBackground, isShowSprite = isShowSprite,
                ppuX = ppuX, ppuY = ppuY,
            )

            NTSC_PPU_POST_RENDER_LINE -> executeLine240()
            NTSC_PPU_POST_RENDER_LINE + 1 -> executeLine241(ppuX = ppuX)
            NTSC_PPU_PRE_RENDER_LINE -> executeLine261(
                isShowBackground = isShowBackground, isShowSprite = isShowSprite,
                ppuX = ppuX, ppuY = ppuY,
            )
        }
        // ラインのドット描画
        if (ppuY <= NTSC_PPU_VISIBLE_FRAME_LAST_LINE && ppuX <= NTSC_PPU_VISIBLE_LINE_LAST_X) {
            drawLine(
                ppuMask = ppuMask, isShowBackground = isShowBackground, isShowSprite = isShowSprite,
                ppuX = ppuX, ppuY = ppuY,
            )
        }
        // VBLANK関連のフラグクリア
        isWrittenVerticalBlankFlag = false
        isClearedVerticalBlankHasStartedFlag = false
        // インクリメント等（フレーム終了判定付き）
        return executeIncrement(isShowBackground = isShowBackground, isShowSprite = isShowSprite)
    }

    private object FetchType {
        const val NONE = 0
        const val NT = 1
        const val AT = 2
        const val BG_LF = 3
        const val BG_HF = 4
        const val UPDATE_D = 5
    }

    private val fetchTypeAtX = IntArray(size = NTSC_PPU_LINE_LAST_X + 1) { x ->
        if (x == 257) return@IntArray FetchType.UPDATE_D
        if (x in 1..256 || x in 321..336) {
            return@IntArray when (x % 8) {
                2 -> FetchType.NT
                4 -> FetchType.AT
                6 -> FetchType.BG_LF
                0 -> FetchType.BG_HF
                else -> FetchType.NONE
            }
        }
        FetchType.NONE
    }

    // 主にBGのフェッチ
    private fun fetchBackground(ppuX: Int) {
        when (fetchTypeAtX[ppuX]) {
            // NTフェッチ
            FetchType.NT -> {
                bgFetchState.patternNoF = ppuBus.readMemory(address = ppuRegisters.ppuScroll.tileAddress)
            }
            // ATフェッチ
            FetchType.AT -> {
                bgFetchState.attributeF = ppuBus.readMemory(address = ppuRegisters.ppuScroll.attributeAddress)
                // a12チェック
                a12.address = ppuRegisters.ppuControl.backgroundPatternTableAddress
            }
            // BG lowフェッチ
            FetchType.BG_LF -> {
                bgFetchState.patternAddress = ppuRegisters.ppuControl.backgroundPatternTableAddress +
                        bgFetchState.patternNoF.toInt() * PATTERN_TABLE_ELEMENT_SIZE +
                        ppuRegisters.ppuScroll.fineY
                bgFetchState.patternLF = ppuBus.readMemory(address = bgFetchState.patternAddress)
            }
            // BG highフェッチ
            FetchType.BG_HF -> {
                bgFetchState.patternHF =
                    ppuBus.readMemory(address = bgFetchState.patternAddress + PATTERN_TABLE_ELEMENT_SIZE / 2)
                // https://www.nesdev.org/wiki/PPU_rendering
                // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                // At dot 256 of each scanline
                //  If rendering is enabled, the PPU increments the vertical position in v.
                //  The effective Y scroll coordinate is incremented, which is a complex operation that
                //  will correctly skip the attribute table memory regions, and wrap to the next nametable appropriately.
                if (ppuX == 256) ppuRegisters.internal.incrementY()
                // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                // https://www.nesdev.org/wiki/PPU_rendering
                // Between dot 328 of a scanline, and 256 of the next scanline
                //  If rendering is enabled, the PPU increments the horizontal position in v many times across the scanline,
                //  it begins at dots 328 and 336, and will continue through the next scanline at 8, 16, 24... 240, 248, 256
                //  (every 8 dots across the scanline until 256).
                //  Across the scanline the effective coarse X scroll coordinate is incremented repeatedly,
                //  which will also wrap to the next nametable appropriately.
                ppuRegisters.internal.incrementCoarseX()
                // 下記のように記述されてはいるが
                // The shifters are reloaded during ticks 9, 17, 25, ..., 257.
                bgFetchState.shift()
            }
            // https://www.nesdev.org/wiki/PPU_rendering
            // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
            // At dot 257 of each scanline
            FetchType.UPDATE_D -> {
                ppuRegisters.internal.updateVForDot257OfEachScanline()
            }
            // other
            else -> Unit
        }
    }

    // スプライトの評価
    private fun evaluateSprites(ppuX: Int, ppuY: Int) {
        // https://www.nesdev.org/wiki/PPU_OAM
        // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
        // https://www.nesdev.org/wiki/PPU_sprite_evaluation
        when (ppuX) {
            // Secondary OAMクリア／実際の動作とは異なる
            //in 1..64 -> {
            //    if (ppuX == 1) {
            1 -> fetchingSprites.clear()
            //    }
            //}
            // 評価／実際の動作とは異なる
            in /*65*/66../*256*/192 -> {
                if (ppuX % 2 == 0 && fetchingSprites.size <= 8) {
                    //val i = ppuX - 64
                    val no = ((ppuX - 64) / 2) - 1
                    //if (i % 2 == 0 /*&& no < 64*/) {
                    val sprite = FetchSprite.obtain(ppuRegisters.ppuControl, objectAttributeMemory, no)
                    if (ppuY + 1 - sprite.offsetY in 0..<sprite.spriteHeight) {
                        fetchingSprites += sprite
                        // 実機はバグがあるため実際の動作とは違うが、一応スプライトオーバーフローを設定
                        if (fetchingSprites.size > 8) {
                            ppuRegisters.ppuStatus.isSpriteOverflow = true
                        }
                    } else {
                        sprite.recycle()
                    }
                    //}
                }
            }
        }
    }

    // スプライトのフェッチ
    private fun fetchSprites(ppuX: Int, ppuY: Int) {
        when (ppuX) {
            in 257..320 -> {
                // https://www.nesdev.org/wiki/PPU_registers#Values_during_rendering
                // OAMADDR is set to 0 during each of ticks 257–320 (the sprite tile loading interval) of the pre-render and visible scanlines.
                // This also means that at the end of a normal complete rendered frame, OAMADDR will always have returned to 0.
                ppuRegisters.oamAddress = 0u
                // スプライトフェッチ
                // https://www.nesdev.org/wiki/PPU_OAM
                // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                // https://www.nesdev.org/wiki/PPU_sprite_evaluation
                when (ppuX % 8) {
                    // a12チェック
                    4 -> {
                        val index = (ppuX - 260) / 8
                        val sprite = fetchingSprites.getOrNull(index)
                        a12.address = if (ppuRegisters.ppuControl.isSprite8x16.not()) {
                            ppuRegisters.ppuControl.spritePatternTableAddress
                        } else if (index < 8 && sprite != null) {
                            sprite.spritePatternTableAddress
                        } else {
                            0x1000
                        }
                    }
                    // low
                    6 -> {
                        if (ppuY != NTSC_PPU_PRE_RENDER_LINE) {
                            val index = (ppuX - 262) / 8
                            val sprite = fetchingSprites.getOrNull(index)
                            sprite?.fetchLinePatternL(ppuBus = ppuBus, y = ppuY + 1)
                        }
                    }
                    // high
                    0 -> {
                        if (ppuY != NTSC_PPU_PRE_RENDER_LINE) {
                            val index = (ppuX - 264) / 8
                            val sprite = fetchingSprites.getOrNull(index)
                            sprite?.fetchLinePatternH(ppuBus = ppuBus, y = ppuY + 1)
                        }
                    }
                }
                // TODO: これで良い？
                if (ppuX == 320) {
                    for (i in drawingSprites.indices) {
                        drawingSprites[i].recycle()
                    }
                    drawingSprites.clear()
                    drawingSpriteX.fill(element = false)
                    if (ppuY != NTSC_PPU_PRE_RENDER_LINE) {
                        for (i in fetchingSprites.indices) {
                            val sprite = fetchingSprites[i]
                            when {
                                i < 8 -> {
                                    drawingSprites += sprite
                                    // 該当するスプライトがある座標を保持
                                    repeat(times = 8) { index ->
                                        val x = sprite.offsetX + index
                                        if (x in 0..NTSC_PPU_VISIBLE_LINE_LAST_X) {
                                            drawingSpriteX[x] = true
                                        }
                                    }
                                }

                                else -> sprite.recycle()
                            }
                        }
                    } else {
                        // Pre-render line では fetchingSprites の中身が Visible frame last line のときの上記処理で
                        // recycle済みのため何もしない
                    }
                }
            }
        }
    }

    // Visible frame
    private fun executeLine0to239(isShowBackground: Boolean, isShowSprite: Boolean, ppuX: Int, ppuY: Int) {
        if (isShowBackground || isShowSprite) {
            fetchBackground(ppuX = ppuX)
            evaluateSprites(ppuX = ppuX, ppuY = ppuY)
            fetchSprites(ppuX = ppuX, ppuY = ppuY)
        }
    }

    // VBLANKラインの一つ前
    private fun executeLine240() {
        // 下記リンクの準備／フラグのクリア処理
        // https://www.nesdev.org/wiki/PPU_frame_timing#VBL_Flag_Timing
        isClearedVerticalBlankHasStartedFlag = false
    }

    // VBLANKライン
    private fun executeLine241(ppuX: Int) {
        if (ppuX == 1) {
            // VBLANK開始 1PPU サイクル前（1ドット目）でPPUSTATUSでクリアしていた場合NMI割り込みしない
            // https://www.nesdev.org/wiki/PPU_frame_timing#VBL_Flag_Timing
            if (isClearedVerticalBlankHasStartedFlag.not()) {
                // Line 241 の１ドット目（２番目のドット）にきてたら VBLANK フラグを立てて NMI 割り込みする
                // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                ppuRegisters.ppuStatus.isVerticalBlankHasStarted = true
            }
//println("isVerticalBlankHasStarted=${ppuRegisters.ppuStatus.isVerticalBlankHasStarted}, masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY, isOddFrame=$isOddFrame")// TODO: デバッグ
            // TODO: レジスターの変更を通知するようにしたい
            // NMI割り込み要求
            if (ppuRegisters.ppuStatus.isVerticalBlankHasStarted && ppuRegisters.ppuControl.isVerticalBlankingInterval) {
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
//println("interrupter.requestNMI() from ppu cycle. masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY")// TODO: デバッグ
                interrupter.requestNMI()
            }
        } else {
            // その他のサイクルでは isClearedVerticalBlankHasStartedFlag をクリア
            isClearedVerticalBlankHasStartedFlag = false
        }
    }

    // Pre-render line
    private fun executeLine261(isShowBackground: Boolean, isShowSprite: Boolean, ppuX: Int, ppuY: Int) {
        if (isShowBackground || isShowSprite) {
            fetchBackground(ppuX = ppuX)
            fetchSprites(ppuX = ppuX, ppuY = ppuY)
        }
        when (ppuX) {
            // クリア VBLANK, Sprite0Hit, SpriteOverflow
            1 -> {
                // line 261の先頭１ドット目（２番目のドット）で各フラグのクリア
                // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                ppuRegisters.ppuStatus.isVerticalBlankHasStarted = false
                ppuRegisters.ppuStatus.isSprite0Hit = false
                ppuRegisters.ppuStatus.isSpriteOverflow = false
                if (isWrittenVerticalBlankFlag && ppuRegisters.ppuControl.isVerticalBlankingInterval) {
                    // NMI割り込み要求クリア
//println("interrupter.requestNMI(levelLow=false) from ppu cycle. masterClockCount=$masterClockCount, ppuX=$ppuX, ppuY=$ppuY")// TODO: デバッグ
                    interrupter.requestNMI(levelLow = false)
                }
            }
            // vの上位３ビットをtにコピー
            in 280..304 -> {
                if (isShowBackground || isShowSprite) {
                    // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg
                    // During dots 280 to 304 of the pre-render scanline (end of vblank)
                    ppuRegisters.internal.updateVForDot280To304OfPreRenderScanline()
                }
            }
        }
    }

    // インクリメント（フレーム終了判定付き）
    private fun executeIncrement(isShowBackground: Boolean, isShowSprite: Boolean): Boolean {
        // インクリメント
        ppuX++
        if (ppuX > NTSC_PPU_LINE_LAST_X) {
            ppuX = NTSC_PPU_VISIBLE_LINE_FIRST_X
            ppuY++
            if (ppuY == NTSC_PPU_POST_RENDER_LINE) {
                // フレーム終了
                return true
            } else if (ppuY > NTSC_PPU_PRE_RENDER_LINE) {
                ppuY = NTSC_PPU_VISIBLE_FRAME_FIRST_LINE
                // 奇数／偶数フレーム反転
                isOddFrame = isOddFrame.not()
            }
        }
        // 偶数／奇数フレームの対応 https://www.nesdev.org/wiki/PPU_frame_timing#Even/Odd_Frames
        // Even/Odd Frames
        // - The PPU has an even/odd flag that is toggled every frame, regardless of whether rendering is enabled or disabled.
        // - With rendering disabled (background and sprites disabled in PPUMASK ($2001)),
        //   each PPU frame is 341*262=89342 PPU clocks long. There is no skipped clock every other frame.
        // - With rendering enabled, each odd PPU frame is one PPU clock shorter than normal.
        //   This is done by skipping the first idle tick on the first visible scanline
        //   (by jumping directly from (339,261) on the pre-render scanline to (0,0) on the first visible scanline
        //   and doing the last cycle of the last dummy nametable fetch there instead; see this diagram).
        // - By keeping rendering disabled until after the time when the clock is skipped on odd frames,
        //   you can get a different color dot crawl pattern than normal (it looks more like that of interlace,
        //   where colors flicker between two states rather than the normal three).
        //   Presumably Battletoads (and others) encounter this, since it keeps the BG disabled until well after this time each frame.
        if ((isShowBackground || isShowSprite) &&
            isOddFrame && ppuX == NTSC_PPU_LINE_LAST_X && ppuY == NTSC_PPU_PRE_RENDER_LINE
        ) {
            // BGかスプライトが表示中の場合、奇数フレームの最後は１サイクルスキップする
            // https://www.nesdev.org/w/images/default/4/4f/Ppu.svg の右下
            // This dot is skipped by jumping directly from (339, 261) to (0, 0) on odd frames.
            // Because of the 1-dot delay, this behaves like skipping (0, 0) on even frames.
            ppuX = NTSC_PPU_VISIBLE_LINE_FIRST_X
            ppuY = NTSC_PPU_VISIBLE_FRAME_FIRST_LINE
            isOddFrame = isOddFrame.not()
        }
        // フレーム未終了
        return false
    }

    // 透明BG(パレット0)描画
    private fun drawBGTranslucent(ppuMask: PPUMask, pixelIndex: Int) {
        pixelsRGB32[pixelIndex] = /* 0 */convertPaletteToRGB32(
            palette = ppuBus.readMemory(address = 0x3F00),
            ppuMask = ppuMask,
        )
    }

    // BG描画（透明描画を返す）
    private fun drawBG(ppuMask: PPUMask, pixelIndex: Int, ppuX: Int): Boolean {
        val ppuScroll = ppuRegisters.ppuScroll
        val relativeX = ppuScroll.fineX + (ppuX and 0x07)
        val patternBitPos = 15 - relativeX
        // パターンから色番号取得
        val colorNoL = (bgFetchState.patternLS shr patternBitPos) and 0x01
        val colorNoH = (bgFetchState.patternHS shr patternBitPos) and 0x01
        val colorNo = (colorNoH shl 1) or colorNoL
        if (colorNo == 0) {
            // 透明BG描画
            drawBGTranslucent(ppuMask, pixelIndex)
            return true
        } else {
            // ２タイル分、先行フェッチしている前提でのドット反映？
            val shiftOffset = patternBitPos and 0x08 // if (patternBitPos >= 8) 8 else 0
            val coarseX = ppuScroll.coarseX - (1 shl (shiftOffset shr 3)) // if (patternBitPos >= 8) 2u else 1u
            val coarseY = ppuScroll.coarseY
            // スクロールの位置を考慮するためcoarseXYを使用して属性の位置（0-3）を決定して2倍する
            val paletteIndexX2 = ((coarseY and 0b10) shl 1) or (coarseX and 0b10)
            val paletteH = ((bgFetchState.attributeS shr (paletteIndexX2 or shiftOffset)) and 0b11) shl 2
            // パレット取得
            val paletteAddress = 0x3F00 or paletteH or colorNo
            val palette = ppuBus.readMemory(address = paletteAddress)
            pixelsRGB32[pixelIndex] = convertPaletteToRGB32(palette = palette, ppuMask = ppuMask)
            // 非透明
            return false
        }
    }

    // スプライト描画
    private fun drawSprite(ppuMask: PPUMask, pixelIndex: Int, ppuX: Int, isTranslucentBackgroundPixel: Boolean) {
        if (drawingSpriteX[ppuX].not()) return
        for (i in drawingSprites.indices) {
            val sprite = drawingSprites[i]
            val colorNo = sprite.getColorNo(x = ppuX)
            if (colorNo <= 0) continue
            // 以降、描画処理など
            val paletteAddress = 0x3F10 or sprite.paletteH or colorNo
            val palette = ppuBus.readMemory(address = paletteAddress)
            if (sprite.isFront || isTranslucentBackgroundPixel) {
                pixelsRGB32[pixelIndex] = convertPaletteToRGB32(palette = palette, ppuMask = ppuMask)
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
                isTranslucentBackgroundPixel.not() &&
                //ppuMask.isShowBackground && // isTranslucentBackgroundPixel.not() でチェック済みとなる
                ppuRegisters.ppuStatus.isSprite0Hit.not() &&
                (ppuX >= 8 || (ppuMask.isShowBackgroundLeft8Pixels && ppuMask.isShowSpriteLeft8Pixels)) &&
                ppuX != NTSC_PPU_VISIBLE_LINE_LAST_X
            ) {
                ppuRegisters.ppuStatus.isSprite0Hit = true
            }
            // １つ描画したら終了
            break
        }
    }

    private fun drawLineTranslucent(ppuMask: PPUMask, pixelIndex: Int) {
        drawBGTranslucent(ppuMask = ppuMask, pixelIndex = pixelIndex)
    }

    private fun drawLineWithBGOnly(ppuMask: PPUMask, pixelIndex: Int, ppuX: Int) {
        drawBG(ppuMask = ppuMask, pixelIndex = pixelIndex, ppuX = ppuX)
    }

    private fun drawLineWithSpriteOnly(ppuMask: PPUMask, pixelIndex: Int, ppuX: Int) {
        drawBGTranslucent(ppuMask = ppuMask, pixelIndex = pixelIndex)
        drawSprite(ppuMask = ppuMask, pixelIndex = pixelIndex, ppuX = ppuX, isTranslucentBackgroundPixel = true)
    }

    private fun drawLineWithBGAndSprite(ppuMask: PPUMask, pixelIndex: Int, ppuX: Int) {
        val translucent = drawBG(ppuMask = ppuMask, pixelIndex = pixelIndex, ppuX = ppuX)
        drawSprite(ppuMask = ppuMask, pixelIndex = pixelIndex, ppuX = ppuX, isTranslucentBackgroundPixel = translucent)
    }

    // ドット描画
    private fun drawLine(ppuMask: PPUMask, isShowBackground: Boolean, isShowSprite: Boolean, ppuX: Int, ppuY: Int) {
        val pixelIndex = NTSC_PPU_VISIBLE_LINE_X_COUNT * ppuY + ppuX
        // 描画モード別描画
        if (isShowBackground) {
            if (isShowSprite) {
                drawLineWithBGAndSprite(ppuMask = ppuMask, pixelIndex = pixelIndex, ppuX = ppuX)
            } else {
                drawLineWithBGOnly(ppuMask = ppuMask, pixelIndex = pixelIndex, ppuX = ppuX)
            }
        } else if (isShowSprite) {
            drawLineWithSpriteOnly(ppuMask = ppuMask, pixelIndex = pixelIndex, ppuX = ppuX)
        } else {
            drawLineTranslucent(ppuMask = ppuMask, pixelIndex = pixelIndex)
        }
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
