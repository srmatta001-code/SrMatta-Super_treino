package br.srmatta.treino

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max

const val REQ_SUMMARY_GALLERY = 42
const val REQ_SUMMARY_CAMERA = 43

fun MainActivity.showWorkoutSummary(sessionId: Long) {
    val h = db.summary(sessionId)
    if (h == null) {
        toast("Não encontrei os dados deste treino")
        show(2)
        return
    }
    summaryPhotoSessionId = sessionId
    content.removeAllViews()
    timerBar?.visibility = View.GONE
    val sets = db.setsOf(sessionId)
    val reps = sets.sumOf { it.reps }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(24))
    }
    col.addView(label("Treino concluído!", 25f, C.text, true))
    col.addView(label(h.kind ?: "Treino livre", 15f, C.purpleLight, true), params(t = 4))
    col.addView(label(SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", PT).format(Date(h.started)), 13f, C.muted), params(t = 4))
    col.addView(card().apply {
        addView(label("DURAÇÃO", 12f, C.muted, true))
        addView(label(fmtDuration(h.ended - h.started), 24f, C.text, true), params(t = 3))
        addView(label("VOLUME TOTAL", 12f, C.muted, true), params(t = 12))
        addView(label("${fmtThousands(h.volume)} kg", 22f, C.purpleLight, true), params(t = 3))
        addView(label("${h.exercises} exercícios · ${h.sets} séries · $reps repetições", 13f, C.text), params(t = 5))
    }, params(t = 14))
    col.addView(label("Cargas utilizadas", 16f, C.text, true), params(t = 16, b = 6))
    sets.groupBy { it.exercise }.forEach { (name, rows) ->
        col.addView(card().apply {
            addView(label(name, 14f, C.blueLight, true))
            rows.forEachIndexed { i, set ->
                addView(label("${i + 1}ª série · ${fmtNum(set.weight)} kg × ${set.reps}", 13f, C.text), params(t = 3))
            }
        }, params(b = 6))
    }
    col.addView(label("Cartão para compartilhar", 16f, C.text, true), params(t = 16, b = 8))
    val preview = ImageView(this).apply {
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageBitmap(makeWorkoutCard(h, sets))
    }
    col.addView(preview, params(h = dp(420)))
    col.addView(label("A foto escolhida será usada como fundo. A localização não é coletada.", 12f, C.muted), params(t = 6))
    col.addView(primaryButton("Tirar foto com a câmera") { takeWorkoutPhoto(sessionId) }, params(t = 14))
    col.addView(secondaryButton("Escolher foto da galeria") { chooseWorkoutPhoto(sessionId) }, params(t = 8))
    col.addView(primaryButton("Compartilhar cartão") { shareWorkoutCard(sessionId) }, params(t = 12))
    col.addView(secondaryButton("Concluir") { show(2) }, params(t = 8))
    content.addView(ScrollView(this).apply { addView(col) }, android.widget.FrameLayout.LayoutParams(MP, MP))
}

private fun MainActivity.takeWorkoutPhoto(id: Long) {
    summaryPhotoSessionId = id
    try {
        val file = File(cacheDir, "camera_$id.jpg")
        pendingSummaryCameraPath = file.absolutePath
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_SUMMARY_CAMERA)
    } catch (_: Exception) { toast("Câmera não disponível") }
}

private fun MainActivity.chooseWorkoutPhoto(id: Long) {
    summaryPhotoSessionId = id
    try {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_SUMMARY_GALLERY)
    } catch (_: Exception) { toast("Galeria não disponível") }
}

fun MainActivity.storeWorkoutPhoto(id: Long, bitmap: Bitmap) {
    try {
        File(filesDir, "workout_bg_$id.jpg").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
        }
        showWorkoutSummary(id)
    } catch (_: Exception) { toast("Não consegui salvar a foto") }
}

fun MainActivity.storeWorkoutPhoto(id: Long, uri: android.net.Uri) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("imagem inválida")
        storeWorkoutPhoto(id, bitmap)
    } catch (_: Exception) { toast("Não consegui carregar a foto") }
}

private fun MainActivity.makeWorkoutCard(h: Summary, sets: List<SetRow>): Bitmap {
    val w = 1080
    val ht = 1350
    val bmp = Bitmap.createBitmap(w, ht, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val photo = File(filesDir, "workout_bg_${h.id}.jpg")
    val bg = if (photo.exists()) BitmapFactory.decodeFile(photo.absolutePath) else null
    if (bg == null) canvas.drawColor(Color.rgb(13, 14, 26)) else {
        val scale = max(w.toFloat() / bg.width, ht.toFloat() / bg.height)
        val bw = bg.width * scale
        val bh = bg.height * scale
        canvas.drawBitmap(bg, null, RectF((w-bw)/2, (ht-bh)/2, (w+bw)/2, (ht+bh)/2), Paint(3))
        bg.recycle()
    }
    canvas.drawRect(0f, 0f, w.toFloat(), ht.toFloat(), Paint().apply { color = 0xB8000000.toInt() })
    val heading = Paint(3).apply { color = Color.WHITE; textSize = 62f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    val accent = Paint(3).apply { color = Color.rgb(183,156,255); textSize = 40f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    val body = Paint(3).apply { color = Color.LTGRAY; textSize = 34f }
    var y = 150f
    canvas.drawText("SRMATTA SUPER TREINO", 70f, y, accent)
    y += 125f
    canvas.drawText((h.kind ?: "Treino livre").take(26), 70f, y, heading)
    y += 72f
    canvas.drawText(SimpleDateFormat("dd/MM/yyyy · HH:mm", PT).format(Date(h.started)), 70f, y, body)
    y += 145f
    canvas.drawText("${fmtThousands(h.volume)} kg", 70f, y, accent)
    y += 55f
    canvas.drawText("VOLUME TOTAL", 70f, y, body)
    y += 115f
    canvas.drawText("Duração: ${fmtDuration(h.ended-h.started)}", 70f, y, heading.apply { textSize = 42f })
    y += 70f
    canvas.drawText("${h.exercises} exercícios · ${h.sets} séries · ${sets.sumOf { it.reps }} reps", 70f, y, body)
    y += 130f
    canvas.drawText("CARGAS UTILIZADAS", 70f, y, accent)
    y += 65f
    sets.groupBy { it.exercise }.forEach { (name, rows) ->
        if (y < ht - 85) {
            canvas.drawText("${name.take(28)} — ${fmtNum(rows.maxOf { it.weight })} kg", 70f, y, body)
            y += 54f
        }
    }
    canvas.drawText("Registrado no SrMatta Super Treino", 70f, ht - 38f, body.apply { textSize = 24f })
    return bmp
}

private fun MainActivity.shareWorkoutCard(id: Long) {
    try {
        val h = db.summary(id) ?: throw IllegalStateException()
        val bmp = makeWorkoutCard(h, db.setsOf(id))
        val file = File(cacheDir, "treino_$id.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, "Cartão do treino", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Compartilhar treino"))
    } catch (_: Exception) { toast("Não consegui preparar o cartão") }
}
