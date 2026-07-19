package com.tuapp.macro

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.*

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: View
    private lateinit var settingsButton: View
    private lateinit var settingsPanel: View
    private lateinit var sharedPrefs: SharedPreferences

    private var macroActive = false
    private var tapSpeed = 50L
    private var buttonSize = 80
    private var buttonColor = Color.argb(200, 0, 255, 255)
    private var buttonAlpha = 200
    private var borderWidth = 4

    private var isDragging = false
    private var isSettingsOpen = false
    private var isTapping = false
    private val handler = Handler(Looper.getMainLooper())
    private var tapRunnable: Runnable? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sharedPrefs = getSharedPreferences("macro_prefs", Context.MODE_PRIVATE)
        loadPreferences()
        createFloatingButton()
        createSettingsButton()
        createSettingsPanel()
    }

    private fun loadPreferences() {
        macroActive = sharedPrefs.getBoolean("macro_active", false)
        tapSpeed = sharedPrefs.getLong("tap_speed", 50L)
        buttonSize = sharedPrefs.getInt("button_size", 80)
        buttonColor = sharedPrefs.getInt("button_color", Color.argb(200, 0, 255, 255))
        buttonAlpha = sharedPrefs.getInt("button_alpha", 200)
        borderWidth = sharedPrefs.getInt("border_width", 4)
    }

    // ---------- BOTÓN FLOTANTE PRINCIPAL ----------
    private fun createFloatingButton() {
        val params = WindowManager.LayoutParams(
            dpToPx(buttonSize), dpToPx(buttonSize),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            x = (dm.widthPixels - dpToPx(buttonSize)) / 2
            y = (dm.heightPixels - dpToPx(buttonSize)) / 2
        }

        floatingButton = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val radius = width / 2f - dpToPx(borderWidth) / 2f
                paint.color = buttonColor
                paint.alpha = buttonAlpha
                canvas.drawCircle(width / 2f, height / 2f, radius, paint)
                borderPaint.strokeWidth = dpToPx(borderWidth).toFloat()
                canvas.drawCircle(width / 2f, height / 2f, radius, borderPaint)
                // destello neón
                paint.color = Color.argb(50, 255, 255, 255)
                canvas.drawCircle(width / 2f, height / 2f, radius * 0.8f, paint)
            }
        }
        floatingButton.layoutParams = ViewGroup.LayoutParams(dpToPx(buttonSize), dpToPx(buttonSize))

        floatingButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!macroActive) {
                        isDragging = true
                        val dx = event.rawX - lastTouchX
                        val dy = event.rawY - lastTouchY
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        windowManager.updateViewLayout(floatingButton, params)
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!macroActive && !isDragging) {
                        // no hacer nada (se mueve)
                    } else if (macroActive && !isDragging) {
                        toggleTapping()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingButton, params)
    }

    // ---------- BOTÓN DE AJUSTES (ENGRANAJE) ----------
    private fun createSettingsButton() {
        val params = WindowManager.LayoutParams(
            dpToPx(40), dpToPx(40),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dpToPx(10)
            y = dpToPx(50)
        }

        settingsButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_ATOP)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { toggleSettingsPanel() }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastTouchX
                        val dy = event.rawY - lastTouchY
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        windowManager.updateViewLayout(settingsButton, params)
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        true
                    }
                    else -> false
                }
            }
        }
        windowManager.addView(settingsButton, params)
    }

    // ---------- PANEL DE CONFIGURACIÓN ----------
    private fun createSettingsPanel() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        settingsPanel = inflater.inflate(R.layout.settings_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        settingsPanel.visibility = View.GONE
        windowManager.addView(settingsPanel, params)

        setupSettingsControls()
    }

    private fun setupSettingsControls() {
        val switchMacro = settingsPanel.findViewById<Switch>(R.id.switch_macro)
        val seekSpeed = settingsPanel.findViewById<SeekBar>(R.id.seekbar_speed)
        val seekSize = settingsPanel.findViewById<SeekBar>(R.id.seekbar_size)
        val seekAlpha = settingsPanel.findViewById<SeekBar>(R.id.seekbar_alpha)
        val seekBorder = settingsPanel.findViewById<SeekBar>(R.id.seekbar_border)
        val btnColor = settingsPanel.findViewById<Button>(R.id.btn_color)
        val btnClose = settingsPanel.findViewById<ImageView>(R.id.btn_close)
        val tvSpeed = settingsPanel.findViewById<TextView>(R.id.tv_speed_value)
        val tvSize = settingsPanel.findViewById<TextView>(R.id.tv_size_value)
        val tvAlpha = settingsPanel.findViewById<TextView>(R.id.tv_alpha_value)
        val tvBorder = settingsPanel.findViewById<TextView>(R.id.tv_border_value)

        switchMacro.isChecked = macroActive
        seekSpeed.progress = (tapSpeed / 10).toInt()
        seekSize.progress = buttonSize - 40
        seekAlpha.progress = buttonAlpha - 50
        seekBorder.progress = borderWidth - 1
        tvSpeed.text = "$tapSpeed ms"
        tvSize.text = "${buttonSize}dp"
        tvAlpha.text = "$buttonAlpha"
        tvBorder.text = "${borderWidth}dp"

        switchMacro.setOnCheckedChangeListener { _, checked ->
            macroActive = checked
            sharedPrefs.edit().putBoolean("macro_active", checked).apply()
            Toast.makeText(this, if (checked) "Macro Activada" else "Macro Desactivada", Toast.LENGTH_SHORT).show()
        }

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, p: Int, from: Boolean) {
                tapSpeed = (p * 10).coerceAtLeast(10).toLong()
                tvSpeed.text = "$tapSpeed ms"
                sharedPrefs.edit().putLong("tap_speed", tapSpeed).apply()
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, p: Int, from: Boolean) {
                buttonSize = 40 + p
                tvSize.text = "${buttonSize}dp"
                sharedPrefs.edit().putInt("button_size", buttonSize).apply()
                updateButtonSize()
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        seekAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, p: Int, from: Boolean) {
                buttonAlpha = 50 + p
                tvAlpha.text = "$buttonAlpha"
                sharedPrefs.edit().putInt("button_alpha", buttonAlpha).apply()
                floatingButton.invalidate()
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        seekBorder.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, p: Int, from: Boolean) {
                borderWidth = 1 + p
                tvBorder.text = "${borderWidth}dp"
                sharedPrefs.edit().putInt("border_width", borderWidth).apply()
                floatingButton.invalidate()
            }
            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {}
        })

        btnColor.setOnClickListener { showColorPicker() }
        btnClose.setOnClickListener { closeSettingsPanel() }
    }

    private fun showColorPicker() {
        val colors = arrayOf("#00FFFF", "#FF00FF", "#FF0000", "#00FF00", "#FFFF00", "#FFFFFF")
        AlertDialog.Builder(this)
            .setTitle("Selecciona Color")
            .setItems(colors.map { "●" }.toTypedArray()) { _, which ->
                buttonColor = Color.parseColor(colors[which])
                sharedPrefs.edit().putInt("button_color", buttonColor).apply()
                floatingButton.invalidate()
            }
            .show()
    }

    private fun updateButtonSize() {
        val params = floatingButton.layoutParams as WindowManager.LayoutParams
        params.width = dpToPx(buttonSize)
        params.height = dpToPx(buttonSize)
        windowManager.updateViewLayout(floatingButton, params)
        floatingButton.invalidate()
    }

    private fun toggleSettingsPanel() {
        if (settingsPanel.visibility == View.VISIBLE) closeSettingsPanel()
        else openSettingsPanel()
    }

    private fun openSettingsPanel() {
        settingsPanel.visibility = View.VISIBLE
        isSettingsOpen = true
    }

    private fun closeSettingsPanel() {
        settingsPanel.visibility = View.GONE
        isSettingsOpen = false
    }

    // ---------- AUTO-TAP ----------
    private fun toggleTapping() {
        if (isTapping) stopTapping() else startTapping()
    }

    private fun startTapping() {
        if (!macroActive) return
        isTapping = true
        Toast.makeText(this, "Auto‑tap iniciado", Toast.LENGTH_SHORT).show()
        val location = IntArray(2)
        floatingButton.getLocationOnScreen(location)
        val x = location[0] + floatingButton.width / 2f
        val y = location[1] + floatingButton.height / 2f

        tapRunnable = object : Runnable {
            override fun run() {
                if (isTapping && macroActive) {
                    TapAccessibilityService.performTap(x, y)
                    handler.postDelayed(this, tapSpeed)
                }
            }
        }
        handler.post(tapRunnable!!)
    }

    private fun stopTapping() {
        isTapping = false
        handler.removeCallbacks(tapRunnable!!)
        Toast.makeText(this, "Auto‑tap detenido", Toast.LENGTH_SHORT).show()
    }

    // ---------- UTILIDADES ----------
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        stopTapping()
        windowManager.removeView(floatingButton)
        windowManager.removeView(settingsButton)
        windowManager.removeView(settingsPanel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}