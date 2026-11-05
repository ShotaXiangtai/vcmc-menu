package jp.vcmc.menu.logic

import kotlin.math.max

data class Page<T>(val items: List<T>, val index: Int, val count: Int) {
    val hasPrevious get() = index > 0
    val hasNext get() = index < count - 1
}

/** 範囲外のページは端に丸める。人数が減って最後のページが消えていても空のページを出さないため。 */
fun <T> paginate(all: List<T>, requested: Int, size: Int): Page<T> {
    val count = max(1, (all.size + size - 1) / size)
    val index = requested.coerceIn(0, count - 1)
    return Page(all.drop(index * size).take(size), index, count)
}
