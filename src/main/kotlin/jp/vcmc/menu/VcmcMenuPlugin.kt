package jp.vcmc.menu

import jp.vcmc.menu.command.MenuCommand
import jp.vcmc.menu.config.ConfigIssue
import jp.vcmc.menu.config.MenuDefinition
import jp.vcmc.menu.config.MenuParser
import jp.vcmc.menu.config.Messages
import jp.vcmc.menu.config.ParsedMenu
import jp.vcmc.menu.gui.InputDialogs
import jp.vcmc.menu.gui.MenuGui
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

class VcmcMenuPlugin : JavaPlugin() {
    lateinit var messages: Messages
        private set
    lateinit var gui: MenuGui
        private set
    lateinit var dialogs: InputDialogs
        private set

    /** 画面を開くときに読むだけで、開いている画面は読み直しの影響を受けない。 */
    @Volatile
    lateinit var menu: MenuDefinition
        private set

    override fun onEnable() {
        messages = Messages(this)
        gui = MenuGui(this)
        dialogs = InputDialogs(this)
        reloadAll()
        server.pluginManager.registerEvents(gui, this)
        server.pluginManager.registerEvents(dialogs, this)
        MenuCommand(this).register()
    }

    /** menu.yml と messages.yml を読み直して、見つかった問題を返す。 */
    fun reloadAll(): List<ConfigIssue> {
        messages.reload()
        return loadMenu()
    }

    /**
     * entity を所有するリージョンの次の tick で実行する。ログアウト済みなら何もしない。
     * インベントリのクリックイベントの中で画面を閉じたり開いたりしないよう、GUI の処理はすべてこれを通す。
     */
    fun runNextTick(entity: Entity, block: () -> Unit) {
        entity.scheduler.run(this, { block() }, null)
    }

    private fun loadMenu(): List<ConfigIssue> {
        val file = File(dataFolder, MENU_FILE)
        if (!file.exists()) saveResource(MENU_FILE, false)
        val parsed = try {
            MenuParser(::resolveIcon).parse(YamlConfiguration().apply { load(file) })
        } catch (e: Exception) {
            ParsedMenu(null, listOf(ConfigIssue(MENU_FILE, "読み込めませんでした: ${e.message}")))
        }
        parsed.issues.forEach { logger.severe("[$MENU_FILE] ${it.path}: ${it.message}") }
        menu = parsed.definition ?: fallbackMenu()
        return parsed.issues
    }

    /** 使える定義がないときは、起動中なら今の内容を使い続け、起動時なら jar 内の既定値を使う。 */
    private fun fallbackMenu(): MenuDefinition {
        if (::menu.isInitialized) {
            logger.severe("$MENU_FILE を使えないので、今のメニューをそのまま使います")
            return menu
        }
        logger.severe("$MENU_FILE を使えないので、既定のメニューを使います")
        val yaml = requireNotNull(getResource(MENU_FILE)) { "jar に $MENU_FILE がありません" }.use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
        }
        return requireNotNull(MenuParser(::resolveIcon).parse(yaml).definition) { "既定の $MENU_FILE が不正です" }
    }

    private fun resolveIcon(name: String): Material? = Material.matchMaterial(name)?.takeIf { it.isItem && !it.isAir }

    private companion object {
        const val MENU_FILE = "menu.yml"
    }
}
