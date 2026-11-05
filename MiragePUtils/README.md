<div align="center">

# MiragePUtils

**Надстройка над LiteBans для Minecraft Paper 1.21.4+**

Умная лесенка наказаний · Меню причин · Анимации наказаний · История · Discord-логи

`Paper 1.21.4+` · `Java 21` · `Требуется LiteBans` · `Палитра: &f + &c`

</div>

---

## О плагине

**MiragePUtils** не заменяет LiteBans, а делает работу модераторов с ним удобнее и зрелищнее.

Модератор открывает меню `/punish <игрок>` и выбирает **причину**. Плагин смотрит, сколько раз игрок уже нарушал именно это правило, и выдаёт наказание нужной строгости по настраиваемой **«лесенке»**. Перед этим наказываемого игрока **поднимает в воздух**, вокруг него вспыхивают эффекты, и в финале — вспышка, взрыв и молния. Только после анимации плагин запускает команду LiteBans, и тот выдаёт бан, мут, предупреждение или кик.

Баны, муты и предупреждения **хранит и выдаёт LiteBans** — вся его история, оповещения, ограничения и Discord-интеграция продолжают работать как раньше.

---

## Возможности

### Анимации наказаний
Игрока на несколько секунд «замораживает» в воздухе, он неуязвим и не может выполнять команды.

| Анимация | Что происходит | По умолчанию для |
|---|---|---|
| **ASCENSION** — Взлёт | Игрок вращается и поднимается вверх по спирали из белых и красных частиц, от земли к нему тянется луч, звук колокольчиков нарастает. Финал: вспышка, взрыв, молния | Бан |
| **VORTEX** — Вихрь | Игрок кружится в воронке частиц, портал затягивает его вверх. Финал: взрыв | IP-бан |
| **STORM** — Шторм | Молнии бьют вокруг подвешенного игрока, искры, красное кольцо. Финал: удар молнии | Кик |
| **SILENCE** — Тишина | Сужающиеся кольца вокруг головы, звук «заряда». Финал: звуковая волна (Warden) | Мут |
| **WARNING** — Вспышка | Игрок слегка подпрыгивает, красные импульсы. Финал: искры и звук наковальни | Предупреждение |

- Игроку на экране показывается заголовок с типом наказания и причиной.
- Высота подъёма ограничивается потолком — игрок не застревает в блоках.
- Если игрок вышел во время анимации, наказание применяется сразу.
- После анимации игрок возвращается на исходное место (для бана — до кика), флаги полёта и неуязвимости восстанавливаются.
- Анимацию можно назначить для типа наказания или для отдельной причины. Предпросмотр: `/mirageputils preview <тип>`.

### Умная лесенка и шаблоны причин
- Любое количество шаблонов причин в `config.yml` (читы, оскорбления, спам, реклама, грифинг и т.д.).
- У каждой причины своя лесенка: `WARN → MUTE 1h → MUTE 1d → BAN perm`.
- Учитывается история игрока **именно по этой причине**. Лесенку игрока можно сбросить (`/mirageputils reset <игрок>`).
- Свои иконки, описания, анимации и отдельное право для особо тяжёлых причин.

### Меню
- **`/punish <игрок>`** — меню причин: для каждой показано, **какое наказание получит игрок** и какая анимация сыграет.
- `Shift + ЛКМ` в меню — выдать **тихо**.
- **`/phistory <игрок>`** — история наказаний, выданных через MiragePUtils (с листанием страниц).
- Оформление в белых и красных тонах (`&f` + `&c`).

### Интеграция с LiteBans
- Плагин **зависит от LiteBans** (`depend` в `plugin.yml`) — без него не запустится.
- Команда LiteBans выполняется **от имени модератора**, поэтому в истории LiteBans он указан как выдавший, а права LiteBans (`litebans.ban`, `litebans.tempmute` и т.д.) продолжают действовать.
- Иммунитет `litebans.exempt.*` учитывается: для защищённых игроков анимация не запускается.
- Шаблоны всех команд настраиваются в `config.yml` (раздел `litebans.commands`).

### Прочее
- Журнал выданных через плагин наказаний в **SQLite** (по умолчанию) или **MySQL/MariaDB**, запросы асинхронные.
- **Discord webhook** с записью каждого наказания, выданного через MiragePUtils.
- Все тексты в `messages.yml`.

---

## Что убрано и почему

В плагине **нет** собственных `/ban`, `/tempban`, `/ipban`, `/unban`, `/mute`, `/tempmute`, `/unmute`, `/warn`, `/unwarn`, `/kick`, `/banlist`, `/mutelist` и аналогов — всё это уже есть в LiteBans, и дубли только конфликтовали бы с ним. Если эти команды нужны — используйте команды LiteBans.

---

## Требования

| Что | Версия |
|---|---|
| Сервер | **Paper 1.21.4** и новее (или форки Paper) |
| Java | **21** и новее |
| Обязательный плагин | **LiteBans** |
| База данных | SQLite (входит в Paper) или MySQL/MariaDB |

---

## Установка

1. Установите **LiteBans** и убедитесь, что он работает.
2. Положите `MiragePUtils-1.0.0.jar` в папку `plugins/` и перезапустите сервер.
3. Настройте `plugins/MiragePUtils/config.yml` и `messages.yml`.
4. Выдайте права стаффу и используйте `/mirageputils reload`.
5. Проверьте анимации: `/mirageputils preview ascension`.

---

## Команды

| Команда | Описание | Право |
|---|---|---|
| `/punish <игрок>` | Открыть меню причин | `mirageputils.punish` |
| `/punish <игрок> <причина> [-s]` | Выдать наказание по шаблону сразу, без меню | `mirageputils.punish` |
| `/phistory <игрок> [страница]` | История наказаний через MiragePUtils (в консоли — текст) | `mirageputils.history` |
| `/mirageputils` (`/mpu`) | Информация о плагине | — |
| `/mirageputils help` | Справка | — |
| `/mirageputils preview <тип>` | Предпросмотр анимации на себе: `ascension`, `storm`, `vortex`, `silence`, `warning` | `mirageputils.admin` |
| `/mirageputils reset <игрок>` | Сбросить лесенку игрока | `mirageputils.admin` |
| `/mirageputils reload` | Перезагрузить конфиг и сообщения | `mirageputils.admin` |

---

## Права

| Право | Описание | По умолчанию |
|---|---|---|
| `mirageputils.*` | Все права плагина | OP |
| `mirageputils.punish` | `/punish` и меню | OP |
| `mirageputils.punish.severe` | Тяжёлые причины (угрозы, DDoS) | OP |
| `mirageputils.history` | `/phistory` | OP |
| `mirageputils.silent` | Тихие наказания (`-s`, Shift + ЛКМ) | OP |
| `mirageputils.admin` | `reload`, `preview`, `reset` | OP |

Кроме этих прав, модератору нужны права **LiteBans** на соответствующие команды: `litebans.ban`, `litebans.tempban`, `litebans.ipban`, `litebans.tempipban`, `litebans.mute`, `litebans.tempmute`, `litebans.warn`, `litebans.tempwarn`, `litebans.kick`. Проверку этих прав перед анимацией можно отключить: `litebans.check-permissions: false`.

---

## Настройка

### Шаблоны причин

```yaml
reasons:
  toxicity:                      # ID причины (/punish ник toxicity)
    name: "Оскорбления в чате"   # название в меню и причина для LiteBans
    icon: WRITABLE_BOOK
    animation: SILENCE           # необязательно: своя анимация для этой причины
    permission: ""               # необязательно: право для этой причины
    description:
      - "&fОскорбления игроков,"
      - "&fтоксичное поведение."
    ladder:
      - "WARN"
      - "MUTE 1h"
      - "MUTE 6h"
      - "MUTE 1d"
      - "MUTE 7d"
```

Формат шага: `ТИП СРОК`. ТИП — `WARN`, `MUTE`, `BAN`, `IPBAN`, `KICK`. Срок: `30s` `15m` `12h` `7d` `2w` `1mo` `1y` или `perm`. Для `WARN` без срока берётся `warns.default-duration`. Если нарушений больше, чем шагов, повторяется последний шаг. В комплекте 10 готовых причин.

### Анимации

```yaml
animations:
  enabled: true
  by-type:
    BAN: ASCENSION
    IPBAN: VORTEX
    MUTE: SILENCE
    WARN: WARNING
    KICK: STORM
```

Приоритет: анимация в шаблоне причины → `by-type` → значение по умолчанию. Тип `NONE` отключает анимацию. `animations.enabled: false` отключает все.

### Команды LiteBans

```yaml
litebans:
  execute-as: staff        # staff | console
  commands:
    ban: "ban {silent}{player} {reason}"
    tempban: "tempban {silent}{player} {duration} {reason}"
    # ...
```

Плейсхолдеры: `{player}`, `{duration}` (например `7d`), `{reason}`, `{silent}`. Если в вашей версии LiteBans синтаксис отличается (например, другой флаг тихого режима), поправьте шаблон и `litebans.silent-flag`.

### База данных и webhook

```yaml
database:
  type: sqlite   # или mysql
webhook:
  enabled: true
  url: "https://discord.com/api/webhooks/ID/TOKEN"
```

---

## Важно знать

- Лесенка и `/phistory` считают наказания, выданные **через MiragePUtils**. Наказания, выданные напрямую командами LiteBans, в них не попадают.
- Анимация запускается, только если наказываемый игрок **в сети**. Для игрока оффлайн наказание выдаётся сразу.
- На время анимации игрока перемещают телепортом каждый тик — плагины, которые запоминают телепорты (например, `/back`), могут записать позиции в воздухе.

---

## Сборка из исходников

```bash
mvn clean package
```

Готовый `.jar` появится в `target/` (нужны JDK 21 и Maven). LiteBans при сборке не нужен — плагин работает с ним через команды.

```
src/main/java/org/example/sawervatoryy/miragePUtils/
├── MiragePUtils.java       — главный класс
├── PunishManager.java      — лесенка, проверки, запуск команд LiteBans
├── Animations.java         — движок анимаций
├── AnimationType.java      — типы анимаций
├── AnimationListener.java  — защита игрока во время анимации
├── Commands.java           — /punish, /phistory, /mirageputils
├── Menus.java / MenuListener.java / MenuHolder.java — GUI
├── Database.java           — журнал SQLite / MySQL
├── DiscordWebhook.java     — логи в Discord
├── Messages.java / TimeUtil.java
└── PunishType / Punishment / ReasonDef / Target
src/main/resources/ plugin.yml, config.yml, messages.yml
```

---

## Автор

<div align="center">

**Плагин создан студией MirageStudio**

| | |
|---|---|
| **Студия** | MirageStudio |
| **Автор** | sawervatoryy |
| **Discord-сервер** | https://discord.gg/dWPenZegK |

</div>
