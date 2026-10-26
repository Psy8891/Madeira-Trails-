package pt.vereda.core.maps

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun of(file: File): String = file.inputStream().use { of(it) }
}
