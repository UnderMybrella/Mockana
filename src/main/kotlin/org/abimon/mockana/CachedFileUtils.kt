package info.spiralframework.base.properties

import org.abimon.mockana.CachedFileReadOnlyProperty
import java.io.File

fun <T> cache(file: File, op: (File) -> T): CachedFileReadOnlyProperty<Any, T> = CachedFileReadOnlyProperty(file, op)