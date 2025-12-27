package jp.mito.famiemukt.test

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters
import jp.mito.famiemukt.emurator.cpu.Instruction
import jp.mito.famiemukt.emurator.cpu.Instructions
import jp.mito.famiemukt.emurator.cpu.addressing.Addressing
import jp.mito.famiemukt.emurator.cpu.instruction.*
import kotlin.test.*

class InstructionsTest {
    @BeforeTest
    fun setup() {
        Logger.setLogWriters(CommonWriter())
        Logger.setMinSeverity(Severity.Info)
    }

    @Test
    fun testInstructionsCount() {
        assertEquals(expected = 256, actual = Instructions.size)
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

    //------------------------------------------------------------------------------------------------------------------
    // Official
    //------------------------------------------------------------------------------------------------------------------

    @Test
    fun testADC() {
        /* https://www.nesdev.org/wiki/Instruction_reference#ADC
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$69	2	2
            Zero Page	$65	2	3
            Zero Page,X	$75	2	4
            Absolute	$6D	3	4
            Absolute,X	$7D	3	4 (5 if page crossed)
            Absolute,Y	$79	3	4 (5 if page crossed)
            (Indirect,X)	$61	2	6
            (Indirect),Y	$71	2	5 (6 if page crossed) */
        assertTrue(actual = ADC.isAddCyclePageCrossed)
        checkInstruction(code = 0x69, opCode = ADC, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0x65, opCode = ADC, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x75, opCode = ADC, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x6D, opCode = ADC, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0x7D, opCode = ADC, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0x79, opCode = ADC, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0x61, opCode = ADC, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0x71, opCode = ADC, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testAND() {
        /* https://www.nesdev.org/wiki/Instruction_reference#AND
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$29	2	2
            Zero Page	$25	2	3
            Zero Page,X	$35	2	4
            Absolute	$2D	3	4
            Absolute,X	$3D	3	4 (5 if page crossed)
            Absolute,Y	$39	3	4 (5 if page crossed)
            (Indirect,X)	$21	2	6
            (Indirect),Y	$31	2	5 (6 if page crossed) */
        assertTrue(actual = AND.isAddCyclePageCrossed)
        checkInstruction(code = 0x29, opCode = AND, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0x25, opCode = AND, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x35, opCode = AND, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x2D, opCode = AND, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0x3D, opCode = AND, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0x39, opCode = AND, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0x21, opCode = AND, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0x31, opCode = AND, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testASL() {
        /* https://www.nesdev.org/wiki/Instruction_reference#ASL
            Addressing mode	Opcode	Bytes	Cycles
            Accumulator	$0A	1	2
            Zero Page	$06	2	5
            Zero Page,X	$16	2	6
            Absolute	$0E	3	6
            Absolute,X	$1E	3	7 */
        assertFalse(actual = ASL.isAddCyclePageCrossed)
        checkInstruction(code = 0x0A, opCode = ASL, addressing = Addressing.Accumulator, cycle = 2)
        checkInstruction(code = 0x06, opCode = ASL, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0x16, opCode = ASL, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0x0E, opCode = ASL, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0x1E, opCode = ASL, addressing = Addressing.AbsoluteX, cycle = 7)
    }

    @Test
    fun testBCC() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BCC
            Addressing mode	Opcode	Bytes	Cycles
            Relative	$90	2	2 (3 if branch taken, 4 if page crossed)* */
        assertTrue(actual = BCC.isAddCyclePageCrossed)
        checkInstruction(code = 0x90, opCode = BCC, addressing = Addressing.Relative, cycle = 2)
    }

    @Test
    fun testBCS() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BCS
            Addressing mode	Opcode	Bytes	Cycles
            Relative	$B0	2	2 (3 if branch taken, 4 if page crossed)* */
        assertTrue(actual = BCS.isAddCyclePageCrossed)
        checkInstruction(code = 0xB0, opCode = BCS, addressing = Addressing.Relative, cycle = 2)
    }

    @Test
    fun testBEQ() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BEQ
            Addressing mode	Opcode	Bytes	Cycles
            Relative	$F0	2	2 (3 if branch taken, 4 if page crossed)* */
        assertTrue(actual = BEQ.isAddCyclePageCrossed)
        checkInstruction(code = 0xF0, opCode = BEQ, addressing = Addressing.Relative, cycle = 2)
    }

    @Test
    fun testBIT() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BIT
            Addressing mode	Opcode	Bytes	Cycles
            Zero page	$24	2	3
            Absolute	$2C	3	4 */
        assertFalse(actual = BIT.isAddCyclePageCrossed)
        checkInstruction(code = 0x24, opCode = BIT, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x2C, opCode = BIT, addressing = Addressing.Absolute, cycle = 4)
    }

    @Test
    fun testBMI() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BMI
            Addressing mode	Opcode	Bytes	Cycles
            Relative	$30	2	2 (3 if branch taken, 4 if page crossed)* */
        assertTrue(actual = BMI.isAddCyclePageCrossed)
        checkInstruction(code = 0x30, opCode = BMI, addressing = Addressing.Relative, cycle = 2)
    }

    @Test
    fun testBNE() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BNE
            Addressing mode	Opcode	Bytes	Cycles
            Relative	$D0	2	2 (3 if branch taken, 4 if page crossed)* */
        assertTrue(actual = BNE.isAddCyclePageCrossed)
        checkInstruction(code = 0xD0, opCode = BNE, addressing = Addressing.Relative, cycle = 2)
    }

    @Test
    fun testBPL() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BPL
            Addressing mode	Opcode	Bytes	Cycles
            Relative	$10	2	2 (3 if branch taken, 4 if page crossed)* */
        assertTrue(actual = BPL.isAddCyclePageCrossed)
        checkInstruction(code = 0x10, opCode = BPL, addressing = Addressing.Relative, cycle = 2)
    }

    @Test
    fun testBRK() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BRK
            Addressing mode	Opcode	Bytes	Cycles	Notes
            Implied	$00	1	7	Although BRK only uses 1 byte, its return address skips the following byte.
            #Immediate	$00	2	7	Because BRK skips the following byte, it is often considered a 2-byte instruction. */
        assertFalse(actual = BRK.isAddCyclePageCrossed)
        checkInstruction(code = 0x00, opCode = BRK, addressing = Addressing.Implied, cycle = 7)
    }

    @Test
    fun testBVC() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BVC
            Addressing mode	Opcode	Bytes	Cycles
            Relative	$50	2	2 (3 if branch taken, 4 if page crossed)* */
        assertTrue(actual = BVC.isAddCyclePageCrossed)
        checkInstruction(code = 0x50, opCode = BVC, addressing = Addressing.Relative, cycle = 2)
    }

    @Test
    fun testBVS() {
        /* https://www.nesdev.org/wiki/Instruction_reference#BVS
            Addressing mode	Opcode	Bytes	Cycles
            Relative	$70	2	2 (3 if branch taken, 4 if page crossed)* */
        assertTrue(actual = BVS.isAddCyclePageCrossed)
        checkInstruction(code = 0x70, opCode = BVS, addressing = Addressing.Relative, cycle = 2)
    }

    @Test
    fun testCLC() {
        /* https://www.nesdev.org/wiki/Instruction_reference#CLC
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$18	1	2 */
        assertFalse(actual = CLC.isAddCyclePageCrossed)
        checkInstruction(code = 0x18, opCode = CLC, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testCLD() {
        /* https://www.nesdev.org/wiki/Instruction_reference#CLD
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$D8	1	2 */
        assertFalse(actual = CLD.isAddCyclePageCrossed)
        checkInstruction(code = 0xD8, opCode = CLD, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testCLI() {
        /* https://www.nesdev.org/wiki/Instruction_reference#CLI
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$58	1	2 */
        assertFalse(actual = CLI.isAddCyclePageCrossed)
        checkInstruction(code = 0x58, opCode = CLI, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testCLV() {
        /* https://www.nesdev.org/wiki/Instruction_reference#CLV
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$B8	1	2 */
        assertFalse(actual = CLV.isAddCyclePageCrossed)
        checkInstruction(code = 0xB8, opCode = CLV, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testCMP() {
        /* https://www.nesdev.org/wiki/Instruction_reference#CMP
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$C9	2	2
            Zero Page	$C5	2	3
            Zero Page,X	$D5	2	4
            Absolute	$CD	3	4
            Absolute,X	$DD	3	4 (5 if page crossed)
            Absolute,Y	$D9	3	4 (5 if page crossed)
            (Indirect,X)	$C1	2	6
            (Indirect),Y	$D1	2	5 (6 if page crossed) */
        assertTrue(actual = CMP.isAddCyclePageCrossed)
        checkInstruction(code = 0xC9, opCode = CMP, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xC5, opCode = CMP, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xD5, opCode = CMP, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0xCD, opCode = CMP, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0xDD, opCode = CMP, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0xD9, opCode = CMP, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0xC1, opCode = CMP, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0xD1, opCode = CMP, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testCPX() {
        /* https://www.nesdev.org/wiki/Instruction_reference#CPX
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$E0	2	2
            Zero Page	$E4	2	3
            Absolute	$EC	3	4 */
        assertFalse(actual = CPX.isAddCyclePageCrossed)
        checkInstruction(code = 0xE0, opCode = CPX, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xE4, opCode = CPX, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xEC, opCode = CPX, addressing = Addressing.Absolute, cycle = 4)
    }

    @Test
    fun testCPY() {
        /* https://www.nesdev.org/wiki/Instruction_reference#CPY
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$C0	2	2
            Zero Page	$C4	2	3
            Absolute	$CC	3	4 */
        assertFalse(actual = CPY.isAddCyclePageCrossed)
        checkInstruction(code = 0xC0, opCode = CPY, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xC4, opCode = CPY, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xCC, opCode = CPY, addressing = Addressing.Absolute, cycle = 4)
    }

    @Test
    fun testDEC() {
        /* https://www.nesdev.org/wiki/Instruction_reference#DEC
            Addressing mode	Opcode	Bytes	Cycles
            Zero Page	$C6	2	5
            Zero Page,X	$D6	2	6
            Absolute	$CE	3	6
            Absolute,X	$DE	3	7 */
        assertFalse(actual = DEC.isAddCyclePageCrossed)
        checkInstruction(code = 0xC6, opCode = DEC, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0xD6, opCode = DEC, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0xCE, opCode = DEC, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0xDE, opCode = DEC, addressing = Addressing.AbsoluteX, cycle = 7)
    }

    @Test
    fun testDEX() {
        /* https://www.nesdev.org/wiki/Instruction_reference#DEX
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$CA	1	2 */
        assertFalse(actual = DEX.isAddCyclePageCrossed)
        checkInstruction(code = 0xCA, opCode = DEX, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testDEY() {
        /* https://www.nesdev.org/wiki/Instruction_reference#DEY
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$88	1	2 */
        assertFalse(actual = DEY.isAddCyclePageCrossed)
        checkInstruction(code = 0x88, opCode = DEY, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testEOR() {
        /* https://www.nesdev.org/wiki/Instruction_reference#EOR
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$49	2	2
            Zero Page	$45	2	3
            Zero Page,X	$55	2	4
            Absolute	$4D	3	4
            Absolute,X	$5D	3	4 (5 if page crossed)
            Absolute,Y	$59	3	4 (5 if page crossed)
            (Indirect,X)	$41	2	6
            (Indirect),Y	$51	2	5 (6 if page crossed) */
        assertTrue(actual = EOR.isAddCyclePageCrossed)
        checkInstruction(code = 0x49, opCode = EOR, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0x45, opCode = EOR, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x55, opCode = EOR, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x4D, opCode = EOR, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0x5D, opCode = EOR, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0x59, opCode = EOR, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0x41, opCode = EOR, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0x51, opCode = EOR, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testINC() {
        /* https://www.nesdev.org/wiki/Instruction_reference#INC
            Addressing mode	Opcode	Bytes	Cycles
            Zero Page	$E6	2	5
            Zero Page,X	$F6	2	6
            Absolute	$EE	3	6
            Absolute,X	$FE	3	7 */
        assertFalse(actual = INC.isAddCyclePageCrossed)
        checkInstruction(code = 0xE6, opCode = INC, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0xF6, opCode = INC, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0xEE, opCode = INC, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0xFE, opCode = INC, addressing = Addressing.AbsoluteX, cycle = 7)
    }

    @Test
    fun testINX() {
        /* https://www.nesdev.org/wiki/Instruction_reference#INX
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$E8	1	2 */
        assertFalse(actual = INX.isAddCyclePageCrossed)
        checkInstruction(code = 0xE8, opCode = INX, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testINY() {
        /* https://www.nesdev.org/wiki/Instruction_reference#INY
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$C8	1	2 */
        assertFalse(actual = INY.isAddCyclePageCrossed)
        checkInstruction(code = 0xC8, opCode = INY, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testJMP() {
        /* https://www.nesdev.org/wiki/Instruction_reference#JMP
            Addressing mode	Opcode	Bytes	Cycles
            Absolute	$4C	3	3
            (Indirect)	$6C	3	5 */
        assertFalse(actual = JMP.isAddCyclePageCrossed)
        checkInstruction(code = 0x4C, opCode = JMP, addressing = Addressing.Absolute, cycle = 3)
        checkInstruction(code = 0x6C, opCode = JMP, addressing = Addressing.Indirect, cycle = 5)
    }

    @Test
    fun testJSR() {
        /* https://www.nesdev.org/wiki/Instruction_reference#JSR
            Addressing mode	Opcode	Bytes	Cycles
            Absolute	$20	3	6 */
        assertFalse(actual = JSR.isAddCyclePageCrossed)
        checkInstruction(code = 0x20, opCode = JSR, addressing = Addressing.Absolute, cycle = 6)
    }

    @Test
    fun testLDA() {
        /* https://www.nesdev.org/wiki/Instruction_reference#LDA
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$A9	2	2
            Zero Page	$A5	2	3
            Zero Page,X	$B5	2	4
            Absolute	$AD	3	4
            Absolute,X	$BD	3	4 (5 if page crossed)
            Absolute,Y	$B9	3	4 (5 if page crossed)
            (Indirect,X)	$A1	2	6
            (Indirect),Y	$B1	2	5 (6 if page crossed) */
        assertTrue(actual = LDA.isAddCyclePageCrossed)
        checkInstruction(code = 0xA9, opCode = LDA, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xA5, opCode = LDA, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xB5, opCode = LDA, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0xAD, opCode = LDA, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0xBD, opCode = LDA, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0xB9, opCode = LDA, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0xA1, opCode = LDA, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0xB1, opCode = LDA, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testLDX() {
        /* https://www.nesdev.org/wiki/Instruction_reference#LDX
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$A2	2	2
            Zero Page	$A6	2	3
            Zero Page,Y	$B6	2	4
            Absolute	$AE	3	4
            Absolute,Y	$BE	3	4 (5 if page crossed) */
        assertTrue(actual = LDX.isAddCyclePageCrossed)
        checkInstruction(code = 0xA2, opCode = LDX, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xA6, opCode = LDX, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xB6, opCode = LDX, addressing = Addressing.ZeroPageY, cycle = 4)
        checkInstruction(code = 0xAE, opCode = LDX, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0xBE, opCode = LDX, addressing = Addressing.AbsoluteY, cycle = 4)
    }

    @Test
    fun testLDY() {
        /* https://www.nesdev.org/wiki/Instruction_reference#LDY
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$A0	2	2
            Zero Page	$A4	2	3
            Zero Page,X	$B4	2	4
            Absolute	$AC	3	4
            Absolute,X	$BC	3	4 (5 if page crossed) */
        assertTrue(actual = LDY.isAddCyclePageCrossed)
        checkInstruction(code = 0xA0, opCode = LDY, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xA4, opCode = LDY, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xB4, opCode = LDY, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0xAC, opCode = LDY, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0xBC, opCode = LDY, addressing = Addressing.AbsoluteX, cycle = 4)
    }

    @Test
    fun testLSR() {
        /* https://www.nesdev.org/wiki/Instruction_reference#LSR
            Addressing mode	Opcode	Bytes	Cycles
            Accumulator	$4A	1	2
            Zero Page	$46	2	5
            Zero Page,X	$56	2	6
            Absolute	$4E	3	6
            Absolute,X	$5E	3	7 */
        assertFalse(actual = LSR.isAddCyclePageCrossed)
        checkInstruction(code = 0x4A, opCode = LSR, addressing = Addressing.Accumulator, cycle = 2)
        checkInstruction(code = 0x46, opCode = LSR, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0x56, opCode = LSR, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0x4E, opCode = LSR, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0x5E, opCode = LSR, addressing = Addressing.AbsoluteX, cycle = 7)
    }

    @Test
    fun testNOP() {
        /* https://www.nesdev.org/wiki/Instruction_reference#NOP
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$EA	1	2 */
        assertFalse(actual = NOP.isAddCyclePageCrossed)
        checkInstruction(code = 0xEA, opCode = NOP, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testORA() {
        /* https://www.nesdev.org/wiki/Instruction_reference#ORA
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$09	2	2
            Zero Page	$05	2	3
            Zero Page,X	$15	2	4
            Absolute	$0D	3	4
            Absolute,X	$1D	3	4 (5 if page crossed)
            Absolute,Y	$19	3	4 (5 if page crossed)
            (Indirect,X)	$01	2	6
            (Indirect),Y	$11	2	5 (6 if page crossed) */
        assertTrue(actual = ORA.isAddCyclePageCrossed)
        checkInstruction(code = 0x09, opCode = ORA, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0x05, opCode = ORA, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x15, opCode = ORA, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x0D, opCode = ORA, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0x1D, opCode = ORA, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0x19, opCode = ORA, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0x01, opCode = ORA, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0x11, opCode = ORA, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testPHA() {
        /* https://www.nesdev.org/wiki/Instruction_reference#PHA
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$48	1	3 */
        assertFalse(actual = PHA.isAddCyclePageCrossed)
        checkInstruction(code = 0x48, opCode = PHA, addressing = Addressing.Implied, cycle = 3)
    }

    @Test
    fun testPHP() {
        /* https://www.nesdev.org/wiki/Instruction_reference#PHP
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$08	1	3 */
        assertFalse(actual = PHP.isAddCyclePageCrossed)
        checkInstruction(code = 0x08, opCode = PHP, addressing = Addressing.Implied, cycle = 3)
    }

    @Test
    fun testPLA() {
        /* https://www.nesdev.org/wiki/Instruction_reference#PLA
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$68	1	4 */
        assertFalse(actual = PLA.isAddCyclePageCrossed)
        checkInstruction(code = 0x68, opCode = PLA, addressing = Addressing.Implied, cycle = 4)
    }

    @Test
    fun testPLP() {
        /* https://www.nesdev.org/wiki/Instruction_reference#PLP
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$28	1	4 */
        assertFalse(actual = PLP.isAddCyclePageCrossed)
        checkInstruction(code = 0x28, opCode = PLP, addressing = Addressing.Implied, cycle = 4)
    }

    @Test
    fun testROL() {
        /* https://www.nesdev.org/wiki/Instruction_reference#ROL
            Addressing mode	Opcode	Bytes	Cycles
            Accumulator	$2A	1	2
            Zero Page	$26	2	5
            Zero Page,X	$36	2	6
            Absolute	$2E	3	6
            Absolute,X	$3E	3	7 */
        assertFalse(actual = ROL.isAddCyclePageCrossed)
        checkInstruction(code = 0x2A, opCode = ROL, addressing = Addressing.Accumulator, cycle = 2)
        checkInstruction(code = 0x26, opCode = ROL, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0x36, opCode = ROL, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0x2E, opCode = ROL, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0x3E, opCode = ROL, addressing = Addressing.AbsoluteX, cycle = 7)
    }

    @Test
    fun testROR() {
        /* https://www.nesdev.org/wiki/Instruction_reference#ROR
            Addressing mode	Opcode	Bytes	Cycles
            Accumulator	$6A	1	2
            Zero Page	$66	2	5
            Zero Page,X	$76	2	6
            Absolute	$6E	3	6
            Absolute,X	$7E	3	7 */
        assertFalse(actual = ROR.isAddCyclePageCrossed)
        checkInstruction(code = 0x6A, opCode = ROR, addressing = Addressing.Accumulator, cycle = 2)
        checkInstruction(code = 0x66, opCode = ROR, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0x76, opCode = ROR, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0x6E, opCode = ROR, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0x7E, opCode = ROR, addressing = Addressing.AbsoluteX, cycle = 7)
    }

    @Test
    fun testRTI() {
        /* https://www.nesdev.org/wiki/Instruction_reference#RTI
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$40	1	6 */
        assertFalse(actual = RTI.isAddCyclePageCrossed)
        checkInstruction(code = 0x40, opCode = RTI, addressing = Addressing.Implied, cycle = 6)
    }

    @Test
    fun testRTS() {
        /* https://www.nesdev.org/wiki/Instruction_reference#RTS
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$60	1	6 */
        assertFalse(actual = RTS.isAddCyclePageCrossed)
        checkInstruction(code = 0x60, opCode = RTS, addressing = Addressing.Implied, cycle = 6)
    }

    @Test
    fun testSBC() {
        /* https://www.nesdev.org/wiki/Instruction_reference#SBC
            Addressing mode	Opcode	Bytes	Cycles
            #Immediate	$E9	2	2
            Zero Page	$E5	2	3
            Zero Page,X	$F5	2	4
            Absolute	$ED	3	4
            Absolute,X	$FD	3	4 (5 if page crossed)
            Absolute,Y	$F9	3	4 (5 if page crossed)
            (Indirect,X)	$E1	2	6
            (Indirect),Y	$F1	2	5 (6 if page crossed) */
        assertTrue(actual = SBC.isAddCyclePageCrossed)
        checkInstruction(code = 0xE9, opCode = SBC, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xE5, opCode = SBC, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xF5, opCode = SBC, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0xED, opCode = SBC, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0xFD, opCode = SBC, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0xF9, opCode = SBC, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0xE1, opCode = SBC, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0xF1, opCode = SBC, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testSEC() {
        /* https://www.nesdev.org/wiki/Instruction_reference#SEC
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$38	1	2 */
        assertFalse(actual = SEC.isAddCyclePageCrossed)
        checkInstruction(code = 0x38, opCode = SEC, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testSED() {
        /* https://www.nesdev.org/wiki/Instruction_reference#SED
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$F8	1	2 */
        assertFalse(actual = SED.isAddCyclePageCrossed)
        checkInstruction(code = 0xF8, opCode = SED, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testSEI() {
        /* https://www.nesdev.org/wiki/Instruction_reference#SEI
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$78	1	2 */
        assertFalse(actual = SEI.isAddCyclePageCrossed)
        checkInstruction(code = 0x78, opCode = SEI, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testSTA() {
        /* https://www.nesdev.org/wiki/Instruction_reference#STA
            Addressing mode	Opcode	Bytes	Cycles
            Zero Page	$85	2	3
            Zero Page,X	$95	2	4
            Absolute	$8D	3	4
            Absolute,X	$9D	3	5
            Absolute,Y	$99	3	5
            (Indirect,X)	$81	2	6
            (Indirect),Y	$91	2	6 */
        assertFalse(actual = STA.isAddCyclePageCrossed)
        checkInstruction(code = 0x85, opCode = STA, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x95, opCode = STA, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x8D, opCode = STA, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0x9D, opCode = STA, addressing = Addressing.AbsoluteX, cycle = 5)
        checkInstruction(code = 0x99, opCode = STA, addressing = Addressing.AbsoluteY, cycle = 5)
        checkInstruction(code = 0x81, opCode = STA, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0x91, opCode = STA, addressing = Addressing.IndirectY, cycle = 6)
    }

    @Test
    fun testSTX() {
        /* https://www.nesdev.org/wiki/Instruction_reference#STX
            Addressing mode	Opcode	Bytes	Cycles
            Zero Page	$86	2	3
            Zero Page,Y	$96	2	4
            Absolute	$8E	3	4 */
        assertFalse(actual = STX.isAddCyclePageCrossed)
        checkInstruction(code = 0x86, opCode = STX, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x96, opCode = STX, addressing = Addressing.ZeroPageY, cycle = 4)
        checkInstruction(code = 0x8E, opCode = STX, addressing = Addressing.Absolute, cycle = 4)
    }

    @Test
    fun testSTY() {
        /* https://www.nesdev.org/wiki/Instruction_reference#STY
            Addressing mode	Opcode	Bytes	Cycles
            Zero Page	$84	2	3
            Zero Page,X	$94	2	4
            Absolute	$8C	3	4 */
        assertFalse(actual = STY.isAddCyclePageCrossed)
        checkInstruction(code = 0x84, opCode = STY, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x94, opCode = STY, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x8C, opCode = STY, addressing = Addressing.Absolute, cycle = 4)
    }

    @Test
    fun testTAX() {
        /* https://www.nesdev.org/wiki/Instruction_reference#TAX
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$AA	1	2 */
        assertFalse(actual = TAX.isAddCyclePageCrossed)
        checkInstruction(code = 0xAA, opCode = TAX, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testTAY() {
        /* https://www.nesdev.org/wiki/Instruction_reference#TAY
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$A8	1	2 */
        assertFalse(actual = TAY.isAddCyclePageCrossed)
        checkInstruction(code = 0xA8, opCode = TAY, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testTSX() {
        /* https://www.nesdev.org/wiki/Instruction_reference#TSX
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$BA	1	2 */
        assertFalse(actual = TSX.isAddCyclePageCrossed)
        checkInstruction(code = 0xBA, opCode = TSX, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testTXA() {
        /* https://www.nesdev.org/wiki/Instruction_reference#TXA
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$8A	1	2 */
        assertFalse(actual = TXA.isAddCyclePageCrossed)
        checkInstruction(code = 0x8A, opCode = TXA, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testTXS() {
        /* https://www.nesdev.org/wiki/Instruction_reference#TXS
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$9A	1	2 */
        assertFalse(actual = TXS.isAddCyclePageCrossed)
        checkInstruction(code = 0x9A, opCode = TXS, addressing = Addressing.Implied, cycle = 2)
    }

    @Test
    fun testTYA() {
        /* https://www.nesdev.org/wiki/Instruction_reference#TYA
            Addressing mode	Opcode	Bytes	Cycles
            Implied	$98	1	2 */
        assertFalse(actual = TYA.isAddCyclePageCrossed)
        checkInstruction(code = 0x98, opCode = TYA, addressing = Addressing.Implied, cycle = 2)
    }

    //------------------------------------------------------------------------------------------------------------------
    // Unofficial https://www.masswerk.at/6502/6502_instruction_set.html#illegals
    //------------------------------------------------------------------------------------------------------------------

    @Test
    fun testALR() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#ALR
            addressing	assembler	opc	bytes	cycles
            immediate	ALR #oper	4B	2	2  	 */
        assertFalse(actual = ALR.isAddCyclePageCrossed)
        checkInstruction(code = 0x4B, opCode = ALR, addressing = Addressing.Immediate, cycle = 2)
    }

    @Test
    fun testANC() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#ANC
            addressing	assembler	opc	bytes	cycles
            immediate	ANC #oper	0B	2	2
           https://www.masswerk.at/6502/6502_instruction_set.html#ANC2
            addressing	assembler	opc	bytes	cycles
            immediate	ANC #oper	2B	2	2   */
        assertFalse(actual = ANC.isAddCyclePageCrossed)
        checkInstruction(code = 0x0B, opCode = ANC, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0x2B, opCode = ANC, addressing = Addressing.Immediate, cycle = 2)
    }

    @Test
    fun testANE() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#ANE
            addressing	assembler	opc	bytes	cycles	1
            immediate	ANE #oper	8B	2	2  	††  */
        assertFalse(actual = ANE.isAddCyclePageCrossed)
        checkInstruction(code = 0x8B, opCode = ANE, addressing = Addressing.Immediate, cycle = 2)
    }

    @Test
    fun testARR() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#ARR
            addressing	assembler	opc	bytes	cycles
            immediate	ARR #oper	6B	2	2  	*/
        assertFalse(actual = ARR.isAddCyclePageCrossed)
        checkInstruction(code = 0x6B, opCode = ARR, addressing = Addressing.Immediate, cycle = 2)
    }

    @Test
    fun testDCP() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#DCP
            addressing	assembler	opc	bytes	cycles
            zeropage	DCP oper	C7	2	5
            zeropage,X	DCP oper,X	D7	2	6
            absolute	DCP oper	CF	3	6
            absolute,X	DCP oper,X	DF	3	7
            absolute,Y	DCP oper,Y	DB	3	7
            (indirect,X)	DCP (oper,X)	C3	2	8
            (indirect),Y	DCP (oper),Y	D3	2	8  	*/
        assertFalse(actual = DCP.isAddCyclePageCrossed)
        checkInstruction(code = 0xC7, opCode = DCP, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0xD7, opCode = DCP, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0xCF, opCode = DCP, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0xDF, opCode = DCP, addressing = Addressing.AbsoluteX, cycle = 7)
        checkInstruction(code = 0xDB, opCode = DCP, addressing = Addressing.AbsoluteY, cycle = 7)
        checkInstruction(code = 0xC3, opCode = DCP, addressing = Addressing.IndirectX, cycle = 8)
        checkInstruction(code = 0xD3, opCode = DCP, addressing = Addressing.IndirectY, cycle = 8)
    }

    @Test
    fun testISB() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#ISC
            addressing	assembler	opc	bytes	cycles
            zeropage	ISC oper	E7	2	5
            zeropage,X	ISC oper,X	F7	2	6
            absolute	ISC oper	EF	3	6
            absolute,X	ISC oper,X	FF	3	7
            absolute,Y	ISC oper,Y	FB	3	7
            (indirect,X)	ISC (oper,X)	E3	2	8
            (indirect),Y	ISC (oper),Y	F3	2	8  	*/
        assertFalse(actual = ISB.isAddCyclePageCrossed)
        checkInstruction(code = 0xE7, opCode = ISB, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0xF7, opCode = ISB, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0xEF, opCode = ISB, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0xFF, opCode = ISB, addressing = Addressing.AbsoluteX, cycle = 7)
        checkInstruction(code = 0xFB, opCode = ISB, addressing = Addressing.AbsoluteY, cycle = 7)
        checkInstruction(code = 0xE3, opCode = ISB, addressing = Addressing.IndirectX, cycle = 8)
        checkInstruction(code = 0xF3, opCode = ISB, addressing = Addressing.IndirectY, cycle = 8)
    }

    @Test
    fun testLAS() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#LAS
            addressing	assembler	opc	bytes	cycles
            absolute,Y	LAS oper,Y	BB	3	4* 	  	*/
        assertTrue(actual = LAS.isAddCyclePageCrossed)
        checkInstruction(code = 0xBB, opCode = LAS, addressing = Addressing.AbsoluteY, cycle = 4)
    }

    @Test
    fun testLAX() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#LAX
            addressing	assembler	opc	bytes	cycles
            zeropage	LAX oper	A7	2	3
            zeropage,Y	LAX oper,Y	B7	2	4
            absolute	LAX oper	AF	3	4
            absolute,Y	LAX oper,Y	BF	3	4*
            (indirect,X)	LAX (oper,X)	A3	2	6
            (indirect),Y	LAX (oper),Y	B3	2	5* 	*/
        assertTrue(actual = LAX.isAddCyclePageCrossed)
        checkInstruction(code = 0xA7, opCode = LAX, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0xB7, opCode = LAX, addressing = Addressing.ZeroPageY, cycle = 4)
        checkInstruction(code = 0xAF, opCode = LAX, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0xBF, opCode = LAX, addressing = Addressing.AbsoluteY, cycle = 4)
        checkInstruction(code = 0xA3, opCode = LAX, addressing = Addressing.IndirectX, cycle = 6)
        checkInstruction(code = 0xB3, opCode = LAX, addressing = Addressing.IndirectY, cycle = 5)
    }

    @Test
    fun testLXA() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#LXA
            addressing	assembler	opc	bytes	cycles
            immediate	LXA #oper	AB	2	2  	†† 	  	*/
        assertFalse(actual = LXA.isAddCyclePageCrossed)
        checkInstruction(code = 0xAB, opCode = LXA, addressing = Addressing.Immediate, cycle = 2)
    }

    @Test
    fun testRLA() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#RLA
            addressing	assembler	opc	bytes	cycles
            zeropage	RLA oper	27	2	5
            zeropage,X	RLA oper,X	37	2	6
            absolute	RLA oper	2F	3	6
            absolute,X	RLA oper,X	3F	3	7
            absolute,Y	RLA oper,Y	3B	3	7
            (indirect,X)	RLA (oper,X)	23	2	8
            (indirect),Y	RLA (oper),Y	33	2	8  	*/
        assertFalse(actual = RLA.isAddCyclePageCrossed)
        checkInstruction(code = 0x27, opCode = RLA, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0x37, opCode = RLA, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0x2F, opCode = RLA, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0x3F, opCode = RLA, addressing = Addressing.AbsoluteX, cycle = 7)
        checkInstruction(code = 0x3B, opCode = RLA, addressing = Addressing.AbsoluteY, cycle = 7)
        checkInstruction(code = 0x23, opCode = RLA, addressing = Addressing.IndirectX, cycle = 8)
        checkInstruction(code = 0x33, opCode = RLA, addressing = Addressing.IndirectY, cycle = 8)
    }

    @Test
    fun testRRA() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#RRA
            addressing	assembler	opc	bytes	cycles
            zeropage	RRA oper	67	2	5
            zeropage,X	RRA oper,X	77	2	6
            absolute	RRA oper	6F	3	6
            absolute,X	RRA oper,X	7F	3	7
            absolute,Y	RRA oper,Y	7B	3	7
            (indirect,X)	RRA (oper,X)	63	2	8
            (indirect),Y	RRA (oper),Y	73	2	8  	*/
        assertFalse(actual = RRA.isAddCyclePageCrossed)
        checkInstruction(code = 0x67, opCode = RRA, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0x77, opCode = RRA, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0x6F, opCode = RRA, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0x7F, opCode = RRA, addressing = Addressing.AbsoluteX, cycle = 7)
        checkInstruction(code = 0x7B, opCode = RRA, addressing = Addressing.AbsoluteY, cycle = 7)
        checkInstruction(code = 0x63, opCode = RRA, addressing = Addressing.IndirectX, cycle = 8)
        checkInstruction(code = 0x73, opCode = RRA, addressing = Addressing.IndirectY, cycle = 8)
    }

    @Test
    fun testSAX() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#SAX
            addressing	assembler	opc	bytes	cycles
            zeropage	SAX oper	87	2	3
            zeropage,Y	SAX oper,Y	97	2	4
            absolute	SAX oper	8F	3	4
            (indirect,X)	SAX (oper,X)	83	2	6  	*/
        assertFalse(actual = SAX.isAddCyclePageCrossed)
        checkInstruction(code = 0x87, opCode = SAX, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x97, opCode = SAX, addressing = Addressing.ZeroPageY, cycle = 4)
        checkInstruction(code = 0x8F, opCode = SAX, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0x83, opCode = SAX, addressing = Addressing.IndirectX, cycle = 6)
    }

    @Test
    fun testSBX() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#SBX
            addressing	assembler	opc	bytes	cycles
            immediate	SBX #oper	CB	2	2  	*/
        assertFalse(actual = SBX.isAddCyclePageCrossed)
        checkInstruction(code = 0xCB, opCode = SBX, addressing = Addressing.Immediate, cycle = 2)
    }

    @Test
    fun testSHX() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#SHX
            addressing	assembler	opc	bytes	cycles
            absolute,Y	SHX oper,Y	9E	3	5  	†  	*/
        assertFalse(actual = SHX.isAddCyclePageCrossed)
        checkInstruction(code = 0x9E, opCode = SHX, addressing = Addressing.AbsoluteY, cycle = 5)
    }

    @Test
    fun testSHY() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#SHY
            addressing	assembler	opc	bytes	cycles
            absolute,X	SHY oper,X	9C	3	5  	†  	*/
        assertFalse(actual = SHY.isAddCyclePageCrossed)
        checkInstruction(code = 0x9C, opCode = SHY, addressing = Addressing.AbsoluteX, cycle = 5)
    }

    @Test
    fun testSLO() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#SLO
            addressing	assembler	opc	bytes	cycles
            zeropage	SLO oper	07	2	5
            zeropage,X	SLO oper,X	17	2	6
            absolute	SLO oper	0F	3	6
            absolute,X	SLO oper,X	1F	3	7
            absolute,Y	SLO oper,Y	1B	3	7
            (indirect,X)	SLO (oper,X)	03	2	8
            (indirect),Y	SLO (oper),Y	13	2	8  	*/
        assertFalse(actual = SLO.isAddCyclePageCrossed)
        checkInstruction(code = 0x07, opCode = SLO, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0x17, opCode = SLO, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0x0F, opCode = SLO, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0x1F, opCode = SLO, addressing = Addressing.AbsoluteX, cycle = 7)
        checkInstruction(code = 0x1B, opCode = SLO, addressing = Addressing.AbsoluteY, cycle = 7)
        checkInstruction(code = 0x03, opCode = SLO, addressing = Addressing.IndirectX, cycle = 8)
        checkInstruction(code = 0x13, opCode = SLO, addressing = Addressing.IndirectY, cycle = 8)
    }

    @Test
    fun testSRE() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#SRE
            addressing	assembler	opc	bytes	cycles
            zeropage	SRE oper	47	2	5
            zeropage,X	SRE oper,X	57	2	6
            absolute	SRE oper	4F	3	6
            absolute,X	SRE oper,X	5F	3	7
            absolute,Y	SRE oper,Y	5B	3	7
            (indirect,X)	SRE (oper,X)	43	2	8
            (indirect),Y	SRE (oper),Y	53	2	8  	*/
        assertFalse(actual = SRE.isAddCyclePageCrossed)
        checkInstruction(code = 0x47, opCode = SRE, addressing = Addressing.ZeroPage, cycle = 5)
        checkInstruction(code = 0x57, opCode = SRE, addressing = Addressing.ZeroPageX, cycle = 6)
        checkInstruction(code = 0x4F, opCode = SRE, addressing = Addressing.Absolute, cycle = 6)
        checkInstruction(code = 0x5F, opCode = SRE, addressing = Addressing.AbsoluteX, cycle = 7)
        checkInstruction(code = 0x5B, opCode = SRE, addressing = Addressing.AbsoluteY, cycle = 7)
        checkInstruction(code = 0x43, opCode = SRE, addressing = Addressing.IndirectX, cycle = 8)
        checkInstruction(code = 0x53, opCode = SRE, addressing = Addressing.IndirectY, cycle = 8)
    }

    @Test
    fun testTAS() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#TAS
            addressing	assembler	opc	bytes	cycles
            absolute,Y	TAS oper,Y	9B	3	5  	†  	*/
        assertFalse(actual = TAS.isAddCyclePageCrossed)
        checkInstruction(code = 0x9B, opCode = TAS, addressing = Addressing.AbsoluteY, cycle = 5)
    }

    @Test
    fun testUSBC() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#USBC
            addressing	assembler	opc	bytes	cycles
            immediate	USBC #oper	EB	2	2  	*/
        assertFalse(actual = USBC.isAddCyclePageCrossed)
        checkInstruction(code = 0xEB, opCode = USBC, addressing = Addressing.Immediate, cycle = 2)
    }

    @Test
    fun testNOPs() {
        /* https://www.masswerk.at/6502/6502_instruction_set.html#NOPs
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
            FC	absolute,X	3	4*	*/
        assertTrue(actual = NOPs.isAddCyclePageCrossed)
        checkInstruction(code = 0x1A, opCode = NOPs, addressing = Addressing.Implied, cycle = 2)
        checkInstruction(code = 0x3A, opCode = NOPs, addressing = Addressing.Implied, cycle = 2)
        checkInstruction(code = 0x5A, opCode = NOPs, addressing = Addressing.Implied, cycle = 2)
        checkInstruction(code = 0x7A, opCode = NOPs, addressing = Addressing.Implied, cycle = 2)
        checkInstruction(code = 0xDA, opCode = NOPs, addressing = Addressing.Implied, cycle = 2)
        checkInstruction(code = 0xFA, opCode = NOPs, addressing = Addressing.Implied, cycle = 2)
        checkInstruction(code = 0x80, opCode = NOPs, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0x82, opCode = NOPs, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0x89, opCode = NOPs, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xC2, opCode = NOPs, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0xE2, opCode = NOPs, addressing = Addressing.Immediate, cycle = 2)
        checkInstruction(code = 0x04, opCode = NOPs, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x44, opCode = NOPs, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x64, opCode = NOPs, addressing = Addressing.ZeroPage, cycle = 3)
        checkInstruction(code = 0x14, opCode = NOPs, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x34, opCode = NOPs, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x54, opCode = NOPs, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x74, opCode = NOPs, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0xD4, opCode = NOPs, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0xF4, opCode = NOPs, addressing = Addressing.ZeroPageX, cycle = 4)
        checkInstruction(code = 0x0C, opCode = NOPs, addressing = Addressing.Absolute, cycle = 4)
        checkInstruction(code = 0x1C, opCode = NOPs, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0x3C, opCode = NOPs, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0x5C, opCode = NOPs, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0x7C, opCode = NOPs, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0xDC, opCode = NOPs, addressing = Addressing.AbsoluteX, cycle = 4)
        checkInstruction(code = 0xFC, opCode = NOPs, addressing = Addressing.AbsoluteX, cycle = 4)
    }

    //------------------------------------------------------------------------------------------------------------------
    // Check
    //------------------------------------------------------------------------------------------------------------------

    private fun checkInstruction(code: Int, opCode: OpCode, addressing: Addressing, cycle: Int) {
        Instructions[code].also { instruction ->
            val codeHex = code.toString(radix = 16).padStart(length = 2, padChar = '0')
            val message = "[$codeHex] $opCode $addressing $cycle / $instruction"
            assertEquals(expected = opCode, actual = instruction.opCode, message = message)
            assertEquals(expected = addressing, actual = instruction.addressing, message = message)
            assertEquals(expected = cycle, actual = instruction.cycle, message = message)
            checkPageCrossedSimple(instruction)
        }
    }

    private fun checkPageCrossedSimple(instruction: Instruction) {
        val isSupportPageCrossed = instruction.addressing in
                setOf(Addressing.Relative, Addressing.AbsoluteX, Addressing.AbsoluteY, Addressing.IndirectY)
        // BUSとレジスター用意
        val bus = object : CPUBus {
            override fun writeMemIO(address: Int, value: UByte) = Unit
            override fun readWordMemIO(address: Int): UShort = 0x00FFu
            override fun readMemIO(address: Int): UByte =
                if (instruction.addressing is Addressing.IndirectY && address != 0x0000) 0x00u else 0xFFu
        }
        val registers = CPURegisters(X = 1u, Y = 1u, PC = 0x00FFu)
        // 実行
        val result = instruction.opCode.execute(instruction = instruction, bus = bus, registers = registers)
        // 結果チェック
        val branched = result and 0xFFFF_0000.toInt() != 0
        val addCycles = result and 0x0000_FFFF
        val message = "$instruction, result=$result, branched=$branched, addCycles=$addCycles"
        if (instruction.opCode !is BranchOpCode) {
            if (isSupportPageCrossed && instruction.opCode.isAddCyclePageCrossed) {
                assertNotEquals(illegal = 0, actual = addCycles, message = message)
            } else {
                assertEquals(expected = 0, actual = addCycles, message = message)
            }
        } else if (branched.not()) {
            assertEquals(expected = 0, actual = addCycles, message = message)
        } else if (isSupportPageCrossed && instruction.opCode.isAddCyclePageCrossed) {
            assertNotEquals(illegal = 0, actual = addCycles, message = message)
            assertNotEquals(illegal = 1, actual = addCycles, message = message)
        } else {
            assertEquals(expected = 1, actual = addCycles, message = message)
        }
    }
}
