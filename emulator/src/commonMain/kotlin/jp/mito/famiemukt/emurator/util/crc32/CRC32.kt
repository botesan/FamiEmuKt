package jp.mito.famiemukt.emurator.util.crc32

// CRC32を計算するためのテーブル
@OptIn(ExperimentalUnsignedTypes::class)
private val CRC32_TABLE: UIntArray = UIntArray(256) {
    var crc = it.toUInt()
    repeat(times = 8) {
        crc = if (crc and 1u == 0u) {
            crc.shr(1)
        } else {
            crc.shr(1) xor 0xEDB88320u
        }
    }
    crc
}

// CRC32を計算する
@OptIn(ExperimentalUnsignedTypes::class)
fun calcCRC32(data: ByteArray): UInt {
    var crc = 0xFFFF_FFFFu
    for (byte in data) {
        crc = CRC32_TABLE[((crc xor byte.toUInt()) and 0xFFu).toInt()] xor crc.shr(8)
    }
    return crc xor 0xFFFF_FFFFu
}
