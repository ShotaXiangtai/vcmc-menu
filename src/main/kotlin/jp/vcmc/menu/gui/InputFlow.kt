package jp.vcmc.menu.gui

import jp.vcmc.menu.config.ButtonAction
import jp.vcmc.menu.config.InputSpec
import jp.vcmc.menu.config.MenuButton

/** コマンドボタンを押してから実行するまでの途中経過。入力はプレイヤー選択とダイアログをまたいで順に集める。 */
data class InputFlow(
    val button: MenuButton,
    val command: ButtonAction.RunCommand,
    /** キャンセルしたときに戻る画面。 */
    val returnTo: String,
    val values: Map<String, String> = emptyMap(),
) {
    fun nextInput(): InputSpec? = command.inputs.firstOrNull { it.key !in values }

    fun with(key: String, value: String): InputFlow = copy(values = values + (key to value))

    fun commandLine(): String = command.template.render(values)
}
