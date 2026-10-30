package br.srmatta.treino

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.ceil
import kotlin.math.max

const val HOME = 4
const val REQ_PHOTO = 41
/** Ordem das abas na barra: Início, Treino, Rotinas, Histórico, Evolução, Ajuda. */
val NAV_IDS = listOf(HOME, 0, 1, 2, 3, 5)

class MainActivity : Activity() {

    internal lateinit var db: Db
    internal lateinit var content: FrameLayout
    private lateinit var navTabs: List<TextView>
    private lateinit var timerBar: LinearLayout
    private lateinit var globalElapsedView: TextView
    private lateinit var globalRestView: TextView
    private val handler = Handler(Looper.getMainLooper())

    // Cronômetros (o de descanso usa o relógio do sistema, então não "anda" errado).
    private var restEnd = 0L
    private var restDone = false
    private var restView: TextView? = null
    private var elapsedView: TextView? = null
    private var sessionStart = 0L
    internal var selectedEx: String? = null

    internal val prefs by lazy { getSharedPreferences("srmatta", MODE_PRIVATE) }
    private var restSecs: Int
        get() = prefs.getInt("rest", 90)
        set(v) {
            prefs.edit().putInt("rest", v).apply()
        }

    /** Rotina em andamento (-1 = treino livre). */
    private var activeRoutine: Long
        get() = prefs.getLong("routine", -1L)
        set(v) {
            prefs.edit().putLong("routine", v).apply()
        }
    internal var calOffset = 0
    internal var homeTab = 0

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C.bg)
        }
        root.addView(brandHeader(), LinearLayout.LayoutParams(MP, WRAP))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MP, 0, 1f))
        root.addView(buildTimerBar(), LinearLayout.LayoutParams(MP, WRAP))
        root.addView(buildNav(), LinearLayout.LayoutParams(MP, WRAP))
        setContentView(root)
        show(if (intent.getBooleanExtra("open_active_workout", false) && db.openSession() != null) 0 else HOME)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7001)
        if (db.openSession() != null) startWorkoutTimerService()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_active_workout", false) && db.openSession() != null) {
            show(0)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PHOTO && resultCode == RESULT_OK) data?.data?.let { savePhoto(it) }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    // ---------- Navegação ----------

    private fun buildNav(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(C.card)
        }
        val names = listOf("Início", "Treino", "Rotinas", "Histórico", "Evolução", "Ajuda")
        navTabs = names.mapIndexed { k, n ->
            TextView(this).apply {
                text = n
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener { show(NAV_IDS[k]) }
            }
        }
        navTabs.forEach { bar.addView(it, LinearLayout.LayoutParams(0, WRAP, 1f)) }
        return bar
    }

    internal fun show(i: Int) {
        restView = null
        elapsedView = null
        content.removeAllViews()
        val v = when (i) {
            0 -> trainPage()
            1 -> routinesPage()
            2 -> historyPage()
            3 -> progressPage()
            5 -> helpPage()
            else -> homePage()
        }
        content.addView(v, FrameLayout.LayoutParams(MP, MP))
        navTabs.forEachIndexed { k, t ->
            t.setTextColor(if (NAV_IDS[k] == i) C.blueLight else C.muted)
            t.typeface = if (NAV_IDS[k] == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        // Tela acesa enquanto há um treino aberto (para ver o descanso na academia).
        if (db.openSession() != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        tick()
    }

    internal fun page(title: String, body: LinearLayout.() -> Unit): View {
        val sv = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        if (title.isNotEmpty()) col.addView(label(title, 26f, C.text, true))
        col.body()
        sv.addView(col, FrameLayout.LayoutParams(MP, WRAP))
        return sv
    }

    internal fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun brandHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(9), dp(18), dp(8))
        setBackgroundColor(C.card)
        addView(label("PROJETO MONSTRO", 20f, C.text, true))
        addView(label("SrMatta- IA", 15f, C.blueLight, true), LinearLayout.LayoutParams(MP, WRAP).apply {
            topMargin = dp(1)
        })
    }

    // Barra fixa dos cronômetros: permanece visível em todas as abas.
    private fun buildTimerBar(): View {
        timerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(C.card)
            visibility = View.GONE
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(label("TEMPO DE TREINO", 10f, C.muted, true))
        globalElapsedView = label("00:00", 22f, C.text, true)
        left.addView(globalElapsedView)
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        right.addView(label("DESCANSO", 10f, C.muted, true))
        globalRestView = label("—", 22f, C.purpleLight, true)
        right.addView(globalRestView)
        timerBar.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
        timerBar.addView(right, LinearLayout.LayoutParams(0, WRAP, 1f))
        return timerBar
    }

    // ---------- Cronômetros ----------

    private fun startWorkoutTimerService() {
        val started = db.openSession()?.started ?: System.currentTimeMillis()
        val intent = android.content.Intent(this, WorkoutTimerService::class.java)
            .setAction(WorkoutTimerService.ACTION_START)
            .putExtra(WorkoutTimerService.EXTRA_SESSION_START, started)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun sendTimerCommand(action: String, value: Int = 0) {
        val intent = android.content.Intent(this, WorkoutTimerService::class.java).setAction(action)
            .putExtra(WorkoutTimerService.EXTRA_VALUE, value)
        if (android.os.Build.VERSION.SDK_INT >= 26 && action == WorkoutTimerService.ACTION_START) startForegroundService(intent) else startService(intent)
    }

    private fun startRest() {
        restDone = false
        sendTimerCommand(WorkoutTimerService.ACTION_REST, restSecs)
        tick()
    }

    private fun adjustRest(deltaSec: Int) {
        sendTimerCommand(WorkoutTimerService.ACTION_ADJUST_REST, deltaSec)
        tick()
    }

    private fun stopRest() {
        sendTimerCommand(WorkoutTimerService.ACTION_STOP_REST)
        tick()
    }

    @Suppress("DEPRECATION")
    private fun testVibration() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) {
                toast("O Android informou que este aparelho não possui vibrador.")
                return
            }
            val pattern = longArrayOf(0, 350, 150, 350, 150, 550)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                vibrator.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            toast("Comando de vibração enviado.")
        } catch (e: Exception) {
            toast("Não foi possível vibrar: ${e.message ?: "verifique as configurações do aparelho"}")
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val active = db.openSession()
        timerBar.visibility = if (active != null) View.VISIBLE else View.GONE
        restEnd = prefs.getLong(WorkoutTimerService.PREF_REST_END, 0L)
        restDone = prefs.getBoolean(WorkoutTimerService.PREF_REST_DONE, false)
        val restText: String
        val restColor: Int
        when {
            restEnd > now -> {
                val seconds = ceil((restEnd - now) / 1000.0).toInt()
                restText = fmtClock(seconds)
                restColor = if (seconds <= 10) C.warn else C.purpleLight
            }
            restDone -> {
                restText = "Pronto!"
                restColor = C.ok
            }
            else -> {
                restText = "—"
                restColor = C.muted
            }
        }
        globalRestView.text = restText
        globalRestView.setTextColor(restColor)
        restView?.text = restText
        restView?.setTextColor(restColor)
        if (active != null) {
            val elapsed = fmtClock(((System.currentTimeMillis() - active.started) / 1000).toInt())
            globalElapsedView.text = elapsed
            elapsedView?.text = elapsed
        }
    }

    // ---------- Aba Treino ----------

    private fun trainPage(): View = page("SrMatta Treino") {
        val s = db.openSession()
        if (s == null) idleSection() else activeSection(s)
    }

    private fun LinearLayout.idleSection() {
        val month = db.countSince(startOfMonth())
        val last = db.history(1).firstOrNull()
        addView(label("Pronto para treinar?", 16f, C.muted), params(t = 2, b = 16))
        addView(card().apply {
            addView(label("Neste mês", 13f, C.muted))
            addView(label(plural(month, "treino", "treinos"), 28f, C.text, true))
            if (last != null) {
                addView(
                    label("Último: ${fmtWeekday(last.started)} · ${plural(last.sets, "série", "séries")}", 13f, C.muted),
                    params(t = 4)
                )
            }
        }, params(b = 16))
        addView(coachCard(), params(b = 16))
        addView(primaryButton("Iniciar treino livre") {
            activeRoutine = -1L
            db.startSession()
            startWorkoutTimerService()
            show(0)
        }, params())

        val routines = db.routines()
        if (routines.isEmpty()) {
            addView(
                label("Dica: crie rotinas (Treino A, B, C) ou adicione um plano pronto na aba Rotinas.", 13f, C.muted),
                params(t = 14)
            )
        } else {
            addView(label("Ou comece por uma rotina", 16f, C.text, true), params(t = 22, b = 4))
            for (r in routines) {
                addView(card().apply {
                    addView(label(r.name, 16f, C.blueLight, true))
                    addView(label(r.exercises.joinToString(" · "), 12f, C.muted), params(t = 4))
                    setOnClickListener {
                        activeRoutine = r.id
                        db.startSession(r.name)
                        startWorkoutTimerService()
                        show(0)
                    }
                }, params(t = 8))
            }
        }
    }

    private fun LinearLayout.activeSection(s: Session) {
        sessionStart = s.started
        val sets = db.setsOf(s.id)

        // Os cronômetros ficam na barra fixa abaixo do conteúdo, em todas as abas.

        val controls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(smallButton("−15 s") { adjustRest(-15) }, params(w = 0, weight = 1f, r = 6))
        controls.addView(smallButton("+15 s") { adjustRest(15) }, params(w = 0, weight = 1f, r = 6))
        controls.addView(smallButton("Parar") { stopRest() }, params(w = 0, weight = 1f, r = 6))
        var preset: TextView? = null
        preset = smallButton("Tempo: ${restSecs}s") {
            val i = REST_OPTIONS.indexOf(restSecs)
            restSecs = REST_OPTIONS[(i + 1) % REST_OPTIONS.size]
            preset?.text = "Tempo: ${restSecs}s"
        }
        controls.addView(preset, params(w = 0, weight = 1.3f))
        addView(controls, params(b = 12))

        // Formulário da série.
        val names = (db.recentExercises() + DEFAULT_EXERCISES).distinctBy { it.lowercase() }
        val ex = styled(AutoCompleteTextView(context), "Nome do exercício")
        ex.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, names))
        ex.threshold = 1
        ex.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ex.setOnClickListener { ex.showDropDown() }

        val kg = styled(EditText(context), "kg")
        kg.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        kg.gravity = Gravity.CENTER
        val reps = styled(EditText(context), "reps")
        reps.inputType = InputType.TYPE_CLASS_NUMBER
        reps.gravity = Gravity.CENTER

        val lastSet = sets.lastOrNull()
        if (lastSet != null) {
            ex.setText(lastSet.exercise)
            kg.setText(fmtInput(lastSet.weight))
            reps.setText(lastSet.reps.toString())
        } else {
            reps.setText("10")
        }
        ex.setOnItemClickListener { _, _, pos, _ ->
            val name = ex.adapter.getItem(pos) as String
            db.lastSetFor(name)?.let {
                kg.setText(fmtInput(it.weight))
                reps.setText(it.reps.toString())
            }
        }

        // Treinador: sugere carga e repetições a partir do último treino deste exercício.
        val customMuscles = db.customMuscles()
        val sug = label("", 13f, C.purpleLight, true)
        sug.visibility = View.GONE
        var suggestion: Suggestion? = null
        fun refreshSuggestion() {
            val name = ex.text.toString().trim()
            suggestion = if (name.isEmpty()) null
            else Coach.suggest(db.lastSessionSets(name), Muscles.of(name, customMuscles))
            val s = suggestion
            if (s == null) {
                sug.visibility = View.GONE
            } else {
                sug.visibility = View.VISIBLE
                sug.text = "Sugestão: ${fmtNum(s.weight)} kg × ${s.reps}\n${s.note}\nToque para usar"
            }
        }
        sug.setOnClickListener {
            suggestion?.let {
                kg.setText(fmtInput(it.weight))
                reps.setText(it.reps.toString())
            }
        }
        ex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshSuggestion()
        })
        refreshSuggestion()

        // Rotina em andamento: lista dos exercícios com ✓ nos que já têm série. Toque para preencher o formulário.
        val routine = if (activeRoutine >= 0) db.routine(activeRoutine) else null
        if (routine != null) {
            val done = sets.map { it.exercise.lowercase() }.toSet()
            addView(card().apply {
                addView(label(routine.name, 15f, C.purpleLight, true))
                for (name in routine.exercises) {
                    val ok = name.lowercase() in done
                    addView(label((if (ok) "✓  " else "○  ") + name, 15f, if (ok) C.ok else C.text).apply {
                        setPadding(0, dp(9), 0, dp(9))
                        setOnClickListener {
                            ex.setText(name)
                            val last = db.lastSetFor(name)
                            if (last != null) {
                                kg.setText(fmtInput(last.weight))
                                reps.setText(last.reps.toString())
                            }
                        }
                    })
                }
            }, params(b = 12))
        }

        addView(card().apply {
            addView(label("Exercício", 13f, C.muted))
            addView(ex, params(t = 6, h = dp(48)))
            addView(sug, params(t = 8))
            addView(label("Carga (kg)", 13f, C.muted), params(t = 14))
            addView(stepper(kg, 2.5, true), params(t = 6))
            addView(label("Repetições", 13f, C.muted), params(t = 14))
            addView(stepper(reps, 1.0, false), params(t = 6))
            addView(primaryButton("Registrar série") { register(s.id, ex, kg, reps) }, params(t = 18))
        }, params(b = 12))

        // Séries já feitas hoje, agrupadas por exercício.
        if (sets.isNotEmpty()) {
            addView(label("Séries de hoje", 16f, C.text, true), params(t = 8, b = 8))
            for ((name, list) in sets.groupBy { it.exercise }) {
                addView(card().apply {
                    addView(label(name, 15f, C.blueLight, true))
                    list.forEachIndexed { i, st ->
                        val row = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        row.addView(label("${i + 1}ª série", 14f, C.muted), params(w = dp(84)))
                        row.addView(label("${fmtNum(st.weight)} kg × ${st.reps}", 16f, C.text, true), params(w = 0, weight = 1f))
                        row.addView(label("×", 24f, C.danger, true).apply {
                            setPadding(dp(14), dp(2), dp(4), dp(2))
                            setOnClickListener {
                                db.deleteSet(st.id)
                                show(0)
                            }
                        })
                        addView(row, params(t = 8))
                    }
                }, params(b = 8))
            }
        }

        addView(secondaryButton("Finalizar treino") { confirmFinish(s.id, sets.size) }, params(t = 16))
    }

    private fun stepper(field: EditText, step: Double, decimal: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun bump(delta: Double) {
            val cur = parseNum(field.text.toString()) ?: 0.0
            val nv = max(0.0, cur + delta)
            field.setText(if (decimal) fmtInput(nv) else nv.toInt().toString())
        }
        row.addView(stepButton("−") { bump(-step) }, params(w = dp(54), h = dp(48)))
        row.addView(field, params(w = 0, h = dp(48), weight = 1f, l = 8, r = 8))
        row.addView(stepButton("+") { bump(step) }, params(w = dp(54), h = dp(48)))
        return row
    }

    /** Usa a grafia já existente do exercício (ignora maiúsculas/minúsculas e espaços extras). */
    private fun canon(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return clean
        val known = db.recentExercises() + DEFAULT_EXERCISES
        return known.firstOrNull { it.equals(clean, ignoreCase = true) }
            ?: clean.replaceFirstChar { it.uppercase() }
    }

    private fun register(sessionId: Long, ex: AutoCompleteTextView, kg: EditText, reps: EditText) {
        val name = canon(ex.text.toString())
        val w = parseNum(kg.text.toString())
        val r = reps.text.toString().trim().toIntOrNull()
        when {
            name.isEmpty() -> toast("Informe o exercício")
            w == null || w < 0 -> toast("Informe a carga (use 0 para peso do corpo)")
            r == null || r <= 0 -> toast("Informe as repetições")
            else -> {
                db.addSet(sessionId, name, r, w)
                startRest()
                show(0)
                // Exercício novo (fora da lista padrão): pergunta o músculo uma única vez.
                if (Muscles.of(name, db.customMuscles()) == null) askMuscle(name)
            }
        }
    }

    private fun confirmFinish(id: Long, nSets: Int) {
        val msg = if (nSets == 0) "Nenhuma série registrada. O treino será descartado."
        else "Salvar este treino com ${plural(nSets, "série", "séries")}?"
        AlertDialog.Builder(this)
            .setTitle("Finalizar treino")
            .setMessage(msg)
            .setPositiveButton(if (nSets == 0) "Descartar" else "Finalizar") { _, _ ->
                db.endSession(id)
                activeRoutine = -1L
                stopRest()
                sendTimerCommand(WorkoutTimerService.ACTION_STOP)
                show(2)
            }
            .setNegativeButton("Continuar", null)
            .show()
    }

    // ---------- Aba Histórico ----------

    private fun historyPage(): View = page("Histórico") {
        val list = db.history()
        if (list.isEmpty()) {
            addView(label("Nenhum treino finalizado ainda.\nComece pela aba Treino.", 15f, C.muted), params(t = 12))
        }
        for (h in list) {
            addView(card().apply {
                addView(label(fmtWeekday(h.started), 17f, C.text, true))
                addView(label(h.kind ?: "Treino livre", 12f, C.purpleLight, true), params(t = 2))
                addView(
                    label(
                        "${fmtDuration(h.ended - h.started)} · ${plural(h.exercises, "exercício", "exercícios")} · ${plural(h.sets, "série", "séries")}",
                        13f, C.muted
                    ), params(t = 2)
                )
                addView(label("Volume: ${fmtThousands(h.volume)} kg", 13f, C.purpleLight), params(t = 6))
                addView(label(h.names.replace(",", ", "), 12f, C.muted).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }, params(t = 6))
                setOnClickListener { showSessionDialog(h) }
            }, params(t = 10))
        }
    }

    private fun showSessionDialog(h: Summary) {
        val sb = StringBuilder()
        for ((name, list) in db.setsOf(h.id).groupBy { it.exercise }) {
            sb.append(name).append('\n')
            list.forEachIndexed { i, s -> sb.append("  ${i + 1}. ${fmtNum(s.weight)} kg × ${s.reps}\n") }
            sb.append('\n')
        }
        AlertDialog.Builder(this)
            .setTitle(fmtWeekday(h.started))
            .setMessage(sb.toString().trim())
            .setPositiveButton("Fechar", null)
            .setNegativeButton("Excluir") { _, _ -> confirmDelete(h) }
            .show()
    }

    private fun confirmDelete(h: Summary) {
        AlertDialog.Builder(this)
            .setTitle("Excluir treino?")
            .setMessage("O treino de ${fmtWeekday(h.started)} será apagado. Isso não pode ser desfeito.")
            .setPositiveButton("Excluir") { _, _ ->
                db.deleteSession(h.id)
                show(2)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Aba Evolução ----------

    private fun progressPage(): View = page("Evolução") {
        // ----- Músculos treinados nos últimos 7 dias -----
        addView(label("Músculos · últimos 7 dias", 18f, C.text, true), params(t = 16, b = 8))
        addView(muscleCard(), params())

        // ----- Peso corporal -----
        addView(label("Peso corporal", 18f, C.text, true), params(t = 28, b = 8))
        val body = db.bodyAll()
        val bodyChart = ChartView(context)
        bodyChart.points = body.map { it.day to it.kg }
        val bodySummary = if (body.isEmpty()) {
            "Sem registros ainda."
        } else {
            val first = body.first()
            val last = body.last()
            var t = "Atual: ${fmtNum(last.kg)} kg"
            if (body.size > 1) t += " · ${signed(last.kg - first.kg)} kg desde ${fmtDay(first.day)}"
            t
        }
        addView(card().apply {
            addView(bodyChart, params(h = dp(180)))
            addView(label(bodySummary, 13f, C.muted), params(t = 8))
        }, params())

        val kgIn = styled(EditText(context), "Seu peso hoje (kg)")
        kgIn.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val addRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addRow.addView(kgIn, params(w = 0, h = dp(48), weight = 1f, r = 8))
        addRow.addView(smallButton("Salvar") {
            val v = parseNum(kgIn.text.toString())
            if (v == null || v < 20 || v > 400) {
                toast("Informe um peso válido")
            } else {
                db.addBody(v)
                show(3)
            }
        }, params(w = dp(92), h = dp(48)))
        addView(addRow, params(t = 10))

        for (row in body.takeLast(5).reversed()) {
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(label(fmtDay(row.day), 14f, C.muted), params(w = dp(70)))
            line.addView(label("${fmtNum(row.kg)} kg", 15f, C.text, true), params(w = 0, weight = 1f))
            line.addView(label("×", 24f, C.danger, true).apply {
                setPadding(dp(14), dp(2), dp(4), dp(2))
                setOnClickListener {
                    db.deleteBody(row.id)
                    show(3)
                }
            })
            addView(line, params(t = 8))
        }

        // ----- Cargas por exercício -----
        addView(label("Cargas por exercício", 18f, C.text, true), params(t = 28, b = 8))
        val exs = db.exerciseNames()
        if (exs.isEmpty()) {
            addView(label("Registre séries na aba Treino para acompanhar a evolução das cargas.", 14f, C.muted))
            return@page
        }
        if (selectedEx == null || selectedEx !in exs) selectedEx = exs.first()

        val loadChart = ChartView(context)
        val loadSummary = label("", 13f, C.muted)
        fun refresh() {
            val name = selectedEx ?: return
            val pts = db.loadSeries(name)
            loadChart.points = pts
            loadSummary.text = if (pts.isEmpty()) "" else
                "Melhor: ${fmtNum(pts.maxOf { it.second })} kg · Última: ${fmtNum(pts.last().second)} kg · ${plural(pts.size, "treino", "treinos")}"
        }

        val spinner = Spinner(context)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, exs)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(exs.indexOf(selectedEx), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (exs[position] != selectedEx) {
                    selectedEx = exs[position]
                    refresh()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        refresh()

        addView(card().apply {
            addView(spinner, params())
            addView(loadChart, params(h = dp(180), t = 8))
            addView(loadSummary, params(t = 8))
        }, params())
    }

    // ---------- Aba Rotinas ----------

    private fun routinesPage(): View = page("Rotinas") {
        addView(primaryButton("Nova rotina") { editRoutine(null) }, params(t = 14, b = 4))

        val routines = db.routines()
        if (routines.isEmpty()) {
            addView(label("Nenhuma rotina ainda. Crie a sua ou adicione um plano pronto abaixo.", 14f, C.muted), params(t = 10))
        }
        for (r in routines) {
            addView(card().apply {
                addView(label(r.name, 17f, C.blueLight, true))
                addView(label(plural(r.exercises.size, "exercício", "exercícios"), 12f, C.muted), params(t = 2))
                addView(label(r.exercises.joinToString(" · "), 13f, C.text), params(t = 6))
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(smallButton("Iniciar") { startRoutine(r) }, params(w = 0, weight = 1f, r = 6))
                row.addView(smallButton("Editar") { editRoutine(r) }, params(w = 0, weight = 1f, r = 6))
                row.addView(smallButton("Excluir") { confirmDeleteRoutine(r) }, params(w = 0, weight = 1f))
                addView(row, params(t = 12))
            }, params(t = 10))
        }

        addView(label("Planos prontos", 18f, C.text, true), params(t = 28, b = 4))
        for (p in PLANS) {
            addView(card().apply {
                addView(label(p.name, 16f, C.purpleLight, true))
                addView(label(p.about, 13f, C.muted), params(t = 2))
                for (d in p.days) {
                    addView(label(d.name + ": " + d.exercises.joinToString(", "), 12f, C.text), params(t = 6))
                }
                addView(smallButton("Adicionar às minhas rotinas") {
                    p.days.forEach { db.saveRoutine(null, it.name, it.exercises) }
                    toast("${p.name} adicionado")
                    show(1)
                }, params(t = 12))
            }, params(t = 10))
        }
    }

    internal fun startRoutine(r: Routine) {
        if (db.openSession() != null) {
            toast("Já existe um treino em andamento. Finalize-o antes.")
            return
        }
        activeRoutine = r.id
        db.startSession(r.name)
        startWorkoutTimerService()
        show(0)
    }

    private fun confirmDeleteRoutine(r: Routine) {
        AlertDialog.Builder(this)
            .setTitle("Excluir rotina?")
            .setMessage("A rotina \"${r.name}\" será apagada. Seus treinos já feitos não são afetados.")
            .setPositiveButton("Excluir") { _, _ ->
                db.deleteRoutine(r.id)
                if (activeRoutine == r.id) activeRoutine = -1L
                show(1)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Passo 1: nome. Passo 2: escolher os exercícios. */
    private fun editRoutine(r: Routine?) {
        val input = styled(EditText(this), "Nome (ex.: Treino A)")
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.setText(r?.name ?: "")
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, FrameLayout.LayoutParams(MP, dp(48)))
        }
        AlertDialog.Builder(this)
            .setTitle(if (r == null) "Nova rotina" else "Editar rotina")
            .setView(box)
            .setPositiveButton("Próximo") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) toast("Informe o nome") else pickExercises(r, name)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickExercises(r: Routine?, name: String, extra: List<String> = emptyList()) {
        val all = (DEFAULT_EXERCISES + db.exerciseNames() + (r?.exercises ?: emptyList()) + extra)
            .distinctBy { it.trim().lowercase() }
        val chosen = (r?.exercises.orEmpty() + extra).map { it.lowercase() }.toSet()
        val checked = BooleanArray(all.size) { all[it].lowercase() in chosen }
        AlertDialog.Builder(this)
            .setTitle("Exercícios de $name")
            .setMultiChoiceItems(all.toTypedArray(), checked) { _, i, c -> checked[i] = c }
            .setNeutralButton("Adicionar exercício") { _, _ ->
                val field = styled(EditText(this), "Nome do novo exercício")
                field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                AlertDialog.Builder(this)
                    .setTitle("Novo exercício")
                    .setView(field)
                    .setPositiveButton("Adicionar") { _, _ ->
                        val value = field.text.toString().trim().replace(Regex("\\s+"), " ")
                        if (value.isBlank()) toast("Digite o nome do exercício")
                        else pickExercises(r, name, extra + value)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setPositiveButton("Salvar") { _, _ ->
                val picked = all.filterIndexed { i, _ -> checked[i] }
                if (picked.isEmpty()) {
                    toast("Escolha ao menos um exercício")
                } else {
                    db.saveRoutine(r?.id, name, picked)
                    show(1)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Músculos ----------

    private fun askMuscle(exercise: String) {
        AlertDialog.Builder(this)
            .setTitle("Qual músculo trabalha \"$exercise\"?")
            .setItems(Muscles.ALL.toTypedArray()) { _, i -> db.setMuscle(exercise, Muscles.ALL[i]) }
            .setNegativeButton("Agora não", null)
            .show()
    }

    /** Séries por grupo muscular nos últimos 7 dias, em barras. */
    private fun muscleCard(): LinearLayout {
        val custom = db.customMuscles()
        val since = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val totals = HashMap<String, Int>()
        var unknown = 0
        for ((ex, n) in db.setsByExerciseSince(since)) {
            val m = Muscles.of(ex, custom)
            if (m == null) unknown += n else totals[m] = (totals[m] ?: 0) + n
        }
        val top = max(1, totals.values.maxOrNull() ?: 0)
        val c = card()
        if (totals.isEmpty() && unknown == 0) {
            c.addView(label("Nenhuma série nos últimos 7 dias.", 14f, C.muted))
            return c
        }
        for (m in Muscles.ALL) {
            val n = totals[m] ?: 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(label(m, 14f, if (n > 0) C.text else C.muted), params(w = dp(78)))
            val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (n > 0) bar.addView(View(this).apply { background = rounded(C.purple, 4) }, params(w = 0, h = dp(10), weight = n.toFloat()))
            if (top - n > 0) bar.addView(View(this).apply { background = rounded(C.card2, 4) }, params(w = 0, h = dp(10), weight = (top - n).toFloat()))
            row.addView(bar, params(w = 0, weight = 1f, r = 10))
            row.addView(label(n.toString(), 14f, if (n > 0) C.purpleLight else C.muted, true), params(w = dp(28)))
            c.addView(row, params(t = if (m == Muscles.ALL.first()) 0 else 10))
        }
        if (unknown > 0) {
            c.addView(label("$unknown séries sem músculo definido.", 12f, C.muted), params(t = 10))
        }
        return c
    }

    // ---------- Ajuda, versão e atualizações ----------

    private fun helpPage(): View = page("Ajuda") {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val installedCode = if (android.os.Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        addView(card().apply {
            addView(label("PROJETO MONSTRO", 17f, C.text, true))
            addView(label("Versão ${packageInfo.versionName} (código $installedCode)", 14f, C.muted), params(t = 8))
            addView(label("Confira se existe uma versão mais recente do aplicativo.", 13f, C.muted), params(t = 8))
            addView(primaryButton("Verificar atualizações") { checkForUpdates() }, params(t = 14))
        }, params(t = 12, b = 12))

        addView(card().apply {
            addView(label("Alarme de descanso", 16f, C.text, true))
            addView(label("Corneta de treino e vibração ao terminar o descanso.", 13f, C.muted), params(t = 8))
            addView(secondaryButton("Testar vibração agora") { testVibration() }, params(t = 10))
        }, params(b = 12))

        addView(card().apply {
            addView(label("Dúvidas", 16f, C.text, true))
            addView(label("Envie sua pergunta e escolha como prefere receber a resposta.", 13f, C.muted), params(t = 8))
            addView(primaryButton("Enviar uma dúvida") { showDoubtForm() }, params(t = 12))
        }, params(b = 12))
    }

    private fun showDoubtForm() {
        val personName = styled(EditText(this), "Seu nome").apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val question = styled(EditText(this), "Digite sua dúvida").apply {
            minLines = 4
            maxLines = 6
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val responseMethod = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("E-mail", "WhatsApp"))
        }
        val contact = styled(EditText(this), "Seu e-mail para resposta").apply { setSingleLine(true) }
        responseMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    contact.hint = "Seu e-mail para resposta"
                    contact.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                } else {
                    contact.hint = "Seu WhatsApp com DDD"
                    contact.inputType = InputType.TYPE_CLASS_PHONE
                }
                contact.text?.clear()
            }
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(label("Nome", 13f, C.muted))
            addView(personName, params(h = dp(48), t = 5))
            addView(label("Sua dúvida", 13f, C.muted), params(t = 12))
            addView(question, params(h = dp(112), t = 5))
            addView(label("Como deseja receber a resposta?", 13f, C.muted), params(t = 12))
            addView(responseMethod, params(h = dp(48), t = 4))
            addView(contact, params(h = dp(48), t = 8, b = 8))
        }
        AlertDialog.Builder(this)
            .setTitle("Enviar dúvida")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Continuar") { _, _ ->
                val name = personName.text.toString().trim()
                val doubt = question.text.toString().trim()
                val method = responseMethod.selectedItem.toString()
                val replyTo = contact.text.toString().trim()
                val validContact = if (method == "E-mail") replyTo.contains("@") && replyTo.substringAfter("@").contains(".")
                    else replyTo.filter { it.isDigit() }.length >= 10
                if (name.isBlank() || doubt.isBlank() || !validContact) {
                    toast("Preencha nome, dúvida e um contato válido.")
                } else {
                    sendDoubtEmail(name, doubt, method, replyTo)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendDoubtEmail(name: String, doubt: String, method: String, replyTo: String) {
        val body = "DÚVIDA — PROJETO MONSTRO\n\nNome: $name\nPergunta: $doubt\n\nResponder por: $method\nContato para resposta: $replyTo"
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:srmatta001@gmail.com")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Dúvida recebida pelo Projeto Monstro")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(mail, "Enviar dúvida por e-mail"))
        } catch (_: Exception) {
            toast("Nenhum aplicativo de e-mail configurado neste aparelho.")
        }
    }

    private fun currentInstalledVersionCode(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        return if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun sendSupportEmail(subject: String) {
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:srmatta001@gmail.com")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        try { startActivity(mail) } catch (_: Exception) { toast("Nenhum aplicativo de e-mail encontrado.") }
    }

    private fun checkForUpdates() {
        toast("Verificando atualizações…")
        Thread {
            try {
                val connection = (URL("https://api.github.com/repos/srmatta001-code/SrMatta-Super_treino/releases/latest").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "SrMatta-Super-Treino")
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val release = JSONObject(response)
                val body = release.optString("body")
                val remoteCode = Regex("versionCode\\s*=\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
                    ?: throw IllegalStateException("A publicação não informou o código da versão.")
                val assets = release.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name") == "Projeto-Monstro.apk") apkUrl = asset.optString("browser_download_url")
                }
                if (apkUrl.isNullOrBlank()) throw IllegalStateException("APK não encontrado na publicação.")
                runOnUiThread {
                    if (remoteCode.toLong() > currentInstalledVersionCode()) {
                        AlertDialog.Builder(this)
                            .setTitle("Atualização disponível")
                            .setMessage("Nova versão encontrada (código $remoteCode). Deseja baixar e instalar?")
                            .setPositiveButton("Baixar e instalar") { _, _ -> downloadAndInstall(apkUrl!!) }
                            .setNegativeButton("Depois", null)
                            .show()
                    } else toast("Seu aplicativo já está atualizado.")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Não foi possível verificar: ${e.message ?: "erro de conexão"}") }
            }
        }.start()
    }

    private fun downloadAndInstall(apkUrl: String) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Permitir instalação")
                .setMessage("Autorize o SrMatta Super Treino a instalar atualizações baixadas por ele. Depois toque novamente em Verificar atualizações.")
                .setPositiveButton("Abrir configuração") { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Cancelar", null).show()
            return
        }
        toast("Baixando atualização…")
        Thread {
            try {
                val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                val apk = File(cacheDir, "SrMattaTreino-update.apk")
                connection.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
                if (apk.length() < 100000) throw IllegalStateException("Arquivo APK inválido ou incompleto.")
                runOnUiThread {
                    try {
                        val uri = FileProvider.getUriForFile(this, "$packageName.provider", apk)
                        val install = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(install)
                    } catch (e: Exception) { toast("Não foi possível abrir o instalador: ${e.message}") }
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Falha no download: ${e.message ?: "erro de conexão"}") }
            }
        }.start()
    }

}
