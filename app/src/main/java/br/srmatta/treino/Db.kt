package br.srmatta.treino

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Session(val id: Long, val started: Long)
data class SetRow(val id: Long, val sessionId: Long, val exercise: String, val reps: Int, val weight: Double)
data class Summary(
    val id: Long, val started: Long, val ended: Long,
    val sets: Int, val volume: Double, val exercises: Int, val names: String, val kind: String?
)
data class Totals(val volume: Double, val timeMs: Long, val sessions: Int, val sets: Int)
data class ExStat(val name: String, val sets: Int, val best: Double, val sessions: Int)
data class BodyRow(val id: Long, val day: Long, val kg: Double)
data class Routine(val id: Long, val name: String, val exercises: List<String>)

/** Banco local (SQLite do próprio Android): tudo funciona sem internet e sem conta. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "srmatta.db", null, 3) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE session(id INTEGER PRIMARY KEY AUTOINCREMENT, started INTEGER NOT NULL, ended INTEGER)")
        db.execSQL(
            "CREATE TABLE sets(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "session_id INTEGER NOT NULL REFERENCES session(id) ON DELETE CASCADE, " +
                "exercise TEXT NOT NULL, reps INTEGER NOT NULL, weight REAL NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_sets_session ON sets(session_id)")
        db.execSQL("CREATE INDEX idx_sets_exercise ON sets(exercise)")
        db.execSQL("CREATE TABLE body(id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER NOT NULL, kg REAL NOT NULL)")
        createV2(db)
        createV3(db)
    }

    /** Versão 3: tipo do treino (nome da rotina) e medidas do corpo. */
    private fun createV3(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE session ADD COLUMN kind TEXT")
        db.execSQL("CREATE TABLE measure(id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER NOT NULL, kind TEXT NOT NULL, cm REAL NOT NULL)")
        db.execSQL("CREATE INDEX idx_measure_kind ON measure(kind)")
    }

    /** Tabelas da versão 2: rotinas e músculo escolhido para exercícios personalizados. */
    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE routine(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE routine_ex(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "routine_id INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE, " +
                "exercise TEXT NOT NULL, pos INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_routine_ex ON routine_ex(routine_id)")
        db.execSQL("CREATE TABLE exmuscle(exercise TEXT PRIMARY KEY, muscle TEXT NOT NULL)")
    }

    // Os dados da versão 1 (treinos, séries, peso) ficam intactos; só entram tabelas novas.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createV2(db)
        if (oldVersion < 3) createV3(db)
    }

    private inline fun <T> query(sql: String, args: Array<String> = emptyArray(), read: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(read(c))
        }
        return out
    }

    // ---------- Treinos ----------

    fun openSession(): Session? =
        query("SELECT id, started FROM session WHERE ended IS NULL ORDER BY id DESC LIMIT 1") {
            Session(it.getLong(0), it.getLong(1))
        }.firstOrNull()

    fun startSession(kind: String? = null): Long =
        writableDatabase.insert("session", null, ContentValues().apply {
            put("started", System.currentTimeMillis())
            if (kind != null) put("kind", kind)
        })

    /** Finaliza o treino; se não tiver nenhuma série, descarta. */
    fun endSession(id: Long) {
        val n = query("SELECT COUNT(*) FROM sets WHERE session_id=?", arrayOf(id.toString())) { it.getInt(0) }.first()
        if (n == 0) {
            deleteSession(id)
        } else {
            writableDatabase.update(
                "session",
                ContentValues().apply { put("ended", System.currentTimeMillis()) },
                "id=?", arrayOf(id.toString())
            )
        }
    }

    fun deleteSession(id: Long) {
        writableDatabase.delete("session", "id=?", arrayOf(id.toString()))
    }

    // ---------- Séries ----------

    fun addSet(sessionId: Long, exercise: String, reps: Int, weight: Double) {
        writableDatabase.insert("sets", null, ContentValues().apply {
            put("session_id", sessionId)
            put("exercise", exercise)
            put("reps", reps)
            put("weight", weight)
        })
    }

    fun deleteSet(id: Long) {
        writableDatabase.delete("sets", "id=?", arrayOf(id.toString()))
    }

    private fun readSet(c: Cursor) =
        SetRow(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getDouble(4))

    fun setsOf(sessionId: Long): List<SetRow> =
        query(
            "SELECT id, session_id, exercise, reps, weight FROM sets WHERE session_id=? ORDER BY id",
            arrayOf(sessionId.toString())
        ) { readSet(it) }

    fun lastSetFor(exercise: String): SetRow? =
        query(
            "SELECT id, session_id, exercise, reps, weight FROM sets WHERE exercise=? ORDER BY id DESC LIMIT 1",
            arrayOf(exercise)
        ) { readSet(it) }.firstOrNull()

    /** Exercícios já usados, do mais recente para o mais antigo. */
    fun recentExercises(): List<String> =
        query("SELECT exercise FROM sets GROUP BY exercise ORDER BY MAX(id) DESC") { it.getString(0) }

    fun exerciseNames(): List<String> =
        query("SELECT DISTINCT exercise FROM sets ORDER BY exercise COLLATE NOCASE") { it.getString(0) }

    // ---------- Histórico ----------

    fun summary(id: Long): Summary? =
        query(
            "SELECT s.id, s.started, s.ended, COUNT(t.id), COALESCE(SUM(t.reps * t.weight), 0), " +
                "COUNT(DISTINCT t.exercise), COALESCE(GROUP_CONCAT(DISTINCT t.exercise), ''), s.kind " +
                "FROM session s LEFT JOIN sets t ON t.session_id = s.id " +
                "WHERE s.id=? AND s.ended IS NOT NULL GROUP BY s.id",
            arrayOf(id.toString())
        ) {
            Summary(it.getLong(0), it.getLong(1), it.getLong(2), it.getInt(3), it.getDouble(4),
                it.getInt(5), it.getString(6), if (it.isNull(7)) null else it.getString(7))
        }.firstOrNull()

    fun history(limit: Int = 200): List<Summary> =
        query(
            "SELECT s.id, s.started, s.ended, COUNT(t.id), COALESCE(SUM(t.reps * t.weight), 0), " +
                "COUNT(DISTINCT t.exercise), COALESCE(GROUP_CONCAT(DISTINCT t.exercise), ''), s.kind " +
                "FROM session s LEFT JOIN sets t ON t.session_id = s.id " +
                "WHERE s.ended IS NOT NULL GROUP BY s.id ORDER BY s.started DESC LIMIT $limit"
        ) {
            Summary(it.getLong(0), it.getLong(1), it.getLong(2), it.getInt(3), it.getDouble(4), it.getInt(5), it.getString(6),
                if (it.isNull(7)) null else it.getString(7))
        }

    fun countSince(ms: Long): Int =
        query("SELECT COUNT(*) FROM session WHERE ended IS NOT NULL AND started >= ?", arrayOf(ms.toString())) {
            it.getInt(0)
        }.first()

    /** Maior carga de cada treino para um exercício: (data, kg). */
    fun loadSeries(exercise: String): List<Pair<Long, Double>> =
        query(
            "SELECT s.started, MAX(t.weight) FROM sets t JOIN session s ON s.id = t.session_id " +
                "WHERE t.exercise = ? GROUP BY s.id ORDER BY s.started",
            arrayOf(exercise)
        ) { it.getLong(0) to it.getDouble(1) }

    // ---------- Treinador ----------

    /** Séries (repetições, carga) do último treino finalizado que teve o exercício. */
    fun lastSessionSets(exercise: String): List<Pair<Int, Double>> =
        query(
            "SELECT t.reps, t.weight FROM sets t JOIN session s ON s.id = t.session_id " +
                "WHERE t.exercise = ? COLLATE NOCASE AND s.ended IS NOT NULL AND s.id = (" +
                "SELECT MAX(s2.id) FROM session s2 JOIN sets t2 ON t2.session_id = s2.id " +
                "WHERE t2.exercise = ? COLLATE NOCASE AND s2.ended IS NOT NULL) ORDER BY t.id",
            arrayOf(exercise, exercise)
        ) { it.getInt(0) to it.getDouble(1) }

    /** Data do último treino de cada exercício. */
    fun lastTrainedByExercise(): List<Pair<String, Long>> =
        query(
            "SELECT t.exercise, MAX(s.started) FROM sets t JOIN session s ON s.id = t.session_id " +
                "WHERE s.ended IS NOT NULL GROUP BY t.exercise"
        ) { it.getString(0) to it.getLong(1) }

    /** Tipos (nomes de rotina) de treinos finalizados desde a data. */
    fun kindsSince(ms: Long): Set<String> =
        query(
            "SELECT DISTINCT kind FROM session WHERE ended IS NOT NULL AND kind IS NOT NULL AND started >= ?",
            arrayOf(ms.toString())
        ) { it.getString(0) }.toSet()

    // ---------- Estatísticas gerais ----------

    fun totals(): Totals {
        val a = query(
            "SELECT COALESCE(SUM(t.reps * t.weight), 0), COUNT(*) FROM sets t " +
                "JOIN session s ON s.id = t.session_id WHERE s.ended IS NOT NULL"
        ) { it.getDouble(0) to it.getInt(1) }.first()
        val b = query(
            "SELECT COUNT(*), COALESCE(SUM(ended - started), 0) FROM session WHERE ended IS NOT NULL"
        ) { it.getInt(0) to it.getLong(1) }.first()
        return Totals(a.first, b.second, b.first, a.second)
    }

    /** Quantos treinos de cada tipo (rotina). Treino sem rotina = "Livre". */
    fun kindCounts(): List<Pair<String, Int>> =
        query(
            "SELECT COALESCE(kind, 'Livre'), COUNT(*) FROM session WHERE ended IS NOT NULL " +
                "GROUP BY COALESCE(kind, 'Livre') ORDER BY COUNT(*) DESC"
        ) { it.getString(0) to it.getInt(1) }

    fun exerciseStats(): List<ExStat> =
        query(
            "SELECT t.exercise, COUNT(*), MAX(t.weight), COUNT(DISTINCT t.session_id) FROM sets t " +
                "JOIN session s ON s.id = t.session_id WHERE s.ended IS NOT NULL " +
                "GROUP BY t.exercise ORDER BY COUNT(*) DESC"
        ) { ExStat(it.getString(0), it.getInt(1), it.getDouble(2), it.getInt(3)) }

    // ---------- Medidas do corpo ----------

    /** Uma medida por dia e por tipo: salvar de novo no mesmo dia substitui. */
    fun addMeasure(kind: String, cm: Double) {
        val day = startOfDay(System.currentTimeMillis())
        val w = writableDatabase
        w.delete("measure", "day=? AND kind=?", arrayOf(day.toString(), kind))
        w.insert("measure", null, ContentValues().apply {
            put("day", day)
            put("kind", kind)
            put("cm", cm)
        })
    }

    fun measuresOf(kind: String): List<Pair<Long, Double>> =
        query("SELECT day, cm FROM measure WHERE kind=? ORDER BY day", arrayOf(kind)) {
            it.getLong(0) to it.getDouble(1)
        }

    // ---------- Calendário e músculos ----------

    /** Dias do mês (1..31) em que houve treino finalizado. */
    fun trainedDays(fromMs: Long, toMs: Long): Set<Int> {
        val cal = java.util.Calendar.getInstance()
        return query(
            "SELECT started FROM session WHERE ended IS NOT NULL AND started >= ? AND started < ?",
            arrayOf(fromMs.toString(), toMs.toString())
        ) {
            cal.timeInMillis = it.getLong(0)
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        }.toSet()
    }

    /** Séries por exercício em treinos finalizados desde a data. */
    fun setsByExerciseSince(ms: Long): List<Pair<String, Int>> =
        query(
            "SELECT t.exercise, COUNT(*) FROM sets t JOIN session s ON s.id = t.session_id " +
                "WHERE s.ended IS NOT NULL AND s.started >= ? GROUP BY t.exercise",
            arrayOf(ms.toString())
        ) { it.getString(0) to it.getInt(1) }

    fun customMuscles(): Map<String, String> =
        query("SELECT exercise, muscle FROM exmuscle") { it.getString(0) to it.getString(1) }.toMap()

    fun setMuscle(exercise: String, muscle: String) {
        writableDatabase.insertWithOnConflict("exmuscle", null, ContentValues().apply {
            put("exercise", exercise)
            put("muscle", muscle)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- Rotinas ----------

    fun routines(): List<Routine> {
        val byId = LinkedHashMap<Long, Pair<String, MutableList<String>>>()
        query("SELECT id, name FROM routine ORDER BY id") { byId[it.getLong(0)] = it.getString(1) to mutableListOf<String>() }
        query("SELECT routine_id, exercise FROM routine_ex ORDER BY routine_id, pos") {
            byId[it.getLong(0)]?.second?.add(it.getString(1))
        }
        return byId.map { (id, v) -> Routine(id, v.first, v.second) }
    }

    fun routine(id: Long): Routine? = routines().firstOrNull { it.id == id }

    /** Cria (id = null) ou atualiza uma rotina, mantendo a ordem dos exercícios. */
    fun saveRoutine(id: Long?, name: String, exercises: List<String>) {
        val w = writableDatabase
        w.beginTransaction()
        try {
            val rid = if (id == null) {
                w.insert("routine", null, ContentValues().apply { put("name", name) })
            } else {
                w.update("routine", ContentValues().apply { put("name", name) }, "id=?", arrayOf(id.toString()))
                w.delete("routine_ex", "routine_id=?", arrayOf(id.toString()))
                id
            }
            exercises.forEachIndexed { i, ex ->
                w.insert("routine_ex", null, ContentValues().apply {
                    put("routine_id", rid)
                    put("exercise", ex)
                    put("pos", i)
                })
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun deleteRoutine(id: Long) {
        writableDatabase.delete("routine", "id=?", arrayOf(id.toString()))
    }

    // ---------- Peso corporal ----------

    /** Uma medição por dia: salvar de novo no mesmo dia substitui. */
    fun addBody(kg: Double) {
        val day = startOfDay(System.currentTimeMillis())
        val w = writableDatabase
        w.delete("body", "day=?", arrayOf(day.toString()))
        w.insert("body", null, ContentValues().apply {
            put("day", day)
            put("kg", kg)
        })
    }

    fun bodyAll(): List<BodyRow> =
        query("SELECT id, day, kg FROM body ORDER BY day") { BodyRow(it.getLong(0), it.getLong(1), it.getDouble(2)) }

    fun deleteBody(id: Long) {
        writableDatabase.delete("body", "id=?", arrayOf(id.toString()))
    }
}
