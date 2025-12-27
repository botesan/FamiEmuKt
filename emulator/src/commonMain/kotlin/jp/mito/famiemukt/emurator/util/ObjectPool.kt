package jp.mito.famiemukt.emurator.util

interface Poolable<T> where T : Poolable<T> {
    class ObjectPool<T>(initialCapacity: Int) {
        private val pool = ArrayDeque<T>(initialCapacity)
        val size: Int get() = pool.size
        fun obtain(): T? = pool.removeLastOrNull()
        fun recycle(obj: T) {
            @Suppress("ConstantConditionIf")
            if (false) {
                check(value = pool.none { it === obj }) {
                    "Object is already in the pool: $obj"
                }
            }
            pool.addLast(element = obj)
        }
    }

    fun recycle()
}
