package jp.mito.famiemukt.emurator.cpu.addressing

import jp.mito.famiemukt.emurator.cpu.CPUBus
import jp.mito.famiemukt.emurator.cpu.CPURegisters

data class Operand(val operand: Int, val addCycle: Int)

sealed interface Addressing {
    val name: String
    fun operand(bus: CPUBus, registers: CPURegisters): Operand
    fun read(operand: Operand, bus: CPUBus): UByte
    fun write(value: UByte, operand: Operand, bus: CPUBus, registers: CPURegisters)

    //////////////////////////////////////

    object Undefine : BaseAddressing(name = "Undefine")

    object Implied : BaseAddressing(name = "Implied")

    object Accumulator : OperandAccumulatorAddressing(name = "Accumulator") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand =
            Operand(operand = registers.A.toInt(), addCycle = 0)
    }

    object Immediate : OperandAccumulatorAddressing(name = "Immediate") {
        override fun write(value: UByte, operand: Operand, bus: CPUBus, registers: CPURegisters) = notSupported()
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand =
            Operand(operand = fetch(bus, registers).toInt(), addCycle = 0)
    }

    object ZeroPage : MemoryAddressing(name = "ZeroPage") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand =
            Operand(operand = fetch(bus, registers).toInt(), addCycle = 0)
    }

    object ZeroPageX : MemoryAddressing(name = "ZeroPageX") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand =
            Operand(
                operand = ((fetch(bus, registers) + registers.X) and 0xFFU).toInt(),
                addCycle = 0,
            )
    }

    object ZeroPageY : MemoryAddressing(name = "ZeroPageY") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand =
            Operand(
                operand = ((fetch(bus, registers) + registers.Y) and 0xFFU).toInt(),
                addCycle = 0,
            )
    }

    object Relative : BaseAddressing(name = "Relative") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand {
            val fetched = fetch(bus, registers).toByte()
            val base = registers.PC.toInt()
            val result = fetched + base
            return Operand(
                operand = result and 0xFFFF,
                addCycle = if ((base xor result) and 0xFF00 == 0) 0 else 1,
            )
        }
    }

    object Absolute : MemoryAddressing(name = "Absolute") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand =
            Operand(operand = fetchWord(bus, registers).toInt(), addCycle = 0)
    }

    object AbsoluteX : MemoryAddressing(name = "AbsoluteX") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand {
            val base = fetchWord(bus, registers).toUInt()
            val result = base + registers.X
            return Operand(
                operand = result.toInt() and 0xFFFF,
                addCycle = if ((base xor result) and 0xFF00U == 0U) 0 else 1,
            )
        }
    }

    object AbsoluteY : MemoryAddressing(name = "AbsoluteY") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand {
            val base = fetchWord(bus, registers).toUInt()
            val result = base + registers.Y
            return Operand(
                operand = result.toInt() and 0xFFFF,
                addCycle = if ((base xor result) and 0xFF00U == 0U) 0 else 1,
            )
        }
    }

    object Indirect : MemoryAddressing(name = "Indirect") {
        override fun write(value: UByte, operand: Operand, bus: CPUBus, registers: CPURegisters) = notSupported()
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand {
            // Indirect 繰り上げ等を行わない？
            val address = fetchWord(bus, registers).toInt()
            val nextAddress = (address and 0xFF00) or ((address + 1) and 0x00FF)
            val l = bus.readMemIO(address = address)
            val h = bus.readMemIO(address = nextAddress)
            return Operand(operand = l.toInt() or (h.toInt() shl 8), addCycle = 0)
        }
    }

    object IndirectX : MemoryAddressing(name = "IndirectX") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand {
            // Indirect 繰り上げ等を行わない？
            val address = ((fetch(bus, registers) + registers.X) and 0xFFU).toInt()
            val nextAddress = (address and 0xFF00) or ((address + 1) and 0x00FF)
            val l = bus.readMemIO(address = address)
            val h = bus.readMemIO(address = nextAddress)
            return Operand(operand = l.toInt() or (h.toInt() shl 8), addCycle = 0)
        }
    }

    object IndirectY : MemoryAddressing(name = "IndirectY") {
        override fun operand(bus: CPUBus, registers: CPURegisters): Operand {
            // Indirect 繰り上げ等を行わない？
            val address = fetch(bus, registers).toInt()
            val nextAddress = (address and 0xFF00) or ((address + 1) and 0x00FF)
            val l = bus.readMemIO(address = address)
            val h = bus.readMemIO(address = nextAddress)
            val base = ((l.toUInt() or (h.toUInt() shl 8)))
            val result = base + registers.Y
            return Operand(
                operand = result.toInt() and 0xFFFF,
                addCycle = if (((base xor result) and 0xFF00U) == 0U) 0 else 1,
            )
        }
    }
}

sealed class BaseAddressing(override val name: String) : Addressing {
    override fun toString(): String = this::class.simpleName + "(" + name + ")"
    protected fun notSupported(): Nothing = error("$name not supported")
    override fun operand(bus: CPUBus, registers: CPURegisters): Operand = notSupported()
    override fun read(operand: Operand, bus: CPUBus): UByte = notSupported()
    override fun write(value: UByte, operand: Operand, bus: CPUBus, registers: CPURegisters): Unit =
        notSupported()
}

sealed class OperandAccumulatorAddressing(name: String) : BaseAddressing(name) {
    override fun read(operand: Operand, bus: CPUBus): UByte = operand.operand.toUByte()
    override fun write(value: UByte, operand: Operand, bus: CPUBus, registers: CPURegisters) {
        registers.A = value
    }
}

sealed class MemoryAddressing(name: String) : BaseAddressing(name) {
    override fun read(operand: Operand, bus: CPUBus): UByte = bus.readMemIO(address = operand.operand)
    override fun write(value: UByte, operand: Operand, bus: CPUBus, registers: CPURegisters) {
        bus.writeMemIO(address = operand.operand, value)
    }
}
