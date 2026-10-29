package br.srmatta.treino

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private data class NutritionTargets(
    val age: Int, val weight: Double, val height: Int, val bmr: Double,
    val maintenance: Double, val calories: Int, val protein: Int, val carbs: Int,
    val fat: Int, val goal: String
)

private fun MainActivity.nutritionAge(): Int? {
    val raw = prefs.getString("birth", "")?.filter { it.isDigit() } ?: return null
    if (raw.length != 8) return null
    val date = try {
        SimpleDateFormat("ddMMyyyy", Locale("pt", "BR")).apply { isLenient = false }.parse(raw)
    } catch (_: Exception) { null } ?: return null
    val birth = Calendar.getInstance().apply { time = date }
    val now = Calendar.getInstance()
    var age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
    return age.takeIf { it in 18..100 }
}

private fun MainActivity.targets(): NutritionTargets? {
    val age = nutritionAge() ?: return null
    val height = prefs.getInt("height", 0).takeIf { it in 80..250 } ?: return null
    val weight = db.bodyAll().lastOrNull()?.kg ?: return null
    val sex = prefs.getString("sex", "Prefiro não informar") ?: "Prefiro não informar"
    val sexFactor = when (sex) {
        "Masculino" -> 5.0
        "Feminino" -> -161.0
        else -> -78.0
    }
    val bmr = 10.0 * weight + 6.25 * height - 5.0 * age + sexFactor
    val activity = prefs.getString("activity", "") ?: ""
    val factor = when {
        activity.startsWith("Leve") -> 1.375
        activity.startsWith("Moderado") -> 1.55
        activity.startsWith("Alto") -> 1.725
        activity.startsWith("Muito alto") -> 1.9
        else -> 1.2
    }
    val maintenance = bmr * factor
    val goal = prefs.getString("goal", "Perder gordura") ?: "Perder gordura"
    val calories = when (goal) {
        "Ganhar massa muscular" -> (maintenance * 1.10).roundToInt()
        "Recomposição corporal" -> (maintenance * 0.95).roundToInt()
        "Manter o peso" -> maintenance.roundToInt()
        else -> (maintenance * 0.85).roundToInt()
    }.coerceAtLeast(1200)
    val proteinPerKg = if (goal == "Perder gordura" || goal == "Recomposição corporal") 2.0 else 1.8
    val protein = (weight * proteinPerKg).roundToInt()
    val fat = (weight * 0.8).roundToInt()
    val carbs = ((calories - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(50)
    return NutritionTargets(age, weight, height, bmr, maintenance, calories, protein, carbs, fat, goal)
}

fun MainActivity.nutritionInto(container: LinearLayout) {
    container.addView(card().apply {
        addView(label("NUTRIÇÃO PERSONALIZADA", 16f, C.blueLight, true))
        addView(label("Estimativas iniciais calculadas a partir do seu perfil, peso e atividade.", 13f, C.muted), params(t = 6))
        val t = targets()
        if (t == null) {
            addView(label("Para calcular suas metas, preencha a data de nascimento, sexo (se quiser uma estimativa mais específica), altura e registre seu peso na aba Medidas.", 14f, C.text), params(t = 12))
            addView(primaryButton("Completar perfil") { editProfile() }, params(t = 12))
            addView(secondaryButton("Registrar peso") { homeTab = 1; show(HOME) }, params(t = 8))
        } else {
            addView(label(t.goal, 20f, C.purpleLight, true), params(t = 12))
            addView(label("${t.calories} kcal/dia", 30f, C.text, true), params(t = 4))
            addView(label("Metabolismo basal estimado: ${t.bmr.roundToInt()} kcal · Manutenção: ${t.maintenance.roundToInt()} kcal", 12f, C.muted), params(t = 5))
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            fun macro(title: String, amount: Int, color: Int): LinearLayout = card().apply {
                addView(label(title, 12f, C.muted))
                addView(label("${amount} g", 22f, color, true), params(t = 4))
            }
            row.addView(macro("Proteína", t.protein, C.blueLight), params(w = 0, weight = 1f, r = 5))
            row.addView(macro("Carboidrato", t.carbs, C.purpleLight), params(w = 0, weight = 1f, l = 5, r = 5))
            row.addView(macro("Gordura", t.fat, C.ok), params(w = 0, weight = 1f, l = 5))
            addView(row, params(t = 12))
            addView(label("IMC: ${fmtNum(t.weight / ((t.height / 100.0) * (t.height / 100.0)))} · Peso usado: ${fmtNum(t.weight)} kg", 13f, C.muted), params(t = 10))
            addView(label("As calorias e os macros são estimativas, não prescrição clínica. Ajuste conforme evolução, fome e orientação profissional.", 11f, C.muted), params(t = 10))
        }
    }, params(b = 12))

    container.addView(card().apply {
        addView(label("DIETA ECONÔMICA — EXEMPLO", 16f, C.blueLight, true))
        addView(label("Use as metas acima para ajustar as porções. Os alimentos abaixo são sugestões, não uma dieta obrigatória.", 12f, C.muted), params(t = 5))
        val meals = listOf(
            "Refeição 1" to "Ovos + aveia ou pão + fruta",
            "Refeição 2" to "Arroz + feijão + frango, moela ou fígado + legumes",
            "Refeição 3" to "Soja texturizada ou sardinha + arroz/batata + verduras",
            "Lanches opcionais" to "Iogurte natural, leite, fruta ou ovos, conforme a fome e as metas"
        )
        meals.forEach { (title, body) ->
            addView(label(title, 14f, C.text, true), params(t = 12))
            addView(label(body, 13f, C.muted), params(t = 3))
        }
        addView(label("Priorize variedade, frutas, legumes e fibras. Se tiver doença, restrições alimentares ou histórico de transtorno alimentar, procure nutricionista antes de seguir metas restritivas.", 11f, C.muted), params(t = 12))
    }, params(b = 12))

    container.addView(card().apply {
        addView(label("TREINO PARA SEU OBJETIVO", 16f, C.blueLight, true))
        addView(label("Gera rotinas prontas na aba Rotinas, usando seu objetivo e a frequência semanal selecionada no perfil.", 13f, C.muted), params(t = 5))
        addView(primaryButton("Gerar meu treino") { generateGoalWorkout() }, params(t = 12))
        addView(secondaryButton("Ver minhas rotinas") { show(1) }, params(t = 8))
    })
}

private fun MainActivity.generateGoalWorkout() {
    val days = prefs.getInt("training_days", 3).coerceIn(2, 6)
    val goal = prefs.getString("goal", "Perder gordura") ?: "Perder gordura"
    val generated = when {
        days <= 2 -> listOf(PlanDay("Corpo inteiro A", PLANS.first { it.name.startsWith("Iniciante") }.days[0].exercises),
                            PlanDay("Corpo inteiro B", PLANS.first { it.name.startsWith("Iniciante") }.days[1].exercises))
        days == 3 -> PLANS.first { it.name == "ABC" }.days
        days == 4 -> listOf(
            PlanDay("Superior A", listOf("Supino reto", "Remada baixa", "Desenvolvimento com halteres", "Puxada frontal", "Rosca direta", "Tríceps corda")),
            PlanDay("Inferior A", listOf("Agachamento", "Leg press", "Mesa flexora", "Panturrilha em pé", "Abdominal")),
            PlanDay("Superior B", listOf("Supino inclinado com halteres", "Remada unilateral", "Elevação lateral", "Puxada triângulo", "Rosca martelo", "Tríceps testa")),
            PlanDay("Inferior B", listOf("Stiff", "Cadeira extensora", "Cadeira flexora", "Elevação pélvica", "Panturrilha sentado"))
        )
        else -> PLANS.first { it.name == "Upper / Lower + Push / Pull / Legs" }.days
    }
    db.routines().filter { it.name.startsWith("SrMatta Auto ·") }.forEach { db.deleteRoutine(it.id) }
    generated.take(days).forEachIndexed { index, day ->
        db.saveRoutine(null, "SrMatta Auto · ${goal} · ${day.name}", day.exercises)
    }
    toast("Foram criadas ${generated.take(days).size} rotinas. Abra a aba Rotinas.")
    show(1)
}

fun MainActivity.supplementsInto(container: LinearLayout) {
    container.addView(card().apply {
        addView(label("SUPLEMENTOS MAIS BARATOS", 16f, C.blueLight, true))
        addView(label("Pesquise preços atuais na web. O app não inventa valores: confira frete, vendedor, validade e laudo do produto antes de comprar.", 13f, C.muted), params(t = 6))
        val products = listOf(
            Triple("Whey protein", "whey protein 900g preço", "Pesquisar whey"),
            Triple("Creatina monohidratada", "creatina monohidratada 300g preço", "Pesquisar creatina"),
            Triple("Proteína por real", "comparar custo proteína whey ovos frango soja sardinha", "Comparar custo"),
            Triple("Lojas e ofertas", "ofertas whey protein creatina lojas confiáveis Brasil", "Ver ofertas")
        )
        products.forEach { (title, query, button) ->
            addView(label(title, 15f, C.text, true), params(t = 14))
            addView(secondaryButton(button) { openSearch(query) }, params(t = 6))
        }
        addView(label("Dica: compare o custo por 25 g de proteína no whey e o preço por 3–5 g na creatina. Suplemento é opcional; primeiro veja se consegue cumprir a meta com comida.", 12f, C.ok), params(t = 14))
        addView(label("Segurança: evite produtos sem procedência, promessas milagrosas ou misturas sem rótulo claro. Se tiver doença renal, estiver grávida ou usar medicamentos, consulte profissional de saúde antes de suplementar.", 11f, C.muted), params(t = 10))
    }, params())
}

private fun MainActivity.openSearch(query: String) {
    try {
        val url = "https://www.google.com/search?tbm=shop&q=" + Uri.encode(query)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        toast("Não foi possível abrir a pesquisa")
    }
}
