package jp.vcmc.menu.gui

import jp.vcmc.menu.VcmcMenuPlugin
import jp.vcmc.menu.config.ButtonAction
import jp.vcmc.menu.config.InputSpec
import jp.vcmc.menu.config.InputType
import jp.vcmc.menu.config.MenuButton
import jp.vcmc.menu.config.MenuDefinition
import jp.vcmc.menu.config.MenuParser
import jp.vcmc.menu.config.MenuScreen
import jp.vcmc.menu.config.RunVia
import jp.vcmc.menu.logic.Page
import jp.vcmc.menu.logic.paginate
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/**
 * メニューの画面とプレイヤー選択画面。インベントリは開くたびにプレイヤーごとに作る。
 * クリックイベントはプレイヤーを所有するリージョンで発火するので、他のリージョンのものには触らない。
 */
class MenuGui(private val plugin: VcmcMenuPlugin) : Listener {
    private sealed interface View
    private class ScreenView(val screen: MenuScreen) : View
    private class PickerView(val flow: InputFlow, val input: InputSpec, val names: Page<String>) : View

    private class Holder(val view: View) : InventoryHolder {
        lateinit var inv: Inventory
        val openedAtNanos = System.nanoTime()

        /** 1 つの画面で受け付ける操作は 1 回だけ。連打で切り替え系のコマンドが 2 回走らないようにする。 */
        var used = false

        override fun getInventory(): Inventory = inv
    }

    private val messages get() = plugin.messages

    /** 画面を開く。どのスレッドから呼んでもよい。 */
    fun open(player: Player, screenId: String = MenuDefinition.ROOT) {
        plugin.runNextTick(player) { showScreen(player, screenId) }
    }

    /** 次の入力を求める。すべて揃っていればコマンドを実行する。プレイヤーのリージョンで呼ぶこと。 */
    fun advance(player: Player, flow: InputFlow) {
        val input = flow.nextInput()
        when {
            input == null -> finish(player, flow)
            input.type == InputType.PLAYER -> showPicker(player, flow, input, 0)
            else -> plugin.dialogs.showText(player, flow, input)
        }
    }

    private fun finish(player: Player, flow: InputFlow) {
        val line = flow.commandLine()
        player.closeInventory()
        when (flow.command.via) {
            RunVia.SERVER -> player.performCommand(line)
            RunVia.CLIENT -> plugin.dialogs.showClientRun(player, flow, line)
        }
    }

    /** プレイヤーのリージョンで呼ぶこと。 */
    fun showScreen(player: Player, screenId: String) {
        val menu = plugin.menu
        // 読み直しで画面が消えていたら最初の画面へ戻す。
        val screen = menu.screens[screenId] ?: menu.root
        val holder = Holder(ScreenView(screen))
        val inv = Bukkit.createInventory(holder, screen.rows * MenuParser.SLOTS_PER_ROW, messages.parse(screen.title))
        holder.inv = inv
        fill(inv, 0 until inv.size, screen.filler)
        screen.buttons.forEach { (slot, button) -> inv.setItem(slot, buttonItem(button)) }
        player.openInventory(inv)
    }

    private fun showPicker(player: Player, flow: InputFlow, input: InputSpec, requestedPage: Int) {
        val candidates = Bukkit.getOnlinePlayers()
            .filter { it.uniqueId != player.uniqueId && player.canSee(it) }
            .sortedBy { it.name.lowercase() }
        val page = paginate(candidates, requestedPage, PICKER_PAGE_SIZE)
        val holder = Holder(PickerView(flow, input, Page(page.items.map { it.name }, page.index, page.count)))
        val inv = Bukkit.createInventory(
            holder,
            PICKER_SIZE,
            messages.text(
                "picker.title",
                "label" to messages.parse(input.label),
                "page" to Component.text(page.index + 1),
                "pages" to Component.text(page.count),
            ),
        )
        holder.inv = inv
        page.items.forEachIndexed { slot, other -> inv.setItem(slot, playerHead(other)) }
        if (candidates.isEmpty()) {
            inv.setItem(
                PICKER_EMPTY_SLOT,
                item(Material.PAPER, messages.itemNameAt("picker.empty-name"), messages.itemLoreAt("picker.empty-lore")),
            )
        }
        fill(inv, PICKER_PAGE_SIZE until PICKER_SIZE, MenuParser.DEFAULT_FILLER)
        inv.setItem(
            PICKER_SLOT_BACK,
            item(Material.ARROW, messages.itemNameAt("picker.back-name"), messages.itemLoreAt("picker.back-lore")),
        )
        inv.setItem(
            PICKER_SLOT_MANUAL,
            item(Material.NAME_TAG, messages.itemNameAt("picker.manual-name"), messages.itemLoreAt("picker.manual-lore")),
        )
        if (page.hasPrevious) inv.setItem(PICKER_SLOT_PREVIOUS, item(Material.PAPER, messages.itemNameAt("picker.previous-name"), emptyList()))
        if (page.hasNext) inv.setItem(PICKER_SLOT_NEXT, item(Material.PAPER, messages.itemNameAt("picker.next-name"), emptyList()))
        player.openInventory(inv)
    }

    private fun fill(inv: Inventory, slots: IntRange, material: Material) {
        val filler = ItemStack(material).apply { editMeta { it.isHideTooltip = true } }
        for (slot in slots) inv.setItem(slot, filler)
    }

    private fun buttonItem(button: MenuButton): ItemStack =
        item(button.icon, messages.itemName(button.name), button.lore.map { messages.itemName(it) })

    private fun item(material: Material, name: Component, lore: List<Component>): ItemStack =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(name)
                meta.lore(lore)
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }
        }

    private fun playerHead(other: Player): ItemStack =
        item(
            Material.PLAYER_HEAD,
            messages.itemNameAt("picker.player-name", "player" to Component.text(other.name)),
            messages.itemLoreAt("picker.player-lore"),
        ).apply {
            editMeta(SkullMeta::class.java) { it.playerProfile = other.playerProfile }
        }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.getHolder(false) as? Holder ?: return
        event.isCancelled = true
        if (event.clickedInventory != top) return
        // Q キーや数字キーは、カーソルが乗っていただけの誤操作なので実行しない。
        if (!event.click.isLeftClick && !event.click.isRightClick) return
        val player = event.whoClicked as? Player ?: return
        if (holder.used || System.nanoTime() - holder.openedAtNanos < CLICK_GUARD_NANOS) return
        val accepted = when (val view = holder.view) {
            is ScreenView -> clickScreen(player, view, event.slot)
            is PickerView -> clickPicker(player, view, event.slot)
        }
        if (accepted) holder.used = true
    }

    /** 処理を受け付けたら true。何もないスロットでは false を返して、画面を生かしておく。 */
    private fun clickScreen(player: Player, view: ScreenView, slot: Int): Boolean {
        val button = view.screen.buttons[slot] ?: return false
        plugin.runNextTick(player) {
            when (val action = button.action) {
                is ButtonAction.OpenScreen -> showScreen(player, action.screenId)
                ButtonAction.Close -> player.closeInventory()
                is ButtonAction.RunCommand -> advance(player, InputFlow(button, action, view.screen.id))
            }
        }
        return true
    }

    private fun clickPicker(player: Player, view: PickerView, slot: Int): Boolean {
        val names = view.names
        val run: () -> Unit = when (slot) {
            PICKER_SLOT_BACK -> ({ showScreen(player, view.flow.returnTo) })
            PICKER_SLOT_MANUAL -> ({ plugin.dialogs.showText(player, view.flow, view.input) })
            PICKER_SLOT_PREVIOUS -> {
                if (!names.hasPrevious) return false
                ({ showPicker(player, view.flow, view.input, names.index - 1) })
            }
            PICKER_SLOT_NEXT -> {
                if (!names.hasNext) return false
                ({ showPicker(player, view.flow, view.input, names.index + 1) })
            }
            else -> {
                val name = names.items.getOrNull(slot) ?: return false
                ({ advance(player, view.flow.with(view.input.key, name)) })
            }
        }
        plugin.runNextTick(player, run)
        return true
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is Holder) event.isCancelled = true
    }

    private companion object {
        /**
         * 開いた直後のクリックは無視する。前の画面のボタンをダブルクリックしたとき、
         * 2 回目が新しい画面の同じ位置のボタンに当たって、意図しないコマンドが走るのを防ぐ。
         */
        const val CLICK_GUARD_NANOS = 150_000_000L

        const val PICKER_SIZE = 54
        const val PICKER_PAGE_SIZE = 45
        const val PICKER_EMPTY_SLOT = 22
        const val PICKER_SLOT_BACK = 45
        const val PICKER_SLOT_PREVIOUS = 47
        const val PICKER_SLOT_MANUAL = 49
        const val PICKER_SLOT_NEXT = 51
    }
}
