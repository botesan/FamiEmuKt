package jp.mito.famiemukt.emurator.cpu

import jp.mito.famiemukt.emurator.apu.APU
import jp.mito.famiemukt.emurator.cartridge.mapper.Mapper
import jp.mito.famiemukt.emurator.dma.DMA
import jp.mito.famiemukt.emurator.pad.Pad
import jp.mito.famiemukt.emurator.ppu.PPU

/*
https://www.tekepen.com/nes/adrmap.html
アドレス	内容	ミラーアドレス
$0000-$07FF	WRAM
$0800-$1FFF	未使用	$0000-$07FF
$2000-$2007	I/Oポート (PPU)
$2008-$3FFF	未使用	$2000-$2007
$4000-$401F	I/Oポート (APU, etc)
$4020-$5FFF	拡張RAM(特殊なマッパー使用時)
$6000-$7FFF	バッテリーバックアップRAM
$8000-$BFFF	プログラムROM LOW
$C000-$FFFF	プログラムROM HIGH

https://www.nesdev.org/wiki/CPU_memory_map
Address range	Size	Device
$0000–$07FF	$0800	2 KB internal RAM
$0800–$0FFF	$0800	Mirrors of $0000–$07FF
$1000–$17FF	$0800   〃
$1800–$1FFF	$0800   〃
$2000–$2007	$0008	NES PPU registers
$2008–$3FFF	$1FF8	Mirrors of $2000–$2007 (repeats every 8 bytes)
$4000–$4017	$0018	NES APU and I/O registers
$4018–$401F	$0008	APU and I/O functionality that is normally disabled. See CPU Test Mode.
$4020–$FFFF	$BFE0	Cartridge space: PRG ROM, PRG RAM, and mapper registers
 */
interface CPUBus {
    companion object {
        operator fun invoke(
            mapper: Mapper,
            dma: DMA,
            ram: RAM,
            apu: APU,
            ppu: PPU,
            pad: Pad,
        ): CPUBus = CPUBusImpl(
            mapper = mapper,
            dma = dma,
            ram = ram,
            apu = apu,
            ppu = ppu,
            pad = pad,
        )
    }

    fun readWordMemIO(address: Int): UShort
    fun readMemIO(address: Int): UByte
    fun writeMemIO(address: Int, value: UByte)
}

class CPUBusImpl(
    private val mapper: Mapper,
    private val dma: DMA,
    private val ram: RAM,
    private val apu: APU,
    private val ppu: PPU,
    private val pad: Pad,
) : CPUBus {
    init {
        apu.setCPUBus(cpuBus = this)
    }

    override fun readWordMemIO(address: Int): UShort {
        return (readMemIO(address).toInt() or (readMemIO(address + 1).toInt() shl 8)).toUShort()
    }

    override fun readMemIO(address: Int): UByte {
        return when (address) {
            // ホットパス最適化（よくアクセスする領域を先に判定）
            // $8000-$BFFF	プログラムROM LOW
            // $C000-$FFFF	プログラムROM HIGH
            in 0x8000..0xFFFF -> mapper.readPRG(address = address)
            // $0000–$07FF	$0800	2 KB internal RAM
            in 0x0000..0x1FFF -> ram.read(address = address)
            // $2000–$2007	$0008	NES PPU registers
            // https://www.nesdev.org/wiki/PPU_registers
            in 0x2000..0x2001 -> 0.toUByte() // TODO(" write only?")
            0x2002 -> ppu.readPPUStatus()
            0x2003 -> 0.toUByte() // TODO(" write only?")
            0x2004 -> ppu.readObjectAttributeMemory()
            in 0x2005..0x2006 -> 0.toUByte() // TODO(" write only?")
            0x2007 -> ppu.readVideoRAM()
            // $2008–$3FFF	$1FF8	Mirrors of $2000–$2007 (repeats every 8 bytes)
            in 0x2008..0x3FFF -> readMemIO(address = address and 0x2007)
            // $4000–$4017	$0018	NES APU and I/O registers
            // https://www.nesdev.org/wiki/2A03
            in 0x4000..0x4013 -> TODO("Open BUS")
            // https://www.nesdev.org/wiki/APU#Status_($4015)
            /* The status register is used to enable and disable individual channels,
               control the DMC, and can read the status of length counters and APU interrupts.
                $4015 read	IF-D NT21	DMC interrupt (I), frame interrupt (F), DMC active (D), length counter > 0 (N/T/2/1)
                N/T/2/1 will read as 1 if the corresponding length counter is greater than 0. For the triangle channel,
                the status of the linear counter is irrelevant.
                D will read as 1 if the DMC bytes remaining is more than 0.
                Reading this register clears the frame interrupt flag (but not the DMC interrupt flag).
                If an interrupt flag was set at the same moment of the read, it will read back as 1 but it will not be cleared.
                This register is internal to the CPU and so the external CPU data bus is disconnected when reading it.
                Therefore the returned value cannot be seen by external devices and the value does not affect open bus.
                Bit 5 is open bus. Because the external bus is disconnected when reading $4015,
                the open bus value comes from the last cycle that did not read $4015. */
            0x4015 -> apu.readEnableStatus()
            // https://www.nesdev.org/wiki/PPU_registers#OAMDMA
            0x4014 -> TODO("Open BUS")
            // https://www.nesdev.org/wiki/Input_devices
            0x4016, 0x4017 -> pad.read(address = address)
            // APU and I/O functionality that is normally disabled. https://www.nesdev.org/wiki/CPU_Test_Mode
            in 0x4018..0x401F -> 0.toUByte()
            // $4020–$FFFF	$BFE0	Cartridge space: PRG ROM, PRG RAM, and mapper registers
            // $4020-$5FFF	拡張RAM(特殊なマッパー使用時)
            in 0x4020..0x5FFF -> mapper.readExt(address = address)
            // $6000-$7FFF	バッテリーバックアップRAM
            in 0x6000..0x7FFF -> mapper.readBackup(address = address)
            // 範囲外
            else -> error("Illegal read address. $address(${address.toString(radix = 16)}")
        }
    }

    override fun writeMemIO(address: Int, value: UByte) {
        when (address) {
            in 0x0000..0x1FFF -> ram.write(address = address, value = value)
            // $2000–$2007	$0008	NES PPU registers
            // https://www.nesdev.org/wiki/PPU_registers
            0x2000 -> ppu.writePPUControl(value = value)
            0x2001 -> ppu.writePPUMask(value = value)
            0x2002 -> Unit // TODO(" read only?")
            0x2003 -> ppu.writeObjectAttributeMemoryAddress(value = value)
            0x2004 -> ppu.writeObjectAttributeMemory(value = value)
            0x2005 -> ppu.writePPUScroll(value = value)
            0x2006 -> ppu.writePPUAddress(value = value)
            0x2007 -> ppu.writeVideoRAM(value = value)
            // $2008–$3FFF	$1FF8	Mirrors of $2000–$2007 (repeats every 8 bytes)
            in 0x2008..0x3FFF -> writeMemIO(address = address and 0x2007, value = value)
            // $4000–$4017	$0018	NES APU and I/O registers
            // $4018–$401F	$0008	APU and I/O functionality that is normally disabled. See CPU Test Mode.
            // https://www.nesdev.org/wiki/2A03
            // https://www.nesdev.org/wiki/APU
            // https://www.nesdev.org/wiki/APU_Pulse
            0x4000 -> apu.writePulse1DLCV(value = value)
            0x4001 -> apu.writePulse1EPNS(value = value)
            0x4002 -> apu.writePulse1TLow(value = value)
            0x4003 -> apu.writePulse1LT(value = value)
            0x4004 -> apu.writePulse2DLCV(value = value)
            0x4005 -> apu.writePulse2EPNS(value = value)
            0x4006 -> apu.writePulse2TLow(value = value)
            0x4007 -> apu.writePulse2LT(value = value)
            // https://www.nesdev.org/wiki/APU_Triangle
            0x4008 -> apu.writeTriangleCR(value = value)
            0x4009 -> Unit // 未使用
            0x400A -> apu.writeTriangleTLow(value = value)
            0x400B -> apu.writeTriangleLT(value = value)
            // https://www.nesdev.org/wiki/APU_Noise
            0x400C -> apu.writeNoiseLCV(value = value)
            0x400D -> Unit // 未使用
            0x400E -> apu.writeNoiseMP(value = value)
            0x400F -> apu.writeNoiseLR(value = value)
            // https://www.nesdev.org/wiki/APU_DMC
            0x4010 -> apu.writeDMCILR(value = value)
            0x4011 -> apu.writeDMCD(value = value)
            0x4012 -> apu.writeDMCA(value = value)
            0x4013 -> apu.writeDMCL(value = value)
            // https://www.nesdev.org/wiki/APU#Status_($4015)
            0x4015 -> apu.writeEnableStatus(value = value)
            // https://www.nesdev.org/wiki/APU_Frame_Counter
            0x4017 -> apu.writeFrameCounter(value = value)
            // https://www.nesdev.org/wiki/PPU_registers#OAMDMA
            0x4014 -> dma.copyObjectAttributeMemory(fromAddress = (value.toUInt() shl 8).toUShort())
            // https://www.nesdev.org/wiki/Input_devices
            0x4016 -> pad.write(address = address, value = value)
            // APU and I/O functionality that is normally disabled. https://www.nesdev.org/wiki/CPU_Test_Mode
            in 0x4018..0x401F -> Unit
            // $4020–$FFFF	$BFE0	Cartridge space: PRG ROM, PRG RAM, and mapper registers
            // $4020-$5FFF	拡張RAM(特殊なマッパー使用時)
            in 0x4020..0x5FFF -> mapper.writeExt(address = address, value = value)
            // $6000-$7FFF	バッテリーバックアップRAM
            in 0x6000..0x7FFF -> mapper.writeBackup(address = address, value = value)
            // $8000-$BFFF	プログラムROM LOW
            // $C000-$FFFF	プログラムROM HIGH
            in 0x8000..0xFFFF -> mapper.writePRG(address = address, value = value)
            // 範囲外
            else -> error("Illegal write address. $address(${address.toString(radix = 16)}")
        }
    }
}
