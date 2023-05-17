package jp.mito.famiemukt.emurator.cpu

import jp.mito.famiemukt.emurator.util.*

/**
 * レジスター
 * @property A Accumulator
 * @property X X
 * @property Y Y
 * @property PC Program Counter
 * @property S Stack Pointer
 * @property P Processor Status
 */
@Suppress("PropertyName")
data class CPURegisters(
    var A: UByte = 0U,
    var X: UByte = 0U,
    var Y: UByte = 0U,
    var PC: UShort = 0U,
    var S: UByte = 0U,
    val P: ProcessorStatus = ProcessorStatus(),
)

/**
 * プロセッサーステータス
 * @property value 実値
 */
@Suppress("PropertyName")
data class ProcessorStatus(
    // 電源投入時の状態（Iのみチェックあり）
    // https://www.nesdev.org/wiki/CPU_power_up_state
    var value: UByte = BIT_MASK_2,
) {
    /** Carry */
    var C: Boolean
        get() = (value and BIT_MASK_0) != 0.toUByte()
        set(value) {
            this.value = (if (value) this.value or BIT_MASK_0 else this.value and BIT_MASK_0.inv())
        }

    /** Zero */
    var Z: Boolean
        get() = (value and BIT_MASK_1) != 0.toUByte()
        set(value) {
            this.value = (if (value) this.value or BIT_MASK_1 else this.value and BIT_MASK_1.inv())
        }

    /** Interrupt Disable */
    var I: Boolean
        get() = (value and BIT_MASK_2) != 0.toUByte()
        set(value) {
            this.value = (if (value) this.value or BIT_MASK_2 else this.value and BIT_MASK_2.inv())
        }

    /** Decimal（未使用） */
    @Suppress("MemberVisibilityCanBePrivate")
    var D: Boolean
        get() = (value and BIT_MASK_3) != 0.toUByte()
        set(value) {
            this.value = (if (value) this.value or BIT_MASK_3 else this.value and BIT_MASK_3.inv())
        }

    /** Break */
    var B: Boolean
        get() = (value and BIT_MASK_4) != 0.toUByte()
        set(value) {
            this.value = (if (value) this.value or BIT_MASK_4 else this.value and BIT_MASK_4.inv())
        }

    /** Reserved（常にtrue） */
    @Suppress("MemberVisibilityCanBePrivate")
    var R: Boolean
        get() = (value and BIT_MASK_5) != 0.toUByte()
        private set(value) {
            this.value = (if (value) this.value or BIT_MASK_5 else this.value and BIT_MASK_5.inv())
        }

    /** Overflow */
    var V: Boolean
        get() = (value and BIT_MASK_6) != 0.toUByte()
        set(value) {
            this.value = (if (value) this.value or BIT_MASK_6 else this.value and BIT_MASK_6.inv())
        }

    /** Negative */
    var N: Boolean
        get() = (value and BIT_MASK_7) != 0.toUByte()
        set(value) {
            this.value = (if (value) this.value or BIT_MASK_7 else this.value and BIT_MASK_7.inv())
        }

    init {
        // 固定値を設定
        R = true
    }
}
