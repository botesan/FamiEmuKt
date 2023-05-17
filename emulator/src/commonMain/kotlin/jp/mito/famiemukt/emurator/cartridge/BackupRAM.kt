package jp.mito.famiemukt.emurator.cartridge

import jp.mito.famiemukt.emurator.util.VisibleForTesting

// バッテリーバックアップ
// initDataを0xFFクリアすると動作しないものがある（まじかる☆タルるートくん）
@OptIn(ExperimentalUnsignedTypes::class)
class BackupRAM(initData: UByteArray = UByteArray(size = 0x8000)) {
    @Suppress("PropertyName")
    @VisibleForTesting
    val _backup: UByteArray get() = backup
    private val backup: UByteArray = UByteArray(size = 0x8000).also { initData.copyInto(it) }
    fun read(index: Int): UByte = backup[index and 0x7FFF]
    fun write(index: Int, value: UByte) {
        backup[index and 0x7FFF] = value
    }
}
