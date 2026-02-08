import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DatabaseMigrations {

    fun runAllMigrations() {
        migration_001_AddSubscriptionDate()
        migration_002_AddSubscriptionStatusFields()
        migration_003_AddSubscribedAtToUserPerformanceSubscriptions()
        migration_004_AddNotificationCountToSubscribers()
    }

    /**
     * Миграция #001: Добавление поля subscription_date в таблицу Subscribers
     */
    private fun migration_001_AddSubscriptionDate() {
        transaction {
            try {
                // SQLite требует формат без 'T' или нужно использовать правильный DEFAULT
                val defaultDate = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

                exec("ALTER TABLE Subscribers ADD COLUMN subscription_date TEXT NOT NULL DEFAULT '$defaultDate'")
                println("✅ Миграция #001: Колонка subscription_date успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #001: Колонка subscription_date уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #001: Ошибка - ${e.message}")
                    throw e
                }
            }
        }
    }

    /**
     * Миграция #002: Добавление полей is_active и unsubscription_date для soft delete
     */
    private fun migration_002_AddSubscriptionStatusFields() {
        transaction {
            // Добавляем флаг активности
            try {
                exec("ALTER TABLE Subscribers ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1")
                println("✅ Миграция #002: Колонка is_active успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #002: Колонка is_active уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #002: Ошибка при добавлении is_active - ${e.message}")
                }
            }

            // Добавляем дату отписки (может быть NULL, поэтому без DEFAULT)
            try {
                exec("ALTER TABLE Subscribers ADD COLUMN unsubscription_date TEXT")
                println("✅ Миграция #002: Колонка unsubscription_date успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #002: Колонка unsubscription_date уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #002: Ошибка при добавлении unsubscription_date - ${e.message}")
                }
            }
        }
    }

    /**
     * Миграция #003: Добавление поля subscribed_at в таблицу UserPerformanceSubscriptions
     */
    private fun migration_003_AddSubscribedAtToUserPerformanceSubscriptions() {
        transaction {
            try {
                val defaultDate = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

                exec("ALTER TABLE UserPerformanceSubscriptions ADD COLUMN subscribed_at TEXT NOT NULL DEFAULT '$defaultDate'")
                println("✅ Миграция #003: Колонка subscribed_at успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #003: Колонка subscribed_at уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #003: Ошибка - ${e.message}")
                    throw e
                }
            }
        }
    }

    /**
     * Миграция #004: Добавление поля notification_count в таблицу Subscribers
     */
    private fun migration_004_AddNotificationCountToSubscribers() {
        transaction {
            try {
                exec("ALTER TABLE Subscribers ADD COLUMN notification_count INTEGER NOT NULL DEFAULT 0")
                println("✅ Миграция #004: Колонка notification_count успешно добавлена")
            } catch (e: Exception) {
                if (e.message?.contains("duplicate column name", ignoreCase = true) == true) {
                    println("ℹ️ Миграция #004: Колонка notification_count уже существует, пропускаем")
                } else {
                    println("⚠️ Миграция #004: Ошибка - ${e.message}")
                    throw e
                }
            }
        }
    }
}