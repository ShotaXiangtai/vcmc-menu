package jp.vcmc.menu.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandTemplateTest {
    @Test
    fun `leading slash and spaces are removed`() {
        assertEquals("sit", CommandTemplate.of(" /sit ").raw)
    }

    @Test
    fun `placeholders are listed in order without duplicates`() {
        assertEquals(listOf("group", "player"), CommandTemplate.of("bolt group add {group} {player} {group}").placeholders)
    }

    @Test
    fun `values are embedded`() {
        val line = CommandTemplate.of("bolt group add {group} {player}").render(mapOf("group" to "friends", "player" to "Steve"))
        assertEquals("bolt group add friends Steve", line)
    }

    @Test
    fun `regex replacement syntax in a value is kept literally`() {
        assertEquals("bolt password a\$1{b}", CommandTemplate.of("bolt password {password}").render(mapOf("password" to "a\$1{b}")))
    }

    @Test
    fun `a value that looks like another placeholder is not expanded again`() {
        val line = CommandTemplate.of("x {a} {b}").render(mapOf("a" to "{b}", "b" to "B"))
        assertEquals("x {b} B", line)
    }
}

class SanitizeArgumentTest {
    @Test
    fun `surrounding spaces are trimmed`() {
        assertEquals("Steve", sanitizeArgument("  Steve ", 16))
    }

    @Test
    fun `japanese text is allowed`() {
        assertEquals("友達", sanitizeArgument("友達", 32))
    }

    @Test
    fun `blank and missing values are rejected`() {
        assertNull(sanitizeArgument(null, 16))
        assertNull(sanitizeArgument("   ", 16))
    }

    @Test
    fun `length is enforced on the server side`() {
        assertEquals("a".repeat(16), sanitizeArgument("a".repeat(16), 16))
        assertNull(sanitizeArgument("a".repeat(17), 16))
    }

    @Test
    fun `values that would shift arguments are rejected`() {
        assertNull(sanitizeArgument("foo bar", 32))
        assertNull(sanitizeArgument("foo　bar", 32))
        assertNull(sanitizeArgument("foo\tbar", 32))
        assertNull(sanitizeArgument("foo\nbar", 32))
    }

    @Test
    fun `characters that cannot be typed in chat are rejected`() {
        assertNull(sanitizeArgument("§cred", 32))
        assertNull(sanitizeArgument("a\u0007b", 32))
    }
}

class PagingTest {
    private val names = (1..100).map { "p$it" }

    @Test
    fun `an empty list still has one page`() {
        val page = paginate(emptyList<String>(), 0, 45)
        assertEquals(Page(emptyList(), 0, 1), page)
        assertEquals(false, page.hasNext)
        assertEquals(false, page.hasPrevious)
    }

    @Test
    fun `an exact multiple does not create an empty last page`() {
        assertEquals(1, paginate(names.take(45), 0, 45).count)
        assertEquals(2, paginate(names.take(46), 0, 45).count)
    }

    @Test
    fun `pages are cut in order`() {
        val page = paginate(names, 1, 45)
        assertEquals("p46", page.items.first())
        assertEquals(45, page.items.size)
        assertEquals(true, page.hasPrevious)
        assertEquals(true, page.hasNext)
    }

    @Test
    fun `out of range pages are clamped`() {
        assertEquals(2, paginate(names, 99, 45).index)
        assertEquals(0, paginate(names, -1, 45).index)
    }
}
