import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions


object WebScraper {
    private fun parseRepertoire(): List<Performance> {
        val baseUrl = "https://vakhtangov.ru"
        val url = "$baseUrl/shows/now/"
        val performances = mutableListOf<Performance>()

        try {
            val doc: Document = Jsoup.connect(url).get()
            // каждая сцена
            val sections = doc.select("section.shows-stage")
            for (section in sections) {
                val scene = section.selectFirst("header.shows-stage-header h2")?.text() ?: continue

                val shows = section.select("article.shows-item")
                for (show in shows) {
                    val link = show.selectFirst("a") ?: continue
                    val title = link.selectFirst("h1")?.text() ?: continue
                    val href = link.attr("href")

                    performances.add(
                        Performance(
                            title = title,
                            url = href,
                            scene = scene
                        )
                    )
                }
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
            for ((it, title, url, scene) in performances) {
                addPerformance(title, url, scene)
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
            val showAfisha = document.select("ul.show-afisha > li")

            for (li in showAfisha) {
//                val date = li.select("span.date").firstOrNull()?.text()?.trim()?.removeSuffix(",") ?: continue
                // 1) Попробуем явно выбрать вложенный span.date (внутренний)
                //    Если его нет — возьмём первый попавшийся span.date (фоллбэк).
                val dateElem = li.selectFirst("span.date > span.date")
                    ?: li.selectFirst("p.info span.date")

                // raw может быть:
                //  - "28 сентября,"(правильно)
                //  - "28 сентября, воскресенье, 19:00" (если попали на внешний span) — возьмём первые данные до запятой
                val rawDate = dateElem?.text()?.trim().orEmpty()
                val date = rawDate
                    .split(",") // берём первый фрагмент до запятой — это "28 сентября"
                    .firstOrNull()
                    ?.trim()
                    ?.removeSuffix(",")
                    .orEmpty()

                val dayOfWeek = li.select("span.weekday").firstOrNull()?.text()?.trim()?.removeSuffix(",") ?: ""
                val time = li.select("span.time").firstOrNull()?.text()?.trim() ?: ""

                // Проверяем доступность билетов
                val ticketButton = li.select("a.js-buy-tickets-btn").firstOrNull()
                val ticketsAvailable = ticketButton != null && !ticketButton.hasClass("disabled")

                // Пропускаем служебные строки типа "Билеты: от 800 руб."
                if (date.isNotBlank() && time.isNotBlank()) {
                    schedules.add(
                        Schedule(
                            date = date,
                            dayOfWeek = dayOfWeek,
                            time = time,
                            ticketsAvailable = ticketsAvailable
                        )
                    )
                }
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
