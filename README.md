# Vakhtangov Bot

Telegram-бот для отслеживания билетов на спектакли [Театра им. Вахтангова](https://vakhtangov.ru). Бот работает как тонкий клиент: весь функционал поиска билетов и хранения подписок реализован в [tickets-backend](https://github.com/lobanovsky/tickets-backend). Бот принимает push-уведомления от бэкенда и отправляет сообщения подписчикам в Telegram.

## Архитектура

```
Telegram User
    │  команды: /perfs, /status, /mysubs, /subs
    ▼
VakhtangovBot  ──── REST API ────►  tickets-backend
    │                              (подписки, спектакли,
    │                               поиск билетов)
    │◄── POST /webhook/notifications ──┘
    │    (когда найдены билеты)
    ▼
Telegram User  ←── уведомление о билетах
```

Бот не содержит базы данных и не парсит сайт театра — всё это делает бэкенд.

## Возможности

- Просмотр списка спектаклей с возможностью подписки/отписки
- Push-уведомления при появлении билетов с прямой ссылкой на покупку
- Просмотр своих подписок со ссылками на страницы спектаклей
- Административная команда: список всех подписчиков по спектаклям

## Команды бота

| Команда | Описание |
|---------|----------|
| `/perfs` | Список спектаклей — подписаться / отписаться |
| `/status` | Мои активные подписки |
| `/mysubs` | Мои подписки со ссылками на страницы спектаклей |
| `/subs` | *(admin)* Все подписчики по спектаклям |

## Стек

- **Kotlin** + **Coroutines**
- **kotlin-telegram-bot** — Telegram Bot API
- **Ktor** — HTTP-клиент для вызовов бэкенда + HTTP-сервер для webhook-эндпоинта
- **kotlinx.serialization** — JSON

## Быстрый старт

### Переменные окружения

Создайте файл `.env`:

```env
TELEGRAM_BOT_TOKEN=your_bot_token
BACKEND_URL=http://localhost:8080
VAKHTANGOV_API_KEY=vakhtangov-secret
WEBHOOK_SECRET=your-webhook-secret
WEBHOOK_PORT=8081
DEV_MODE=0
TZ=Europe/Moscow
```

| Переменная | Описание |
|------------|----------|
| `TELEGRAM_BOT_TOKEN` | Токен бота от [@BotFather](https://t.me/BotFather) |
| `BACKEND_URL` | URL tickets-backend (напр. `http://localhost:8080`) |
| `VAKHTANGOV_API_KEY` | API-ключ для tickets-backend |
| `WEBHOOK_SECRET` | Секрет для проверки входящих webhook-запросов |
| `WEBHOOK_PORT` | Порт HTTP-сервера для webhook (default `8081`) |
| `DEV_MODE` | `0` — боевой режим, `1` — без webhook-сервера |
| `TZ` | Часовой пояс (рекомендуется `Europe/Moscow`) |

### Запуск через Docker Compose

```bash
TAG=latest docker-compose up -d
```

### Локальная сборка и запуск

```bash
# Собрать fat JAR
./gradlew shadowJar

# Запустить локально
./gradlew run
```

## Webhook от бэкенда

Бот поднимает HTTP-сервер и слушает `POST /webhook/notifications`. Бэкенд должен отправлять запрос с заголовком `X-Webhook-Secret` при обнаружении доступных билетов.

**Формат запроса:**
```json
{
  "id": "uuid",
  "telegramId": 123456789,
  "performanceTitle": "Название спектакля",
  "performanceUrl": "https://vakhtangov.ru/shows/...",
  "theatreSlug": "vakhtangov",
  "scheduleSummary": "• Дата: 01.05.2026, Время: 19:00",
  "createdAt": "2026-04-17T10:30:00"
}
```

Бот отправляет сообщение пользователю, затем вызывает `POST /api/notifications/{id}/ack` у бэкенда.

**Тестирование webhook локально:**
```bash
curl -X POST http://localhost:8081/webhook/notifications \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: your-webhook-secret" \
  -d '{"id":"test-id","telegramId":YOUR_TELEGRAM_ID,"performanceTitle":"Гамлет","performanceUrl":"https://vakhtangov.ru/shows/hamlet","theatreSlug":"vakhtangov","scheduleSummary":"• Дата: 01.05.2026, Время: 19:00","createdAt":"2026-04-17T10:00:00"}'
```

> **Примечание:** Поддержка отправки webhooks на стороне бэкенда (хранение `webhook_url` для каждого театра и HTTP POST при создании уведомления) — отдельная задача.

## Структура

```
VakhtangovBot.kt  — Точка входа, запуск бота и webhook-сервера
Commands.kt       — Обработчики команд и callback-кнопок (вызывают ApiClient)
ApiClient.kt      — HTTP-клиент для всех вызовов tickets-backend
WebhookServer.kt  — Ktor HTTP-сервер для приёма push-уведомлений
Model.kt          — DTO для ответов бэкенда
Extensions.kt     — Утилиты логирования
```

## Деплой

GitHub Actions (`.github/workflows/deploy-to-proxy-server.yml`) при пуше в `master`:

1. Собирает Docker-образ с тегом из короткого SHA коммита
2. Пушит образ в DockerHub (`lobanovsky/vakhtangov-bot`)
3. По SSH деплоит на Ubuntu-сервер

Порт `WEBHOOK_PORT` должен быть открыт для входящих запросов от бэкенда.
