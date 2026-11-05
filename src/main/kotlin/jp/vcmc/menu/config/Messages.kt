package jp.vcmc.menu.config

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
 * messages.yml の読み込みと MiniMessage への変換。不足しているキーは jar 内の既定値で補う。
 * プレースホルダーは Component で受ける。プレイヤー名や入力値を MiniMessage として解釈させないため。
 */
class Messages(private val plugin: JavaPlugin) {
    private val mini = MiniMessage.miniMessage()

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
    }

    fun parse(template: String, vararg args: Pair<String, Component>): Component {
        val resolvers = TagResolver.builder()
            .resolver(Placeholder.parsed("prefix", config.getString("prefix") ?: ""))
        args.forEach { (name, value) -> resolvers.resolver(Placeholder.component(name, value)) }
        return try {
            mini.deserialize(template, resolvers.build())
        } catch (e: Exception) {
            plugin.logger.warning("MiniMessage の解析に失敗しました: '$template' (${e.message})")
            Component.text(template)
        }
    }

    fun text(path: String, vararg args: Pair<String, Component>): Component =
        parse(config.getString(path) ?: path, *args)

    fun send(target: Audience, path: String, vararg args: Pair<String, Component>) {
        val template = config.getString(path) ?: path
        if (template.isBlank()) return
        target.sendMessage(parse(template, *args))
    }

    /** アイテム名用。インベントリのアイテム名は既定で斜体になるので打ち消す。 */
    fun itemName(template: String, vararg args: Pair<String, Component>): Component =
        parse(template, *args).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    fun itemNameAt(path: String, vararg args: Pair<String, Component>): Component =
        itemName(config.getString(path) ?: path, *args)

    fun itemLoreAt(path: String, vararg args: Pair<String, Component>): List<Component> =
        config.getStringList(path).map { itemName(it, *args) }

    private companion object {
        const val FILE_NAME = "messages.yml"
    }
}
