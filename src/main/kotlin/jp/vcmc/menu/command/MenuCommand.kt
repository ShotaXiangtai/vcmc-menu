package jp.vcmc.menu.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import jp.vcmc.menu.VcmcMenuPlugin
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/** /menu と /menu reload を登録する。 */
class MenuCommand(private val plugin: VcmcMenuPlugin) {
    fun register() {
        plugin.lifecycleManager.registerEventHandler(
            LifecycleEvents.COMMANDS.newHandler { event ->
                event.registrar().register(build(), "メニューを開きます")
            },
        )
    }

    private fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("menu")
            .requires { it.sender.hasPermission(PERM_MENU) }
            .executes(::openMenu)
            .then(
                Commands.literal("reload")
                    .requires { it.sender.hasPermission(PERM_RELOAD) }
                    .executes(::reload),
            )
            .build()

    private fun openMenu(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val player = source.executor as? Player ?: source.sender as? Player
        if (player == null) plugin.messages.send(source.sender, "common.player-only") else plugin.gui.open(player)
        return Command.SINGLE_SUCCESS
    }

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val issues = plugin.reloadAll()
        if (issues.isEmpty()) {
            plugin.messages.send(sender, "reload.ok")
        } else {
            plugin.messages.send(sender, "reload.issues", "count" to Component.text(issues.size))
        }
        return Command.SINGLE_SUCCESS
    }

    private companion object {
        const val PERM_MENU = "vcmcmenu.command.menu"
        const val PERM_RELOAD = "vcmcmenu.admin.reload"
    }
}
