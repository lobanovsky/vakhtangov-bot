data class Performance(
    val title: String,
    val url: String,
)

data class Schedule(
    val date: String,
    val time: String,
    val scene: String,
    val ticketsAvailable: Boolean
)