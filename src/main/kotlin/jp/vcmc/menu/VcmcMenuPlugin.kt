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
import jp.vcmc.menu.i18n.OutdatedLangFiles
import java.io.File
import java.io.IOException
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
        replaceOutdatedMenu(file)
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

    /**
     * menu.yml が以前のリリースの既定の内容のままなら、脇へ移して新しい既定の内容をコピーし直す。
     * jar を入れ替えても menu.yml は上書きされないので、こうしないと追加したボタンが出ない。編集されたものはそのまま使う。
     */
    private fun replaceOutdatedMenu(file: File) {
        val outdated = getResource(MENU_OUTDATED)?.use { OutdatedLangFiles.parse(String(it.readBytes(), Charsets.UTF_8)) }.orEmpty()
        try {
            val backup = OutdatedLangFiles.moveAside(file, outdated[MENU_FILE].orEmpty())
            if (backup != null) {
                logger.info("$MENU_FILE は以前の既定の内容のままだったので、新しい内容に置き換えます。以前の内容は ${backup.name} に残します。")
                return
            }
        } catch (e: IOException) {
            logger.warning("$MENU_FILE を新しい内容に置き換えられませんでした: ${e.message}")
            return
        }
        // 古い menu.yml のままだと、追加したボタンや修正が反映されず、翻訳も当たらない。気付けるように知らせる。
        if (file.isFile && !isBundledMenu(file)) {
            logger.info("$MENU_FILE は編集されているので、そのまま使います。新しいボタンや修正は、jar 内の $MENU_FILE を見ながら反映してください。")
        }
    }

    private fun isBundledMenu(file: File): Boolean {
        val bundled = getResource(MENU_FILE)?.use { it.readBytes() } ?: return true
        return OutdatedLangFiles.hash(file.readBytes()) == OutdatedLangFiles.hash(bundled)
    }

    private fun resolveIcon(name: String): Material? = Material.matchMaterial(name)?.takeIf { it.isItem && !it.isAir }

    private companion object {
        const val MENU_FILE = "menu.yml"
        const val MENU_OUTDATED = "menu-outdated.txt"
    }
}
