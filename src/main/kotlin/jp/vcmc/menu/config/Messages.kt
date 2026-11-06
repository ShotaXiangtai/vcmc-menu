package jp.vcmc.menu.config

import jp.vcmc.menu.i18n.Language
import jp.vcmc.menu.i18n.Translations
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

/**
 * 受け取る人の言語で文面を作る。
 * 日本語は messages.yml と menu.yml、ほかの言語は lang フォルダから読む。不足しているキーは jar 内の既定値で補う。
 * プレースホルダーは Component で受ける。プレイヤー名や入力値を MiniMessage として解釈させないため。
 */
class Messages(private val plugin: JavaPlugin) {
    private val mini = MiniMessage.miniMessage()
    private val translations = Translations(plugin)

    @Volatile
    private var config = YamlConfiguration()

    fun reload() {
        val file = File(plugin.dataFolder, FILE_NAME)
        if (!file.exists()) plugin.saveResource(FILE_NAME, false)
        val loaded = YamlConfiguration()
        try {
            loaded.load(file)
        } catch (e: Exception) {
            plugin.logger.severe("$FILE_NAME を読み込めませんでした。既定のメッセージを使います: ${e.message}")
        }
        val defaults = plugin.getResource(FILE_NAME)?.use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
        }
        if (defaults != null) {
            val missing = defaults.getKeys(true).filter { !defaults.isConfigurationSection(it) && !loaded.contains(it, true) }
            if (missing.isNotEmpty()) {
                plugin.logger.warning("$FILE_NAME に不足しているキーを既定値で補います: ${missing.joinToString()}")
            }
            loaded.setDefaults(defaults)
        }
        config = loaded
        translations.reload()
    }

    private fun raw(language: Language, path: String): String =
        translations.string(language, path) ?: config.getString(path) ?: path

    fun parse(language: Language, template: String, vararg args: Pair<String, Component>): Component {
        val resolvers = TagResolver.builder()
            .resolver(Placeholder.parsed("prefix", raw(language, "prefix")))
        args.forEach { (name, value) -> resolvers.resolver(Placeholder.component(name, value)) }
        return try {
            mini.deserialize(template, resolvers.build())
        } catch (e: Exception) {
            plugin.logger.warning("MiniMessage の解析に失敗しました: '$template' (${e.message})")
            Component.text(template)
        }
    }

    fun text(language: Language, path: String, vararg args: Pair<String, Component>): Component =
        parse(language, raw(language, path), *args)

    fun send(target: Audience, path: String, vararg args: Pair<String, Component>) {
        val language = Language.of(target)
        val template = raw(language, path)
        if (template.isBlank()) return
        target.sendMessage(parse(language, template, *args))
    }

    /** アイテム名用。インベントリのアイテム名は既定で斜体になるので打ち消す。 */
    fun itemName(language: Language, template: String, vararg args: Pair<String, Component>): Component =
        parse(language, template, *args).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    fun itemNameAt(language: Language, path: String, vararg args: Pair<String, Component>): Component =
        itemName(language, raw(language, path), *args)

    fun itemLoreAt(language: Language, path: String, vararg args: Pair<String, Component>): List<Component> =
        (translations.stringList(language, path) ?: config.getStringList(path)).map { itemName(language, it, *args) }

    /** 画面のタイトル。翻訳が無ければ menu.yml の文面を使う。 */
    fun screenTitle(language: Language, screen: MenuScreen): String =
        translations.string(language, "menu.screens.${screen.id}.title") ?: screen.title

    /** ボタンの名前。翻訳が無ければ menu.yml の文面を使う。 */
    fun buttonName(language: Language, screenId: String, button: MenuButton): String =
        translations.string(language, "${buttonPath(screenId, button)}.name") ?: button.name

    /** ボタンの説明。翻訳が無ければ menu.yml の文面を使う。 */
    fun buttonLore(language: Language, screenId: String, button: MenuButton): List<String> =
        translations.stringList(language, "${buttonPath(screenId, button)}.lore") ?: button.lore

    /** 入力欄の説明。翻訳が無ければ menu.yml の文面を使う。 */
    fun inputLabel(language: Language, screenId: String, button: MenuButton, input: InputSpec): String =
        translations.string(language, "${buttonPath(screenId, button)}.inputs.${input.key}.label") ?: input.label

    private fun buttonPath(screenId: String, button: MenuButton) = "menu.screens.$screenId.buttons.${button.id}"

    private companion object {
        const val FILE_NAME = "messages.yml"
    }
}
