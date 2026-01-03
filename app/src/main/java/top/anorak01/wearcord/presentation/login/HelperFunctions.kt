package top.anorak01.wearcord.presentation.login

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import top.anorak01.wearcord.presentation.LoginReply
import top.anorak01.wearcord.presentation.MFAReply

suspend fun sendLogin(email: String, password: String): Pair<String?, Boolean> {
    // Implement your network call to send the message here
    // Return true if successful, false otherwise

    val json = """
        {
            "login": "$email",
            "password": "$password",
            "undelete": false
        }
    """.trimIndent()

    val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)

    val client = OkHttpClient()
    val url = "https://discord.com/api/v9/auth/login"
    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .headers(
            Headers.headersOf(
                "Content-Type",
                "application/json"
            )
        )

        .build()

    println("request built")
    client.newCall(request).execute().use { response ->
        if (response.code != 200) {
            println("Request failed with code ${response.code}")
            println(response.body.string())
            println(response.headers)
            return Pair(null, false)
        }

        println("Request succeeded")

        val body = response.body.string()

        if (body.isBlank()) return Pair(null, false)

        val json = Json { ignoreUnknownKeys = true }

        var parsedBody: LoginReply? = null
        try {
            parsedBody = json.decodeFromString<LoginReply>(body)
        } catch (e: Exception) {
            // the incoming body is probably 2fa stuff like authenticator, not gut, but do nothing rn
            // TODO: make 2fa flow
        }

        if (parsedBody == null) return Pair(null, false)

        println("Parsed body: $parsedBody")
        if (parsedBody.mfa) {
            return Pair(parsedBody.ticket, true)
        } else {
            return Pair(parsedBody.ticket, false)
        }
    }
}

suspend fun send2FA(ticket: String, code: String): String? {
    // Implement your network call to send the message here
    // Return true if successful, false otherwise

    val json = """
        {
            "code": "$code",
            "ticket": "$ticket",
            "login_source": null,
            "gift_code_sku_id": null
        }
    """.trimIndent()

    println(json)

    val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)

    val client = OkHttpClient()
    val url = "https://discord.com/api/v9/auth/mfa/totp"
    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .headers(
            Headers.headersOf(
                "Content-Type",
                "application/json",
                "Authorisation",
                "None"
            )
        )

        .build()

    println("request built")
    client.newCall(request).execute().use { response ->
        if (response.code != 200) {
            println("Request failed with code ${response.code}")
            println(response.body.string())
            println(response.headers)
            return null
        }

        println("Request succeeded")

        val body = response.body.string()

        if (body.isBlank()) return null

        val json = Json { ignoreUnknownKeys = true }

        var parsedBody: MFAReply? = null
        try {
            parsedBody = json.decodeFromString<MFAReply>(body)
        } catch (e: Exception) {
            // the incoming body is probably 2fa stuff like authenticator, not gut, but do nothing rn
            // TODO: make 2fa flow
        }

        if (parsedBody == null) return null

        println("Parsed body: $parsedBody")
        return parsedBody.token
    }
}

fun generateQrCodeBitmap(text: String, width: Int = 512, height: Int = 512): Bitmap? {
    if (text.isBlank()) return null
    return try {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            "https://discordapp.com/ra/" + text,
            BarcodeFormat.QR_CODE,
            width,
            height,
            null
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace() // Log the error
        null
    }
}
