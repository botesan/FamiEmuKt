package jp.mito.famiemukt.emurator.cpu

import co.touchlab.kermit.Logger
import jp.mito.famiemukt.emurator.NTSC_CPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.cartridge.NothingStateM2CycleObserver
import jp.mito.famiemukt.emurator.cartridge.StateM2CycleObserver
import jp.mito.famiemukt.emurator.cpu.addressing.fetch
import jp.mito.famiemukt.emurator.cpu.instruction.BRK
import jp.mito.famiemukt.emurator.cpu.instruction.JAM
import jp.mito.famiemukt.emurator.cpu.instruction.RTI
import jp.mito.famiemukt.emurator.cpu.logic.INTERRUPT_ADDRESS_IRQ
import jp.mito.famiemukt.emurator.cpu.logic.INTERRUPT_ADDRESS_NMI
import jp.mito.famiemukt.emurator.cpu.logic.executeInterrupt
import jp.mito.famiemukt.emurator.dma.DMA
import jp.mito.famiemukt.emurator.util.toHex

class CPU(
    private val cpuRegisters: CPURegisters,
    private val cpuBus: CPUBus,
    private val dma: DMA,
    private val stateObserver: StateM2CycleObserver = NothingStateM2CycleObserver,
) {
    /** 電源投入時 */
    fun setPowerOnState() {
        executeInterrupt(type = InterruptType.RESET, cpuBus, cpuRegisters)
    }

    /** マスタークロックカウンター */
    private var masterClockCount: Int = 0

    /**
     * マスタークロックを１つ進める
     * @return 実行完了した命令
     */
    fun executeMasterClockStep(): CPUResult? {
        if (++masterClockCount >= NTSC_CPU_CYCLES_PER_MASTER_CLOCKS) {
            stateObserver.notifyM2OneCycle()
            val result = executeCPUClockStep()
            masterClockCount = 0
            return result
        }
        return null
    }

    /** トータルCPUクロックカウンター */
    private var totalCPUClockCount: Int = 0

    /** 実行中CPUクロックカウンター */
    private var executingCPUClockCount: Int = 0

    /** フェッチ命令 */
    private var fetchedInstruction: Instruction? = null

    /** 実行中命令 */
    private var executingInstruction: CPUResult? = null

    /** ポーリングした割り込み */
    private var polledInterrupt: InterruptType? = null

    /** 実行中の割り込み */
    private var executingInterrupt: InterruptType? = null

    /** 乗っ取り割り込み */
    private var hijackInterrupt: InterruptType? = null

    /**
     * CPUクロックを１つ進める
     * @return 実行完了した命令
     */
    private fun executeCPUClockStep(): CPUResult? {
        // カウント
        totalCPUClockCount++
        executingCPUClockCount++
        // DMAの処理待ち
        if (dma.executeCPUCycleStepIfNeed(cpuBus = cpuBus, currentCPUCycles = totalCPUClockCount)) {
            executingCPUClockCount--
            return CPUResult.obtain(addCycle = 1)
        }
        // 割り込みがポーリングしていれば割り込みを実行（次の命令を実行する前に実行）
        // https://www.nesdev.org/wiki/CPU_interrupts
        val fetchedInstruction = fetchedInstruction
        val polledInterrupt = polledInterrupt
        if (fetchedInstruction == null && polledInterrupt != null) {
            val executingInterrupt = executingInterrupt
            if (executingInterrupt != null) {
                // 割り込みの乗っ取り処理（IRQ <= NMI）IRQ実行前
                hijackInterruptIRQPrepare(interrupt = executingInterrupt)
                // 割り込み処理の実行完了待ち
                if (executingCPUClockCount < executingInterrupt.executeCycle) return null
                // 割り込み実行
                executeInterrupt(type = executingInterrupt, cpuBus, cpuRegisters)
//println("executingInterrupt=$executingInterrupt") // TODO: デバッグ用
                // 割り込みの乗っ取り処理（IRQ <= NMI）IRQ実行後
                hijackInterruptIRQIfNeeded(interrupt = executingInterrupt)
                executingCPUClockCount = 0
                this.polledInterrupt = null
                this.executingInterrupt = null
                return CPUResult.obtain(interrupt = executingInterrupt)
            } else {
                // 割り込み実行対象設定
                executingCPUClockCount = 1
                this.executingInterrupt = polledInterrupt
//println("this.executingInterrupt=${this.executingInterrupt}, polledInterrupt=$polledInterrupt") // TODO: デバッグ用
                return null
            }
        }
        // フェッチ
        if (fetchedInstruction == null) {
            val opcode = fetch(bus = cpuBus, registers = cpuRegisters)
            val instruction = Instructions[opcode.toInt()]
            // executingCPUClockCount = 1 // すでに 1 になっているはず
            when {
                instruction.opCode === JAM -> Logger.d { "${instruction.opCode} instruction : ${opcode.toHex()} / $instruction / pc=${cpuRegisters.PC.toHex()}" }
                instruction.isUnofficial -> Logger.d { "Unofficial instruction : ${opcode.toHex()} / $instruction / pc=${cpuRegisters.PC.toHex()}" }
                else -> Unit
            }
            this.fetchedInstruction = instruction
            // 割り込みのポーリング
            this.polledInterrupt = pollingInterrupt()
            //
            return null
        }
        // 割り込みの乗っ取り処理（BRK <= NMI,IRQ）BRK実行前
        hijackInterruptBRKPrepare(instruction = fetchedInstruction)
        // 割り込みのポーリング
        if (executingCPUClockCount < fetchedInstruction.pollInterruptCycle) this.polledInterrupt = pollingInterrupt()
        // 実行待ち
        if (executingCPUClockCount < fetchedInstruction.cycle) return null
        // 実行 or 実行中取得
        val executingInstruction = executingInstruction ?: run {
//if (this.polledInterrupt != null) println("this.polledInterrupt=${this.polledInterrupt}, executingInstruction=$executingInstruction") // TODO: デバッグ用
            // 実行
            val result = fetchedInstruction.opCode.execute(fetchedInstruction, cpuBus, cpuRegisters)
            val addCycle = result and 0x0000_FFFF
            val branched = (result and 0x0001_0000) != 0
            val executingInstruction = CPUResult.obtain(
                addCycle = addCycle,
                branched = branched,
                instruction = fetchedInstruction,
            )
            this.executingInstruction = executingInstruction
//if (isRequestedNMI) println("this.executingInstruction=${this.executingInstruction}") // TODO: デバッグ用
            if (fetchedInstruction.opCode === RTI) {
                // 割り込みのポーリング
                this.polledInterrupt = pollingInterrupt()
            }
//// TODO: デバッグ用
//if (this.polledInterrupt != null) println("this.polledInterrupt=${this.polledInterrupt}, totalCPUClockCount=$totalCPUClockCount, executingInstruction=$executingInstruction")
            // 割り込みの乗っ取り処理
            val hijackInterrupt = hijackInterrupt
            if (hijackInterrupt != null) {
//// TODO: デバッグ用
//if (this.polledInterrupt != null) println("hijackInterrupt=${hijackInterrupt}, totalCPUClockCount=$totalCPUClockCount, executingInstruction=$executingInstruction")
                hijackInterruptBRKIfNeeded(interrupt = hijackInterrupt, instruction = fetchedInstruction)
                // 乗っ取ったらポーリング無効
                this.polledInterrupt = null
            }
            // 実行結果返す
            executingInstruction
        }
        // 分岐命令のページまたぎ時の割り込みのポーリング
        /* https://www.nesdev.org/wiki/CPU_interrupts#Branch_instructions_and_interrupts
           Branch instructions and interrupts
           The branch instructions have more subtle interrupt polling behavior.
           Interrupts are always polled before the second CPU cycle (the operand fetch),
           but not before the third CPU cycle on a taken branch.
           Additionally, for taken branches that cross a page boundary, interrupts are polled before the PCH fixup cycle
           (see [1] for a tick-by-tick breakdown of the branch instructions).
           An interrupt being detected at either of these polling points (including only being detected at the first one)
           will trigger a CPU interrupt. */
        /* [1] https://www.nesdev.org/6502_cpu.txt
            Relative addressing (BCC, BCS, BNE, BEQ, BPL, BMI, BVC, BVS)
            #   address  R/W description
            --- --------- --- ---------------------------------------------
            1     PC      R  fetch opcode, increment PC
            2     PC      R  fetch operand, increment PC
            3     PC      R  Fetch opcode of next instruction,
                             If branch is taken, add operand to PCL.
                             Otherwise increment PC.
            4+    PC*     R  Fetch opcode of next instruction.
                             Fix PCH. If it did not change, increment PC.
            5!    PC      R  Fetch opcode of next instruction,
                             increment PC.
            Notes: The opcode fetch of the next instruction is included to
                  this diagram for illustration purposes. When determining
                  real execution times, remember to subtract the last
                  cycle.
                  * The high byte of Program Counter (PCH) may be invalid
                    at this time, i.e. it may be smaller or bigger by $100.
                  + If branch is taken, this cycle will be executed.
                  ! If branch occurs to different page, this cycle will be
                    executed. */
        if (executingInstruction.branched && executingInstruction.addCycle > 1) {
            if (executingCPUClockCount == executingInstruction.executeCycle - 1) {
                this.polledInterrupt = pollingInterrupt()
            }
        }
        // 実行終了待ち
        if (executingCPUClockCount < executingInstruction.executeCycle) return null
        // 実行完了
        executingCPUClockCount = 0
        this.fetchedInstruction = null
        this.executingInstruction = null
        return executingInstruction
    }

    private fun hijackInterruptBRKPrepare(instruction: Instruction) {
        // 検出処理
        when {
            // 1サイクルから4サイクルに上位の割り込みがあった場合、乗っ取られる
            // https://www.nesdev.org/wiki/CPU_interrupts#Interrupt_hijacking
            executingCPUClockCount !in 1..4 -> Unit
            // 割り込みの乗っ取り処理（BRK <= NMI,IRQ）
            instruction.opCode === BRK -> {
                if (isRequestedNMI) {
                    Logger.d { "BRK <= NMI 1 $totalCPUClockCount" }
                    // BRK実行前状態、乗っ取り割り込み保持
                    hijackInterrupt = InterruptType.NMI
                } else if (isRequestedIRQ) {
                    Logger.d { "BRK <= IRQ 1 $totalCPUClockCount" }
                    // BRK実行前状態、乗っ取り割り込み保持
                    hijackInterrupt = InterruptType.IRQ
                }
            }
        }
    }

    private fun hijackInterruptBRKIfNeeded(interrupt: InterruptType, instruction: Instruction) {
        // BRK用の実行処理
        val hijackInterrupt = hijackInterrupt
        if (instruction.opCode === BRK) {
            // BRK実行後状態、割り込みの乗っ取り処理（BRK <= NMI,IRQ）
            if (interrupt === InterruptType.NMI) {
                Logger.d { "BRK <= NMI 2 $totalCPUClockCount" }
                // 呼び出しアドレスをNMIに変更
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_NMI)
            } else if (interrupt === InterruptType.IRQ) {
                Logger.d { "BRK <= IRQ 2 $totalCPUClockCount" }
                // BRK実行後状態、呼び出しアドレスをIRQに変更（実質変更無し）
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_IRQ)
            }
        }
        if (hijackInterrupt != null) clearInterruptRequest(type = hijackInterrupt)
        clearInterruptRequest(type = interrupt)
        this.hijackInterrupt = null
    }

    private fun hijackInterruptIRQPrepare(interrupt: InterruptType) {
        // 検出処理
        when {
            // 1サイクルから4サイクルに上位の割り込みがあった場合、乗っ取られる
            // https://www.nesdev.org/wiki/CPU_interrupts#Interrupt_hijacking
            executingCPUClockCount !in 1..4 -> Unit
            // 割り込みの乗っ取り処理（IRQ <= NMI）
            interrupt === InterruptType.IRQ && isRequestedNMI -> {
                Logger.d { "IRQ <= NMI 1 $totalCPUClockCount" }
                // IQR実行前状態、乗っ取り割り込み保持
                hijackInterrupt = InterruptType.NMI
            }
        }
    }

    private fun hijackInterruptIRQIfNeeded(interrupt: InterruptType) {
        // IRQ用の実行処理
        val hijackInterrupt = hijackInterrupt
        when {
            // 割り込みの乗っ取り処理（IRQ <= NMI）
            interrupt === InterruptType.IRQ && hijackInterrupt === InterruptType.NMI -> {
                Logger.d { "IRQ <= NMI 2 $totalCPUClockCount" }
                // IRQ実行後状態、呼び出しアドレスをNMIに変更
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_NMI)
            }
        }
        if (hijackInterrupt != null) clearInterruptRequest(type = hijackInterrupt)
        clearInterruptRequest(type = interrupt)
        this.hijackInterrupt = null
    }

    private var isRequestedRESET: Boolean = false
    private var isRequestedNMI: Boolean = false
    var isRequestedIRQ: Boolean = false
        private set

    fun requestInterruptREST() {
        isRequestedRESET = true
    }

    fun requestInterruptNMI(levelLow: Boolean) {
        isRequestedNMI = levelLow
//println("requestInterruptNMI: levelLow=$levelLow, masterClockCount=$masterClockCount, totalCPUClockCount=$totalCPUClockCount, PC=${cpuRegisters.PC.toHex()}, executingCPUClockCount=$executingCPUClockCount, fetchedInstruction=${this.fetchedInstruction}, executingInstruction=${this.executingInstruction}, polledInterrupt=${this.polledInterrupt}") // TODO: デバッグ用
        if (levelLow.not()) return
        val fetchedInstruction = fetchedInstruction
        val executingInterrupt = executingInterrupt
        if (fetchedInstruction == null && executingInterrupt != null) {
//println("requestInterruptNMI: executingInterrupt=${executingInterrupt}") // TODO: デバッグ用
            // 割り込みの乗っ取り処理（IRQ <= NMI）IRQ実行前
            hijackInterruptIRQPrepare(interrupt = executingInterrupt)
        } else if (polledInterrupt == null && fetchedInstruction != null && executingCPUClockCount <= fetchedInstruction.pollInterruptCycle) {
            // 必要ならポーリングする
            if (masterClockCount == 0) {
                polledInterrupt = pollingInterrupt()
//println("requestInterruptNMI: polledInterrupt=${this.polledInterrupt}") // TODO: デバッグ用
            }
        }
    }

    fun requestInterruptOnIRQ() {
        isRequestedIRQ = true
//if (cpuRegisters.P.I.not()) println("requestInterruptOnIRQ: masterClockCount=$masterClockCount, totalCPUClockCount=$totalCPUClockCount, PC=${cpuRegisters.PC.toHex()}, I=${cpuRegisters.P.I}, executingCPUClockCount=$executingCPUClockCount, fetchedInstruction=${this.fetchedInstruction}, executingInstruction=${this.executingInstruction}, polledInterrupt=${this.polledInterrupt}") // TODO: デバッグ用
        // 必要ならポーリングする
        val fetchedInstruction = fetchedInstruction
        if (polledInterrupt == null && fetchedInstruction != null && executingCPUClockCount < fetchedInstruction.pollInterruptCycle) {
            if (masterClockCount == 0) {
                polledInterrupt = pollingInterrupt()
//println("requestInterruptOnIRQ: polledInterrupt=${this.polledInterrupt}") // TODO: デバッグ用
            }
        }
    }

    fun requestInterruptOffIRQ() {
        isRequestedIRQ = false
    }

    private fun pollingInterrupt(): InterruptType? {
        // Iフラグに関係しない割り込み
        // https://www.nesdev.org/wiki/Status_flags#I:_Interrupt_Disable
        // When set, IRQ interrupts are inhibited. NMI, BRK, and reset are not affected.
        when {
            isRequestedRESET -> return InterruptType.RESET
            isRequestedNMI -> {
//println("pollingInterrupt()=InterruptType.NM: masterClockCount=$masterClockCount, totalCPUClockCount=$totalCPUClockCount, PC=${cpuRegisters.PC.toHex()}") // TODO: デバッグ用
                return InterruptType.NMI
            }

            isRequestedIRQ -> Unit
            else -> return null
        }
        // IRQの割り込み無視チェック
        return if (cpuRegisters.P.I) null else {
//println("pollingInterrupt()=InterruptType.IRQ: masterClockCount=$masterClockCount, totalCPUClockCount=$totalCPUClockCount, PC=${cpuRegisters.PC.toHex()}") // TODO: デバッグ用
            InterruptType.IRQ
        }
    }

    private fun clearInterruptRequest(type: InterruptType) {
        // リクエストフラグ解除
        when (type) {
            InterruptType.RESET -> isRequestedRESET = false
            InterruptType.NMI -> isRequestedNMI = false
            InterruptType.IRQ -> Unit
            InterruptType.BRK -> error("BRK")
        }
    }

    fun debugInfo(nest: Int): String = buildString {
        append(" ".repeat(n = nest)).appendLine(cpuRegisters)
    }
}
