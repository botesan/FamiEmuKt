package jp.mito.famiemukt.emurator.cpu.instruction

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction
import jp.mito.famiemukt.emurator.cpu.addressing.Addressing

// 非公式命令
// https://www.masswerk.at/6502/6502_instruction_set.html
//  "Illegal" Opcodes in Details
//   ALR (ASR) ANC ANC (ANC2) ANE (XAA) ARR DCP (DCM) ISC (ISB, INS) LAS (LAR) LAX LXA (LAX immediate) RLA RRA
//   SAX (AXS, AAX) SBX (AXS, SAX) SHA (AHX, AXA) SHX (A11, SXA, XAS) SHY (A11, SYA, SAY) SLO (ASO) SRE (LSE)
//   TAS (XAS, SHS) USBC (SBC) NOPs (including DOP, TOP) JAM (KIL, HLT)
// https://www.nesdev.org/6502_cpu.txt

/* ALR (ASR)    AND oper + LSR
    A AND oper, 0 -> [76543210] -> C
    N	Z	C	I	D	V
    +	+	+	-	-	-
    addressing	assembler	opc	bytes	cycles
    immediate	ALR #oper	4B	2	2 */
object ALR : UnofficialOpCode(name = "ALR", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val and = registers.A and memory
        val result = and.toUInt() shr 1
        registers.A = result.toUByte()
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0U)
        registers.P.C = ((and.toUInt() and 0x01U) != 0U)
        operand.recycle()
        return 0
    }
}

/* ANC    AND oper + set C as ASL
    A AND oper, bit(7) -> C
    N	Z	C	I	D	V
    +	+	+	-	-	-
    addressing	assembler	opc	bytes	cycles
    immediate	ANC #oper	0B	2	2

   ANC (ANC2)    AND oper + set C as ROL
    effectively the same as instr. 0B
    A AND oper, bit(7) -> C
    N	Z	C	I	D	V
    +	+	+	-	-	-
    addressing	assembler	opc	bytes	cycles
    immediate	ANC #oper	2B	2	2 */
object ANC : UnofficialOpCode(name = "ANC", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = registers.A and memory
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        registers.P.C = ((result.toUInt() and 0x80U) != 0U)
        operand.recycle()
        return 0
    }
}

/* ANE (XAA)    * OR X + AND oper
    Highly unstable, do not use.
    A base value in A is determined based on the contets of A and a constant, which may be typically $00, $ff, $ee, etc.
    The value of this constant depends on temerature, the chip series, and maybe other factors, as well.
    In order to eliminate these uncertaincies from the equation, use either 0 as the operand or a value of $FF in the accumulator.
    (A OR CONST) AND X AND oper -> A
    N	Z	C	I	D	V
    +	+	-	-	-	-
    addressing	assembler	opc	bytes	cycles
    immediate	ANE #oper	8B	2	2  	†† */
object ANE : UnofficialOpCode(name = "ANE", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val c = 0xff.toUByte() // 定数（環境依存。0x00,0xff,0xeeなど）
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = (registers.A or c) and registers.X and memory
        registers.A = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        operand.recycle()
        return 0
    }
}

/* ARR    AND oper + ROR
    This operation involves the adder:
    V-flag is set according to (A AND oper) + oper
    The carry is not set, but bit 7 (sign) is exchanged with the carry
    A AND oper, C -> [76543210] -> C
    N	Z	C	I	D	V
    +	+	+	-	-	+
    addressing	assembler	opc	bytes	cycles
    immediate	ARR #oper	6B	2	2
    ---
    ARR $6B         This instruction first performs an AND
                    between the accumulator and the immediate
                    parameter, then it shifts the accumulator to
                    the right. However, this is not the whole
                    truth. See the description below. */
object ARR : UnofficialOpCode(name = "ARR", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val and = registers.A and memory
        val result = if (registers.P.C) {
            (and.toUInt() shr 1) or 0b1000_0000U
        } else {
            (and.toUInt() shr 1)
        }
        registers.A = result.toUByte()
        registers.P.N = result.toByte() < 0
        // whenの記述だとテストに通る／コメントのソースだと解釈が間違っている？
        //val resultV = and.toByte() + memory.toByte()
        //registers.P.V = (resultV < Byte.MIN_VALUE || resultV > Byte.MAX_VALUE)
        when (and.toUInt() and 0xC0U) {
            0x40U, 0x80U -> registers.P.V = true
            0x00U, 0xC0U -> registers.P.V = false
        }
        registers.P.Z = (result.toByte() == 0.toByte())
        registers.P.C = ((and.toUInt() and 0x80U) != 0U)
        operand.recycle()
        return 0
    }
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
object DCP : UnofficialOpCode(name = "DCP", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = bus.readMemIO(address = operand.operand)
        val dec = (memory - 1U).toUByte()
        bus.writeMemIO(address = operand.operand, value = dec)
        val cmp = registers.A - dec
        registers.P.N = cmp.toUByte().toByte() < 0
        registers.P.Z = (cmp == 0U)
        registers.P.C = ((cmp and 0x100U) == 0U)
        operand.recycle()
        return 0
    }
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
object ISB : UnofficialOpCode(name = "ISB", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = bus.readMemIO(address = operand.operand)
        val inc = (memory + 1U).toUByte()
        bus.writeMemIO(address = operand.operand, value = inc)
        val a = registers.A
        val carry = if (registers.P.C.not()) 1 else 0
        val sbcS = a.toByte() - inc.toByte() - carry
        val sbcU = a - inc - carry.toUInt()
        registers.A = sbcU.toUByte()
        registers.P.N = sbcU.toByte() < 0
        registers.P.V = (sbcS < Byte.MIN_VALUE || sbcS > Byte.MAX_VALUE)
        registers.P.Z = (sbcS == 0)
        registers.P.C = ((sbcU and 0x100U) == 0U)
        operand.recycle()
        return 0
    }
}

/* LAS (LAR)    LDA/TSX oper
    M AND SP -> A, X, SP
    N	Z	C	I	D	V
    +	+	-	-	-	-
    addressing	assembler	opc	bytes	cycles
    absolute,Y	LAS oper,Y	BB	3	4* 	*/
object LAS : UnofficialOpCode(name = "LAS", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = memory and registers.S
        registers.A = result
        registers.X = result
        registers.S = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
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
object LAX : UnofficialOpCode(name = "LAX", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val result = bus.readMemIO(address = operand.operand)
        registers.A = result
        registers.X = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        val addCycle = operand.addCycle
        operand.recycle()
        return addCycle
    }
}

/* LXA (LAX immediate)    Store * AND oper in A and X
    Highly unstable, involves a 'magic' constant, see ANE
    (A OR CONST) AND oper -> A -> X
    N	Z	C	I	D	V
    +	+	-	-	-	-
    addressing	assembler	opc	bytes	cycles
    immediate	LXA #oper	AB	2	2  	††
   ---
   LXA $AB         C=Lehti:   A = X = ANE
                   Alternate: A = X = (A & #byte) */
object LXA : UnofficialOpCode(name = "LXA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val c = 0xff.toUByte() // 定数（環境依存。0x00,0xff,0xeeなど？）
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = (registers.A or c) and memory
        registers.A = result
        registers.X = result
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0.toUByte())
        operand.recycle()
        return 0
    }
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
object RLA : UnofficialOpCode(name = "RLA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = bus.readMemIO(address = operand.operand)
        val rol = (memory.toUInt() shl 1) or (if (registers.P.C) 0x01U else 0x00U)
        bus.writeMemIO(address = operand.operand, value = rol.toUByte())
        registers.P.C = ((rol and 0x100U) != 0U)
        val and = registers.A and rol.toUByte()
        registers.A = and
        registers.P.N = and.toByte() < 0
        registers.P.Z = (and == 0.toUByte())
        operand.recycle()
        return 0
    }
}

/* RRA  ROR oper + ADC oper 非公式コマンド
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
object RRA : UnofficialOpCode(name = "RRA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = bus.readMemIO(address = operand.operand)
        val ror = (memory.toUInt() shr 1) or (if (registers.P.C) 0b1000_0000U else 0b0000_0000U)
        instruction.addressing.write(ror.toUByte(), operand, bus, registers)
        registers.P.C = (memory and 0x001U) != 0.toUByte()
        val a = registers.A
        val carry = if (registers.P.C) 1 else 0
        val resultS = a.toByte() + ror.toByte() + carry
        val resultU = a + ror + carry.toUInt()
        registers.A = resultU.toUByte()
        registers.P.N = resultU.toByte() < 0
        registers.P.V = (resultS < Byte.MIN_VALUE || resultS > Byte.MAX_VALUE)
        registers.P.Z = (resultU.toByte() == 0.toByte())
        registers.P.C = ((resultU and 0x100U) != 0U)
        operand.recycle()
        return 0
    }
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
object SAX : UnofficialOpCode(name = "SAX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val result = registers.A and registers.X
        bus.writeMemIO(address = operand.operand, value = result)
        operand.recycle()
        return 0
    }
}

/* SBX (AXS, SAX)    CMP and DEX at once, sets flags like CMP
    (A AND X) - oper -> X
    N	Z	C	I	D	V
    +	+	+	-	-	-
    addressing	assembler	opc	bytes	cycles
    immediate	SBX #oper	CB	2	2
    ---
    SBX $CB         Carry and Decimal flags are ignored but the
                    Carry flag will be set in substraction. This
                    is due to the CMP command, which is executed
                    instead of the real SBC. */
object SBX : UnofficialOpCode(name = "SBX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = instruction.addressing.read(operand, bus)
        val result = (registers.A and registers.X) - memory
        registers.X = result.toUByte()
        registers.P.N = result.toByte() < 0
        registers.P.Z = (result == 0U)
        registers.P.C = ((result and 0x100U) == 0U)
        operand.recycle()
        return 0
    }
}

/* SHA (AHX, AXA)    Stores A AND X AND (high-byte of addr. + 1) at addr.
    unstable: sometimes 'AND (H+1)' is dropped, page boundary crossings may not work
    (with the high-byte of the value used as the high-byte of the address)
    A AND X AND (H+1) -> M
    N	Z	C	I	D	V
    -	-	-	-	-	-
    addressing	assembler	opc	bytes	cycles
    absolute,Y	SHA oper,Y	9F	3	5  	†
    (indirect),Y	SHA (oper),Y	93	2	6  	†
    ---
    SHA $93,$9F     Store (A & X & (ADDR_HI + 1))
                    Note: The value to be stored is copied also
                    to ADDR_HI if page boundary is crossed. */
object SHA : UnofficialOpCode(name = "SHA", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val result = (registers.A and registers.X and ((operand.operand ushr 8) + 1).toUByte())
        instruction.addressing.write(value = result, operand = operand, bus = bus, registers = registers)
        operand.recycle()
        return 0
    }
}

/* SHX (A11, SXA, XAS)    Stores X AND (high-byte of addr. + 1) at addr.
    unstable: sometimes 'AND (H+1)' is dropped, page boundary crossings may not work
    (with the high-byte of the value used as the high-byte of the address)
    X AND (H+1) -> M
    N	Z	C	I	D	V
    -	-	-	-	-	-
    addressing	assembler	opc	bytes	cycles
    absolute,Y	SHX oper,Y	9E	3	5  	†
    ---
    SHX $9E         Store (X & (ADDR_HI + 1))
                    Note: The value to be stored is copied also
                    to ADDR_HI if page boundary is crossed. */
object SHX : UnofficialOpCode(name = "SHX", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val result = (registers.X and ((operand.operand ushr 8) + 1).toUByte())
        if (operand.addCycle == 0) {
            instruction.addressing.write(value = result, operand = operand, bus = bus, registers = registers)
        } else {
            bus.writeMemIO(address = (registers.X.toUInt() shl 8).toInt(), value = result)
        }
        operand.recycle()
        return 0
    }
}

/* SHY (A11, SYA, SAY)    Stores Y AND (high-byte of addr. + 1) at addr.
    unstable: sometimes 'AND (H+1)' is dropped, page boundary crossings may not work
    (with the high-byte of the value used as the high-byte of the address)
    Y AND (H+1) -> M
    N	Z	C	I	D	V
    -	-	-	-	-	-
    addressing	assembler	opc	bytes	cycles
    absolute,X	SHY oper,X	9C	3	5  	†
    ---
    SHY $9C         Store (Y & (ADDR_HI + 1))
                    Note: The value to be stored is copied also
                    to ADDR_HI if page boundary is crossed. */
object SHY : UnofficialOpCode(name = "SHY", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val result = (registers.Y and ((operand.operand ushr 8) + 1).toUByte())
        if (operand.addCycle == 0) {
            instruction.addressing.write(value = result, operand = operand, bus = bus, registers = registers)
        } else {
            bus.writeMemIO(address = (registers.Y.toUInt() shl 8).toInt(), value = result)
        }
        operand.recycle()
        return 0
    }
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
object SLO : UnofficialOpCode(name = "SLO", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = bus.readMemIO(address = operand.operand)
        val asl = memory.toUInt() shl 1
        bus.writeMemIO(address = operand.operand, value = asl.toUByte())
        registers.P.C = ((asl and 0x100U) != 0U)
        val opa = registers.A or asl.toUByte()
        registers.A = opa
        registers.P.N = opa.toByte() < 0
        registers.P.Z = (opa == 0.toUByte())
        operand.recycle()
        return 0
    }
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
object SRE : UnofficialOpCode(name = "SRE", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val memory = bus.readMemIO(address = operand.operand)
        val lsr = memory.toUInt() shr 1
        bus.writeMemIO(address = operand.operand, value = lsr.toUByte())
        registers.P.C = ((memory and 0x01U) != 0.toUByte())
        val eor = registers.A xor lsr.toUByte()
        registers.A = eor
        registers.P.N = eor.toByte() < 0
        registers.P.Z = (eor == 0.toUByte())
        operand.recycle()
        return 0
    }
}

/* TAS (XAS, SHS)    Puts A AND X in SP and stores A AND X AND (high-byte of addr. + 1) at addr.
    unstable: sometimes 'AND (H+1)' is dropped, page boundary crossings may not work
    (with the high-byte of the value used as the high-byte of the address)
    A AND X -> SP, A AND X AND (H+1) -> M
    N	Z	C	I	D	V
    -	-	-	-	-	-
    addressing	assembler	opc	bytes	cycles
    absolute,Y	TAS oper,Y	9B	3	5  	†
    ---
    SHS $9B         SHA and TXS, where X is replaced by (A & X).
                    Note: The value to be stored is copied also
                    to ADDR_HI if page boundary is crossed. */
object TAS : UnofficialOpCode(name = "TAS", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int {
        val operand = instruction.addressing.operand(bus, registers)
        val ax = registers.A and registers.X
        registers.S = ax
        val result = ax and ((operand.operand ushr 8) + 1).toUByte()
        instruction.addressing.write(value = result, operand = operand, bus = bus, registers = registers)
        operand.recycle()
        return 0
    }
}

/* USBC (SBC) SBC oper + NOP
    effectively same as normal SBC immediate, instr. E9.
    A - M - C̅ -> A
    N	Z	C	I	D	V
    +	+	+	-	-	+
    addressing	assembler	opc	bytes	cycles
    immediate	USBC #oper	EB	2	2  	*/
object USBC : UnofficialOpCode(name = "SBC", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int =
        SBC.execute(instruction, bus, registers)
}

/* NOPs (including DOP, TOP)
   Instructions effecting in 'no operations' in various address modes. Operands are ignored.

   N	Z	C	I	D	V
   -	-	-	-	-	-
   opc	addressing	bytes	cycles
   1A	implied	1	2
   3A	implied	1	2
   5A	implied	1	2
   7A	implied	1	2
   DA	implied	1	2
   FA	implied	1	2
   80	immediate	2	2
   82	immediate	2	2
   89	immediate	2	2
   C2	immediate	2	2
   E2	immediate	2	2
   04	zeropage	2	3
   44	zeropage	2	3
   64	zeropage	2	3
   14	zeropage,X	2	4
   34	zeropage,X	2	4
   54	zeropage,X	2	4
   74	zeropage,X	2	4
   D4	zeropage,X	2	4
   F4	zeropage,X	2	4
   0C	absolute	3	4
   1C	absolute,X	3	4*
   3C	absolute,X	3	4*
   5C	absolute,X	3	4*
   7C	absolute,X	3	4*
   DC	absolute,X	3	4*
   FC	absolute,X	3	4* */
object NOPs : UnofficialOpCode(name = "NOP", isAddCyclePageCrossed = true) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int =
        when (instruction.addressing) {
            Addressing.Implied -> 0
            else -> instruction.addressing.operand(bus, registers).addCycle
        }
}

/* JAM (KIL, HLT)
   These instructions freeze the CPU.
   The processor will be trapped infinitely in T1 phase with $FF on the data bus. — Reset required.
   Instruction codes: 02, 12, 22, 32, 42, 52, 62, 72, 92, B2, D2, F2 */
object JAM : UnofficialOpCode(name = "JAM", isAddCyclePageCrossed = false) {
    override fun execute(instruction: Instruction, bus: CPUBus, registers: CPURegisters): Int = error(name)
}
