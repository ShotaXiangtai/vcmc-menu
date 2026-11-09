package jp.vcmc.menu.config

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import kotlin.test.Test
import jp.vcmc.menu.i18n.OutdatedLangFiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** jar に入る menu.yml の中身を確認する。編集しなくても、サイトと README のコマンドがすべて使えること。 */
class DefaultMenuTest {
    private val parsed: ParsedMenu = run {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream("menu.yml"))
        val yaml = InputStreamReader(stream, Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
        MenuParser { Material.matchMaterial(it) }.parse(yaml)
    }
    private val menu = assertNotNull(parsed.definition)

    private val runButtons: List<Pair<MenuButton, ButtonAction.RunCommand>> =
        menu.screens.values.flatMap { screen -> screen.buttons.values }
            .mapNotNull { button -> (button.action as? ButtonAction.RunCommand)?.let { button to it } }

    @Test
    fun `there are no issues`() {
        assertEquals(emptyList(), parsed.issues)
    }

    @Test
    fun `all player commands are present`() {
        val expected = listOf(
            // https://www.vcmc.jp/commands.html（/voteshop は /vote shop の省略形なので除く）
            "hub", "sit", "lay", "crawl", "spin", "co i", "gchest",
            "stream on", "stream off", "rec on", "rec off",
            "vote", "votegui", "vote shop", "vote total", "vote next", "vote last", "vote top",
            "vote streak", "vote best", "vote today", "vote help",
            // vcmc-enderchest の README
            "enderchest",
            // resource-core の README（/resource は同じ 4 つの移動を選ぶ GUI なので除く）
            "main", "resource overworld", "resource nether", "resource end",
            // Bolt の Wiki とソース（/bolt modify の player は /bolt edit と同じ動作なので除く）
            "bolt lock private", "bolt lock display", "bolt lock deposit", "bolt lock withdrawal", "bolt lock public",
            "bolt unlock", "bolt info",
            "bolt edit add {player}", "bolt edit remove {player}",
            "bolt modify add normal group {group}", "bolt modify remove normal group {group}",
            "bolt modify add normal password {password}", "bolt modify remove normal password {password}",
            "bolt trust add player {player}", "bolt trust remove player {player}", "bolt trust",
            "bolt group create {group}", "bolt group add {group} {player}", "bolt group remove {group} {player}",
            "bolt group list {group}", "bolt group delete {group}",
            "bolt transfer {player}", "bolt requests list",
            "bolt requests accept {player}", "bolt requests deny {player}", "bolt requests cancel {player}",
            "bolt mode persist", "bolt mode nolock", "bolt mode nospam",
            "bolt password {password}", "bolt help", "bolt help {command}",
        )
        assertEquals(expected.sorted(), runButtons.map { it.second.template.raw }.sorted())
    }

    @Test
    fun `the list of outdated menus is valid and does not contain the current menu`() {
        // サーバーの menu.yml が以前の既定のままなら、起動時に新しい既定の内容へ置き換える。
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream("menu-outdated.txt"))
        val outdated = OutdatedLangFiles.parse(stream.use { String(it.readBytes(), Charsets.UTF_8) })
        assertEquals(setOf("menu.yml"), outdated.keys)
        val hashes = outdated.getValue("menu.yml")
        assertTrue(hashes.isNotEmpty())
        hashes.forEach { assertTrue(it.matches(Regex("[0-9a-f]{64}")), it) }
        val current = assertNotNull(javaClass.classLoader.getResourceAsStream("menu.yml")).use { it.readBytes() }
        assertFalse(OutdatedLangFiles.hash(current) in hashes)
    }

    @Test
    fun `admin commands are not exposed`() {
        val admin = runButtons.map { it.second.template.raw }
            .filter { it.startsWith("resource-core") || it.startsWith("rc ") || it.startsWith("hubblock") || it.startsWith("bolt admin") }
        assertEquals(emptyList(), admin)
    }

    @Test
    fun `velocity commands go through the client and everything else runs on the server`() {
        // /hub、/stream、/rec は Velocity のコマンドなので、サーバー側から実行しても届かない。
        val client = runButtons.filter { it.second.via == RunVia.CLIENT }.map { it.second.template.raw }
        assertEquals(listOf("hub", "rec off", "rec on", "stream off", "stream on"), client.sorted())
    }

    @Test
    fun `every screen can be reached from main and has a way out`() {
        val reached = mutableSetOf<String>()
        fun visit(id: String) {
            if (!reached.add(id)) return
            menu.screens.getValue(id).buttons.values
                .mapNotNull { (it.action as? ButtonAction.OpenScreen)?.screenId }
                .forEach(::visit)
        }
        visit(MenuDefinition.ROOT)
        assertEquals(menu.screens.keys, reached)
        menu.screens.values.filter { it.id != MenuDefinition.ROOT }.forEach { screen ->
            assertTrue(screen.buttons.values.any { it.action is ButtonAction.OpenScreen }, "${screen.id} に戻るボタンがありません")
        }
    }

    @Test
    fun `angle brackets in lore are shown literally`() {
        // <名前> は MiniMessage のタグではないので、そのまま表示される。
        val plain = PlainTextComponentSerializer.plainText()
        val lore = menu.screens.getValue("protection-access").buttons.getValue(11).lore
            .map { plain.serialize(MiniMessage.miniMessage().deserialize(it)) }
        assertTrue("/bolt edit add <名前>" in lore, lore.toString())
    }

    @Test
    fun `every lore line that names a command matches what the button runs`() {
        // 説明の /... と実行内容がずれていないこと。入力は <...> と {...} で表記が違うので前半だけを比べる。
        val plain = PlainTextComponentSerializer.plainText()
        runButtons.forEach { (button, action) ->
            val shown = button.lore.map { plain.serialize(MiniMessage.miniMessage().deserialize(it)) }
                .single { it.startsWith("/") }
                .removePrefix("/").substringBefore(" <")
            assertEquals(action.template.raw.substringBefore(" {"), shown, button.id)
        }
    }
}
