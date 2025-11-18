package jp.mito.famiemukt.emurator.cpu

import jp.mito.famiemukt.emurator.NTSC_CPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.cpu.addressing.fetch
import jp.mito.famiemukt.emurator.cpu.instruction.*
import jp.mito.famiemukt.emurator.cpu.logic.*
import jp.mito.famiemukt.emurator.dma.DMA
import jp.mito.famiemukt.emurator.util.toHex

class CPU(
    private val cpuRegisters: CPURegisters,
    private val cpuBus: CPUBus,
    private val dma: DMA,
) {
    data class CPUResult(
        val addCycle: Int = 0,
        val instruction: Instruction? = null,
        val interrupt: InterruptType? = null,
    ) {
        val executeCycle: Int
            get() = addCycle + (instruction?.cycle ?: 0) + (interrupt?.executeCycle ?: 0)
    }

    /** 電源投入時 */
    fun setPowerOnState() {
        // TODO: 電源投入時とリセットを分ける？
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

    /** DMA実行サイクル */
    private var dmaExecuteCycle: Int = 0

    /**
     * CPUクロックを１つ進める
     * @return 実行完了した命令
     */
    private fun executeCPUClockStep(): CPUResult? {
        // カウント
        totalCPUClockCount++
        executingCPUClockCount++
        // TODO: DMAの処理待ち／これで良い？
        if (dmaExecuteCycle > 0) {
            dmaExecuteCycle--
            executingCPUClockCount--
            return CPUResult(addCycle = 1)
        }
        // 割り込みがポーリングしていれば割り込みを実行（次の命令を実行する前に実行）
        // https://www.nesdev.org/wiki/CPU_interrupts
        val polledInterrupt = polledInterrupt
        if (polledInterrupt != null) {
            val executingInterrupt = executingInterrupt
            if (executingInterrupt != null) {
                // 割り込みの乗っ取り処理（IRQ <= NMI）IRQ実行後
                hijackInterruptIfNeeded(interrupt = executingInterrupt)
                // 割り込み処理の実行完了待ち
                if (executingCPUClockCount < executingInterrupt.executeCycle) return null
                // 実行完了
                clearInterruptRequest(type = executingInterrupt)
                hijackInterrupt?.also {
                    clearInterruptRequest(type = it)
                    hijackInterrupt = null
                }
                executingCPUClockCount = 0
                this.polledInterrupt = null
                this.executingInterrupt = null
                return CPUResult(interrupt = executingInterrupt)
            } else {
                // 割り込み実行
                executeInterrupt(type = polledInterrupt, cpuBus, cpuRegisters)
                executingCPUClockCount = 1
                this.executingInterrupt = polledInterrupt
                return null
            }
        }
        // フェッチ
        val fetchedInstruction = fetchedInstruction
        if (fetchedInstruction == null) {
            val opcode = fetch(bus = cpuBus, registers = cpuRegisters)
            val instruction = Instructions[opcode.toInt()]
            executingCPUClockCount = 1
            when (instruction.opCode) {
                JAM -> println("${instruction.opCode} instruction : ${opcode.toHex()} / $instruction / pc=${cpuRegisters.PC.toHex()}")
                else -> Unit
            }
            if (instruction.isUnofficial) {
                println("Unofficial instruction : ${opcode.toHex()} / $instruction / pc=${cpuRegisters.PC.toHex()}")
            }
            this.fetchedInstruction = instruction
            return null
        }
        // 割り込みの乗っ取り処理（BRK <= NMI,IRQ）BRK実行前
        hijackInterruptIfNeeded(instruction = fetchedInstruction)
        // 実行待ち
        if (executingCPUClockCount < fetchedInstruction.cycle) return null
        // 実行 or 実行中取得
        val executingInstruction = executingInstruction ?: run {
            // I/PCレジスター保持
            val beforeRegisterI = cpuRegisters.P.I
            val beforeRegisterPC = cpuRegisters.PC
            // 実行
            val addCycle = fetchedInstruction.opCode.execute(fetchedInstruction, cpuBus, cpuRegisters)
            val executingInstruction = CPUResult(addCycle = addCycle, instruction = fetchedInstruction)
            this.executingInstruction = executingInstruction
            // 割り込みのポーリング（実際は２サイクル前？）
            // https://www.nesdev.org/wiki/CPU_interrupts
            this.polledInterrupt = pollingInterrupt(
                beforeRegisterI = beforeRegisterI,
                beforeRegisterPC = beforeRegisterPC,
                instruction = fetchedInstruction,
            )
            // 割り込みの乗っ取り処理
            hijackInterrupt?.also {
                hijackInterruptIfNeeded(interrupt = it, instruction = fetchedInstruction)
                clearInterruptRequest(type = it)
                hijackInterrupt = null
            }
            // 実行結果返す
            executingInstruction
        }
        // 実行終了待ち
        if (executingCPUClockCount < executingInstruction.executeCycle) return null
        // 実行完了
        executingCPUClockCount = 0
        this.fetchedInstruction = null
        this.executingInstruction = null
        // TODO: APUの追加CPU cyclesも含めて取得していいのか確認
        dmaExecuteCycle = dma.getAndClearLastDMACycles(currentCycles = totalCPUClockCount)
        return executingInstruction
    }

    private fun hijackInterruptIfNeeded(interrupt: InterruptType? = null, instruction: Instruction? = null) {
        // BRK用の実行処理
        if (interrupt != null && instruction != null && instruction.opCode === BRK) {
            // BRK実行後状態、割り込みの乗っ取り処理（BRK <= NMI,IRQ）
            if (interrupt === InterruptType.NMI) {
                // println("BRK <= NMI 2")
                // 呼び出しアドレスをNMIに変更
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_NMI)
                // スタックのBフラグ解除
                val s = ProcessorStatus(value = pop(cpuBus, cpuRegisters)).apply { B = false }.value
                push(value = s, cpuBus, cpuRegisters)

            } else if (interrupt === InterruptType.IRQ) {
                //println("BRK <= IRQ 2")
                // BRK実行後状態、呼び出しアドレスをIRQに変更（実質変更無し）
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_IRQ)
                // スタックのBフラグ解除
                val s = ProcessorStatus(value = pop(cpuBus, cpuRegisters)).apply { B = false }.value
                push(value = s, cpuBus, cpuRegisters)
            }
            return
        }
        // 検出処理
        when {
            hijackInterrupt != null -> Unit
            // 1サイクルから4サイクルに上位の割り込みがあった場合、乗っ取られる
            // https://www.nesdev.org/wiki/CPU_interrupts#Interrupt_hijacking
            executingCPUClockCount !in 1..4 -> Unit
            // 割り込みの乗っ取り処理（IRQ <= NMI）
            interrupt === InterruptType.IRQ && isRequestedNMI -> {
                //println("IRQ <= NMI")
                // IRQ実行後状態、呼び出しアドレスをNMIに変更
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_NMI)
                // 乗っ取り割り込み保持
                hijackInterrupt = InterruptType.NMI
            }
            // 割り込みの乗っ取り処理（BRK <= NMI,IRQ）
            instruction != null && instruction.opCode === BRK -> {
                if (isRequestedNMI) {
                    //println("BRK <= NMI 1")
                    // BRK実行前状態、乗っ取り割り込み保持
                    hijackInterrupt = InterruptType.NMI
                } else if (isRequestedIRQ) {
                    //println("BRK <= IRQ 1")
                    // BRK実行前状態、乗っ取り割り込み保持
                    hijackInterrupt = InterruptType.IRQ
                }
            }
        }
    }

    private var isRequestedRESET: Boolean = false
    private var isRequestedNMI: Boolean = false
    var isRequestedIRQ: Boolean = false
        private set

    fun requestInterruptREST() {
        isRequestedRESET = true
    }

    fun requestInterruptNMI() {
        isRequestedNMI = true
        //println("requestInterruptNMI")
    }

    fun requestInterruptOnIRQ() {
        isRequestedIRQ = true
        //println("requestInterruptOnIRQ")
    }

    fun requestInterruptOffIRQ() {
        isRequestedIRQ = false
    }

    private fun pollingInterrupt(
        beforeRegisterI: Boolean,
        beforeRegisterPC: UShort,
        instruction: Instruction
    ): InterruptType? {
        // https://www.nesdev.org/wiki/CPU_interrupts#Branch_instructions_and_interrupts
        when (instruction.opCode) {
            // 分岐命令で、かつページまたぎをしていなければポーリングしない
            BCS, BCC,
            BEQ, BNE,
            BMI, BPL,
            BVC, BVS -> {
                if (beforeRegisterPC and 0xff00u == cpuRegisters.PC and 0xff00u) {
                    return null
                }
            }
            // 他の命令は次へ進む
            else -> Unit
        }
        // Iフラグに関係しない割り込み
        // https://www.nesdev.org/wiki/Status_flags#I:_Interrupt_Disable
        // When set, IRQ interrupts are inhibited. NMI, BRK, and reset are not affected.
        when {
            isRequestedRESET -> return InterruptType.RESET
            isRequestedNMI -> return InterruptType.NMI
            isRequestedIRQ -> Unit
            else -> return null
        }
        // IRQの割り込み無視チェック
        return when (instruction.opCode) {
            // 実行前のIフラグで判断
            CLI, SEI, PLP -> if (beforeRegisterI) null else InterruptType.IRQ
            // Iフラグで判断
            else -> if (cpuRegisters.P.I) null else InterruptType.IRQ
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
