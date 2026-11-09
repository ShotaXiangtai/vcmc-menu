package jp.vcmc.menu.i18n

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat

/**
 * 以前のリリースの既定の内容のまま残っているファイルを見分ける。
 * lang などのファイルは一度コピーするとそちらが優先されるので、jar の内容を変えてもサーバーには反映されない。
 * 編集されていないファイルだけを脇へ移して、新しい内容をコピーし直せるようにする。
 */
object OutdatedLangFiles {
    /** 改行コードの違いを無視した SHA-256。jar を Windows で作るか Linux で作るかで改行コードが変わる。 */
    fun hash(bytes: ByteArray): String {
        val normalized = String(bytes, Charsets.UTF_8).replace("\r", "").toByteArray(Charsets.UTF_8)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized))
    }

    /** 「zh_hk.yml <SHA-256>」の行を読む。# より後ろと空行は無視する。 */
    fun parse(text: String): Map<String, Set<String>> =
        text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .mapNotNull { line ->
                val parts = line.split(' ', '\t').filter(String::isNotEmpty)
                if (parts.size == 2) parts[0] to parts[1].lowercase() else null
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

    /**
     * file が以前の既定の内容（outdated のどれか）と同じなら、名前の後ろに .old を付けて移し、移した先を返す。
     * .old がすでにあれば上書きする。前に置き換えたときの、以前の既定の内容なので失うものはない。
     */
    fun moveAside(file: File, outdated: Set<String>): File? {
        if (outdated.isEmpty() || !file.isFile) return null
        if (hash(file.readBytes()) !in outdated) return null
        val backup = File(file.parentFile, "${file.name}.old")
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw IOException("${file.name} を ${backup.name} へ移せませんでした: ${e.message}", e)
        }
        return backup
    }
}
