package br.srmatta.treino

/** Grupos musculares e planos prontos. Tudo em código: nenhum arquivo extra, nenhum custo de memória. */
object Muscles {
    val ALL = listOf("Peito", "Costas", "Ombros", "Bíceps", "Tríceps", "Pernas", "Glúteos", "Abdômen")

    private val DEFAULT = mapOf(
        "Supino reto" to "Peito", "Supino inclinado" to "Peito", "Crucifixo" to "Peito", "Flexão de braço" to "Peito",
        "Desenvolvimento" to "Ombros", "Elevação lateral" to "Ombros",
        "Tríceps pulley" to "Tríceps", "Tríceps testa" to "Tríceps",
        "Puxada frontal" to "Costas", "Remada curvada" to "Costas", "Remada baixa" to "Costas",
        "Barra fixa" to "Costas", "Levantamento terra" to "Costas",
        "Rosca direta" to "Bíceps", "Rosca martelo" to "Bíceps",
        "Agachamento" to "Pernas", "Leg press" to "Pernas", "Cadeira extensora" to "Pernas",
        "Mesa flexora" to "Pernas", "Stiff" to "Pernas", "Avanço" to "Pernas", "Panturrilha em pé" to "Pernas",
        "Elevação pélvica" to "Glúteos",
        "Abdominal" to "Abdômen"
    )

    /** Músculo de um exercício: primeiro o que o usuário escolheu, depois a tabela padrão. */
    fun of(exercise: String, custom: Map<String, String>): String? =
        custom.entries.firstOrNull { it.key.equals(exercise, ignoreCase = true) }?.value
            ?: DEFAULT.entries.firstOrNull { it.key.equals(exercise, ignoreCase = true) }?.value
}

data class PlanDay(val name: String, val exercises: List<String>)
data class Plan(val name: String, val about: String, val days: List<PlanDay>)

val PLANS = listOf(
    Plan(
        "ABC", "3 treinos: peito e tríceps, costas e bíceps, pernas e ombros.",
        listOf(
            PlanDay("ABC · A (Peito e Tríceps)", listOf("Supino reto", "Supino inclinado", "Crucifixo", "Tríceps pulley", "Tríceps testa")),
            PlanDay("ABC · B (Costas e Bíceps)", listOf("Puxada frontal", "Remada curvada", "Remada baixa", "Rosca direta", "Rosca martelo")),
            PlanDay("ABC · C (Pernas e Ombros)", listOf("Agachamento", "Leg press", "Cadeira extensora", "Mesa flexora", "Panturrilha em pé", "Desenvolvimento", "Elevação lateral"))
        )
    ),
    Plan(
        "Full Body", "1 treino para o corpo todo, ideal para 2 a 3 vezes por semana.",
        listOf(
            PlanDay("Full Body", listOf("Agachamento", "Supino reto", "Remada curvada", "Desenvolvimento", "Rosca direta", "Tríceps pulley", "Abdominal"))
        )
    ),
    Plan(
        "Push / Pull / Legs", "Empurrar, puxar e pernas, em 3 treinos.",
        listOf(
            PlanDay("PPL · Push", listOf("Supino reto", "Supino inclinado", "Desenvolvimento", "Elevação lateral", "Tríceps pulley")),
            PlanDay("PPL · Pull", listOf("Puxada frontal", "Remada curvada", "Remada baixa", "Rosca direta", "Rosca martelo")),
            PlanDay("PPL · Legs", listOf("Agachamento", "Leg press", "Mesa flexora", "Stiff", "Panturrilha em pé"))
        )
    )
)
