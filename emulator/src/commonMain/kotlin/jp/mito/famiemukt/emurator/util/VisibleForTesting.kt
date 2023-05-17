package jp.mito.famiemukt.emurator.util

import kotlin.annotation.AnnotationTarget.*

/**
 * テストコードからのみアクセス可能にするアノテーション
 * TODO: できれば使用しないようにソースを修正する
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@MustBeDocumented
@Target(CLASS, ANNOTATION_CLASS, PROPERTY, FIELD, CONSTRUCTOR, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
annotation class VisibleForTesting
