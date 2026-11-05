package jp.vcmc.menu.config

import jp.vcmc.menu.logic.CommandTemplate
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection

data class ConfigIssue(val path: String, val message: String)

/** definition が null なら、最初の画面が無いなどの理由でメニューとして使えない。 */
data class ParsedMenu(val definition: MenuDefinition?, val issues: List<ConfigIssue>)

/**
 * menu.yml を読む。不正な値はサーバーを止めず、問題として報告して既定値を使うか、そのボタンだけを省く。
 * @param resolveIcon アイテムとして使える Material を名前から引く。使えなければ null。
 */
class MenuParser(private val resolveIcon: (String) -> Material?) {
    private val issues = mutableListOf<ConfigIssue>()

    fun parse(root: ConfigurationSection): ParsedMenu {
        issues.clear()
        val section = root.getConfigurationSection("screens")
        if (section == null) {
            issue("screens", "画面が定義されていません")
            return ParsedMenu(null, issues.toList())
        }
        val screenIds = section.getKeys(false)
        val screens = linkedMapOf<String, MenuScreen>()
        for (id in screenIds) {
            val screen = section.getConfigurationSection(id)
            if (screen == null) issue("screens.$id", "画面の定義になっていません") else screens[id] = parseScreen(id, screen, screenIds)
        }
        if (MenuDefinition.ROOT !in screens) {
            issue("screens", "最初に開く画面 '${MenuDefinition.ROOT}' がありません")
            return ParsedMenu(null, issues.toList())
        }
        return ParsedMenu(MenuDefinition(screens), issues.toList())
    }

    private fun parseScreen(id: String, section: ConfigurationSection, screenIds: Set<String>): MenuScreen {
        val path = "screens.$id"
        var rows = section.getInt("rows", DEFAULT_ROWS)
        if (rows !in 1..MAX_ROWS) {
            issue("$path.rows", "1 から $MAX_ROWS で指定してください: $rows（$DEFAULT_ROWS を使います）")
            rows = DEFAULT_ROWS
        }
        val fillerName = section.getString("filler")
        val filler = if (fillerName == null) DEFAULT_FILLER else resolveIcon(fillerName) ?: run {
            issue("$path.filler", "アイテムとして使えません: $fillerName")
            DEFAULT_FILLER
        }
        val buttons = linkedMapOf<Int, MenuButton>()
        val buttonsSection = section.getConfigurationSection("buttons")
        buttonsSection?.getKeys(false)?.forEach { buttonId ->
            val buttonPath = "$path.buttons.$buttonId"
            val buttonSection = buttonsSection.getConfigurationSection(buttonId)
            if (buttonSection == null) {
                issue(buttonPath, "ボタンの定義になっていません")
                return@forEach
            }
            val slot = buttonSection.getInt("slot", -1)
            if (slot !in 0 until rows * SLOTS_PER_ROW) {
                issue("$buttonPath.slot", "0 から ${rows * SLOTS_PER_ROW - 1} で指定してください: ${buttonSection.getString("slot")}")
                return@forEach
            }
            val existing = buttons[slot]
            if (existing != null) {
                issue("$buttonPath.slot", "スロット $slot は ${existing.id} が使っています")
                return@forEach
            }
            val button = parseButton(buttonId, buttonPath, buttonSection, screenIds) ?: return@forEach
            buttons[slot] = button
        }
        return MenuScreen(id, section.getString("title") ?: id, rows, filler, buttons)
    }

    private fun parseButton(
        id: String,
        path: String,
        section: ConfigurationSection,
        screenIds: Set<String>,
    ): MenuButton? {
        val action = parseAction(path, section, screenIds) ?: return null
        val iconName = section.getString("icon")
        val icon = iconName?.let(resolveIcon) ?: run {
            issue("$path.icon", "アイテムとして使えません: $iconName（${FALLBACK_ICON.name} を使います）")
            FALLBACK_ICON
        }
        return MenuButton(id, icon, section.getString("name") ?: id, section.getStringList("lore"), action)
    }

    private fun parseAction(path: String, section: ConfigurationSection, screenIds: Set<String>): ButtonAction? {
        val command = section.getString("command")
        val open = section.getString("open")
        val close = section.getBoolean("close")
        if (listOf(command != null, open != null, close).count { it } != 1) {
            issue(path, "command、open、close のどれか 1 つを指定してください")
            return null
        }
        return when {
            close -> ButtonAction.Close
            open != null -> {
                if (open in screenIds) {
                    ButtonAction.OpenScreen(open)
                } else {
                    issue("$path.open", "画面がありません: $open")
                    null
                }
            }
            else -> parseCommand(path, section, command.orEmpty())
        }
    }

    private fun parseCommand(path: String, section: ConfigurationSection, line: String): ButtonAction? {
        val template = CommandTemplate.of(line)
        if (template.raw.isEmpty()) {
            issue("$path.command", "コマンドが空です")
            return null
        }
        val inputsSection = section.getConfigurationSection("inputs")
        val inputs = inputsSection?.getKeys(false)?.map { parseInput("$path.inputs.$it", it, inputsSection) }.orEmpty()
        if (inputs.map { it.key }.toSet() != template.placeholders.toSet()) {
            issue("$path.inputs", "command の {...} と inputs のキーが一致しません: ${template.placeholders} / ${inputs.map { it.key }}")
            return null
        }
        val via = when (val name = section.getString("via", "server")?.lowercase()) {
            "server" -> RunVia.SERVER
            "client" -> RunVia.CLIENT
            else -> {
                issue("$path.via", "server か client で指定してください: $name（server を使います）")
                RunVia.SERVER
            }
        }
        return ButtonAction.RunCommand(template, inputs, via)
    }

    private fun parseInput(path: String, key: String, parent: ConfigurationSection): InputSpec {
        val section = parent.getConfigurationSection(key)
        if (section == null) issue(path, "入力の定義になっていません（文字入力として扱います）")
        val type = when (val name = section?.getString("type", "text")?.lowercase() ?: "text") {
            "player" -> InputType.PLAYER
            "text" -> InputType.TEXT
            else -> {
                issue("$path.type", "player か text で指定してください: $name（text を使います）")
                InputType.TEXT
            }
        }
        val defaultLength = if (type == InputType.PLAYER) PLAYER_NAME_LENGTH else DEFAULT_TEXT_LENGTH
        var maxLength = section?.getInt("max-length", defaultLength) ?: defaultLength
        if (maxLength !in 1..MAX_TEXT_LENGTH) {
            issue("$path.max-length", "1 から $MAX_TEXT_LENGTH で指定してください: $maxLength（$defaultLength を使います）")
            maxLength = defaultLength
        }
        return InputSpec(key, type, section?.getString("label") ?: key, maxLength)
    }

    private fun issue(path: String, message: String) {
        issues += ConfigIssue(path, message)
    }

    companion object {
        const val SLOTS_PER_ROW = 9
        const val MAX_ROWS = 6
        const val DEFAULT_ROWS = 3
        const val PLAYER_NAME_LENGTH = 16
        const val DEFAULT_TEXT_LENGTH = 32

        /** チャットのコマンド長の上限（256 文字）に、コマンド本体の分を残した値。 */
        const val MAX_TEXT_LENGTH = 128
        val DEFAULT_FILLER = Material.GRAY_STAINED_GLASS_PANE
        val FALLBACK_ICON = Material.STONE
    }
}
