package com.emirgurbuz.ilkuygulama

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val title = TextView(this).apply {
            text = "İlk Uygulamam"
            textSize = 28f
            setTextColor(Color.rgb(30, 41, 59))
            gravity = Gravity.CENTER
        }

        val button = Button(this).apply {
            text = "Bana Tıkla"
            textSize = 18f
        }

        val result = TextView(this).apply {
            text = ""
            textSize = 26f
            setTextColor(Color.rgb(22, 101, 52))
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 0)
        }

        button.setOnClickListener {
            result.text = "Emir Gürbüz"
        }

        layout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        layout.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 32
        })

        layout.addView(result, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(layout)
    }
}
