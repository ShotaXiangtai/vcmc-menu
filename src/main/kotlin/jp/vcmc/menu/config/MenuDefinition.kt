package jp.vcmc.menu.config

import jp.vcmc.menu.logic.CommandTemplate
import org.bukkit.Material

enum class InputType { PLAYER, TEXT }

data class InputSpec(val key: String, val type: InputType, val label: String, val maxLength: Int)

enum class RunVia {
    /** このサーバーでプレイヤーとして実行する。 */
    SERVER,

    /**
     * プレイヤーのクライアントから送らせる。Velocity のコマンドはこちらでないと届かない。
     * クライアントに勝手にコマンドを送らせる手段はないので、確認ダイアログのボタンを押してもらう。
     */
    CLIENT,
}

sealed interface ButtonAction {
    data class OpenScreen(val screenId: String) : ButtonAction
    data object Close : ButtonAction
    data class RunCommand(val template: CommandTemplate, val inputs: List<InputSpec>, val via: RunVia) : ButtonAction
}

data class MenuButton(
    val id: String,
    val icon: Material,
    val name: String,
    val lore: List<String>,
    val action: ButtonAction,
)

data class MenuScreen(
    val id: String,
    val title: String,
    val rows: Int,
    val filler: Material,
    val buttons: Map<Int, MenuButton>,
)

data class MenuDefinition(val screens: Map<String, MenuScreen>) {
    val root: MenuScreen get() = screens.getValue(ROOT)

    companion object {
        /** /menu で最初に開く画面。 */
        const val ROOT = "main"
    }
}
