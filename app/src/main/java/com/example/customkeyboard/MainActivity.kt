package com.example.customkeyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    // ================== عناصر الواجهة ==================
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedTextView: TextView
    private lateinit var wordsPerLineEditText: EditText
    private lateinit var hugeTextInput: EditText
    private lateinit var extractButton: Button
    private lateinit var startTypingButton: Button
    private lateinit var stopTypingButton: Button
    private lateinit var toggleNeon: Switch
    private lateinit var themeSpinner: Spinner

    // ================== دورة حياة النشاط ==================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ربط العناصر بالـ XML
        speedSeekBar = findViewById(R.id.speedSeekBar)
        speedTextView = findViewById(R.id.speedTextView)
        wordsPerLineEditText = findViewById(R.id.wordsPerLineEditText)
        hugeTextInput = findViewById(R.id.hugeTextInput)
        extractButton = findViewById(R.id.extractButton)
        startTypingButton = findViewById(R.id.startTypingButton)
        stopTypingButton = findViewById(R.id.stopTypingButton)
        toggleNeon = findViewById(R.id.toggleNeon)
        themeSpinner = findViewById(R.id.themeSpinner)

        // ========== 1. إعداد شريط السرعة (SeekBar) ==========
        speedSeekBar.max = 145 // المدى: 5 إلى 150
        speedSeekBar.progress = 75 // القيمة الافتراضية = 80 ملي
        speedTextView.text = "80 ms"

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 5
                speedTextView.text = "$speed ms"
                // إرسال السرعة إلى الخدمة عبر Broadcast
                sendCommandToService("UPDATE_SPEED", speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ========== 2. عدد الكلمات لكل سطر (الافتراضي 6) ==========
        wordsPerLineEditText.setText("6")
        wordsPerLineEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val count = wordsPerLineEditText.text.toString().toIntOrNull() ?: 6
                val validCount = count.coerceAtLeast(1)
                wordsPerLineEditText.setText(validCount.toString())
                sendCommandToService("UPDATE_WORDS_PER_LINE", validCount)
            }
        }

        // ========== 3. زر استخراج الكلمات من النص الطويل ==========
        extractButton.setOnClickListener {
            val text = hugeTextInput.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, "⚠️ الرجاء إدخال نص طويل أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // تفكيك النص إلى كلمات فردية + تنقية + إزالة التكرارات نهائياً
            val words = text.split(Regex("[\\s\\n\\r]+"))
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .distinct() // إزالة التكرار (LinkedHashSet)

            if (words.isEmpty()) {
                Toast.makeText(this, "⚠️ لم يتم العثور على كلمات صالحة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // إرسال القائمة الفريدة إلى خدمة الكيبورد
            sendWordListToService(words)
            Toast.makeText(
                this,
                "✅ تم استخراج ${words.size} كلمة فريدة وإرسالها للكيبورد",
                Toast.LENGTH_LONG
            ).show()
        }

        // ========== 4. زر بدء التسطير (اختباري داخل الواجهة) ==========
        startTypingButton.setOnClickListener {
            sendCommandToService("START_TYPING", 0)
            Toast.makeText(this, "▶️ بدء التسطير التفاعلي", Toast.LENGTH_SHORT).show()
        }

        // ========== 5. زر إيقاف التسطير ==========
        stopTypingButton.setOnClickListener {
            sendCommandToService("STOP_TYPING", 0)
            Toast.makeText(this, "⏹️ إيقاف التسطير", Toast.LENGTH_SHORT).show()
        }

        // ========== 6. مفتاح تفعيل الزخرفة النيون (مؤقتاً للتجربة) ==========
        toggleNeon.setOnCheckedChangeListener { _, isChecked ->
            sendCommandToService("TOGGLE_NEON", if (isChecked) 1 else 0)
        }

        // ========== 7. تغيير الثيم (سيتم تطبيقه لاحقاً) ==========
        // نتركه للتوسع المستقبلي
    }

    // ================== دوال الإرسال للخدمة ==================

    /**
     * إرسال أوامر رقمية للخدمة (سرعة، عدد كلمات، تشغيل، إيقاف)
     */
    private fun sendCommandToService(action: String, value: Int) {
        val intent = Intent(action).apply {
            setPackage(packageName)
            putExtra("VALUE", value)
        }
        sendBroadcast(intent)
    }

    /**
     * إرسال قائمة الكلمات المستخرجة إلى الخدمة
     */
    private fun sendWordListToService(words: List<String>) {
        val intent = Intent("UPDATE_WORDS").apply {
            setPackage(packageName)
            putStringArrayListExtra("WORDS", ArrayList(words))
        }
        sendBroadcast(intent)
    }

    // ================== دالة مساعدة لفتح الكيبورد ==================
    // يمكن استدعاؤها لاحقاً من زر داخل الواجهة
    private fun openKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    // ================== تسجيل واستقبال الحالة من الخدمة (اختياري) ==================
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "STATUS_UPDATE" -> {
                    val status = intent.getStringExtra("STATUS") ?: ""
                    // يمكننا عرض الحالة في Toast أو تحديث واجهة
                    // Toast.makeText(this@MainActivity, status, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // تسجيل المستقبل لاستقبال ردود الخدمة
        LocalBroadcastManager.getInstance(this).registerReceiver(
            statusReceiver,
            IntentFilter("STATUS_UPDATE")
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }
}
