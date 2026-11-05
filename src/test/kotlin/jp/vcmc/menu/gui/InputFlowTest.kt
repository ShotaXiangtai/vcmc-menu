package jp.vcmc.menu.gui

import jp.vcmc.menu.config.ButtonAction
import jp.vcmc.menu.config.InputSpec
import jp.vcmc.menu.config.InputType
import jp.vcmc.menu.config.MenuButton
import jp.vcmc.menu.config.RunVia
import jp.vcmc.menu.logic.CommandTemplate
import org.bukkit.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputFlowTest {
    private val group = InputSpec("group", InputType.TEXT, "group", 32)
    private val player = InputSpec("player", InputType.PLAYER, "player", 16)

    private fun flow(line: String, vararg inputs: InputSpec): InputFlow {
        val action = ButtonAction.RunCommand(CommandTemplate.of(line), inputs.toList(), RunVia.SERVER)
        return InputFlow(MenuButton("id", Material.STONE, "name", emptyList(), action), action, "main")
    }

    @Test
    fun `a command without inputs is ready immediately`() {
        val flow = flow("sit")
        assertNull(flow.nextInput())
        assertEquals("sit", flow.commandLine())
    }

    @Test
    fun `inputs are asked in written order until all are filled`() {
        val first = flow("bolt group add {group} {player}", group, player)
        assertEquals(group, first.nextInput())

        val second = first.with("group", "friends")
        assertEquals(player, second.nextInput())

        val done = second.with("player", "Steve")
        assertNull(done.nextInput())
        assertEquals("bolt group add friends Steve", done.commandLine())
    }

    @Test
    fun `with does not change the original flow`() {
        // キャンセルして別のボタンから入り直したときに、前の入力が残らないこと。
        val first = flow("bolt group list {group}", group)
        first.with("group", "friends")
        assertEquals(group, first.nextInput())
    }
}
