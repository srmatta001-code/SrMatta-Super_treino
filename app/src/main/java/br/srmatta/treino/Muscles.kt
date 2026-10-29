package br.srmatta.treino

/** Grupos musculares, biblioteca de exercícios e planos prontos. Tudo em código: sem arquivos extras. */
object Muscles {
    val ALL = listOf("Peito", "Costas", "Ombros", "Bíceps", "Tríceps", "Pernas", "Glúteos", "Abdômen")

    private val LIBRARY: List<Pair<String, List<String>>> = listOf(
        "Peito" to listOf(
            "Supino reto", "Supino inclinado", "Supino declinado", "Supino reto com halteres",
            "Supino inclinado com halteres", "Crucifixo", "Voador", "Crossover", "Flexão de braço", "Paralelas"
        ),
        "Costas" to listOf(
            "Puxada frontal", "Puxada triângulo", "Barra fixa", "Remada curvada", "Remada baixa",
            "Remada unilateral", "Remada cavalinho", "Levantamento terra", "Crucifixo invertido",
            "Face pull", "Encolhimento"
        ),
        "Ombros" to listOf(
            "Desenvolvimento", "Desenvolvimento com halteres", "Arnold press", "Elevação lateral",
            "Elevação lateral inclinado", "Elevação frontal"
        ),
        "Bíceps" to listOf(
            "Rosca direta", "Rosca alternada", "Rosca martelo", "Rosca concentrada", "Rosca scott", "Rosca inversa"
        ),
        "Tríceps" to listOf(
            "Tríceps pulley", "Tríceps corda", "Tríceps testa", "Rosca francesa", "Tríceps francês",
            "Mergulho no banco", "Tríceps coice"
        ),
        "Pernas" to listOf(
            "Agachamento", "Agachamento frontal", "Agachamento hack", "Leg press", "Cadeira extensora",
            "Mesa flexora", "Cadeira flexora", "Stiff", "Avanço", "Passada", "Afundo búlgaro",
            "Cadeira abdutora", "Cadeira adutora", "Panturrilha em pé", "Panturrilha sentado"
        ),
        "Glúteos" to listOf("Elevação pélvica", "Glúteo na polia", "Glúteo quatro apoios"),
        "Abdômen" to listOf(
            "Abdominal", "Abdominal infra", "Elevação de pernas", "Prancha", "Abdominal na polia"
        )
    )

    /** Todos os exercícios, agrupados por músculo, na ordem da biblioteca. */
    val NAMES: List<String> = LIBRARY.flatMap { it.second }

    private val DEFAULT: Map<String, String> =
        LIBRARY.flatMap { (m, list) -> list.map { it to m } }.toMap()

    /** Músculo de um exercício: primeiro o que o usuário escolheu, depois a biblioteca. */
    fun of(exercise: String, custom: Map<String, String>): String? =
        custom.entries.firstOrNull { it.key.equals(exercise, ignoreCase = true) }?.value
            ?: DEFAULT.entries.firstOrNull { it.key.equals(exercise, ignoreCase = true) }?.value
}

data class PlanDay(val name: String, val exercises: List<String>)
data class Plan(val name: String, val about: String, val days: List<PlanDay>)

val PLANS = listOf(
    Plan(
        "Iniciante · Corpo inteiro A/B", "2 treinos alternados, o corpo todo em cada um. Bom para 3 dias por semana.",
        listOf(
            PlanDay("Iniciante · A", listOf(
                "Supino reto com halteres", "Puxada frontal", "Agachamento", "Cadeira flexora",
                "Desenvolvimento", "Panturrilha em pé", "Abdominal"
            )),
            PlanDay("Iniciante · B", listOf(
                "Supino inclinado com halteres", "Remada baixa", "Leg press", "Stiff",
                "Elevação lateral", "Panturrilha sentado", "Elevação de pernas"
            ))
        )
    ),
    Plan(
        "Upper / Lower + Push / Pull / Legs", "5 dias: superiores, inferiores, empurrar, puxar e pernas.",
        listOf(
            PlanDay("U/L+PPL · Upper", listOf(
                "Supino reto", "Remada curvada", "Desenvolvimento", "Puxada frontal", "Voador", "Rosca direta", "Rosca francesa"
            )),
            PlanDay("U/L+PPL · Lower", listOf(
                "Agachamento", "Leg press", "Stiff", "Mesa flexora", "Panturrilha em pé"
            )),
            PlanDay("U/L+PPL · Push", listOf(
                "Supino inclinado", "Supino reto com halteres", "Crucifixo", "Elevação lateral", "Tríceps corda"
            )),
            PlanDay("U/L+PPL · Pull", listOf(
                "Barra fixa", "Remada baixa", "Crucifixo invertido", "Encolhimento", "Rosca alternada"
            )),
            PlanDay("U/L+PPL · Legs", listOf(
                "Agachamento", "Passada", "Stiff", "Cadeira extensora", "Panturrilha sentado"
            ))
        )
    ),
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
    ),
    Plan(
        "Ombros com halteres", "Treino focado em ombros, só com halteres e polia.",
        listOf(
            PlanDay("Ombros · Halteres", listOf(
                "Desenvolvimento com halteres", "Elevação lateral inclinado", "Crucifixo invertido", "Face pull"
            ))
        )
    ),
    Plan(
        "Bíceps com halteres", "Treino de braço com halteres.",
        listOf(
            PlanDay("Bíceps · Halteres", listOf("Rosca alternada", "Rosca concentrada", "Rosca martelo"))
        )
    ),
    Plan(
        "Pernas pesado", "Dia de pernas com agachamentos e máquinas.",
        listOf(
            PlanDay("Pernas · Pesado", listOf(
                "Agachamento", "Agachamento frontal", "Leg press", "Cadeira extensora", "Mesa flexora"
            ))
        )
    )
)
