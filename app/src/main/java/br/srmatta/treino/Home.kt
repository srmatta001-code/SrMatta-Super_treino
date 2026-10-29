package br.srmatta.treino

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.min

/** Redes sociais. Os links só abrem no navegador/app do aparelho; o SrMatta Treino em si não usa internet. */
private val SOCIALS = listOf(
    Triple("TikTok", "@srmattaa", "https://www.tiktok.com/@srmattaa"),
    Triple("Twitch", "srmatta_", "https://www.twitch.tv/srmatta_"),
    Triple("Instagram", "@andreh_matta", "https://www.instagram.com/andreh_matta/")
)
private const val BAND = "Valandil"
private val HOME_TABS = listOf("Resumo", "Medidas", "Exercícios", "Calendário", "Redes")

// ---------- Página ----------

fun MainActivity.homePage(): View = page("") {
    addView(profileHeader(), params(t = 4, b = 14))

    val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
    val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    HOME_TABS.forEachIndexed { i, n ->
        chips.addView(TextView(context).apply {
            text = n
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(9), dp(16), dp(9))
            if (i == homeTab) {
                background = gradientBg(18)
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            } else {
                background = rounded(C.card, 18)
                setTextColor(C.muted)
            }
            setOnClickListener {
                homeTab = i
                show(HOME)
            }
        }, params(w = WRAP, r = 8))
    }
    hs.addView(chips)
    addView(hs, params(b = 14))

    val a = this@homePage
    when (homeTab) {
        0 -> summaryInto(a)
        1 -> measuresInto(a)
        2 -> exercisesInto(a)
        3 -> addView(a.calendarCard())
        else -> socialInto(a)
    }
}

// ---------- Cabeçalho: foto, logo, idade e peso ----------

private fun MainActivity.logoText(size: Float): TextView {
    val t = label("SrMatta", size, C.text, true)
    t.paint.shader = LinearGradient(0f, 0f, t.paint.measureText("SrMatta"), 0f, C.blue, C.purple, Shader.TileMode.CLAMP)
    return t
}

/** Aceita "15031990" ou "15/03/1990" e devolve "15/03/1990" (ou null se não tiver 8 dígitos). */
private fun normalizeBirth(raw: String): String? {
    val d = raw.filter { it.isDigit() }
    if (d.length != 8) return null
    return d.substring(0, 2) + "/" + d.substring(2, 4) + "/" + d.substring(4)
}

private fun ageOf(birth: String): Int? {
    val norm = normalizeBirth(birth) ?: return null
    val d = try {
        SimpleDateFormat("dd/MM/yyyy", PT).apply { isLenient = false }.parse(norm)
    } catch (e: Exception) {
        null
    } ?: return null
    val b = Calendar.getInstance().apply { time = d }
    val n = Calendar.getInstance()
    var a = n.get(Calendar.YEAR) - b.get(Calendar.YEAR)
    if (n.get(Calendar.DAY_OF_YEAR) < b.get(Calendar.DAY_OF_YEAR)) a--
    return if (a in 0..120) a else null
}

private fun MainActivity.avatar(): View {
    val f = File(filesDir, "profile.jpg")
    val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    val v: View = if (bmp != null) {
        ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    } else {
        TextView(this).apply {
            text = "S"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(C.blue, C.purple)).apply {
                shape = GradientDrawable.OVAL
            }
        }
    }
    v.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
    v.clipToOutline = true
    v.setOnClickListener { pickPhoto() }
    return v
}

private fun MainActivity.profileHeader(): LinearLayout {
    val c = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    c.addView(avatar(), params(w = dp(84), h = dp(84), r = 16))

    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    col.addView(logoText(30f))
    col.addView(label("Treino", 13f, C.muted), params(t = -2))

    val age = ageOf(prefs.getString("birth", "") ?: "")
    val kg = db.bodyAll().lastOrNull()?.kg
    val h = prefs.getInt("height", 0)
    val parts = ArrayList<String>()
    parts.add(if (age != null) "$age anos" else "idade —")
    parts.add(if (kg != null) "${fmtNum(kg)} kg" else "peso —")
    if (h > 0) parts.add(String.format(PT, "%.2f m", h / 100.0))
    col.addView(label(parts.joinToString("  ·  "), 14f, C.text, true), params(t = 8))
    col.addView(smallButton("Editar perfil") { editProfile() }, params(w = WRAP, t = 10))
    c.addView(col, params(w = 0, weight = 1f))
    return c
}

// ---------- Foto de perfil ----------

fun MainActivity.pickPhoto() {
    try {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_PHOTO)
    } catch (e: Exception) {
        toast("Nenhum app de galeria encontrado")
    }
}

/** Reduz a foto para 320x320 (recorte central) e guarda no próprio app: ~30 KB, sem permissões. */
fun MainActivity.savePhoto(uri: Uri) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 800) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("sem imagem")

        // Corrige a rotação de fotos tiradas na câmera (EXIF).
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                val deg = contentResolver.openInputStream(uri)?.use {
                    when (android.media.ExifInterface(it).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
                    )) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
                if (deg != 0f) {
                    src = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(deg) }, true)
                }
            } catch (_: Exception) {
            }
        }

        val side = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, 320, 320, true)
        File(filesDir, "profile.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        show(HOME)
    } catch (e: Exception) {
        toast("Não consegui usar essa foto")
    }
}

// ---------- Editar perfil ----------

private fun MainActivity.editProfile() {
    val name = styled(EditText(this), "Como podemos te chamar?")
    name.setSingleLine(true)
    name.setText(prefs.getString("profile_name", "") ?: "")

    val birth = styled(EditText(this), "dd/mm/aaaa")
    birth.inputType = InputType.TYPE_CLASS_NUMBER
    birth.setText(prefs.getString("birth", "") ?: "")
    birth.addTextChangedListener(object : TextWatcher {
        var busy = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(e: Editable?) {
            if (busy || e == null) return
            busy = true
            val digits = e.toString().filter { it.isDigit() }.take(8)
            val f = StringBuilder()
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) f.append('/')
                f.append(c)
            }
            if (f.toString() != e.toString()) e.replace(0, e.length, f.toString())
            busy = false
        }
    })

    val height = styled(EditText(this), "Altura em centímetros (ex.: 178)")
    height.inputType = InputType.TYPE_CLASS_NUMBER
    prefs.getInt("height", 0).let { if (it > 0) height.setText(it.toString()) }

    val sexOptions = listOf("Prefiro não informar", "Masculino", "Feminino")
    val sex = Spinner(this).apply {
        adapter = ArrayAdapter(this@editProfile, android.R.layout.simple_spinner_dropdown_item, sexOptions)
        setSelection(sexOptions.indexOf(prefs.getString("sex", "Prefiro não informar")).coerceAtLeast(0))
    }
    val activityOptions = listOf(
        "Sedentário — pouco movimento",
        "Leve — exercício 1 a 3 dias/semana",
        "Moderado — exercício 3 a 5 dias/semana",
        "Alto — exercício intenso 6 a 7 dias/semana",
        "Muito alto — trabalho físico e treino"
    )
    val activity = Spinner(this).apply {
        adapter = ArrayAdapter(this@editProfile, android.R.layout.simple_spinner_dropdown_item, activityOptions)
        setSelection(activityOptions.indexOf(prefs.getString("activity", activityOptions[0])).coerceAtLeast(0))
    }

    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(label("Nome", 13f, C.muted))
        addView(name, params(h = dp(48), t = 6))
        addView(label("Data de nascimento", 13f, C.muted), params(t = 12))
        addView(birth, params(h = dp(48), t = 6))
        addView(label("Sexo biológico (opcional; usado em estimativas)", 13f, C.muted), params(t = 12))
        addView(sex, params(h = dp(48), t = 4))
        addView(label("Altura (cm)", 13f, C.muted), params(t = 12))
        addView(height, params(h = dp(48), t = 6))
        addView(label("Nível de atividade", 13f, C.muted), params(t = 12))
        addView(activity, params(h = dp(56), t = 4))
        addView(label("Seu peso é registrado na aba Medidas. Os dados ficam salvos neste aparelho.", 12f, C.muted), params(t = 12, b = 8))
    }
    AlertDialog.Builder(this)
        .setTitle("Perfil e avaliação física")
        .setView(ScrollView(this).apply { addView(box) })
        .setPositiveButton("Salvar") { _, _ ->
            val n = name.text.toString().trim()
            val b = birth.text.toString().trim()
            val hv = height.text.toString().trim().toIntOrNull() ?: 0
            if (b.isNotEmpty() && ageOf(b) == null) {
                toast("Data inválida. Digite dia, mês e ano, ex.: 15031990")
            } else if (hv != 0 && hv !in 80..250) {
                toast("Altura inválida")
            } else {
                prefs.edit()
                    .putString("profile_name", n)
                    .putString("birth", normalizeBirth(b) ?: "")
                    .putString("sex", sex.selectedItem.toString())
                    .putInt("height", hv)
                    .putString("activity", activity.selectedItem.toString())
                    .apply()
                show(HOME)
            }
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

// ---------- Resumo ----------

private fun MainActivity.statTile(title: String, value: String): LinearLayout =
    card().apply {
        addView(label(title, 12f, C.muted))
        addView(label(value, 22f, C.text, true), params(t = 2))
    }

private fun LinearLayout.pair(a: View, b: View) {
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    row.addView(a, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = context.dp(5) })
    row.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = context.dp(5) })
    addView(row, LinearLayout.LayoutParams(MP, WRAP).apply { bottomMargin = context.dp(10) })
}

private fun MainActivity.assessmentCard(): LinearLayout {
    val c = card()
    c.addView(label("PERFIL E AVALIAÇÃO FÍSICA", 15f, C.blueLight, true))
    val name = prefs.getString("profile_name", "")?.trim().orEmpty()
    val age = ageOf(prefs.getString("birth", "") ?: "")
    val heightCm = prefs.getInt("height", 0)
    val sex = prefs.getString("sex", "Prefiro não informar") ?: "Prefiro não informar"
    val activity = prefs.getString("activity", "Não informado") ?: "Não informado"
    val last = db.bodyAll().lastOrNull()

    c.addView(label(if (name.isNotEmpty()) "Olá, $name" else "Complete seus dados para começar", 18f, C.text, true), params(t = 8))
    val profileParts = ArrayList<String>()
    profileParts.add(if (age != null) "$age anos" else "idade não informada")
    profileParts.add(sex)
    profileParts.add(if (heightCm > 0) "$heightCm cm" else "altura não informada")
    c.addView(label(profileParts.joinToString("  ·  "), 12f, C.muted), params(t = 4))

    if (last == null) {
        c.addView(label("Registre seu peso para calcular o IMC e acompanhar sua evolução.", 13f, C.muted), params(t = 10))
    } else {
        c.addView(label("Peso atual", 12f, C.muted), params(t = 12))
        c.addView(label("${fmtNum(last.kg)} kg", 26f, C.text, true), params(t = 2))
        if (heightCm in 80..250) {
            val bmi = last.kg / ((heightCm / 100.0) * (heightCm / 100.0))
            val rounded = Math.round(bmi * 10) / 10.0
            c.addView(label("IMC: ${fmtNum(rounded)}", 20f, C.purpleLight, true), params(t = 8))
            if (age != null && age >= 20) {
                val classification = when {
                    bmi < 18.5 -> "Abaixo do peso"
                    bmi < 25.0 -> "Peso adequado"
                    bmi < 30.0 -> "Sobrepeso"
                    bmi < 35.0 -> "Obesidade grau I"
                    bmi < 40.0 -> "Obesidade grau II"
                    else -> "Obesidade grau III"
                }
                val healthy = bmi >= 18.5 && bmi < 25.0
                c.addView(label(classification, 14f, if (healthy) C.ok else C.warn, true), params(t = 2))
                val low = 18.5 * (heightCm / 100.0) * (heightCm / 100.0)
                val high = 24.9 * (heightCm / 100.0) * (heightCm / 100.0)
                c.addView(label("Faixa de referência adulta: ${fmtNum(low)}–${fmtNum(high)} kg", 12f, C.muted), params(t = 5))
            } else {
                c.addView(label("A classificação do IMC adulto só é exibida a partir dos 20 anos.", 12f, C.muted), params(t = 4))
            }
        } else {
            c.addView(label("Informe sua altura para calcular o IMC.", 12f, C.muted), params(t = 4))
        }
    }
    c.addView(label("Atividade: ${activity.substringBefore(" —")}", 12f, C.muted), params(t = 8))
    c.addView(label("O IMC é uma referência inicial; não mede diretamente gordura ou massa muscular.", 11f, C.muted), params(t = 8))
    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(this@assessmentCard.smallButton("Editar perfil") { editProfile() }, this@assessmentCard.params(w = 0, weight = 1f, r = 6))
        addView(this@assessmentCard.smallButton("Registrar peso") { homeTab = 1; show(HOME) }, this@assessmentCard.params(w = 0, weight = 1f))
    }
    c.addView(actions, params(t = 12))
    return c
}

private fun LinearLayout.summaryInto(a: MainActivity) {
    addView(a.assessmentCard(), a.params(b = 12))
    addView(a.coachCard(), a.params(b = 12))
    val t = a.db.totals()
    if (t.sessions == 0) {
        addView(a.card().apply {
            addView(a.label("Seu histórico aparece aqui depois do primeiro treino finalizado.", 14f, C.muted))
        })
        return
    }
    val load = fmtThousands(t.volume) + " kg" + if (t.volume >= 1000) " (${fmtNum(t.volume / 1000)} t)" else ""
    pair(a.statTile("Carga total somada", load), a.statTile("Tempo total", fmtDuration(t.timeMs)))
    pair(a.statTile("Treinos", t.sessions.toString()), a.statTile("Séries", t.sets.toString()))
    pair(
        a.statTile("Tempo médio", fmtDuration(t.timeMs / t.sessions)),
        a.statTile("Carga por treino", fmtThousands(t.volume / t.sessions) + " kg")
    )

    addView(a.label("Tipo de treino", 16f, C.text, true), a.params(t = 10, b = 8))
    val kinds = a.db.kindCounts().take(6)
    val top = kinds.maxOfOrNull { it.second } ?: 1
    addView(a.card().apply {
        kinds.forEachIndexed { i, (name, n) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(a.label(name, 13f, C.text).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, a.params(w = a.dp(120)))
            val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            bar.addView(View(context).apply { background = a.rounded(C.purple, 4) }, a.params(w = 0, h = a.dp(10), weight = n.toFloat()))
            if (top - n > 0) bar.addView(View(context).apply { background = a.rounded(C.card2, 4) }, a.params(w = 0, h = a.dp(10), weight = (top - n).toFloat()))
            row.addView(bar, a.params(w = 0, weight = 1f, r = 10))
            row.addView(a.label(n.toString(), 14f, C.purpleLight, true), a.params(w = a.dp(28)))
            addView(row, a.params(t = if (i == 0) 0 else 10))
        }
    })

    val month = a.db.countSince(startOfMonth())
    val last = a.db.history(1).firstOrNull()
    addView(a.card().apply {
        addView(a.label("Neste mês: ${plural(month, "treino", "treinos")}", 14f, C.text, true))
        if (last != null) {
            addView(
                a.label("Último: ${fmtWeekday(last.started)} · ${last.kind ?: "Treino livre"}", 13f, C.muted),
                a.params(t = 4)
            )
        }
    }, a.params(t = 10))
}

// ---------- Medidas ----------

private fun LinearLayout.measuresInto(a: MainActivity) {
    // Peso corporal
    val body = a.db.bodyAll()
    val h = a.prefs.getInt("height", 0)
    addView(a.card().apply {
        addView(a.label("Peso corporal", 13f, C.muted))
        if (body.isEmpty()) {
            addView(a.label("Sem registro ainda", 18f, C.muted, true), a.params(t = 2))
        } else {
            val last = body.last()
            addView(a.label("${fmtNum(last.kg)} kg", 26f, C.text, true), a.params(t = 2))
            var info = ""
            if (body.size > 1) info += "${signed(last.kg - body.first().kg)} kg desde ${fmtDay(body.first().day)}"
            if (h > 0) {
                val m = h / 100.0
                val bmi = last.kg / (m * m)
                info += (if (info.isEmpty()) "" else "  ·  ") + "IMC ${fmtNum(Math.round(bmi * 10) / 10.0)}"
            }
            if (info.isNotEmpty()) addView(a.label(info, 13f, C.muted), a.params(t = 4))
        }
        val kgIn = a.styled(EditText(context), "Peso de hoje (kg)")
        kgIn.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(kgIn, a.params(w = 0, h = a.dp(48), weight = 1f, r = 8))
        row.addView(a.smallButton("Salvar") {
            val v = parseNum(kgIn.text.toString())
            if (v == null || v < 20 || v > 400) {
                a.toast("Informe um peso válido")
            } else {
                a.db.addBody(v)
                a.show(HOME)
            }
        }, a.params(w = a.dp(92), h = a.dp(48)))
        addView(row, a.params(t = 12))
    }, a.params(b = 14))

    // Medidas do corpo (cm)
    addView(a.label("Medidas do corpo", 16f, C.text, true), a.params(b = 8))
    for (kind in MEASURES) {
        val list = a.db.measuresOf(kind)
        addView(a.card().apply {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            left.addView(a.label(kind, 15f, C.blueLight, true))
            if (list.size > 1) {
                val d = list.last().second - list[list.size - 2].second
                left.addView(a.label("${signed(d)} cm desde ${fmtDay(list[list.size - 2].first)}", 12f, C.muted), a.params(t = 2))
            } else if (list.isEmpty()) {
                left.addView(a.label("Toque para registrar", 12f, C.muted), a.params(t = 2))
            }
            row.addView(left, a.params(w = 0, weight = 1f))
            row.addView(
                a.label(if (list.isEmpty()) "—" else "${fmtNum(list.last().second)} cm", 18f, C.text, true)
            )
            addView(row)
            setOnClickListener { a.askMeasure(kind) }
        }, a.params(b = 8))
    }
}

private fun MainActivity.askMeasure(kind: String) {
    val input = styled(EditText(this), "Medida em cm")
    input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    val box = FrameLayout(this).apply {
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(input, FrameLayout.LayoutParams(MP, dp(48)))
    }
    AlertDialog.Builder(this)
        .setTitle(kind)
        .setView(box)
        .setPositiveButton("Salvar") { _, _ ->
            val v = parseNum(input.text.toString())
            if (v == null || v < 5 || v > 300) {
                toast("Informe uma medida válida")
            } else {
                db.addMeasure(kind, v)
                show(HOME)
            }
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

// ---------- Exercícios ----------

private fun LinearLayout.exercisesInto(a: MainActivity) {
    val stats = a.db.exerciseStats()
    if (stats.isEmpty()) {
        addView(a.card().apply {
            addView(a.label("Os exercícios que você já treinou aparecem aqui, com o recorde de carga.", 14f, C.muted))
        })
        return
    }
    val custom = a.db.customMuscles()
    addView(a.label("Toque num exercício para ver a evolução.", 12f, C.muted), a.params(b = 8))
    for (e in stats) {
        addView(a.card().apply {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            left.addView(a.label(e.name, 15f, C.blueLight, true))
            val muscle = Muscles.of(e.name, custom) ?: "—"
            left.addView(
                a.label("$muscle · ${plural(e.sets, "série", "séries")} · ${plural(e.sessions, "treino", "treinos")}", 12f, C.muted),
                a.params(t = 2)
            )
            row.addView(left, a.params(w = 0, weight = 1f))
            val right = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            right.addView(a.label("Recorde", 11f, C.muted).apply { gravity = Gravity.END })
            right.addView(a.label("${fmtNum(e.best)} kg", 16f, C.purpleLight, true))
            row.addView(right)
            addView(row)
            setOnClickListener {
                a.selectedEx = e.name
                a.show(3)
            }
        }, a.params(b = 8))
    }
}

// ---------- Calendário ----------

fun MainActivity.calendarCard(): LinearLayout {
    val first = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, calOffset)
    }
    val start = startOfDay(first.timeInMillis)
    val next = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.MONTH, 1)
    }
    val trained = db.trainedDays(start, next.timeInMillis)
    val today = Calendar.getInstance()
    val isThisMonth = today.get(Calendar.YEAR) == first.get(Calendar.YEAR) &&
        today.get(Calendar.MONTH) == first.get(Calendar.MONTH)

    val c = card()
    val head = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    head.addView(stepButton("‹") {
        calOffset--
        show(HOME)
    }, params(w = dp(44), h = dp(40)))
    val title = SimpleDateFormat("MMMM yyyy", PT).format(Date(start)).replaceFirstChar { it.uppercase() }
    head.addView(label(title, 16f, C.text, true).apply { gravity = Gravity.CENTER }, params(w = 0, weight = 1f))
    head.addView(stepButton("›") {
        if (calOffset < 0) {
            calOffset++
            show(HOME)
        }
    }, params(w = dp(44), h = dp(40)))
    c.addView(head)

    val wk = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    for (n in listOf("D", "S", "T", "Q", "Q", "S", "S")) {
        wk.addView(label(n, 12f, C.muted).apply { gravity = Gravity.CENTER }, params(w = 0, weight = 1f))
    }
    c.addView(wk, params(t = 10, b = 4))

    val lead = first.get(Calendar.DAY_OF_WEEK) - 1
    val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    var day = 1 - lead
    while (day <= days) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (col in 0 until 7) {
            val cell = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 13f
            }
            if (day in 1..days) {
                cell.text = day.toString()
                if (day in trained) {
                    cell.background = gradientBg(18)
                    cell.setTextColor(Color.WHITE)
                    cell.typeface = Typeface.DEFAULT_BOLD
                } else if (isThisMonth && day == today.get(Calendar.DAY_OF_MONTH)) {
                    cell.setTextColor(C.blueLight)
                    cell.typeface = Typeface.DEFAULT_BOLD
                } else {
                    cell.setTextColor(C.muted)
                }
            }
            row.addView(cell, params(w = 0, h = dp(36), weight = 1f, l = 2, t = 2, r = 2, b = 2))
            day++
        }
        c.addView(row)
    }
    c.addView(label("${plural(trained.size, "dia treinado", "dias treinados")} neste mês", 13f, C.purpleLight), params(t = 8))
    return c
}

// ---------- Redes sociais ----------

private fun MainActivity.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        toast("Não achei um app para abrir o link")
    }
}

private fun LinearLayout.socialInto(a: MainActivity) {
    addView(a.label("Me acompanhe", 16f, C.text, true), a.params(b = 8))
    for ((name, handle, url) in SOCIALS) {
        addView(a.card().apply {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            left.addView(a.label(name, 15f, C.blueLight, true))
            left.addView(a.label(handle, 14f, C.text), a.params(t = 2))
            row.addView(left, a.params(w = 0, weight = 1f))
            row.addView(a.label("Abrir ›", 14f, C.purpleLight, true))
            addView(row)
            setOnClickListener { a.openUrl(url) }
        }, a.params(b = 8))
    }

    // Banda: o link é opcional e fica salvo só no aparelho.
    val link = a.prefs.getString("band_link", "") ?: ""
    addView(a.label("Minha banda", 16f, C.text, true), a.params(t = 10, b = 8))
    addView(a.card().apply {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        left.addView(a.label(BAND, 18f, C.purpleLight, true))
        left.addView(a.label(if (link.isEmpty()) "Toque para adicionar o link" else link, 12f, C.muted).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, a.params(t = 2))
        row.addView(left, a.params(w = 0, weight = 1f))
        if (link.isNotEmpty()) row.addView(a.label("Abrir ›", 14f, C.purpleLight, true))
        addView(row)
        setOnClickListener { if (link.isEmpty()) a.editBandLink() else a.openUrl(link) }
    }, a.params(b = 8))
    if (link.isNotEmpty()) addView(a.smallButton("Trocar link da banda") { a.editBandLink() }, a.params(w = WRAP))
}

private fun MainActivity.editBandLink() {
    val input = styled(EditText(this), "https://...")
    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    input.setText(prefs.getString("band_link", "") ?: "")
    val box = FrameLayout(this).apply {
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(input, FrameLayout.LayoutParams(MP, dp(48)))
    }
    AlertDialog.Builder(this)
        .setTitle("Link da banda $BAND")
        .setView(box)
        .setPositiveButton("Salvar") { _, _ ->
            var v = input.text.toString().trim()
            if (v.isNotEmpty() && !v.startsWith("http://") && !v.startsWith("https://")) v = "https://$v"
            prefs.edit().putString("band_link", v).apply()
            show(HOME)
        }
        .setNegativeButton("Cancelar", null)
        .show()
}
