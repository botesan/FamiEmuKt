package jp.mito.famiemukt.test.cpu

import jp.mito.famiemukt.emurator.cpu.Instructions
import jp.mito.famiemukt.emurator.cpu.addressing.Addressing
import jp.mito.famiemukt.emurator.cpu.instruction.*
import kotlin.test.Test
import kotlin.test.assertEquals

class InstructionsTest {
    @Test
    fun testInstructionsCount() {
        assertEquals(actual = 256, expected = Instructions.size)
    }

    private fun checkInstruction(code: Int, opCode: OpCode, addressing: Addressing, cycle: Int) {
        Instructions[code].also { operator ->
            val codeHex = code.toString(radix = 16).padStart(length = 2, padChar = '0')
            val message = "[$codeHex] $opCode $addressing $cycle / $operator"
            assertEquals(actual = opCode, expected = operator.opCode, message = message)
            assertEquals(actual = addressing, expected = operator.addressing, message = message)
            assertEquals(actual = cycle, expected = operator.cycle, message = message)
        }
    }

    @Test
    fun testCommandHelloWorldNes() {
        // 0x78 SEI Implied
        checkInstruction(code = 0x78, opCode = SEI, addressing = Addressing.Implied, cycle = 2)
        // 0xA2 LDX Immediate
        checkInstruction(code = 0xA2, opCode = LDX, addressing = Addressing.Immediate, cycle = 2)
        // 0x9A TXS Implied
        checkInstruction(code = 0x9A, opCode = TXS, addressing = Addressing.Implied, cycle = 2)
        // 0xA9 LDA Immediate
        checkInstruction(code = 0xA9, opCode = LDA, addressing = Addressing.Immediate, cycle = 2)
        // 0x8D STA Absolute
        checkInstruction(code = 0x8D, opCode = STA, addressing = Addressing.Absolute, cycle = 4)
        // 0xA0 LDY Immediate
        checkInstruction(code = 0xA0, opCode = LDY, addressing = Addressing.Immediate, cycle = 2)
        // 0xBD LDA Absolute, X
        checkInstruction(code = 0xBD, opCode = LDA, addressing = Addressing.AbsoluteX, cycle = 4)
        // 0xE8 INX Implied
        checkInstruction(code = 0xE8, opCode = INX, addressing = Addressing.Implied, cycle = 2)
        // 0x88 DEY Implied
        checkInstruction(code = 0x88, opCode = DEY, addressing = Addressing.Implied, cycle = 2)
        // 0xD0 BNE Relative
        checkInstruction(code = 0xD0, opCode = BNE, addressing = Addressing.Relative, cycle = 2)
        // 0x4C JMP Absolute
        checkInstruction(code = 0x4C, opCode = JMP, addressing = Addressing.Absolute, cycle = 3)
    }

    @Test
    fun testLDA() {
        /*
        LDA メモリからAにロードします。[N.0.0.0.0.0.Z.0]
            アドレッシング	コード	バイト数	サイクル数
            Immediate	$A9	2	2
            Zeropage	$A5	2	3
            Zeropage, X	$B5	2	4
            Absolute	$AD	3	4
            Absolute, X	$BD	3	4
            Absolute, Y	$B9	3	4
            (Indirect, X)	$A1	2	6
            (Indirect), Y	$B1	2	5
         */
        checkInstruction(code = 0xa9, opCode = LDA, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xa5, opCode = LDA, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xb5, opCode = LDA, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0xad, opCode = LDA, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0xbd, opCode = LDA, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0xb9, opCode = LDA, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0xa1, opCode = LDA, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0xb1, opCode = LDA, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testLDX() {
        /*
        LDX メモリからXにロードします。[N.0.0.0.0.0.Z.0]
            アドレッシング	コード	バイト数	サイクル数
            Immediate	$A2	2	2
            Zeropage	$A6	2	3
            Zeropage, Y	$B6	2	4
            Absolute	$AE	3	4
            Absolute, Y	$BE	3	4
         */
        checkInstruction(code = 0xa2, opCode = LDX, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xa6, opCode = LDX, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xb6, opCode = LDX, addressing = Addressing.ZeroPageY, cycle = 4)
        checkInstruction(code = 0xae, opCode = LDX, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0xbe, opCode = LDX, addressing = Addressing.AbsoluteY, cycle = 4)
    }
}
