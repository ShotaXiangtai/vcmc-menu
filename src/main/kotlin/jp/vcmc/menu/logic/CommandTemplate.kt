package jp.vcmc.menu.logic

/** "bolt edit add {player}" のようなコマンドの雛形。先頭の / は持たない。 */
data class CommandTemplate(val raw: String) {
    /** 登場順の入力キー。 */
    val placeholders: List<String>
        get() = PLACEHOLDER.findAll(raw).map { it.groupValues[1] }.distinct().toList()

    /** 入力値を埋め込む。値の中の { } や $ はそのまま扱う。 */
    fun render(values: Map<String, String>): String =
        PLACEHOLDER.replace(raw) { match -> values[match.groupValues[1]] ?: match.value }

    companion object {
        private val PLACEHOLDER = Regex("""\{([a-z0-9_]+)}""")

        fun of(line: String) = CommandTemplate(line.trim().removePrefix("/").trim())
    }
}

/**
 * プレイヤーの入力がコマンドの 1 引数として使えるかを確認し、前後の空白を除いた値を返す。使えなければ null。
 * 空白を許すと引数がずれる。§ と制御文字はチャット欄から打てない文字なので、ここからも通さない。
 */
fun sanitizeArgument(raw: String?, maxLength: Int): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value.length > maxLength) return null
    if (value.any { it.isWhitespace() || it.isISOControl() || it == '§' }) return null
    return value
}
