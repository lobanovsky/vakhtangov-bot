import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions


object WebScraper {
    private fun parseRepertoire(): List<Performance> {
        val baseUrl = "https://theatreofnations.ru"
        val url = "$baseUrl/performances/"
        val performances = mutableListOf<Performance>()

        try {
            // Получаем и парсим HTML-документ
            val document: Document = Jsoup.connect(url).get()

            val links = document.select("a.sidebar-item")

            for (link in links) {
                val title = link.text().trim()
                val relativeUrl = link.attr("href").trim()
                val fullUrl = "$baseUrl$relativeUrl"

                performances.add(Performance(title, fullUrl))
            }

            logger().info("Найдено ${performances.size} спектаклей")
        } catch (e: Exception) {
            logger().error("Ошибка при парсинге страницы: ${e.message}")
        }

        return performances
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

    private fun fetchHtmlWithSelenium(url: String): String? {
        val options = ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
        }
        val driver: WebDriver = ChromeDriver(options)
        return try {
            driver[url]
            driver.pageSource
        } finally {
            driver.quit()
        }
    }

    fun scrapeSchedule(performance: Performance): List<Schedule> {
        val schedules = mutableListOf<Schedule>()

        try {
            // Load the HTML document from the URL
            val html = fetchHtmlWithSelenium(performance.url)
            if (html == null) {
                logger().error("Failed to load HTML content from ${performance.url}")
                return schedules
            }
            val document: Document = Jsoup.parse(html)

//            val document: Document = Jsoup.connect(performance.url).get()

            // Ищем блоки с расписанием
            val scheduleItems = document.select(".play-info__meta-item")

            for (item in scheduleItems) {
                val spans = item.select("span")
                if (spans.size < 2) continue // пропускаем, если нет хотя бы даты и сцены

                val dateTimeText = spans[0].text().trim() // Пример: "15 июн 2025 (Вс) - 19:00"
                val sceneText = spans[1].text().trim()

                val dateTimeParts = dateTimeText.split(" - ")
                if (dateTimeParts.size != 2) continue

                val date = dateTimeParts[0].trim() // "15 июн 2025 (Вс)"
                val time = dateTimeParts[1].trim() // "19:00"

                // Проверяем наличие кнопки "Купить билет"
                val buyButton = item.select("a.btn")
                    .firstOrNull { it.text().contains("Купить билет", ignoreCase = true) }

                val ticketsAvailable = buyButton != null

                schedules.add(
                    Schedule(
                        date = date,
                        time = time,
                        scene = sceneText,
                        ticketsAvailable = ticketsAvailable
                    )
                )
            }

        } catch (e: Exception) {
            logger().warn("Ошибка при парсинге расписания спектакля [${performance.title}]: ${e.message}")
        }

        schedules.forEach {
            logger().info("[${performance.title}] Date: ${it.date}, Time: ${it.time}, Tickets Available: ${it.ticketsAvailable}")
        }
        return schedules
    }
}
