package com.emirgurbuz.ilkuygulama

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val pool = Executors.newSingleThreadExecutor()
    private lateinit var prompt: EditText
    private lateinit var apiKey: EditText
    private lateinit var preview: WebView
    private lateinit var codeView: TextView
    private lateinit var status: TextView
    private lateinit var generateButton: Button
    private var generatedCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 12, 22)
        window.navigationBarColor = Color.rgb(9, 12, 22)

        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            setBackgroundColor(Color.rgb(9, 12, 22))
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(label("AI STÜDYO", 28, Color.WHITE, true))
        page.addView(label("Ne istediğini yaz. Yapay zekâ kodunu oluştursun, önizlemesini burada gösterelim.", 14, Color.LTGRAY))
        page.addView(gap(14))
        page.addView(label("NE TASARLAYALIM?", 13, Color.rgb(100, 220, 255), true))
        prompt = EditText(this).apply {
            hint = "Örn: Siyah arka planlı, neon mavi ışıklı modern bir müzik çalar tasarla..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            minLines = 4
            gravity = Gravity.TOP
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(24, 31, 48))
        }
        page.addView(prompt, LinearLayout.LayoutParams(-1, dp(120)))
        page.addView(gap(10))
        apiKey = EditText(this).apply {
            hint = "Gemini API anahtarın"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = 129
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(24, 31, 48))
        }
        page.addView(apiKey, LinearLayout.LayoutParams(-1, dp(50)))
        page.addView(label("Anahtar cihazda saklanmaz. GitHub'a veya başkasına gönderme.", 11, Color.GRAY))
        generateButton = button("✦  YAPAY ZEKÂ KODU OLUŞTURSUN")
        page.addView(generateButton)
        generateButton.setOnClickListener { generate() }
        page.addView(gap(16))
        page.addView(label("CANLI ÖNİZLEME", 16, Color.WHITE, true))
        preview = WebView(this).apply {
            setBackgroundColor(Color.rgb(18, 23, 35))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadsImagesAutomatically = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val u = request?.url?.scheme ?: return null
                    return if (u == "data" || u == "about") null
                    else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                }
            }
        }
        page.addView(preview, LinearLayout.LayoutParams(-1, dp(360)))
        page.addView(gap(16))
        page.addView(label("OLUŞTURULAN HTML / CSS / JS KODU", 16, Color.WHITE, true))
        codeView = TextView(this).apply {
            text = "Henüz kod oluşturulmadı."
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(190, 230, 255))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(15, 20, 32))
            textIsSelectable = true
        }
        page.addView(codeView, LinearLayout.LayoutParams(-1, dp(260)))
        val copy = button("KODU KOPYALA")
        page.addView(copy)
        copy.setOnClickListener {
            if (generatedCode.isNotBlank()) {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("AI kodu", generatedCode))
                status.text = "Kod panoya kopyalandı."
            }
        }
        status = label("Hazır. Bir tasarım tarif et ve AI'ya gönder.", 12, Color.rgb(80, 230, 180))
        page.addView(status)

        val saved = getPreferences(0).getString("generated_html", null)
        if (!saved.isNullOrBlank()) showCode(saved)
    }

    private fun generate() {
        val key = apiKey.text.toString().trim()
        val idea = prompt.text.toString().trim()
        if (key.isEmpty()) { status.text = "Gemini API anahtarını gir."; return }
        if (idea.isEmpty()) { status.text = "Önce ne tasarlamak istediğini yaz."; return }
        generateButton.isEnabled = false
        status.text = "Yapay zekâ kod yazıyor..."
        pool.execute {
            try {
                val instruction = """
Sen deneyimli bir web arayüzü geliştiricisisin. Kullanıcının isteğine göre güzel, modern ve çalışan bir arayüz tasarla.
Yalnızca TEK DOSYALIK, tam HTML belgesi döndür. CSS style etiketi içinde, JavaScript script etiketi içinde olsun.
Markdown kod çiti, açıklama veya başka metin yazma. Harici kütüphane, internet görseli, dış bağlantı veya sunucu kullanma.
Tüm tasarım kendi içinde çalışsın; mobil ekrana uyumlu, görsel açıdan zengin ve etkileşimli olsun.
İstek: $idea
                """.trimIndent()
                val body = JSONObject().put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction)))
                )).put("generationConfig", JSONObject().put("temperature", 0.8).put("maxOutputTokens", 8192))
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + key)
                val con = url.openConnection() as HttpURLConnection
                con.requestMethod = "POST"
                con.connectTimeout = 20000
                con.readTimeout = 60000
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/json")
                OutputStreamWriter(con.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                val responseCode = con.responseCode
                val stream = if (responseCode in 200..299) con.inputStream else con.errorStream
                val raw = stream.bufferedReader().use { it.readText() }
                con.disconnect()
                if (responseCode !in 200..299) throw Exception("Gemini API $responseCode: " + raw.take(250))
                val answer = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                val html = answer.replace("```html", "").replace("```", "").trim()
                if (!html.contains("<html", true) && !html.contains("<!doctype", true)) {
                    throw Exception("AI geçerli HTML kodu döndürmedi. Tekrar dene.")
                }
                runOnUiThread {
                    showCode(html)
                    getPreferences(0).edit().putString("generated_html", html).apply()
                    status.text = "Kod hazır! Önizlemesini yukarıda görebilirsin."
                    generateButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Hata: " + (e.message ?: "Bağlantı kurulamadı")
                    generateButton.isEnabled = true
                }
            }
        }
    }

    private fun showCode(html: String) {
        generatedCode = html
        codeView.text = html
        val csp = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:; connect-src 'none'; form-action 'none'; base-uri 'none'\">"
        val safeHtml = if (html.contains("<head", true)) {
            html.replaceFirst(Regex("(?i)<head[^>]*>"), "$0$csp")
        } else "<html><head>$csp</head><body>$html</body></html>"
        preview.loadDataWithBaseURL("https://local.invalid/", safeHtml, "text/html", "UTF-8", null)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun label(s: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun button(s: String) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(20, 112, 145))
    }

    override fun onDestroy() {
        preview.destroy()
        pool.shutdown()
        super.onDestroy()
    }
}
