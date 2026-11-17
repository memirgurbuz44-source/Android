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
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val pool = Executors.newSingleThreadExecutor()
    private lateinit var prompt: EditText
    private lateinit var aiEngine: WebView
    private var aiReady = false
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
        page.addView(label("API anahtarı gerekmez. AI hizmeti için Puter hesabınla giriş yapman istenebilir.", 11, Color.GRAY))
        generateButton = button("✦  YAPAY ZEKÂ KODU OLUŞTURSUN")
        page.addView(generateButton)
        generateButton.setOnClickListener { generate() }
        setupAiEngine()
        aiEngine.visibility = View.INVISIBLE
        page.addView(aiEngine, LinearLayout.LayoutParams(1, 1))
        page.addView(gap(16))
        page.addView(label("CANLI ÖNİZLEME", 16, Color.WHITE, true))
        preview = WebView(this).apply {
            setBackgroundColor(Color.rgb(18, 23, 35))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadsImagesAutomatically = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

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
            setTextIsSelectable(true)
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
        val idea = prompt.text.toString().trim()
        if (idea.isEmpty()) { status.text = "Önce ne tasarlamak istediğini yaz."; return }
        if (!aiReady) { status.text = "Yapay zekâ bağlantısı hazırlanıyor. Birkaç saniye bekle."; return }
        generateButton.isEnabled = false
        status.text = "Puter AI kod yazıyor..."
        val instruction = """
Sen deneyimli bir web arayüzü geliştiricisisin. Kullanıcının isteğine göre güzel, modern ve çalışan bir arayüz tasarla.
Yalnızca TEK DOSYALIK, tam HTML belgesi döndür. CSS ve JavaScript HTML içinde olsun.
Markdown kod çiti veya açıklama yazma. Harici kütüphane ve internet bağlantısı kullanma.
Kısa, optimize edilmiş, mobil uyumlu ve çalışan kod üret; gereksiz efekt ve tekrarları kullanma.
İstek: $idea
        """.trimIndent()
        val encoded = org.json.JSONObject.quote(instruction)
        aiEngine.evaluateJavascript("window.generateCode($encoded);", null)
    }

    private fun setupAiEngine() {
        aiEngine = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(AiBridge(), "AndroidBridge")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // SDK readiness is reported by the script load callback.
                }
            }
        }
        val enginePage = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <script src="https://js.puter.com/v2/" onload="AndroidBridge.onReady()"></script></head><body>
            <script>
            window.generateCode = async function(prompt) {
              try {
                if (!window.puter || !puter.ai) throw new Error("Puter AI yüklenemedi. İnternet bağlantını kontrol et.");
                const stream = await puter.ai.chat(prompt, {model: "gpt-5.4-nano", stream: true, max_tokens: 4096, temperature: 0.35});
                let answer = "";
                for await (const part of stream) {
                  if (part?.type === "error") throw new Error(part.message || "AI yanıt üretirken hata oluştu.");
                  if (part?.type === "text" && typeof part.text === "string") { answer += part.text; AndroidBridge.onChunk(part.text); }
                }
                AndroidBridge.onResult(answer);
              } catch (error) {
                let detail = ""; try { detail = typeof error === "string" ? error : JSON.stringify(error); } catch (_) { detail = String(error); }
                AndroidBridge.onError(String(error?.message || detail || "Bilinmeyen hata"));
              }
            };
            </script></body></html>
        """.trimIndent()
        aiEngine.loadDataWithBaseURL("https://puter.com/", enginePage, "text/html", "UTF-8", null)
    }

    inner class AiBridge {
        @JavascriptInterface
        fun onReady() {
            runOnUiThread { aiReady = true; status.text = "AI hazır. Tasarımını yazabilirsin." }
        }

        @JavascriptInterface
        fun onChunk(part: String) {
            runOnUiThread { codeView.text = codeView.text.toString() + part }
        }
        @JavascriptInterface
        fun onResult(answer: String) {
            runOnUiThread {
                val cleaned = answer.replace(Regex("(?i)^\\s*```(?:html)?\\s*"), "").replace(Regex("\\s*```\\s*$"), "").trim()
                val html = if (cleaned.contains("<html", true) || cleaned.contains("<!doctype", true)) cleaned
                    else "<!doctype html><html lang=\\"tr\\"><head><meta charset=\\"utf-8\\"><meta name=\\"viewport\\" content=\\"width=device-width,initial-scale=1\\"></head><body>$cleaned</body></html>"
                if (cleaned.isBlank()) {
                    status.text = "AI boş yanıt verdi. Tekrar dene."
                } else {
                    showCode(html)
                    getPreferences(0).edit().putString("generated_html", html).apply()
                    status.text = "Kod hazır! Önizlemesini yukarıda görebilirsin."
                }
                generateButton.isEnabled = true
            }
        }

        @JavascriptInterface
        fun onError(message: String) {
            runOnUiThread {
                status.text = "AI hatası: " + message.take(220)
                generateButton.isEnabled = true
            }
        }
    }

    private fun showCode(html: String) {
        generatedCode = html
        codeView.text = html
        val csp = "<meta http-equiv='Content-Security-Policy' content=\"default-src https: data: blob: 'unsafe-inline'; img-src https: data: blob:; media-src https: data: blob:; style-src https: 'unsafe-inline'; script-src https: 'unsafe-inline'; font-src https: data:; connect-src https:; object-src 'none'; form-action 'none'; base-uri 'none'\">"
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
        aiEngine.destroy()
        pool.shutdown()
        super.onDestroy()
    }
}
