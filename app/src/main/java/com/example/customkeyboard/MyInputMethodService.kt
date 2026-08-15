package com.example.customkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import kotlinx.coroutines.*

class MyInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    // ================== المحرك الأساسي ==================
    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard

    private var wordList: MutableList<String> = mutableListOf()
    private var shuffledWords: MutableList<String> = mutableListOf()
    private var currentIndex: Int = 0

    private var typingSpeedMs: Long = 80L
    private var wordsPerLine: Int = 6
    private var isTypingActive: Boolean = false
    private var typingJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ================== دورة الحياة ==================
    override fun onCreate() {
        super.onCreate()
        keyboard = Keyboard(this, R.xml.keyboard_layout_arabic)
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetTypingState()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTyping()
    }

    // ================== KeyboardActionListener ==================
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
            Keyboard.KEYCODE_DONE, Keyboard.KEYCODE_ENTER -> {
                // إرسال Enter
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
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

    // ================== دوال التحكم ==================
    fun updateWordList(newWords: List<String>) {
        wordList = newWords.toMutableList()
        resetTypingState()
        mainHandler.post {
            Toast.makeText(applicationContext, "تم استيراد ${wordList.size} كلمة فريدة", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateSpeed(speedMs: Long) {
        typingSpeedMs = speedMs.coerceIn(5, 150)
    }

    fun updateWordsPerLine(count: Int) {
        wordsPerLine = count.coerceAtLeast(1)
    }

    fun startTyping() {
        if (wordList.isEmpty()) {
            mainHandler.post {
                Toast.makeText(applicationContext, "الرجاء إدخال نصوص وحفظها أولاً", Toast.LENGTH_SHORT).show()
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
            while (isTypingActive && currentIndex < shuffledWords.size) {
                val word = shuffledWords[currentIndex]

                for (char in word) {
                    if (!isTypingActive) break
                    pressKey(char)
                    delay(typingSpeedMs + (5..20).random().toLong())
                }

                if (!isTypingActive) break

                currentInputConnection?.commitText(" ", 1)
                delay(typingSpeedMs / 2)

                if ((currentIndex + 1) % wordsPerLine == 0) {
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    delay(typingSpeedMs)
                }

                currentIndex++

                if (currentIndex >= shuffledWords.size && isTypingActive) {
                    shuffledWords = wordList.shuffled().toMutableList()
                    currentIndex = 0
                }
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

    private fun pressKey(char: Char) {
        currentInputConnection?.commitText(char.toString(), 1)
    }

    // ================== مستقبل البث (BroadcastReceiver) ==================
    inner class CommandReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
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
                "TOGGLE_NEON" -> {
                    // سيتم تنفيذ تأثير النيون لاحقاً
                }
                "UPDATE_WORDS" -> {
                    val words = intent.getStringArrayListExtra("WORDS")
                    if (words != null) {
                        updateWordList(words)
                    }
                }
            }
        }
    }
}
