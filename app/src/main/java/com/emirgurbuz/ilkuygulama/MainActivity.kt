package com.emirgurbuz.ilkuygulama

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.content.ContentValues
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import kotlin.math.*

data class GaugeTheme(
    val name:String="NEON SPORT", val bg:Int=Color.rgb(7,13,25),
    val accent:Int=Color.rgb(0,229,255), val second:Int=Color.rgb(255,64,129),
    val maxSpeed:Int=320, val maxRpm:Int=8000, val digital:Boolean=true
) {
    fun json()=JSONObject().put("name",name).put("bg",String.format("#%06X",0xFFFFFF and bg))
        .put("accent",String.format("#%06X",0xFFFFFF and accent))
        .put("second",String.format("#%06X",0xFFFFFF and second))
        .put("maxSpeed",maxSpeed).put("maxRpm",maxRpm).put("digital",digital)
    companion object {
        fun parse(o:JSONObject)=GaugeTheme(o.optString("name","AI DESIGN"),
            Color.parseColor(o.optString("bg","#070D19")),
            Color.parseColor(o.optString("accent","#00E5FF")),
            Color.parseColor(o.optString("second","#FF4081")),
            o.optInt("maxSpeed",320).coerceIn(180,400),
            o.optInt("maxRpm",8000).coerceIn(6000,10000),o.optBoolean("digital",true))
    }
}

class MainActivity:Activity() {
    private val pool=Executors.newSingleThreadExecutor()
    private lateinit var gauge:GaugeView
    private lateinit var prompt:EditText
    private lateinit var key:EditText
    private lateinit var status:TextView
    private lateinit var heading:TextView
    private var theme=GaugeTheme()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(5,10,20); window.navigationBarColor=Color.rgb(5,10,20)
        val scroll=ScrollView(this)
        val page=LinearLayout(this).apply { orientation=1; setPadding(d(18),d(18),d(18),d(28)); setBackgroundColor(Color.rgb(5,10,20)) }
        scroll.addView(page); setContentView(scroll)
        heading=TextView(this).apply { text="KADRAN AI"; textSize=29f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); letterSpacing=.1f }
        page.addView(heading)
        page.addView(label("Yapay zekâyla kendi dijital kokpitini tasarla.",14,Color.LTGRAY))
        val preview=panel()
        preview.addView(label("CANLI TASARIM ÖNİZLEMESİ",11,Color.LTGRAY))
        gauge=GaugeView()
        preview.addView(gauge,LinearLayout.LayoutParams(-1,d(300)))
        page.addView(preview)
        page.addView(space(14)); page.addView(section("1  •  HAZIR TEMALAR"))
        val row=LinearLayout(this).apply { orientation=0 }
        row.addView(chip("NEON") { setTheme(GaugeTheme()) })
        row.addView(chip("KIRMIZI") { setTheme(GaugeTheme("REDLINE",Color.rgb(18,5,10),Color.rgb(255,55,75),Color.rgb(255,190,60),360,9000)) })
        row.addView(chip("LÜKS") { setTheme(GaugeTheme("GOLD EDITION",Color.rgb(17,17,19),Color.rgb(224,190,115),Color.rgb(120,190,180),280,7000)) })
        row.addView(chip("BUZ MAVİ") { setTheme(GaugeTheme("ICE BLUE",Color.rgb(5,15,28),Color.rgb(100,190,255),Color.rgb(170,120,255),300,8000)) })
        page.addView(row)
        page.addView(space(16)); page.addView(section("2  •  AI TASARIM ATÖLYESİ"))
        page.addView(label("Nasıl bir kadran istiyorsun? Tarifini yaz:",13,Color.LTGRAY))
        prompt=EditText(this).apply {
            setText("Fütüristik yarış arabası kokpiti, turkuaz neon ışıklar, sportif ve okunaklı")
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); textSize=14f; minLines=3; gravity=Gravity.TOP
            setPadding(d(12),d(12),d(12),d(12)); setBackgroundColor(Color.rgb(19,29,45))
        }
        page.addView(prompt,LinearLayout.LayoutParams(-1,d(100)))
        page.addView(space(8))
        key=EditText(this).apply {
            hint="Gemini API anahtarını yapıştır"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            inputType=129; textSize=13f; setPadding(d(12),d(10),d(12),d(10)); setBackgroundColor(Color.rgb(19,29,45))
        }
        page.addView(key,LinearLayout.LayoutParams(-1,d(48)))
        page.addView(label("API anahtarı kaydedilmez; yalnızca AI isteğinde kullanılır.",11,Color.GRAY))
        val make=action("✦  AI İLE KADRAN TASARLA")
        page.addView(make); make.setOnClickListener { ai(make) }
        page.addView(space(10))
        val actions=LinearLayout(this).apply { orientation=0 }
        val save=action("TASARIMI KAYDET"); val png=action("PNG OLARAK AL")
        actions.addView(save,LinearLayout.LayoutParams(0,d(48),1f))
        actions.addView(png,LinearLayout.LayoutParams(0,d(48),1f).apply { leftMargin=d(8) })
        page.addView(actions)
        save.setOnClickListener {
            getSharedPreferences("cockpit",0).edit().putString("theme",theme.json().toString()).apply()
            status.text="Tasarım bu cihazda kaydedildi."
        }
        png.setOnClickListener { exportPng() }
        status=label("Hazır. Tema seç veya AI'ya tasarlat.",12,Color.rgb(0,229,180)); page.addView(status)
        page.addView(space(8))
        page.addView(label("Bu ekran tasarım ve önizleme içindir. Gerçek araç hızını ölçmez, araç ECU'suna bağlanmaz.",11,Color.GRAY))
        getSharedPreferences("cockpit",0).getString("theme",null)?.let { runCatching { setTheme(GaugeTheme.parse(JSONObject(it))) } }
    }

    private fun ai(button:Button) {
        val token=key.text.toString().trim(); val request=prompt.text.toString().trim()
        if(token.isEmpty()) { status.text="Gemini API anahtarını gir."; return }
        if(request.isEmpty()) { status.text="Önce tasarım tarifini yaz."; return }
        button.isEnabled=false; status.text="AI tasarım hazırlıyor…"
        pool.execute {
            try {
                val instruction="Create a dashboard gauge theme for: "+request+
                    ". Return ONLY JSON keys name, bg, accent, second, maxSpeed (180-400), maxRpm (6000-10000), digital. Colors must be hex like #00E5FF. High contrast, readable."
                val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text",instruction)))))
                val url=URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="+token)
                val con=url.openConnection() as HttpURLConnection
                con.requestMethod="POST"; con.connectTimeout=20000; con.readTimeout=30000; con.doOutput=true
                con.setRequestProperty("Content-Type","application/json")
                OutputStreamWriter(con.outputStream,Charsets.UTF_8).use { it.write(body.toString()) }
                val code=con.responseCode
                val stream=if(code in 200..299) con.inputStream else con.errorStream
                val raw=stream.bufferedReader().use { it.readText() }; con.disconnect()
                if(code !in 200..299) throw Exception("Gemini API "+code+": "+raw.take(180))
                val answer=JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                val json=answer.substring(answer.indexOf('{'),answer.lastIndexOf('}')+1)
                val generated=GaugeTheme.parse(JSONObject(json))
                runOnUiThread { setTheme(generated); status.text="AI tasarımı hazır: "+generated.name; button.isEnabled=true }
            } catch(e:Exception) {
                runOnUiThread { status.text="AI hatası: "+(e.message ?: "Bağlantı kurulamadı"); button.isEnabled=true }
            }
        }
    }

    private fun exportPng() {
        try {
            val b=Bitmap.createBitmap(gauge.width.coerceAtLeast(1),gauge.height.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            gauge.draw(Canvas(b))
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"KadranAI-"+System.currentTimeMillis()+".png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/KadranAI")
            }
            val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: throw Exception("Galeriye erişilemedi")
            contentResolver.openOutputStream(uri).use { b.compress(Bitmap.CompressFormat.PNG,100,it!!) }
            status.text="PNG galeriye kaydedildi: Pictures/KadranAI"
        } catch(e:Exception) { status.text="PNG hatası: "+e.message }
    }

    private fun setTheme(t:GaugeTheme) { theme=t; gauge.theme=t; gauge.invalidate(); heading.text="KADRAN AI  /  "+t.name }
    private fun d(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun label(s:String,size:Int,color:Int)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(color); setPadding(0,d(5),0,d(5)) }
    private fun section(s:String)=TextView(this).apply { text=s; textSize=15f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0,0,0,d(8)) }
    private fun space(h:Int)=View(this).apply { layoutParams=LinearLayout.LayoutParams(1,d(h)) }
    private fun panel()=LinearLayout(this).apply { orientation=1; setPadding(d(12),d(12),d(12),d(12)); setBackgroundColor(Color.rgb(13,22,37)) }
    private fun action(s:String)=Button(this).apply { text=s; textSize=12f; isAllCaps=false; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(20,105,125)) }
    private fun chip(s:String,click:()->Unit)=Button(this).apply {
        text=s; textSize=9f; isAllCaps=false; setTextColor(Color.CYAN); setBackgroundColor(Color.rgb(18,28,43))
        layoutParams=LinearLayout.LayoutParams(0,d(42),1f).apply { rightMargin=d(3) }; setOnClickListener { click() }
    }

    inner class GaugeView:View(this@MainActivity) {
        var theme=GaugeTheme()
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas) {
            super.onDraw(c); val w=width.toFloat(); val h=height.toFloat(); c.drawColor(theme.bg)
            val x=w/2; val y=h*.49f; val r=min(w*.39f,h*.39f)
            p.style=Paint.Style.STROKE; p.strokeWidth=d(13).toFloat(); p.strokeCap=Paint.Cap.ROUND
            p.color=Color.rgb(35,48,65); c.drawArc(x-r,y-r,x+r,y+r,135f,270f,false,p)
            p.color=theme.accent; p.strokeWidth=d(4).toFloat(); c.drawArc(x-r,y-r,x+r,y+r,135f,205f,false,p)
            p.color=theme.second; p.strokeWidth=d(2).toFloat(); c.drawArc(x-r*.78f,y-r*.78f,x+r*.78f,y+r*.78f,135f,270f,false,p)
            p.style=Paint.Style.FILL; p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.DEFAULT_BOLD
            for(i in 0..8) {
                val a=Math.toRadians(135.0+i*33.75); val cs=cos(a).toFloat(); val sn=sin(a).toFloat()
                p.color=if(i>6) theme.second else Color.WHITE; p.strokeWidth=d(2).toFloat()
                c.drawLine(x+cs*r*.82f,y+sn*r*.82f,x+cs*r*.94f,y+sn*r*.94f,p)
                p.textSize=d(10).toFloat(); c.drawText((i*theme.maxSpeed/8).toString(),x+cs*r*.68f,y+sn*r*.68f+d(4),p)
            }
            val a=Math.toRadians(135.0+205.0*137/theme.maxSpeed)
            p.color=theme.accent; p.strokeWidth=d(4).toFloat()
            c.drawLine(x,y,x+cos(a).toFloat()*r*.62f,y+sin(a).toFloat()*r*.62f,p)
            p.color=Color.WHITE; c.drawCircle(x,y,d(8).toFloat(),p)
            p.textSize=d(43).toFloat(); c.drawText("137",x,y+r*.43f,p)
            p.textSize=d(11).toFloat(); p.color=theme.accent; c.drawText("km/h",x,y+r*.43f+d(19),p)
            p.textSize=d(10).toFloat(); p.color=Color.LTGRAY; c.drawText("RPM  •  3.200",x,h-d(17).toFloat(),p)
            p.textAlign=Paint.Align.LEFT; p.textSize=d(10).toFloat(); p.color=theme.second
            c.drawText("FUEL",d(16).toFloat(),d(24).toFloat(),p); p.color=Color.WHITE; c.drawText("78%",d(16).toFloat(),d(39).toFloat(),p)
            p.textAlign=Paint.Align.RIGHT; p.color=theme.accent; c.drawText("TEMP",w-d(16).toFloat(),d(24).toFloat(),p)
            p.color=Color.WHITE; c.drawText("90°C",w-d(16).toFloat(),d(39).toFloat(),p)
            p.textAlign=Paint.Align.CENTER; p.textSize=d(9).toFloat(); p.color=Color.LTGRAY; c.drawText(theme.name,x,d(17).toFloat(),p)
        }
    }
}
