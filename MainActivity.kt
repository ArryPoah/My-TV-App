package com.me.tvhome

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var heroImg: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var scroll: ScrollView
    private var firstCard: View? = null

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val screenH = resources.displayMetrics.heightPixels
        val heroH = (screenH * 0.58).toInt()

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#14284F"), Color.parseColor("#05070D"))
        )

        heroImg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.9f }
        root.addView(heroImg, FrameLayout.LayoutParams(-1, heroH))

        val fade = View(this)
        fade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00000000, Color.parseColor("#05070D"))
        )
        root.addView(fade, FrameLayout.LayoutParams(-1, heroH))

        heroTitle = TextView(this).apply {
            textSize = 36f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
        }
        root.addView(heroTitle, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = d(60); topMargin = (screenH * 0.40).toInt()
        })

        val clock = TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "h:mm a"
            textSize = 18f; setTextColor(Color.WHITE)
            setPadding(d(16), d(6), d(16), d(6))
            background = GradientDrawable().apply {
                cornerRadius = d(20).toFloat(); setColor(0x55000000)
            }
        }
        root.addView(clock, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.END; topMargin = d(28); rightMargin = d(48)
        })

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false; clipToPadding = false
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
        }
        scroll.addView(list)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = (screenH * 0.52).toInt()
        })
        setContentView(root)

        buildRows(list)
    }

    private fun buildRows(list: LinearLayout) {
        val pm = packageManager
        fun query(cat: String) = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(cat), 0
        ).sortedBy { it.loadLabel(pm).toString().lowercase() }

        val seen = HashSet<String>()
        val apps = (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .filter { it.activityInfo.packageName != packageName && seen.add(it.activityInfo.packageName) }

        apps.chunked(6).forEach { chunk ->
            val hs = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipChildren = false; clipToPadding = false
                setPadding(d(40), d(24), d(40), d(40))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; clipChildren = false
            }
            hs.addView(row)
            chunk.forEach { row.addView(makeTile(it)) }
            list.addView(hs)
        }
        firstCard?.requestFocus()
    }

    private fun makeTile(ri: ResolveInfo): View {
        val pm = packageManager
        val label = ri.loadLabel(pm).toString()
        val banner = ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
        val icon = ri.loadIcon(pm)

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(d(14), 0, d(14), 0)
            }
        }
        val card = FrameLayout(this).apply {
            isFocusable = true; isClickable = true
            elevation = d(4).toFloat()
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, d(16).toFloat())
                }
            }
            val hue = (Math.abs(ri.activityInfo.packageName.hashCode()) % 360).toFloat()
            setBackgroundColor(Color.HSVToColor(floatArrayOf(hue, 0.5f, 0.35f)))
        }
        if (banner != null) {
            card.addView(ImageView(this).apply {
                setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            card.addView(ImageView(this).apply { setImageDrawable(icon) },
                FrameLayout.LayoutParams(d(72), d(72), Gravity.CENTER))
        }
        wrap.addView(card, LinearLayout.LayoutParams(d(220), d(124)))

        val name = TextView(this).apply {
            text = label; textSize = 15f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; alpha = 0f; maxLines = 1
            setPadding(0, d(16), 0, 0)
        }
        wrap.addView(name, LinearLayout.LayoutParams(d(220), -2))

        card.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.18f else 1f
            v.animate().scaleX(s).scaleY(s).translationZ(if (focused) d(16).toFloat() else 0f)
                .setDuration(160).start()
            name.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            if (focused) showHero(label, banner, icon)
        }
        card.setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setComponent(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Can't open $label", Toast.LENGTH_SHORT).show()
            }
        }
        if (firstCard == null) firstCard = card
        return wrap
    }

    private fun showHero(label: String, banner: android.graphics.drawable.Drawable?, icon: android.graphics.drawable.Drawable) {
        heroTitle.text = label
        if (banner != null) {
            heroImg.scaleType = ImageView.ScaleType.CENTER_CROP
            heroImg.setImageDrawable(banner)
        } else {
            heroImg.scaleType = ImageView.ScaleType.CENTER_INSIDE
            heroImg.setImageDrawable(icon)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        scroll.smoothScrollTo(0, 0)
        firstCard?.requestFocus()
    }

    @Deprecated("Launcher ignores back")
    override fun onBackPressed() {}
}
