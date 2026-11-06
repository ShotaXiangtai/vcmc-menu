package jp.vcmc.menu.i18n

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TranslationFilesTest {
    private val menu = TranslationFilesCheck.resource("menu.yml")

    /** lang ファイルと同じキーで並べた、menu.yml の文面。 */
    private val menuTexts: Map<String, String> = buildMap {
        val screens = assertNotNull(menu.getConfigurationSection("screens"))
        for (screenId in screens.getKeys(false)) {
            val screen = assertNotNull(screens.getConfigurationSection(screenId))
            put("menu.screens.$screenId.title", screen.getString("title").orEmpty())
            val buttons = assertNotNull(screen.getConfigurationSection("buttons"))
            for (buttonId in buttons.getKeys(false)) {
                val button = assertNotNull(buttons.getConfigurationSection(buttonId))
                val path = "menu.screens.$screenId.buttons.$buttonId"
                put("$path.name", button.getString("name").orEmpty())
                if (button.isList("lore")) put("$path.lore", button.getStringList("lore").joinToString("\n"))
                button.getConfigurationSection("inputs")?.let { inputs ->
                    for (key in inputs.getKeys(false)) put("$path.inputs.$key.label", inputs.getString("$key.label").orEmpty())
                }
            }
        }
    }

    /** 説明の最後のコマンドの行。<名前> のような部分はタグではなく文字として表示される。 */
    private fun isCommandLine(line: String) = line.startsWith("<dark_gray>/")

    @Test
    fun `どの言語にも日本語と同じ文面がある`() {
        val messages = TranslationFilesCheck.resource("messages.yml")
        TranslationFilesCheck.check(TranslationFilesCheck.texts(messages) + menuTexts, ::isCommandLine)
    }

    /** "<dark_gray>/bolt edit add <名前>" を、コマンドの部分と引数の数に分ける。 */
    private fun commandShape(line: String): Pair<String, Int> {
        val body = line.removePrefix("<dark_gray>")
        return body.substringBefore('<').trim() to body.count { it == '<' }
    }

    @Test
    fun `コマンドの書式はタグとして解釈されずそのまま表示される`() {
        val plainText = PlainTextComponentSerializer.plainText()
        val loreKeys = menuTexts.keys.filter { it.endsWith(".lore") }
        assertTrue(loreKeys.isNotEmpty())

        fun assertLiteral(line: String, message: String) =
            assertEquals(line.removePrefix("<dark_gray>"), plainText.serialize(MiniMessage.miniMessage().deserialize(line)), message)

        loreKeys.forEach { key ->
            menuTexts.getValue(key).split('\n').filter(::isCommandLine).forEach { assertLiteral(it, "ja: $key") }
        }
        TranslationFilesCheck.translated.forEach { (language, file) ->
            loreKeys.forEach { key ->
                file.getStringList(key).filter(::isCommandLine).forEach { assertLiteral(it, "${language.code}: $key") }
            }
        }
    }

    @Test
    fun `説明のコマンドの行は日本語と同じコマンドを指す`() {
        val checked = menuTexts.filterKeys { it.endsWith(".lore") }
        assertTrue(checked.isNotEmpty())
        TranslationFilesCheck.translated.forEach { (language, file) ->
            checked.forEach { (key, japanese) ->
                val expected = japanese.split('\n').filter(::isCommandLine).map(::commandShape)
                val actual = file.getStringList(key).filter(::isCommandLine).map(::commandShape)
                assertEquals(expected, actual, "${language.code}: $key")
            }
        }
    }
}
