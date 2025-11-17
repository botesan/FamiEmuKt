package jp.mito.famiemukt.emurator.cpu

/*
https://www.tekepen.com/nes/adrmap.html
アドレス	内容	ミラーアドレス
$0000-$07FF	WRAM
$0800-$1FFF	未使用	$0000-$07FF

https://www.nesdev.org/wiki/CPU_memory_map
Address range	Size	Device
$0000–$07FF	$0800	2 KB internal RAM
$0800–$0FFF	$0800	Mirrors of $0000–$07FF
$1000–$17FF	$0800   〃
$1800–$1FFF	$0800   〃
 */
@OptIn(ExperimentalUnsignedTypes::class)
class RAM {
    private val memory: UByteArray = UByteArray(size = 0x0800)

    fun read(address: Int): UByte = memory[address and 0x07FF]

    fun read(address: Int, data: UByteArray) {
        memory.copyInto(destination = data, startIndex = address, endIndex = address + data.size)
    }

    fun write(address: Int, value: UByte) {
        memory[address and 0x07FF] = value
    }
}
