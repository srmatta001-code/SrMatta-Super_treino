package br.srmatta.treino

import android.widget.LinearLayout
import java.util.Locale

data class Suggestion(val weight: Double, val reps: Int, val note: String)
data class TodayPick(val routine: Routine, val reason: String)

/**
 * Treinador SrMatta: regras simples de treino aplicadas ao seu próprio histórico.
 * Não é IA de verdade e não usa internet: tudo é calculado no aparelho.
 */
object Coach {
    private const val DAY = 24L * 3600 * 1000
    private const val NEVER = 9999

    private fun kg(d: Double): String =
        String.format(Locale("pt", "BR"), "%.2f", d).trimEnd('0').trimEnd(',')

    private fun days(n: Int): String = if (n == 1) "1 dia" else "$n dias"

    /**
     * Progressão dupla (faixa de 8 a 12 repetições):
     * todas as séries na carga mais alta com 12+ reps → sobe a carga;
     * entre 8 e 11 → mesma carga, uma repetição a mais; abaixo de 8 → consolidar 8 reps.
     * [last] são as séries (repetições, carga) do último treino do exercício.
     */
    fun suggest(last: List<Pair<Int, Double>>, muscle: String?): Suggestion? {
        if (last.isEmpty()) return null
        val top = last.maxOf { it.second }
        val minReps = last.filter { it.second == top }.minOf { it.first }
        if (top == 0.0) {
            return Suggestion(0.0, minReps + 1, "Peso do corpo: tente ${minReps + 1} repetições em cada série.")
        }
        val step = if ((muscle == "Pernas" || muscle == "Costas") && top >= 60.0) 5.0 else 2.5
        return when {
            minReps >= 12 -> Suggestion(
                top + step, 8,
                "Você fez $minReps repetições em todas as séries com ${kg(top)} kg. Hora de subir a carga."
            )
            minReps >= 8 -> Suggestion(
                top, minReps + 1,
                "Mantenha ${kg(top)} kg e tente 1 repetição a mais em cada série."
            )
            else -> Suggestion(
                top, 8,
                "Fique em ${kg(top)} kg até chegar em 8 repetições em todas as séries."
            )
        }
    }

    /** Rotina de hoje: a que trabalha os músculos há mais tempo sem treino (ignora as já feitas hoje). */
    fun pickToday(
        routines: List<Routine>, lastByMuscle: Map<String, Long>, doneToday: Set<String>,
        custom: Map<String, String>, now: Long
    ): TodayPick? {
        var best: TodayPick? = null
        var bestScore = -1.0
        for (r in routines) {
            if (r.name in doneToday) continue
            val muscles = r.exercises.mapNotNull { Muscles.of(it, custom) }.distinct()
            if (muscles.isEmpty()) continue
            val ds = muscles.map { m -> lastByMuscle[m]?.let { ((now - it) / DAY).toInt() } ?: NEVER }
            val capped = ds.map { if (it == NEVER) 30 else it }
            val score = capped.average()
            if (score > bestScore) {
                bestScore = score
                val i = ds.indexOf(ds.max())
                val worst = muscles[i]
                val why = when {
                    ds[i] == NEVER -> "$worst ainda não foi treinado."
                    ds[i] == 0 -> "Todos os músculos dessa rotina foram treinados hoje."
                    else -> "$worst está há ${days(ds[i])} sem treino."
                }
                best = TodayPick(r, why)
            }
        }
        return best
    }

    /** Músculos parados há 7 dias ou mais (ou nunca treinados), do mais esquecido para o menos. */
    fun neglected(lastByMuscle: Map<String, Long>, now: Long, minDays: Int = 7): List<Pair<String, Int>> =
        Muscles.ALL
            .map { m -> m to (lastByMuscle[m]?.let { ((now - it) / DAY).toInt() } ?: NEVER) }
            .filter { it.second >= minDays }
            .sortedByDescending { it.second }

    fun neglectedText(m: String, d: Int): String =
        if (d == NEVER) "$m: ainda não treinado" else "$m: ${days(d)} sem treinar"
}

/** Cartão do Treinador SrMatta: treino do dia e músculos esquecidos. */
fun MainActivity.coachCard(): LinearLayout {
    val c = card()
    c.addView(label("Treinador SrMatta", 16f, C.purpleLight, true))
    val now = System.currentTimeMillis()
    val custom = db.customMuscles()
    val last = HashMap<String, Long>()
    for ((ex, ms) in db.lastTrainedByExercise()) {
        val m = Muscles.of(ex, custom) ?: continue
        if (ms > (last[m] ?: 0L)) last[m] = ms
    }
    if (last.isEmpty()) {
        c.addView(
            label("Finalize seu primeiro treino e eu começo a sugerir o que treinar e quanto de carga usar.", 13f, C.muted),
            params(t = 6)
        )
        return c
    }

    val pick = Coach.pickToday(db.routines(), last, db.kindsSince(startOfDay(now)), custom, now)
    if (pick != null) {
        c.addView(label("Hoje: ${pick.routine.name}", 15f, C.text, true), params(t = 8))
        c.addView(label(pick.reason, 13f, C.muted), params(t = 2))
        if (db.openSession() == null) {
            c.addView(smallButton("Iniciar essa rotina") { startRoutine(pick.routine) }, params(w = WRAP, t = 10))
        }
    }

    val late = Coach.neglected(last, now).take(3)
    if (late.isEmpty()) {
        c.addView(label("Todos os músculos foram treinados nos últimos 7 dias. Boa!", 13f, C.ok), params(t = 12))
    } else {
        c.addView(label("Atenção", 13f, C.warn, true), params(t = 12))
        for ((m, d) in late) c.addView(label("• " + Coach.neglectedText(m, d), 13f, C.text), params(t = 2))
    }
    return c
}
