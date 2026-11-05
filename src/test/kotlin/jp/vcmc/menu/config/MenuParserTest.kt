package jp.vcmc.menu.config

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuParserTest {
    private fun parse(yaml: String): ParsedMenu =
        MenuParser { Material.matchMaterial(it) }.parse(YamlConfiguration().apply { loadFromString(yaml) })

    private fun ParsedMenu.main() = assertNotNull(definition).root

    private fun ParsedMenu.hasIssueAt(path: String) = issues.any { it.path == path }

    @Test
    fun `a minimal menu parses without issues`() {
        val parsed = parse(
            """
            screens:
              main:
                title: hello
                rows: 2
                buttons:
                  sit:
                    slot: 17
                    icon: oak_stairs
                    name: seat
                    lore: [a, b]
                    command: /sit
            """.trimIndent(),
        )
        assertEquals(emptyList(), parsed.issues)
        val screen = parsed.main()
        assertEquals("hello", screen.title)
        assertEquals(2, screen.rows)
        val button = assertNotNull(screen.buttons[17])
        assertEquals(Material.OAK_STAIRS, button.icon)
        assertEquals(listOf("a", "b"), button.lore)
        val action = assertIs<ButtonAction.RunCommand>(button.action)
        assertEquals("sit", action.template.raw)
        assertEquals(RunVia.SERVER, action.via)
    }

    @Test
    fun `missing screens makes the menu unusable`() {
        val parsed = parse("other: 1")
        assertNull(parsed.definition)
        assertTrue(parsed.hasIssueAt("screens"))
    }

    @Test
    fun `a menu without the main screen is unusable`() {
        val parsed = parse("screens:\n  other:\n    rows: 1\n")
        assertNull(parsed.definition)
        assertTrue(parsed.hasIssueAt("screens"))
    }

    @Test
    fun `invalid rows fall back to the default`() {
        val parsed = parse("screens:\n  main:\n    rows: 7\n")
        assertEquals(MenuParser.DEFAULT_ROWS, parsed.main().rows)
        assertTrue(parsed.hasIssueAt("screens.main.rows"))
    }

    @Test
    fun `an unknown icon falls back and keeps the button`() {
        val parsed = parse(
            "screens:\n  main:\n    buttons:\n      a:\n        slot: 0\n        icon: nope\n        close: true\n",
        )
        assertEquals(MenuParser.FALLBACK_ICON, parsed.main().buttons[0]?.icon)
        assertTrue(parsed.hasIssueAt("screens.main.buttons.a.icon"))
    }

    @Test
    fun `a slot outside the screen drops only that button`() {
        val parsed = parse(
            """
            screens:
              main:
                rows: 1
                buttons:
                  outside:
                    slot: 9
                    icon: stone
                    close: true
                  inside:
                    slot: 8
                    icon: stone
                    close: true
            """.trimIndent(),
        )
        assertEquals(setOf(8), parsed.main().buttons.keys)
        assertTrue(parsed.hasIssueAt("screens.main.buttons.outside.slot"))
    }

    @Test
    fun `a missing slot is reported`() {
        val parsed = parse("screens:\n  main:\n    buttons:\n      a:\n        icon: stone\n        close: true\n")
        assertTrue(parsed.main().buttons.isEmpty())
        assertTrue(parsed.hasIssueAt("screens.main.buttons.a.slot"))
    }

    @Test
    fun `duplicate slots keep the first button`() {
        val parsed = parse(
            """
            screens:
              main:
                buttons:
                  first:
                    slot: 3
                    icon: stone
                    close: true
                  second:
                    slot: 3
                    icon: stone
                    close: true
            """.trimIndent(),
        )
        assertEquals("first", parsed.main().buttons[3]?.id)
        assertTrue(parsed.hasIssueAt("screens.main.buttons.second.slot"))
    }

    @Test
    fun `a button needs exactly one action`() {
        val none = parse("screens:\n  main:\n    buttons:\n      a:\n        slot: 0\n        icon: stone\n")
        assertTrue(none.main().buttons.isEmpty())
        assertTrue(none.hasIssueAt("screens.main.buttons.a"))
        val both = parse(
            "screens:\n  main:\n    buttons:\n      a:\n        slot: 0\n        icon: stone\n        close: true\n        command: sit\n",
        )
        assertTrue(both.main().buttons.isEmpty())
        assertTrue(both.hasIssueAt("screens.main.buttons.a"))
    }

    @Test
    fun `open must point to an existing screen, even one defined later`() {
        val parsed = parse(
            """
            screens:
              main:
                buttons:
                  good:
                    slot: 0
                    icon: stone
                    open: later
                  bad:
                    slot: 1
                    icon: stone
                    open: nowhere
              later:
                rows: 1
            """.trimIndent(),
        )
        assertEquals(ButtonAction.OpenScreen("later"), parsed.main().buttons[0]?.action)
        assertNull(parsed.main().buttons[1])
        assertTrue(parsed.hasIssueAt("screens.main.buttons.bad.open"))
    }

    @Test
    fun `placeholders and inputs must match`() {
        val unknownPlaceholder = parse(
            "screens:\n  main:\n    buttons:\n      a:\n        slot: 0\n        icon: stone\n        command: 'bolt edit add {player}'\n",
        )
        assertTrue(unknownPlaceholder.main().buttons.isEmpty())
        assertTrue(unknownPlaceholder.hasIssueAt("screens.main.buttons.a.inputs"))

        val unusedInput = parse(
            """
            screens:
              main:
                buttons:
                  a:
                    slot: 0
                    icon: stone
                    command: sit
                    inputs:
                      player:
                        type: player
            """.trimIndent(),
        )
        assertTrue(unusedInput.main().buttons.isEmpty())
        assertTrue(unusedInput.hasIssueAt("screens.main.buttons.a.inputs"))
    }

    @Test
    fun `inputs keep their written order and defaults`() {
        val parsed = parse(
            """
            screens:
              main:
                buttons:
                  a:
                    slot: 0
                    icon: stone
                    command: 'bolt group add {group} {player}'
                    inputs:
                      group:
                        label: g
                      player:
                        type: player
                        label: p
            """.trimIndent(),
        )
        assertEquals(emptyList(), parsed.issues)
        val action = assertIs<ButtonAction.RunCommand>(parsed.main().buttons[0]?.action)
        assertEquals(
            listOf(
                InputSpec("group", InputType.TEXT, "g", MenuParser.DEFAULT_TEXT_LENGTH),
                InputSpec("player", InputType.PLAYER, "p", MenuParser.PLAYER_NAME_LENGTH),
            ),
            action.inputs,
        )
    }

    @Test
    fun `invalid input settings fall back to defaults`() {
        val parsed = parse(
            """
            screens:
              main:
                buttons:
                  a:
                    slot: 0
                    icon: stone
                    command: 'x {v}'
                    inputs:
                      v:
                        type: color
                        max-length: 999
            """.trimIndent(),
        )
        val action = assertIs<ButtonAction.RunCommand>(parsed.main().buttons[0]?.action)
        assertEquals(InputSpec("v", InputType.TEXT, "v", MenuParser.DEFAULT_TEXT_LENGTH), action.inputs.single())
        assertTrue(parsed.hasIssueAt("screens.main.buttons.a.inputs.v.type"))
        assertTrue(parsed.hasIssueAt("screens.main.buttons.a.inputs.v.max-length"))
    }

    @Test
    fun `via client is parsed and an unknown value falls back to server`() {
        val client = parse(
            "screens:\n  main:\n    buttons:\n      a:\n        slot: 0\n        icon: stone\n        command: hub\n        via: client\n",
        )
        assertEquals(RunVia.CLIENT, assertIs<ButtonAction.RunCommand>(client.main().buttons[0]?.action).via)
        val unknown = parse(
            "screens:\n  main:\n    buttons:\n      a:\n        slot: 0\n        icon: stone\n        command: hub\n        via: proxy\n",
        )
        assertEquals(RunVia.SERVER, assertIs<ButtonAction.RunCommand>(unknown.main().buttons[0]?.action).via)
        assertTrue(unknown.hasIssueAt("screens.main.buttons.a.via"))
    }
}
