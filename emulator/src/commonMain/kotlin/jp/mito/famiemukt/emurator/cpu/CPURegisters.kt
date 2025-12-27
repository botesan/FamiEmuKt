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
    var value: UByte = BIT_MASK_2.toUByte(),
) {
    /** Carry */
    var C: Boolean
        get() = value.isBit(bitMask = BIT_MASK_0)
        set(value) {
            this.value = this.value.applyBit(bitMask = BIT_MASK_0, value = value)
        }

    /** Zero */
    var Z: Boolean
        get() = value.isBit(bitMask = BIT_MASK_1)
        set(value) {
            this.value = this.value.applyBit(bitMask = BIT_MASK_1, value = value)
        }

    /** Interrupt Disable */
    var I: Boolean
        get() = value.isBit(bitMask = BIT_MASK_2)
        set(value) {
            this.value = this.value.applyBit(bitMask = BIT_MASK_2, value = value)
        }

    /** Decimal（未使用） */
    @Suppress("MemberVisibilityCanBePrivate")
    var D: Boolean
        get() = value.isBit(bitMask = BIT_MASK_3)
        set(value) {
            this.value = this.value.applyBit(bitMask = BIT_MASK_3, value = value)
        }

    /** Break */
    var B: Boolean
        get() = value.isBit(bitMask = BIT_MASK_4)
        set(value) {
            this.value = this.value.applyBit(bitMask = BIT_MASK_4, value = value)
        }

    /** Reserved（常にtrue） */
    @Suppress("MemberVisibilityCanBePrivate")
    var R: Boolean
        get() = value.isBit(bitMask = BIT_MASK_5)
        private set(value) {
            this.value = this.value.applyBit(bitMask = BIT_MASK_5, value = value)
        }

    /** Overflow */
    var V: Boolean
        get() = value.isBit(bitMask = BIT_MASK_6)
        set(value) {
            this.value = this.value.applyBit(bitMask = BIT_MASK_6, value = value)
        }

    /** Negative */
    var N: Boolean
        get() = value.isBit(bitMask = BIT_MASK_7)
        set(value) {
            this.value = this.value.applyBit(bitMask = BIT_MASK_7, value = value)
        }

    fun init() {
        // 固定値を設定
        R = true
    }

    init {
        init()
    }
}
