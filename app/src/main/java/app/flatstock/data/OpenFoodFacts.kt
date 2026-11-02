package app.flatstock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Free, open product database (food and many household products); suggests a name for a new barcode. */
object OpenFoodFacts {
    data class Info(val name: String, val brand: String)

    private const val FIELDS = "product_name,product_name_de,product_name_en,brands"
    // Open Food Facts covers food; its sister database covers cleaning products and toiletries.
    private val HOSTS = listOf("world.openfoodfacts.org", "world.openproductsfacts.org")

    suspend fun lookup(barcode: String): Info? = withContext(Dispatchers.IO) {
        if (barcode.isEmpty() || !barcode.all(Char::isDigit)) return@withContext null
        HOSTS.firstNotNullOfOrNull { host -> runCatching { fetch(host, barcode) }.getOrNull() }
    }

    private fun fetch(host: String, barcode: String): Info? {
        val conn = (URL("https://$host/api/v2/product/$barcode.json?fields=$FIELDS").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            // Open Food Facts asks apps to identify themselves.
            setRequestProperty("User-Agent", "FlatStock/0.2 (Android; household use)")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("status") != 1) return null
            val product = json.getJSONObject("product")
            val name = listOf("product_name", "product_name_de", "product_name_en")
                .map { product.optString(it).trim() }
                .firstOrNull { it.isNotEmpty() } ?: return null
            return Info(name, product.optString("brands").substringBefore(',').trim())
        } finally {
            conn.disconnect()
        }
    }
}
