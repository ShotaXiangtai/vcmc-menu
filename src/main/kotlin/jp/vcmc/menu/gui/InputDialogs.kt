package jp.vcmc.menu.gui

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import jp.vcmc.menu.VcmcMenuPlugin
import jp.vcmc.menu.config.InputSpec
import jp.vcmc.menu.i18n.Language
import jp.vcmc.menu.logic.sanitizeArgument
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 文字入力と、クライアント側で実行するコマンドの確認ダイアログ。
 * 応答は識別子だけを受け取り、何の入力なのかはサーバー側の pending で判断する。
 * 応答の中身はクライアントが自由に書き換えられるので信用しない。
 */
class InputDialogs(private val plugin: VcmcMenuPlugin) : Listener {
    /** input が null なら、入力を待たない確認ダイアログ。 */
    private class Pending(val flow: InputFlow, val input: InputSpec?)

    private val pending = ConcurrentHashMap<UUID, Pending>()
    private val messages get() = plugin.messages

    /** プレイヤーのリージョンで呼ぶこと。 */
    fun showText(player: Player, flow: InputFlow, input: InputSpec, invalid: Boolean = false) {
        // チェスト画面の上に重ねると、ダイアログを閉じたときにチェスト画面へ戻ってしまう。
        player.closeInventory()
        pending[player.uniqueId] = Pending(flow, input)
        val language = Language.of(player)
        val error = if (invalid) {
            listOf(DialogBody.plainMessage(messages.text(language, "dialog.invalid", "max" to Component.text(input.maxLength))))
        } else {
            emptyList()
        }
        val label = messages.parse(language, messages.inputLabel(language, flow.returnTo, flow.button, input))
        show(
            player,
            base(language, flow)
                .body(loreBody(language, flow) + error)
                .inputs(listOf(DialogInput.text(INPUT_KEY, label).maxLength(input.maxLength).build()))
                .build(),
            DialogType.confirmation(
                button(language, "dialog.submit", DialogAction.customClick(Key.key(SUBMIT_ID), null)),
                cancelButton(language),
            ),
        )
    }

    /** ボタンを押すとクライアントがコマンドを送るので、プロキシのコマンドにも届く。プレイヤーのリージョンで呼ぶこと。 */
    fun showClientRun(player: Player, flow: InputFlow, line: String) {
        pending[player.uniqueId] = Pending(flow, null)
        val language = Language.of(player)
        val notice = DialogBody.plainMessage(messages.text(language, "dialog.client-run-body", "command" to Component.text(line)))
        show(
            player,
            base(language, flow).body(listOf(notice)).build(),
            DialogType.confirmation(
                button(language, "dialog.client-run-confirm", DialogAction.staticAction(ClickEvent.runCommand("/$line"))),
                cancelButton(language),
            ),
        )
    }

    private fun base(language: Language, flow: InputFlow): DialogBase.Builder =
        DialogBase.builder(messages.parse(language, messages.buttonName(language, flow.returnTo, flow.button)))
            .canCloseWithEscape(true)
            .pause(false)

    private fun loreBody(language: Language, flow: InputFlow): List<DialogBody> =
        messages.buttonLore(language, flow.returnTo, flow.button).map { DialogBody.plainMessage(messages.parse(language, it)) }

    private fun button(language: Language, textPath: String, action: DialogAction): ActionButton =
        ActionButton.builder(messages.text(language, textPath)).action(action).build()

    /** Esc で閉じたときもこのボタンの動作になる。 */
    private fun cancelButton(language: Language): ActionButton =
        button(language, "dialog.cancel", DialogAction.customClick(Key.key(CANCEL_ID), null))

    private fun show(player: Player, base: DialogBase, type: DialogType) {
        player.showDialog(Dialog.create { factory -> factory.empty().base(base).type(type) })
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        val id = event.identifier.asString()
        if (id != SUBMIT_ID && id != CANCEL_ID) return
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        val text = event.dialogResponseView?.getText(INPUT_KEY)
        plugin.runNextTick(player) {
            val state = pending.remove(player.uniqueId) ?: return@runNextTick
            if (id == CANCEL_ID) {
                plugin.gui.showScreen(player, state.flow.returnTo)
            } else if (state.input != null) {
                submit(player, state.flow, state.input, text)
            }
        }
    }

    private fun submit(player: Player, flow: InputFlow, input: InputSpec, text: String?) {
        val value = sanitizeArgument(text, input.maxLength)
        if (value == null) {
            showText(player, flow, input, invalid = true)
        } else {
            plugin.gui.advance(player, flow.with(input.key, value))
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pending.remove(event.player.uniqueId)
    }

    private companion object {
        const val INPUT_KEY = "value"
        const val SUBMIT_ID = "vcmc-menu:submit"
        const val CANCEL_ID = "vcmc-menu:cancel"
    }
}
