package jp.mito.famiemukt.emurator.cpu

import jp.mito.famiemukt.emurator.NTSC_CPU_CYCLES_PER_MASTER_CLOCKS
import jp.mito.famiemukt.emurator.cpu.Addressing.*
import jp.mito.famiemukt.emurator.cpu.InterruptType.*
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

    private fun fetch(): UByte = cpuBus.readMemIO(address = (cpuRegisters.PC++).toInt())
    private fun fetchWord(): UShort = (fetch().toInt() or (fetch().toInt() shl 8)).toUShort()

    private data class FetchedOperand(val operand: Int, val addCycle: Int)

    private fun fetchOperand(addressing: Addressing): FetchedOperand =
        when (addressing) {
            Undefine -> error("Undefined addressing.")
            Implied -> error("$addressing not support operand.")
            Accumulator -> FetchedOperand(cpuRegisters.A.toInt(), 0)
            Immediate -> FetchedOperand(fetch().toInt(), 0)
            ZeroPage -> FetchedOperand(fetch().toInt(), 0)
            ZeroPageX -> FetchedOperand(((fetch() + cpuRegisters.X) and 0xFFU).toInt(), 0)
            ZeroPageY -> FetchedOperand(((fetch() + cpuRegisters.Y) and 0xFFU).toInt(), 0)
            Relative -> {
                val fetched = fetch().toByte()
                val base = cpuRegisters.PC.toInt()
                val result = fetched + base
                FetchedOperand(result and 0xFFFF, if ((base xor result) and 0xFF00 == 0) 0 else 1)
            }

            Absolute -> FetchedOperand(fetchWord().toInt(), 0)
            AbsoluteX -> {
                val base = fetchWord().toUInt()
                val result = base + cpuRegisters.X
                FetchedOperand(result.toInt() and 0xFFFF, if ((base xor result) and 0xFF00U == 0U) 0 else 1)
            }

            AbsoluteY -> {
                val base = fetchWord().toUInt()
                val result = base + cpuRegisters.Y
                FetchedOperand(result.toInt() and 0xFFFF, if ((base xor result) and 0xFF00U == 0U) 0 else 1)
            }
            // Indirect 繰り上げ等を行わない？
            Indirect -> {
                val address = fetchWord().toInt()
                val nextAddress = (address and 0xFF00) or ((address + 1) and 0x00FF)
                val l = cpuBus.readMemIO(address = address)
                val h = cpuBus.readMemIO(address = nextAddress)
                FetchedOperand(l.toInt() or (h.toInt() shl 8), 0)
            }
            // IndirectX 繰り上げ等を行わない？
            IndirectX -> {
                val address = ((fetch() + cpuRegisters.X) and 0xFFU).toInt()
                val nextAddress = (address and 0xFF00) or ((address + 1) and 0x00FF)
                val l = cpuBus.readMemIO(address = address)
                val h = cpuBus.readMemIO(address = nextAddress)
                FetchedOperand(l.toInt() or (h.toInt() shl 8), 0)
            }
            // IndirectY 繰り上げ等を行わない？
            IndirectY -> {
                val address = fetch().toInt()
                val nextAddress = (address and 0xFF00) or ((address + 1) and 0x00FF)
                val l = cpuBus.readMemIO(address = address)
                val h = cpuBus.readMemIO(address = nextAddress)
                val base = ((l.toUInt() or (h.toUInt() shl 8)))
                val result = base + cpuRegisters.Y
                FetchedOperand(result.toInt() and 0xFFFF, if (((base xor result) and 0xFF00U) == 0U) 0 else 1)
            }
        }

    private fun push(value: UByte) {
        cpuBus.writeMemIO(address = 0x0100 + cpuRegisters.S.toInt(), value = value)
        cpuRegisters.S--
    }

    private fun pop(): UByte {
        cpuRegisters.S++
        return cpuBus.readMemIO(address = 0x0100 + cpuRegisters.S.toInt())
    }

    private fun execute(instruction: Instruction): Int {
        var addCycle = 0
//        isBeforeRegisterI = cpuRegisters.P.I // TODO: 削除
        // http://hp.vector.co.jp/authors/VA042397/nes/6502.html
        // https://www.masswerk.at/6502/6502_instruction_set.html
        when (instruction.opCode) {
            OpCode.Undefine -> error("Undefined command.")
            /* LDA メモリからAにロードします。[N.0.0.0.0.0.Z.0] */
            OpCode.LDA -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val result = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                cpuRegisters.A = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* LDX メモリからXにロードします。[N.0.0.0.0.0.Z.0] */
            OpCode.LDX -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val result = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                cpuRegisters.X = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* LDY メモリからYにロードします。[N.0.0.0.0.0.Z.0] */
            OpCode.LDY -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val result = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                cpuRegisters.Y = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* STA Aからメモリにストアします。[0.0.0.0.0.0.0.0] */
            OpCode.STA -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                cpuBus.writeMemIO(address = fetched.operand, value = cpuRegisters.A)
            }
            /* STX Xからメモリにストアします。[0.0.0.0.0.0.0.0] */
            OpCode.STX -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                cpuBus.writeMemIO(address = fetched.operand, value = cpuRegisters.X)
            }
            /* STY Yからメモリにストアします。[0.0.0.0.0.0.0.0] */
            OpCode.STY -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                cpuBus.writeMemIO(address = fetched.operand, value = cpuRegisters.Y)
            }
            /* TAX AをXへコピーします。[N.0.0.0.0.0.Z.0] */
            OpCode.TAX -> {
                val result = cpuRegisters.A
                cpuRegisters.X = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* TAY AをYへコピーします。[N.0.0.0.0.0.Z.0] */
            OpCode.TAY -> {
                val result = cpuRegisters.A
                cpuRegisters.Y = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* TSX SをXへコピーします。[N.0.0.0.0.0.Z.0] */
            OpCode.TSX -> {
                val result = cpuRegisters.S
                cpuRegisters.X = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* TXA XをAへコピーします。[N.0.0.0.0.0.Z.0] */
            OpCode.TXA -> {
                val result = cpuRegisters.X
                cpuRegisters.A = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* TXS XをSへコピーします。[N.0.0.0.0.0.Z.0] → フラグを設定しない下記の記述が正しい？
               X -> SP  N Z C I D V
                        - - - - - - */
            OpCode.TXS -> {
                cpuRegisters.S = cpuRegisters.X
            }
            /* TYA YをAへコピーします。[N.0.0.0.0.0.Z.0] */
            OpCode.TYA -> {
                val result = cpuRegisters.Y
                cpuRegisters.A = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* ADC (A + メモリ + キャリーフラグ) を演算して結果をAへ返します。[N.V.0.0.0.0.Z.C] */
            OpCode.ADC -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val a = cpuRegisters.A
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val carry = if (cpuRegisters.P.C) 1 else 0
                val resultS = a.toByte() + memory.toByte() + carry
                val resultU = a + memory + carry.toUInt()
                cpuRegisters.A = resultU.toUByte()
                cpuRegisters.P.N = resultU.toByte() < 0
                cpuRegisters.P.V = (resultS < Byte.MIN_VALUE || resultS > Byte.MAX_VALUE)
                cpuRegisters.P.Z = (resultU.toByte() == 0.toByte())
                cpuRegisters.P.C = ((resultU and 0x100U) != 0U)
                addCycle += fetched.addCycle
            }
            /* AND Aとメモリを論理AND演算して結果をAへ返します。[N.0.0.0.0.0.Z.0] */
            OpCode.AND -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = cpuRegisters.A and memory
                cpuRegisters.A = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* ASL Aまたはメモリを左へシフトします。[N.0.0.0.0.0.Z.C] */
            OpCode.ASL -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val aOrMemory = if (instruction.addressing === Accumulator) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = aOrMemory.toUInt() shl 1
                if (instruction.addressing === Accumulator) {
                    cpuRegisters.A = result.toUByte()
                } else {
                    cpuBus.writeMemIO(address = fetched.operand, value = result.toUByte())
                }
                cpuRegisters.P.N = result.toUByte().toByte() < 0
                cpuRegisters.P.Z = (result.toUByte() == 0.toUByte())
                cpuRegisters.P.C = ((result and 0x100U) != 0U)
                addCycle += fetched.addCycle
            }
            /* BIT Aとメモリをビット比較演算します。[N.V.0.0.0.0.Z.0]
               bits 7 and 6 of operand are transfered to bit 7 and 6 of SR (N,V);
               the zero-flag is set to the result of operand AND accumulator.  */
            OpCode.BIT -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }.toUInt()
                val result = cpuRegisters.A.toUInt() and memory
                cpuRegisters.P.N = ((memory and 0b1000_0000U) != 0U)
                cpuRegisters.P.Z = (result == 0U)
                cpuRegisters.P.V = ((memory and 0b0100_0000U) != 0U)
                addCycle += fetched.addCycle
            }
            /* CMP Aとメモリを比較演算します。[N.0.0.0.0.0.Z.C]
               Compare Memory with Accumulator A - M */
            OpCode.CMP -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = cpuRegisters.A - memory
                cpuRegisters.P.N = result.toUByte().toByte() < 0
                cpuRegisters.P.Z = (result == 0U)
                cpuRegisters.P.C = ((result and 0x100U) == 0U)
                addCycle += fetched.addCycle
            }
            /* CPX Xとメモリを比較演算します。[N.0.0.0.0.0.Z.C]
               Compare Memory and Index X. X - M */
            OpCode.CPX -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = cpuRegisters.X - memory
                cpuRegisters.P.N = result.toUByte().toByte() < 0
                cpuRegisters.P.Z = (result == 0U)
                cpuRegisters.P.C = ((result and 0x100U) == 0U)
                addCycle += fetched.addCycle
            }
            /* CPY Yとメモリを比較演算します。[N.0.0.0.0.0.Z.C]
               Compare Memory and Index Y. Y - M */
            OpCode.CPY -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = cpuRegisters.Y - memory
                cpuRegisters.P.N = result.toUByte().toByte() < 0
                cpuRegisters.P.Z = (result == 0U)
                cpuRegisters.P.C = ((result and 0x100U) == 0U)
                addCycle += fetched.addCycle
            }
            /* DEC メモリをデクリメントします。[N.0.0.0.0.0.Z.0] */
            OpCode.DEC -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = cpuBus.readMemIO(address = fetched.operand)
                val result = (memory - 1U).toUByte()
                cpuBus.writeMemIO(address = fetched.operand, value = result)
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* DEX Xをデクリメントします。[N.0.0.0.0.0.Z.0] */
            OpCode.DEX -> {
                val result = (cpuRegisters.X - 1U).toUByte()
                cpuRegisters.X = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* DEY Yをデクリメントします。[N.0.0.0.0.0.Z.0] */
            OpCode.DEY -> {
                val result = (cpuRegisters.Y - 1U).toUByte()
                cpuRegisters.Y = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* EOR Aとメモリを論理XOR演算して結果をAへ返します。[N.0.0.0.0.0.Z.0] */
            OpCode.EOR -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = cpuRegisters.A xor memory
                cpuRegisters.A = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* INC メモリをインクリメントします。[N.0.0.0.0.0.Z.0] */
            OpCode.INC -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = cpuBus.readMemIO(address = fetched.operand)
                val result = (memory + 1U).toUByte()
                cpuBus.writeMemIO(address = fetched.operand, value = result)
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* INX Xをインクリメントします。[N.0.0.0.0.0.Z.0] */
            OpCode.INX -> {
                val result = (cpuRegisters.X + 1U).toUByte()
                cpuRegisters.X = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* INY Yをインクリメントします。[N.0.0.0.0.0.Z.0] */
            OpCode.INY -> {
                val result = (cpuRegisters.Y + 1U).toUByte()
                cpuRegisters.Y = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* LSR Aまたはメモリを右へシフトします。[N.0.0.0.0.0.Z.C]
               Shift One Bit Right (Memory or Accumulator) / 0 -> [76543210] -> C */
            OpCode.LSR -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val aOrMemory = if (instruction.addressing === Accumulator) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = aOrMemory.toUInt() shr 1
                if (instruction.addressing === Accumulator) {
                    cpuRegisters.A = result.toUByte()
                } else {
                    cpuBus.writeMemIO(address = fetched.operand, value = result.toUByte())
                }
                cpuRegisters.P.N = result.toUByte().toByte() < 0
                cpuRegisters.P.Z = (result == 0U)
                cpuRegisters.P.C = ((aOrMemory.toUInt() and 0x01U) != 0U)
            }
            /* ORA Aとメモリを論理OR演算して結果をAへ返します。[N.0.0.0.0.0.Z.0] */
            OpCode.ORA -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = cpuRegisters.A or memory
                cpuRegisters.A = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* ROL Aまたはメモリを左へローテートします。[N.0.0.0.0.0.Z.C]
               Rotate One Bit Left (Memory or Accumulator) / C <- [76543210] <- C */
            OpCode.ROL -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val aOrMemory = if (instruction.addressing === Accumulator) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = if (cpuRegisters.P.C) {
                    (aOrMemory.toUInt() shl 1) or 0x01U
                } else {
                    (aOrMemory.toUInt() shl 1)
                }
                if (instruction.addressing === Accumulator) {
                    cpuRegisters.A = result.toUByte()
                } else {
                    cpuBus.writeMemIO(address = fetched.operand, value = result.toUByte())
                }
                cpuRegisters.P.N = result.toUByte().toByte() < 0
                cpuRegisters.P.Z = (result.toUByte() == 0.toUByte())
                cpuRegisters.P.C = ((result and 0x100U) != 0U)
                addCycle += fetched.addCycle
            }
            /* ROR Aまたはメモリを右へローテートします。[N.0.0.0.0.0.Z.C]
               Rotate One Bit Right (Memory or Accumulator) / C -> [76543210] -> C */
            OpCode.ROR -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val aOrMemory = if (instruction.addressing === Accumulator) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val result = if (cpuRegisters.P.C) {
                    (aOrMemory.toUInt() shr 1) or 0b1000_0000U
                } else {
                    (aOrMemory.toUInt() shr 1)
                }
                if (instruction.addressing === Accumulator) {
                    cpuRegisters.A = result.toUByte()
                } else {
                    cpuBus.writeMemIO(address = fetched.operand, value = result.toUByte())
                }
                cpuRegisters.P.N = result.toUByte().toByte() < 0
                cpuRegisters.P.Z = (result == 0U)
                cpuRegisters.P.C = ((aOrMemory and 0x001U) != 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* SBC (A - メモリ - キャリーフラグの反転) を演算して結果をAへ返します。[N.V.0.0.0.0.Z.C] */
            OpCode.SBC -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val a = cpuRegisters.A
                val memory = if (instruction.addressing === Immediate) {
                    fetched.operand.toUByte()
                } else {
                    cpuBus.readMemIO(address = fetched.operand)
                }
                val carry = if (cpuRegisters.P.C.not()) 1 else 0
                val resultS = a.toByte() - memory.toByte() - carry
                val resultU = a - memory - carry.toUInt()
                cpuRegisters.A = resultU.toUByte()
                cpuRegisters.P.N = resultU.toByte() < 0
                cpuRegisters.P.V = (resultS < Byte.MIN_VALUE || resultS > Byte.MAX_VALUE)
                cpuRegisters.P.Z = (resultU.toByte() == 0.toByte())
                cpuRegisters.P.C = ((resultU and 0x100U) == 0U)
                addCycle += fetched.addCycle
            }
            /* PHA Aをスタックにプッシュダウンします。[0.0.0.0.0.0.0.0] */
            OpCode.PHA -> {
                push(value = cpuRegisters.A)
            }
            /* PHP Pをスタックにプッシュダウンします。[0.0.0.0.0.0.0.0]
               The status register will be pushed with the break flag and bit 5 set to 1. */
            OpCode.PHP -> {
                // break flagをセット、bit 5 は予約で 1 セット済み（のはず）
                val value = cpuRegisters.P.copy().apply { B = true }.value
                push(value = value)
            }
            /* PLA スタックからAにポップアップします。[N.0.0.0.0.0.Z.0] */
            OpCode.PLA -> {
                val result = pop()
                cpuRegisters.A = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
            }
            /* PLP スタックからPにポップアップします。[N.V.R.B.D.I.Z.C]
               The status register will be pulled with the break flag and bit 5 ignored. */
            OpCode.PLP -> {
                // break flag と bit 5 を無視（実質break flagを変更前で上書き）
                val value = pop()
                val result = ProcessorStatus(value = value).apply { B = cpuRegisters.P.B }.value
                cpuRegisters.P.value = result
            }
            /* JMP アドレスへジャンプします。[0.0.0.0.0.0.0.0] */
            OpCode.JMP -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                cpuRegisters.PC = fetched.operand.toUShort()
                addCycle += fetched.addCycle
//                printMovePCLog(command = "JMP") // TODO: ログ消去
            }
            /* JSR サブルーチンを呼び出します。[0.0.0.0.0.0.0.0] */
            OpCode.JSR -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                // PCを上位バイトからpush（JSRでpushするのは次の命令の手前のアドレス）
                val value = cpuRegisters.PC - 1U
                val h = (value shr 8).toUByte()
                val l = value.toUByte()
                push(value = h)
                push(value = l)
                cpuRegisters.PC = fetched.operand.toUShort()
                addCycle += fetched.addCycle
//                printMovePCLog(command = "JSR") // TODO: ログ消去
            }
            /* RTS サブルーチンから復帰します。[0.0.0.0.0.0.0.0] */
            OpCode.RTS -> {
                // 下位バイトからpopしてPCへ（JSRでpushされているのは次の命令の手前のアドレス）
                val l = pop()
                val h = pop()
                val value = ((h.toUInt() shl 8) or (l.toUInt())) + 1U
                cpuRegisters.PC = value.toUShort()
//                printMovePCLog(command = "RTS") // TODO: ログ消去
            }
            /* RTI 割り込みルーチンから復帰します。[N.V.R.B.D.I.Z.C]
               The status register is pulled with the break flag and bit 5 ignored.
               Then PC is pulled from the stack. */
            OpCode.RTI -> {
                // break flag と bit 5 を無視（実質break flagを変更前で上書き）
                val value = pop()
                val p = ProcessorStatus(value = value).apply { B = cpuRegisters.P.B }.value
                cpuRegisters.P.value = p
                // 下位バイトからpopしてPCへ
                val l = pop()
                val h = pop()
                val pc = (h.toUInt() shl 8) or (l.toUInt())
                cpuRegisters.PC = pc.toUShort()
//                printMovePCLog(command = "RTI") // TODO: ログ消去
            }
            /* BCC キャリーフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
            OpCode.BCC -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                if (cpuRegisters.P.C.not()) {
                    cpuRegisters.PC = fetched.operand.toUShort()
                    addCycle++
                    addCycle += fetched.addCycle
                }
            }
            /* BCS キャリーフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
            OpCode.BCS -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                if (cpuRegisters.P.C) {
                    cpuRegisters.PC = fetched.operand.toUShort()
                    addCycle++
                    addCycle += fetched.addCycle
                }
            }
            /* BEQ ゼロフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
            OpCode.BEQ -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                if (cpuRegisters.P.Z) {
                    cpuRegisters.PC = fetched.operand.toUShort()
                    addCycle++
                    addCycle += fetched.addCycle
                }
            }
            /* BMI ネガティブフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
            OpCode.BMI -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                if (cpuRegisters.P.N) {
                    cpuRegisters.PC = fetched.operand.toUShort()
                    addCycle++
                    addCycle += fetched.addCycle
                }
            }
            /* BNE ゼロフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
            OpCode.BNE -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                if (cpuRegisters.P.Z.not()) {
                    cpuRegisters.PC = fetched.operand.toUShort()
                    addCycle++
                    addCycle += fetched.addCycle
                }
            }
            /* BPL ネガティブフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
            OpCode.BPL -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                if (cpuRegisters.P.N.not()) {
                    cpuRegisters.PC = fetched.operand.toUShort()
                    addCycle++
                    addCycle += fetched.addCycle
                }
            }
            /* BVC オーバーフローフラグがクリアされている時にブランチします。[0.0.0.0.0.0.0.0] */
            OpCode.BVC -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                if (cpuRegisters.P.V.not()) {
                    cpuRegisters.PC = fetched.operand.toUShort()
                    addCycle++
                    addCycle += fetched.addCycle
                }
            }
            /* BVS オーバーフローフラグがセットされている時にブランチします。[0.0.0.0.0.0.0.0] */
            OpCode.BVS -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                if (cpuRegisters.P.V) {
                    cpuRegisters.PC = fetched.operand.toUShort()
                    addCycle++
                    addCycle += fetched.addCycle
                }
            }
            /* CLC キャリーフラグをクリアします。[0.0.0.0.0.0.0.C] */
            OpCode.CLC -> {
                cpuRegisters.P.C = false
                printMovePCLog(command = "CLC") // TODO: ログ消去
            }
            /* CLD BCDモードから通常モードに戻ります。ファミコンでは実装されていません。[0.0.0.0.D.0.0.0] */
            OpCode.CLD -> {
                cpuRegisters.P.D = false
            }
            /* CLI IRQ割り込みを許可します。[0.0.0.0.0.I.0.0] */
            OpCode.CLI -> {
                cpuRegisters.P.I = false
            }
            /* CLV オーバーフローフラグをクリアします。[0.V.0.0.0.0.0.0] */
            OpCode.CLV -> {
                cpuRegisters.P.V = false
            }
            /* SEC キャリーフラグをセットします。[0.0.0.0.0.0.0.C] */
            OpCode.SEC -> {
                cpuRegisters.P.C = true
            }
            /* SED BCDモードに設定します。ファミコンでは実装されていません。[0.0.0.0.D.0.0.0] */
            OpCode.SED -> {
                cpuRegisters.P.D = true
            }
            /* SEI IRQ割り込みを禁止します。[0.0.0.0.0.I.0.0] */
            OpCode.SEI -> {
                cpuRegisters.P.I = true
            }
            /* BRK ソフトウェア割り込みを起こします。[0.0.0.B.0.0.0.0] */
            OpCode.BRK -> {
                executeInterrupt(type = BRK)
            }
            /* NOP */
            OpCode.NOP -> {
                if (instruction.addressing !== Implied) {
                    // 未定義コマンド類
                    val fetched = fetchOperand(addressing = instruction.addressing)
                    addCycle += fetched.addCycle
                }
            }
            /* LAX LDA oper + LDX oper 非公式コマンド
               M -> A -> X
               N	Z	C	I	D	V
               +	+	-	-	-	-
               addressing	assembler	opc	bytes	cycles
               zeropage	LAX oper	A7	2	3
               zeropage,Y	LAX oper,Y	B7	2	4
               absolute	LAX oper	AF	3	4
               absolut,Y	LAX oper,Y	BF	3	4*
               (indirect,X)	LAX (oper,X)	A3	2	6
               (indirect),Y	LAX (oper),Y	B3	2	5* */
            OpCode.LAX -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val result = cpuBus.readMemIO(address = fetched.operand)
                cpuRegisters.A = result
                cpuRegisters.X = result
                cpuRegisters.P.N = result.toByte() < 0
                cpuRegisters.P.Z = (result == 0.toUByte())
                addCycle += fetched.addCycle
            }
            /* SAX (AXS, AAX) 非公式コマンド
               A and X are put on the bus at the same time (resulting effectively in an AND operation) and stored in M
               A AND X -> M
               N	Z	C	I	D	V
               -	-	-	-	-	-
               addressing	assembler	opc	bytes	cycles
               zeropage	SAX oper	87	2	3
               zeropage,Y	SAX oper,Y	97	2	4
               absolute	SAX oper	8F	3	4
               (indirect,X)	SAX (oper,X)	83	2	6 */
            OpCode.SAX -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val result = cpuRegisters.A and cpuRegisters.X
                cpuBus.writeMemIO(address = fetched.operand, value = result)
                addCycle += fetched.addCycle
            }
            /* DCP (DCM) DEC oper + CMP oper 非公式コマンド
               M - 1 -> M, A - M
               N	Z	C	I	D	V
               +	+	+	-	-	-
               addressing	assembler	opc	bytes	cycles
               zeropage	DCP oper	C7	2	5
               zeropage,X	DCP oper,X	D7	2	6
               absolute	DCP oper	CF	3	6
               absolut,X	DCP oper,X	DF	3	7
               absolut,Y	DCP oper,Y	DB	3	7
               (indirect,X)	DCP (oper,X)	C3	2	8
               (indirect),Y	DCP (oper),Y	D3	2	8 */
            OpCode.DCP -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = cpuBus.readMemIO(address = fetched.operand)
                val dec = (memory - 1U).toUByte()
                cpuBus.writeMemIO(address = fetched.operand, value = dec)
                val cmp = cpuRegisters.A - dec
                cpuRegisters.P.N = cmp.toUByte().toByte() < 0
                cpuRegisters.P.Z = (cmp == 0U)
                cpuRegisters.P.C = ((cmp and 0x100U) == 0U)
            }
            /* ISC (ISB, INS) INC oper + SBC oper 非公式コマンド (=ISB)
               M + 1 -> M, A - M - C -> A
               N	Z	C	I	D	V
               +	+	+	-	-	+
               addressing	assembler	opc	bytes	cycles
               zeropage	ISC oper	E7	2	5
               zeropage,X	ISC oper,X	F7	2	6
               absolute	ISC oper	EF	3	6
               absolut,X	ISC oper,X	FF	3	7
               absolut,Y	ISC oper,Y	FB	3	7
               (indirect,X)	ISC (oper,X)	E3	2	8
               (indirect),Y	ISC (oper),Y	F3	2	8 */
            OpCode.ISB -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = cpuBus.readMemIO(address = fetched.operand)
                val inc = (memory + 1U).toUByte()
                cpuBus.writeMemIO(address = fetched.operand, value = inc)
                val a = cpuRegisters.A
                val carry = if (cpuRegisters.P.C.not()) 1 else 0
                val sbcS = a.toByte() - inc.toByte() - carry
                val sbcU = a - inc - carry.toUInt()
                cpuRegisters.A = sbcU.toUByte()
                cpuRegisters.P.N = sbcU.toByte() < 0
                cpuRegisters.P.V = (sbcS < Byte.MIN_VALUE || sbcS > Byte.MAX_VALUE)
                cpuRegisters.P.Z = (sbcS == 0)
                cpuRegisters.P.C = ((sbcU and 0x100U) == 0U)
            }
            /* SLO (ASO) ASL oper + ORA oper 非公式コマンド
               M = C <- [76543210] <- 0, A OR M -> A
               N	Z	C	I	D	V
               +	+	+	-	-	-
               addressing	assembler	opc	bytes	cycles
               zeropage	SLO oper	07	2	5
               zeropage,X	SLO oper,X	17	2	6
               absolute	SLO oper	0F	3	6
               absolut,X	SLO oper,X	1F	3	7
               absolut,Y	SLO oper,Y	1B	3	7
               (indirect,X)	SLO (oper,X)	03	2	8
               (indirect),Y	SLO (oper),Y	13	2	8 */
            OpCode.SLO -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = cpuBus.readMemIO(address = fetched.operand)
                val asl = memory.toUInt() shl 1
                cpuBus.writeMemIO(address = fetched.operand, value = asl.toUByte())
                cpuRegisters.P.C = ((asl and 0x100U) != 0U)
                val opa = cpuRegisters.A or asl.toUByte()
                cpuRegisters.A = opa
                cpuRegisters.P.N = opa.toByte() < 0
                cpuRegisters.P.Z = (opa == 0.toUByte())
            }
            /* RLA ROL oper + AND oper 非公式コマンド
               M = C <- [76543210] <- C, A AND M -> A
               N	Z	C	I	D	V
               +	+	+	-	-	-
               addressing	assembler	opc	bytes	cycles
               zeropage	RLA oper	27	2	5
               zeropage,X	RLA oper,X	37	2	6
               absolute	RLA oper	2F	3	6
               absolut,X	RLA oper,X	3F	3	7
               absolut,Y	RLA oper,Y	3B	3	7
               (indirect,X)	RLA (oper,X)	23	2	8
               (indirect),Y	RLA (oper),Y	33	2	8 */
            OpCode.RLA -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = cpuBus.readMemIO(address = fetched.operand)
                val rol = (memory.toUInt() shl 1) or (if (cpuRegisters.P.C) 0x01U else 0x00U)
                cpuBus.writeMemIO(address = fetched.operand, value = rol.toUByte())
                cpuRegisters.P.C = ((rol and 0x100U) != 0U)
                val and = cpuRegisters.A and rol.toUByte()
                cpuRegisters.A = and
                cpuRegisters.P.N = and.toByte() < 0
                cpuRegisters.P.Z = (and == 0.toUByte())
            }
            /* SRE (LSE) LSR oper + EOR oper 非公式コマンド
               M = 0 -> [76543210] -> C, A EOR M -> A
               N	Z	C	I	D	V
               +	+	+	-	-	-
               addressing	assembler	opc	bytes	cycles
               zeropage	SRE oper	47	2	5
               zeropage,X	SRE oper,X	57	2	6
               absolute	SRE oper	4F	3	6
               absolut,X	SRE oper,X	5F	3	7
               absolut,Y	SRE oper,Y	5B	3	7
               (indirect,X)	SRE (oper,X)	43	2	8
               (indirect),Y	SRE (oper),Y	53	2	8  	*/
            OpCode.SRE -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = cpuBus.readMemIO(address = fetched.operand)
                val lsr = memory.toUInt() shr 1
                cpuBus.writeMemIO(address = fetched.operand, value = lsr.toUByte())
                cpuRegisters.P.C = ((memory and 0x01U) != 0.toUByte())
                val eor = cpuRegisters.A xor lsr.toUByte()
                cpuRegisters.A = eor
                cpuRegisters.P.N = eor.toByte() < 0
                cpuRegisters.P.Z = (eor == 0.toUByte())
            }
            /* RRA ROR oper + ADC oper 非公式コマンド
               M = C -> [76543210] -> C, A + M + C -> A, C
               N	Z	C	I	D	V
               +	+	+	-	-	+
               addressing	assembler	opc	bytes	cycles
               zeropage	RRA oper	67	2	5
               zeropage,X	RRA oper,X	77	2	6
               absolute	RRA oper	6F	3	6
               absolut,X	RRA oper,X	7F	3	7
               absolut,Y	RRA oper,Y	7B	3	7
               (indirect,X)	RRA (oper,X)	63	2	8
               (indirect),Y	RRA (oper),Y	73	2	8 */
            OpCode.RRA -> {
                val fetched = fetchOperand(addressing = instruction.addressing)
                val memory = cpuBus.readMemIO(address = fetched.operand)
                val ror = (memory.toUInt() shr 1) or (if (cpuRegisters.P.C) 0b1000_0000U else 0b0000_0000U)
                cpuBus.writeMemIO(address = fetched.operand, value = ror.toUByte())
                cpuRegisters.P.C = ((memory and 0x001U) != 0.toUByte())
                val a = cpuRegisters.A
                val carry = if (cpuRegisters.P.C) 1 else 0
                val resultS = a.toByte() + ror.toByte() + carry
                val resultU = a + ror + carry.toUInt()
                cpuRegisters.A = resultU.toUByte()
                cpuRegisters.P.N = resultU.toByte() < 0
                cpuRegisters.P.V = (resultS < Byte.MIN_VALUE || resultS > Byte.MAX_VALUE)
                cpuRegisters.P.Z = (resultS == 0)
                cpuRegisters.P.C = ((resultU and 0x100U) != 0U)
            }
            // 未実装（非公式）
            //else -> TODO("未実装 ${instruction.opCode}")
        }
        // 実行したオペコードを保持
        beforeExecutedOpCode = instruction.opCode
        // ログ用
        beforePC = cpuRegisters.PC
        // クロック数を返す
        return addCycle
    }

    /** 電源投入時 */
    fun setPowerOnState() {
        // TODO: 電源投入時とリセットを分ける？
        executeInterrupt(type = RESET)
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
                executeInterrupt(type = polledInterrupt)
                executingCPUClockCount = 1
                this.executingInterrupt = polledInterrupt
                return null
            }
        }
        // フェッチ
        val fetchedInstruction = fetchedInstruction
        if (fetchedInstruction == null) {
            val opcode = fetch()
            val instruction = Instructions[opcode.toInt()]
            executingCPUClockCount = 1
            if (instruction.opCode == OpCode.Undefine) {
                println("Undefine instruction : ${opcode.toHex()} / $instruction / pc=${cpuRegisters.PC.toHex()}")
            }
            if (instruction.isUnOfficial) {
                println("Un official instruction : ${opcode.toHex()} / $instruction / pc=${cpuRegisters.PC.toHex()}")
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
            val addCycle = execute(fetchedInstruction)
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
        if (interrupt != null && instruction != null && instruction.opCode === OpCode.BRK) {
            // BRK実行後状態、割り込みの乗っ取り処理（BRK <= NMI,IRQ）
            if (interrupt === NMI) {
//                println("BRK <= NMI 2") // TODO: ログ削除
                // 呼び出しアドレスをNMIに変更
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_NMI)
                // スタックのBフラグ解除
                val s = ProcessorStatus(value = pop()).apply { B = false }.value
                push(value = s)

            } else if (interrupt === IRQ) {
//                println("BRK <= IRQ 2") // TODO: ログ削除
                // BRK実行後状態、呼び出しアドレスをIRQに変更（実質変更無し）
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_IRQ)
                // スタックのBフラグ解除
                val s = ProcessorStatus(value = pop()).apply { B = false }.value
                push(value = s)
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
            interrupt === IRQ && isRequestedNMI -> {
//                println("IRQ <= NMI") // TODO: ログ削除
                // IRQ実行後状態、呼び出しアドレスをNMIに変更
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_NMI)
                // 乗っ取り割り込み保持
                hijackInterrupt = NMI
            }
            // 割り込みの乗っ取り処理（BRK <= NMI,IRQ）
            instruction != null && instruction.opCode === OpCode.BRK -> {
                if (isRequestedNMI) {
//                    println("BRK <= NMI 1") // TODO: ログ削除
                    // BRK実行前状態、乗っ取り割り込み保持
                    hijackInterrupt = NMI
                } else if (isRequestedIRQ) {
//                    println("BRK <= IRQ 1") // TODO: ログ削除
                    // BRK実行前状態、乗っ取り割り込み保持
                    hijackInterrupt = IRQ
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
//        println("requestInterruptNMI") // TODO: ログ消去
    }

    fun requestInterruptOnIRQ() {
        isRequestedIRQ = true
//        println("requestInterruptOnIRQ") // TODO: ログ消去
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
            OpCode.BCS, OpCode.BCC,
            OpCode.BEQ, OpCode.BNE,
            OpCode.BMI, OpCode.BPL,
            OpCode.BVC, OpCode.BVS -> {
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
            isRequestedRESET -> return RESET
            isRequestedNMI -> return NMI
            isRequestedIRQ -> Unit
            else -> return null
        }
        // IRQの割り込み無視チェック
        return when (instruction.opCode) {
            // 実行前のIフラグで判断
            OpCode.CLI, OpCode.SEI, OpCode.PLP -> if (beforeRegisterI) null else IRQ
            // Iフラグで判断
            else -> if (cpuRegisters.P.I) null else IRQ
        }
    }

    private fun clearInterruptRequest(type: InterruptType) {
        // リクエストフラグ解除
        when (type) {
            RESET -> isRequestedRESET = false
            NMI -> isRequestedNMI = false
            IRQ -> Unit
            BRK -> error("BRK")
        }
    }

    private fun executeInterrupt(type: InterruptType) {
        // タイプ毎に実行
        when (type) {
            RESET -> {
                // スタック強制移動（厳密にはBusが読み込み専用のままpushが動作するためらしい）
                // 下記サイトを見ると電源ONはその通りで単純なリセット時は違うっぽい（S -= 3）
                // https://www.pagetable.com/?p=410
                // 割り込み（リセット状態の反映）
                // https://www.nesdev.org/wiki/CPU_power_up_state
                // Initial CPU Register Values
                // Register | At Power      | After Reset
                // A, X, Y  | 0	            | unchanged
                // PC       | ($FFFC)       | ($FFFC)
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_RESET)
                // S[1]     | $00 - 3 = $FD | S -= 3
                cpuRegisters.S = 0xFDU // (cpuRegisters.S.toInt() - 3).toUByte() // 0xFDU
                // C        | 0             | unchanged
                // Z        | 0             | unchanged
                // I        | 1             | 1
                cpuRegisters.P.I = true
                // D        | 0             | unchanged
                // V        | 0             | unchanged
                // N        | 0             | unchanged
                printMovePCLog(command = ">RESET") // TODO: ログ消去
            }

            NMI -> {
                // PCを上位バイトからpush
                val pc = cpuRegisters.PC
                val h = (pc.toUInt() shr 8).toUByte()
                val l = pc.toUByte()
                push(value = h)
                push(value = l)
                // break flagをクリア、bit 5 は予約で 1 セット済み（のはず）
                val p = cpuRegisters.P.copy().apply { B = false }.value
                push(value = p)
                // 割り込み
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_NMI)
                cpuRegisters.P.I = true
                printMovePCLog(command = ">NMI") // TODO: ログ消去
            }

            IRQ -> {
                // PCを上位バイトからpush
                val pc = cpuRegisters.PC
                val h = (pc.toUInt() shr 8).toUByte()
                val l = pc.toUByte()
                push(value = h)
                push(value = l)
                // break flagをクリア、bit 5 は予約で 1 セット済み（のはず）
                val p = cpuRegisters.P.copy().apply { B = false }.value
                push(value = p)
                // 割り込み
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_IRQ)
                cpuRegisters.P.I = true
                printMovePCLog(command = ">IRQ") // TODO: ログ消去
            }
            /*
            https://www.masswerk.at/6502/6502_instruction_set.html#BRK
            BRK initiates a software interrupt similar to a hardware interrupt (IRQ).
            The return address pushed to the stack is PC+2, providing an extra byte of spacing for a break mark
            (identifying a reason for the break.)
            The status register will be pushed to the stack with the break flag set to 1.
            However, when retrieved during RTI or by a PLP instruction, the break flag will be ignored.
            The interrupt disable flag is not set automatically. // TODO: 読み間違い？
            interrupt, push PC+2, push SR
             */
            BRK -> {
                // PCを上位バイトからpush
                val pc = cpuRegisters.PC + (2U - 1U)
                val h = (pc shr 8).toUByte()
                val l = pc.toUByte()
                push(value = h)
                push(value = l)
                // break flagをセット、bit 5 は予約で 1 セット済み（のはず）
                val p = cpuRegisters.P.copy().apply { B = true }.value
                push(value = p)
                // 割り込み
                cpuRegisters.PC = cpuBus.readWordMemIO(address = INTERRUPT_ADDRESS_BRK)
                // TODO: 上記読み間違い？必要？
                //  Wiki側の記載では、どの割り込みでもセットされると書いてある
                // https://www.nesdev.org/wiki/Status_flags#I:_Interrupt_Disable
                // Automatically set by the CPU after pushing flags to the stack
                // when any interrupt is triggered (NMI, IRQ/BRK, or reset).
                // Restored to its previous state from the stack when leaving an interrupt handler with RTI.
                cpuRegisters.P.I = true
                printMovePCLog(command = ">BRK") // TODO: ログ消去
            }
        }
        beforePC = cpuRegisters.PC
    }

    fun debugInfo(nest: Int): String = buildString {
        append(" ".repeat(n = nest)).appendLine(cpuRegisters)
    }

    private var beforePC: UShort = 0u
    private var beforeExecutedOpCode: OpCode? = null
    private fun printMovePCLog(command: String) {
        if (false) {
            val r = cpuRegisters
            println("${beforePC.toHex()} : ${command.padEnd(length = 6)} ${r.PC.toHex()}  / S:${r.S.toHex()} / ${r.P.value.toHex()} / $beforeExecutedOpCode")
        }
    }

    companion object {
        private const val INTERRUPT_ADDRESS_RESET: Int = 0xFFFC
        private const val INTERRUPT_ADDRESS_NMI: Int = 0xFFFA
        private const val INTERRUPT_ADDRESS_IRQ: Int = 0xFFFE
        private const val INTERRUPT_ADDRESS_BRK: Int = 0xFFFE
    }
}
