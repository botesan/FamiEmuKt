package jp.mito.famiemukt.emurator.cpu

import jp.mito.famiemukt.emurator.cpu.addressing.Addressing
import jp.mito.famiemukt.emurator.cpu.addressing.Addressing.*
import jp.mito.famiemukt.emurator.cpu.instruction.*

// https://www.tekepen.com/nes/6502.html
// https://www.masswerk.at/6502/6502_instruction_set.html

data class Instruction(
    val opCode: OpCode,
    val addressing: Addressing,
    val cycle: Int,
    val pollInterruptCycle: Int,
    val isUnofficial: Boolean,
)

private fun inst(opCode: OpCode, addressing: Addressing, cycle: Int, pollInterruptCycle: Int) =
    Instruction(
        opCode,
        addressing,
        cycle = cycle,
        pollInterruptCycle = pollInterruptCycle,
        isUnofficial = opCode is UnofficialOpCode,
    )

private fun inst(opCode: OpCode, addressing: Addressing, cycle: Int) =
    inst(opCode, addressing, cycle = cycle, pollInterruptCycle = cycle - 1)

val Instructions: List<Instruction> = listOf(
    /* 0x00 */inst(BRK, Implied, 7),
    /* 0x01 */inst(ORA, IndirectX, 6),
    /* 0x02 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x03 */inst(SLO, IndirectX, 8), // 非公式コマンド
    /* 0x04 */inst(NOPs, ZeroPage, 3), // 非公式コマンド
    /* 0x05 */inst(ORA, ZeroPage, 3),
    /* 0x06 */inst(ASL, ZeroPage, 5),
    /* 0x07 */inst(SLO, ZeroPage, 5), // 非公式コマンド
    /* 0x08 */inst(PHP, Implied, 3),
    /* 0x09 */inst(ORA, Immediate, 2),
    /* 0x0a */inst(ASL, Accumulator, 2),
    /* 0x0b */inst(ANC, Immediate, 2), // 非公式コマンド
    /* 0x0c */inst(NOPs, Absolute, 4), // 非公式コマンド
    /* 0x0d */inst(ORA, Absolute, 4),
    /* 0x0e */inst(ASL, Absolute, 6),
    /* 0x0f */inst(SLO, Absolute, 6), // 非公式コマンド

    /* 0x10 */inst(BPL, Relative, 2),
    /* 0x11 */inst(ORA, IndirectY, 5),
    /* 0x12 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x13 */inst(SLO, IndirectY, 8), // 非公式コマンド
    /* 0x14 */inst(NOPs, ZeroPageX, 4), // 非公式コマンド
    /* 0x15 */inst(ORA, ZeroPageX, 4),
    /* 0x16 */inst(ASL, ZeroPageX, 6),
    /* 0x17 */inst(SLO, ZeroPageX, 6), // 非公式コマンド
    /* 0x18 */inst(CLC, Implied, 2),
    /* 0x19 */inst(ORA, AbsoluteY, 4),
    /* 0x1a */inst(NOPs, Implied, 2), // 非公式コマンド
    /* 0x1b */inst(SLO, AbsoluteY, 7), // 非公式コマンド
    /* 0x1c */inst(NOPs, AbsoluteX, 4), // 非公式コマンド
    /* 0x1d */inst(ORA, AbsoluteX, 4),
    /* 0x1e */inst(ASL, AbsoluteX, 7),
    /* 0x1f */inst(SLO, AbsoluteX, 7), // 非公式コマンド

    /* 0x20 */inst(JSR, Absolute, 6),
    /* 0x21 */inst(AND, IndirectX, 6),
    /* 0x22 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x23 */inst(RLA, IndirectX, 8), // 非公式コマンド
    /* 0x24 */inst(BIT, ZeroPage, 3),
    /* 0x25 */inst(AND, ZeroPage, 3),
    /* 0x26 */inst(ROL, ZeroPage, 5),
    /* 0x27 */inst(RLA, ZeroPage, 5), // 非公式コマンド
    /* 0x28 */inst(PLP, Implied, 4),
    /* 0x29 */inst(AND, Immediate, 2),
    /* 0x2a */inst(ROL, Accumulator, 2),
    /* 0x2b */inst(ANC, Immediate, 2), // 非公式コマンド
    /* 0x2c */inst(BIT, Absolute, 4),
    /* 0x2d */inst(AND, Absolute, 4),
    /* 0x2e */inst(ROL, Absolute, 6),
    /* 0x2f */inst(RLA, Absolute, 6), // 非公式コマンド

    /* 0x30 */inst(BMI, Relative, 2),
    /* 0x31 */inst(AND, IndirectY, 5),
    /* 0x32 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x33 */inst(RLA, IndirectY, 8), // 非公式コマンド
    /* 0x34 */inst(NOPs, ZeroPageX, 4), // 非公式コマンド
    /* 0x35 */inst(AND, ZeroPageX, 4),
    /* 0x36 */inst(ROL, ZeroPageX, 6),
    /* 0x37 */inst(RLA, ZeroPageX, 6), // 非公式コマンド
    /* 0x38 */inst(SEC, Implied, 2),
    /* 0x39 */inst(AND, AbsoluteY, 4),
    /* 0x3a */inst(NOPs, Implied, 2), // 非公式コマンド
    /* 0x3b */inst(RLA, AbsoluteY, 7), // 非公式コマンド
    /* 0x3c */inst(NOPs, AbsoluteX, 4), // 非公式コマンド
    /* 0x3d */inst(AND, AbsoluteX, 4),
    /* 0x3e */inst(ROL, AbsoluteX, 7),
    /* 0x3f */inst(RLA, AbsoluteX, 7), // 非公式コマンド

    /* 0x40 */inst(RTI, Implied, 6),
    /* 0x41 */inst(EOR, IndirectX, 6),
    /* 0x42 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x43 */inst(SRE, IndirectX, 8), // 非公式コマンド
    /* 0x44 */inst(NOPs, ZeroPage, 3), // 非公式コマンド
    /* 0x45 */inst(EOR, ZeroPage, 3),
    /* 0x46 */inst(LSR, ZeroPage, 5),
    /* 0x47 */inst(SRE, ZeroPage, 5), // 非公式コマンド
    /* 0x48 */inst(PHA, Implied, 3),
    /* 0x49 */inst(EOR, Immediate, 2),
    /* 0x4a */inst(LSR, Accumulator, 2),
    /* 0x4b */inst(ALR, Immediate, 2), // 非公式コマンド
    /* 0x4c */inst(JMP, Absolute, 3),
    /* 0x4d */inst(EOR, Absolute, 4),
    /* 0x4e */inst(LSR, Absolute, 6),
    /* 0x4f */inst(SRE, Absolute, 6), // 非公式コマンド

    /* 0x50 */inst(BVC, Relative, 2),
    /* 0x51 */inst(EOR, IndirectY, 5),
    /* 0x52 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x53 */inst(SRE, IndirectY, 8), // 非公式コマンド
    /* 0x54 */inst(NOPs, ZeroPageX, 4), // 非公式コマンド
    /* 0x55 */inst(EOR, ZeroPageX, 4),
    /* 0x56 */inst(LSR, ZeroPageX, 6),
    /* 0x57 */inst(SRE, ZeroPageX, 6), // 非公式コマンド
    /* 0x58 */inst(CLI, Implied, 2),
    /* 0x59 */inst(EOR, AbsoluteY, 4),
    /* 0x5a */inst(NOPs, Implied, 2), // 非公式コマンド
    /* 0x5b */inst(SRE, AbsoluteY, 7), // 非公式コマンド
    /* 0x5c */inst(NOPs, AbsoluteX, 4), // 非公式コマンド
    /* 0x5d */inst(EOR, AbsoluteX, 4),
    /* 0x5e */inst(LSR, AbsoluteX, 7),
    /* 0x5f */inst(SRE, AbsoluteX, 7), // 非公式コマンド

    /* 0x60 */inst(RTS, Implied, 6),
    /* 0x61 */inst(ADC, IndirectX, 6),
    /* 0x62 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x63 */inst(RRA, IndirectX, 8), // 非公式コマンド
    /* 0x64 */inst(NOPs, ZeroPage, 3), // 非公式コマンド
    /* 0x65 */inst(ADC, ZeroPage, 3),
    /* 0x66 */inst(ROR, ZeroPage, 5),
    /* 0x67 */inst(RRA, ZeroPage, 5), // 非公式コマンド
    /* 0x68 */inst(PLA, Implied, 4),
    /* 0x69 */inst(ADC, Immediate, 2),
    /* 0x6a */inst(ROR, Accumulator, 2),
    /* 0x6b */inst(ARR, Immediate, 2), // 非公式コマンド
    /* 0x6c */inst(JMP, Indirect, 5),
    /* 0x6d */inst(ADC, Absolute, 4),
    /* 0x6e */inst(ROR, Absolute, 6),
    /* 0x6f */inst(RRA, Absolute, 6), // 非公式コマンド

    /* 0x70 */inst(BVS, Relative, 2),
    /* 0x71 */inst(ADC, IndirectY, 5),
    /* 0x72 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x73 */inst(RRA, IndirectY, 8), // 非公式コマンド
    /* 0x74 */inst(NOPs, ZeroPageX, 4), // 非公式コマンド
    /* 0x75 */inst(ADC, ZeroPageX, 4),
    /* 0x76 */inst(ROR, ZeroPageX, 6),
    /* 0x77 */inst(RRA, ZeroPageX, 6), // 非公式コマンド
    /* 0x78 */inst(SEI, Implied, 2),
    /* 0x79 */inst(ADC, AbsoluteY, 4),
    /* 0x7a */inst(NOPs, Implied, 2), // 非公式コマンド
    /* 0x7b */inst(RRA, AbsoluteY, 7), // 非公式コマンド
    /* 0x7c */inst(NOPs, AbsoluteX, 4), // 非公式コマンド
    /* 0x7d */inst(ADC, AbsoluteX, 4),
    /* 0x7e */inst(ROR, AbsoluteX, 7),
    /* 0x7f */inst(RRA, AbsoluteX, 7), // 非公式コマンド

    /* 0x80 */inst(NOPs, Immediate, 2), // 非公式コマンド
    /* 0x81 */inst(STA, IndirectX, 6),
    /* 0x82 */inst(NOPs, Immediate, 2), // 非公式コマンド
    /* 0x83 */inst(SAX, IndirectX, 6), // 非公式コマンド
    /* 0x84 */inst(STY, ZeroPage, 3),
    /* 0x85 */inst(STA, ZeroPage, 3),
    /* 0x86 */inst(STX, ZeroPage, 3),
    /* 0x87 */inst(SAX, ZeroPage, 3), // 非公式コマンド
    /* 0x88 */inst(DEY, Implied, 2),
    /* 0x89 */inst(NOPs, Immediate, 2), // 非公式コマンド
    /* 0x8a */inst(TXA, Implied, 2),
    /* 0x8b */inst(ANE, Immediate, 2), // 非公式コマンド
    /* 0x8c */inst(STY, Absolute, 4),
    /* 0x8d */inst(STA, Absolute, 4),
    /* 0x8e */inst(STX, Absolute, 4),
    /* 0x8f */inst(SAX, Absolute, 4), // 非公式コマンド

    /* 0x90 */inst(BCC, Relative, 2),
    /* 0x91 */inst(STA, IndirectY, 6),
    /* 0x92 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0x93 */inst(SHA, IndirectY, 6), // 非公式コマンド
    /* 0x94 */inst(STY, ZeroPageX, 4),
    /* 0x95 */inst(STA, ZeroPageX, 4),
    /* 0x96 */inst(STX, ZeroPageY, 4),
    /* 0x97 */inst(SAX, ZeroPageY, 4), // 非公式コマンド
    /* 0x98 */inst(TYA, Implied, 2),
    /* 0x99 */inst(STA, AbsoluteY, 5),
    /* 0x9a */inst(TXS, Implied, 2),
    /* 0x9b */inst(TAS, AbsoluteY, 5), // 非公式コマンド
    /* 0x9c */inst(SHY, AbsoluteX, 5), // 非公式コマンド
    /* 0x9d */inst(STA, AbsoluteX, 5),
    /* 0x9e */inst(SHX, AbsoluteY, 5), // 非公式コマンド
    /* 0x9f */inst(SHA, AbsoluteY, 5), // 非公式コマンド

    /* 0xa0 */inst(LDY, Immediate, 2),
    /* 0xa1 */inst(LDA, IndirectX, 6),
    /* 0xa2 */inst(LDX, Immediate, 2),
    /* 0xa3 */inst(LAX, IndirectX, 6), // 非公式コマンド
    /* 0xa4 */inst(LDY, ZeroPage, 3),
    /* 0xa5 */inst(LDA, ZeroPage, 3),
    /* 0xa6 */inst(LDX, ZeroPage, 3),
    /* 0xa7 */inst(LAX, ZeroPage, 3), // 非公式コマンド
    /* 0xa8 */inst(TAY, Implied, 2),
    /* 0xa9 */inst(LDA, Immediate, 2),
    /* 0xaa */inst(TAX, Implied, 2),
    /* 0xab */inst(LXA, Immediate, 2), // 非公式コマンド
    /* 0xac */inst(LDY, Absolute, 4),
    /* 0xad */inst(LDA, Absolute, 4),
    /* 0xae */inst(LDX, Absolute, 4),
    /* 0xaf */inst(LAX, Absolute, 4), // 非公式コマンド

    /* 0xb0 */inst(BCS, Relative, 2),
    /* 0xb1 */inst(LDA, IndirectY, 5),
    /* 0xb2 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0xb3 */inst(LAX, IndirectY, 5), // 非公式コマンド
    /* 0xb4 */inst(LDY, ZeroPageX, 4),
    /* 0xb5 */inst(LDA, ZeroPageX, 4),
    /* 0xb6 */inst(LDX, ZeroPageY, 4),
    /* 0xb7 */inst(LAX, ZeroPageY, 4), // 非公式コマンド
    /* 0xb8 */inst(CLV, Implied, 2),
    /* 0xb9 */inst(LDA, AbsoluteY, 4),
    /* 0xba */inst(TSX, Implied, 2),
    /* 0xbb */inst(LAS, AbsoluteY, 4), // 非公式コマンド
    /* 0xbc */inst(LDY, AbsoluteX, 4),
    /* 0xbd */inst(LDA, AbsoluteX, 4),
    /* 0xbe */inst(LDX, AbsoluteY, 4),
    /* 0xbf */inst(LAX, AbsoluteY, 4), // 非公式コマンド

    /* 0xc0 */inst(CPY, Immediate, 2),
    /* 0xc1 */inst(CMP, IndirectX, 6),
    /* 0xc2 */inst(NOPs, Immediate, 2), // 非公式コマンド
    /* 0xc3 */inst(DCP, IndirectX, 8), // 非公式コマンド
    /* 0xc4 */inst(CPY, ZeroPage, 3),
    /* 0xc5 */inst(CMP, ZeroPage, 3),
    /* 0xc6 */inst(DEC, ZeroPage, 5),
    /* 0xc7 */inst(DCP, ZeroPage, 5), // 非公式コマンド
    /* 0xc8 */inst(INY, Implied, 2),
    /* 0xc9 */inst(CMP, Immediate, 2),
    /* 0xca */inst(DEX, Implied, 2),
    /* 0xcb */inst(SBX, Immediate, 2), // 非公式コマンド
    /* 0xcc */inst(CPY, Absolute, 4),
    /* 0xcd */inst(CMP, Absolute, 4),
    /* 0xce */inst(DEC, Absolute, 6),
    /* 0xcf */inst(DCP, Absolute, 6), // 非公式コマンド

    /* 0xd0 */inst(BNE, Relative, 2),
    /* 0xd1 */inst(CMP, IndirectY, 5),
    /* 0xd2 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0xd3 */inst(DCP, IndirectY, 8), // 非公式コマンド
    /* 0xd4 */inst(NOPs, ZeroPageX, 4), // 非公式コマンド
    /* 0xd5 */inst(CMP, ZeroPageX, 4),
    /* 0xd6 */inst(DEC, ZeroPageX, 6),
    /* 0xd7 */inst(DCP, ZeroPageX, 6), // 非公式コマンド
    /* 0xd8 */inst(CLD, Implied, 2),
    /* 0xd9 */inst(CMP, AbsoluteY, 4),
    /* 0xda */inst(NOPs, Implied, 2), // 非公式コマンド
    /* 0xdb */inst(DCP, AbsoluteY, 7), // 非公式コマンド
    /* 0xdc */inst(NOPs, AbsoluteX, 4), // 非公式コマンド
    /* 0xdd */inst(CMP, AbsoluteX, 4),
    /* 0xde */inst(DEC, AbsoluteX, 7),
    /* 0xdf */inst(DCP, AbsoluteX, 7), // 非公式コマンド

    /* 0xe0 */inst(CPX, Immediate, 2),
    /* 0xe1 */inst(SBC, IndirectX, 6),
    /* 0xe2 */inst(NOPs, Immediate, 2), // 非公式コマンド
    /* 0xe3 */inst(ISB, IndirectX, 8), // 非公式コマンド (=ISC)
    /* 0xe4 */inst(CPX, ZeroPage, 3),
    /* 0xe5 */inst(SBC, ZeroPage, 3),
    /* 0xe6 */inst(INC, ZeroPage, 5),
    /* 0xe7 */inst(ISB, ZeroPage, 5), // 非公式コマンド (=ISC)
    /* 0xe8 */inst(INX, Implied, 2),
    /* 0xe9 */inst(SBC, Immediate, 2),
    /* 0xea */inst(NOP, Implied, 2),
    /* 0xeb */inst(USBC, Immediate, 2), // 非公式コマンド (SBC oper + NOP ≒ SBC oper)
    /* 0xec */inst(CPX, Absolute, 4),
    /* 0xed */inst(SBC, Absolute, 4),
    /* 0xee */inst(INC, Absolute, 6),
    /* 0xef */inst(ISB, Absolute, 6), // 非公式コマンド (=ISC)

    /* 0xf0 */inst(BEQ, Relative, 2),
    /* 0xf1 */inst(SBC, IndirectY, 5),
    /* 0xf2 */inst(JAM, Undefine, 2), // 非公式コマンド
    /* 0xf3 */inst(ISB, IndirectY, 8), // 非公式コマンド (=ISC)
    /* 0xf4 */inst(NOPs, ZeroPageX, 4), // 非公式コマンド
    /* 0xf5 */inst(SBC, ZeroPageX, 4),
    /* 0xf6 */inst(INC, ZeroPageX, 6),
    /* 0xf7 */inst(ISB, ZeroPageX, 6), // 非公式コマンド (=ISC)
    /* 0xf8 */inst(SED, Implied, 2),
    /* 0xf9 */inst(SBC, AbsoluteY, 4),
    /* 0xfa */inst(NOPs, Implied, 2), // 非公式コマンド
    /* 0xfb */inst(ISB, AbsoluteY, 7), // 非公式コマンド (=ISC)
    /* 0xfc */inst(NOPs, AbsoluteX, 4), // 非公式コマンド
    /* 0xfd */inst(SBC, AbsoluteX, 4),
    /* 0xfe */inst(INC, AbsoluteX, 7),
    /* 0xff */inst(ISB, AbsoluteX, 7), // 非公式コマンド (=ISC)
)
