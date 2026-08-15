package com.example.customkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.*

class MyInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private lateinit var controlPanelView: View

    private var wordList: MutableList<String> = mutableListOf()
    private var shuffledWords: MutableList<String> = mutableListOf()
    private var currentIndex: Int = 0

    private var typingSpeedMs: Long = 80L
    private var wordsPerLine: Int = 6
    private var isTypingActive: Boolean = false
    private var typingJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isControlPanelVisible: Boolean = false

    override fun onCreate() {
        super.onCreate()
        keyboard = Keyboard(this, R.xml.keyboard_layout_arabic)
        controlPanelView = layoutInflater.inflate(R.layout.control_panel_overlay, null)
        setupControlPanelButtons()
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    private fun setupControlPanelButtons() {
        val btnStart = controlPanelView.findViewById<Button>(R.id.btn_start_typing)
        val btnStop = controlPanelView.findViewById<Button>(R.id.btn_stop_typing)
        val btnSwitch = controlPanelView.findViewById<Button>(R.id.btn_switch_view)

        btnStart.setOnClickListener {
            startTyping()
            Toast.makeText(applicationContext, "▶ بدأ التسطير", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            stopTyping()
            Toast.makeText(applicationContext, "⏹ توقف التسطير", Toast.LENGTH_SHORT).show()
        }

        btnSwitch.setOnClickListener {
            switchToKeyboard()
        }
    }

    private fun switchToControlPanel() {
        isControlPanelVisible = true
        setInputView(controlPanelView)
    }

    private fun switchToKeyboard() {
        isControlPanelVisible = false
        setInputView(keyboardView)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (isControlPanelVisible) {
            switchToKeyboard()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTyping()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
            Keyboard.KEYCODE_DONE, Keyboard.KEYCODE_ENTER -> {
                // استخدم الأرقام الثابتة: ACTION_DOWN=0, ACTION_UP=1, KEYCODE_ENTER=10
                currentInputConnection?.sendKeyEvent(KeyEvent(0, 10))
                currentInputConnection?.sendKeyEvent(KeyEvent(1, 10))
            }
            -3 -> {
                switchToControlPanel()
            }
            else -> {
                val char = primaryCode.toChar()
                currentInputConnection?.commitText(char.toString(), 1)
            }
        }
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeDown() {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeUp() {}

    fun updateWordList(newWords: List<String>) {
        wordList = newWords.toMutableList()
        resetTypingState()
        mainHandler.post {
            Toast.makeText(applicationContext, "✅ تم استيراد ${wordList.size} كلمة", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateSpeed(speedMs: Long) {
        typingSpeedMs = speedMs.coerceIn(5, 150)
        mainHandler.post {
            Toast.makeText(applicationContext, "⚡ السرعة: $typingSpeedMs ملي", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateWordsPerLine(count: Int) {
        wordsPerLine = count.coerceAtLeast(1)
    }

    fun startTyping() {
        if (wordList.isEmpty()) {
            mainHandler.post {
                Toast.makeText(applicationContext, "⚠️ الرجاء إدخال نصوص وحفظها أولاً", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (isTypingActive) {
            stopTyping()
        }

        shuffledWords = wordList.shuffled().toMutableList()
        currentIndex = 0
        isTypingActive = true

        typingJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                while (isTypingActive && currentIndex < shuffledWords.size) {
                    val word = shuffledWords[currentIndex]

                    for (char in word) {
                        if (!isTypingActive) break
                        currentInputConnection?.commitText(char.toString(), 1)
                        delay(typingSpeedMs + (5..20).random().toLong())
                    }

                    if (!isTypingActive) break

                    currentInputConnection?.commitText(" ", 1)
                    delay(typingSpeedMs / 2)

                    if ((currentIndex + 1) % wordsPerLine == 0) {
                        // استخدم الأرقام الثابتة لإرسال Enter
                        currentInputConnection?.sendKeyEvent(KeyEvent(0, 10))
                        currentInputConnection?.sendKeyEvent(KeyEvent(1, 10))
                        delay(typingSpeedMs)
                    }

                    currentIndex++

                    if (currentIndex >= shuffledWords.size && isTypingActive) {
                        shuffledWords = wordList.shuffled().toMutableList()
                        currentIndex = 0
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isTypingActive = false
            }
        }
    }

    fun stopTyping() {
        isTypingActive = false
        typingJob?.cancel()
        typingJob = null
    }

    private fun resetTypingState() {
        stopTyping()
        shuffledWords.clear()
        currentIndex = 0
    }

    inner class CommandReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            try {
                when (intent?.action) {
                    "UPDATE_SPEED" -> {
                        val speed = intent.getIntExtra("VALUE", 80)
                        updateSpeed(speed.toLong())
                    }
                    "UPDATE_WORDS_PER_LINE" -> {
                        val count = intent.getIntExtra("VALUE", 6)
                        updateWordsPerLine(count)
                    }
                    "START_TYPING" -> startTyping()
                    "STOP_TYPING" -> stopTyping()
                    "TOGGLE_NEON" -> {}
                    "UPDATE_WORDS" -> {
                        val words = intent.getStringArrayListExtra("WORDS")
                        if (words != null) {
                            updateWordList(words)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
