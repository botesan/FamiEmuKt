package jp.mito.famiemukt.emurator.util

import co.touchlab.kermit.Logger
import jp.mito.famiemukt.emurator.cartridge.BackupRAM
import jp.mito.famiemukt.emurator.util.crc32.calcCRC32
import jp.mito.famiemukt.emurator.util.deflate.Deflate
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.io.readShortLe

// https://github.com/euan-forrester/retron5
@OptIn(ExperimentalUnsignedTypes::class)
fun loadRetron5SavFile(input: ByteArray): BackupRAM {
    val buffer = Buffer().also { it.write(source = input, startIndex = 0, endIndex = input.size) }
    val headerBuffer = buffer.peek()
    //typedef struct
    //{
    //   uint32_t magic;
    //   uint16_t fmtVer;
    //   uint16_t flags;
    //   uint32_t origSize;
    //   uint32_t packed_size;
    //   uint32_t data_offset;
    //   uint32_t crc32;
    //   uint8_t data[0];
    //} t_retronDataHdr;
    val magic = headerBuffer.readIntLe()
    check(value = magic == 0x354E5452) { "Illegal file header." } // "RTN5"
    val fmtVer = headerBuffer.readShortLe()
    check(value = fmtVer == 1.toShort()) { "Illegal format version." }
    val flags = headerBuffer.readShortLe()
    val origSize = headerBuffer.readIntLe()
    val packedSize = headerBuffer.readIntLe()
    val dataOffset = headerBuffer.readIntLe()
    check(value = dataOffset <= input.size - packedSize) { "Illegal data offset." }
    val crc32 = headerBuffer.readIntLe()
    //
    val firstData = headerBuffer.readByteArray(byteCount = 16)
    Logger.d { "First data:" }
    Logger.d { firstData.asUByteArray().joinToString { it.toString(radix = 16).padStart(2, '0') } }
    //
    buffer.skip(byteCount = dataOffset.toLong())
    val dataSize = buffer.size
    check(value = packedSize.toLong() == dataSize) { "Illegal packed size. packedSize=$packedSize, dataSize=$dataSize, dataOffset=$dataOffset" }
    val data = if (flags.toInt() and 0x01 == 0) {
        buffer.readByteArray()
    } else {
        // 簡易ZLIBヘッダーチェック（圧縮レベルデフォルト、ウインドウサイズ32k、プリセット辞書無し？）
        // https://www.rfc-editor.org/rfc/rfc1950
        if (firstData[0] == 0x78.toByte() && firstData[1] == 0x9C.toByte()) {
            buffer.skip(byteCount = 2)
        }
        // DEFLATE圧縮解除（RFC1951）
        val to = Buffer()
        Deflate().uncompressDeflate(from = buffer, to = to)
        to.readByteArray()
    }
    val dataCRC32 = calcCRC32(data).toInt()
    check(value = crc32 == dataCRC32) { "Illegal CRC. ${crc32.toUShort().toHex()}, ${dataCRC32.toUShort().toHex()}" }
    Logger.d { "$magic $fmtVer $flags $origSize $packedSize $dataOffset $crc32 $dataSize ${data.size}" }
    Logger.d {
        data.asUByteArray().asSequence().windowed(size = 0x0400, step = 0x0400, partialWindows = true)
            .joinToString(separator = "\n---\n") { part ->
                part.windowed(size = 0x20, step = 0x20, partialWindows = true)
                    .joinToString(separator = "\n") { line ->
                        line.joinToString(separator = ",") {
                            it.toString(radix = 16).padStart(length = 2, padChar = '0')
                        }
                    }
            }
    }
    return BackupRAM(initData = data.asUByteArray())
}
