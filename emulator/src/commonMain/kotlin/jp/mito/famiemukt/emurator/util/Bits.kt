package jp.mito.famiemukt.emurator.util

const val BIT_MASK_0: Int = 0b0000_0001
const val BIT_MASK_1: Int = 0b0000_0010
const val BIT_MASK_2: Int = 0b0000_0100
const val BIT_MASK_3: Int = 0b0000_1000
const val BIT_MASK_4: Int = 0b0001_0000
const val BIT_MASK_5: Int = 0b0010_0000
const val BIT_MASK_6: Int = 0b0100_0000
const val BIT_MASK_7: Int = 0b1000_0000

@Suppress("NOTHING_TO_INLINE")
inline fun UByte.isBit(bitMask: Int): Boolean = (this.toInt() and bitMask) != 0

@Suppress("NOTHING_TO_INLINE")
inline fun UByte.applyBit(bitMask: Int, value: Boolean): UByte =
    (if (value) {
        this.toInt() or bitMask
    } else {
        this.toInt() and bitMask.inv()
    }).toUByte()
