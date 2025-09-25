data class Performance(
    val id: Int? = null,
    val title: String,
    val url: String,
    val scene: String? = null,
)

data class Schedule(
    val date: String,
    val dayOfWeek: String,
    val time: String,
    val ticketsAvailable: Boolean
)