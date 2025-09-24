fun main() {
    val token = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("Переменная окружения TELEGRAM_BOT_TOKEN не задана!")
    val dbName = System.getenv("DB_NAME") ?: "./data/nations-bot.db"

    initDatabase(dbName)
    updatePerformancesDatabase()


}

/**
 * Функция для обновления списка спектаклей в базе данных
 */
fun updatePerformancesDatabase() {
    // Получаем список спектаклей с сайта
    val performances = parseRepertoire()

    if (performances.isNotEmpty()) {
        clearPerformances()
        for ((title, url) in performances) {
            addPerformance(title, url)
        }
        logger().info("База данных спектаклей обновлена")
    } else {
        logger().warn("Не удалось получить список спектаклей")
    }
}
