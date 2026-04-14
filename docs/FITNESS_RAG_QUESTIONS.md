# Fitness RAG Questions

Контрольный набор хранится в двух формах:

- человекочитаемый список: этот файл
- машиночитаемый fixture: `demo/fitness-knowledge-corpus/fixtures/rag_questions.json`

## 10 контрольных вопросов

| ID | Вопрос | Ожидаемая суть ответа | Ожидаемые источники |
| --- | --- | --- | --- |
| q01 | Что важнее для похудения: дефицит калорий или время приёма пищи? | Главный фактор - дефицит; тайминг вторичен | `nutrition/calorie_balance.md`, `faq/fitness_faq.md` |
| q02 | Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении? | Около `1.6-2.2 г/кг`, при дефиците полезно ближе к верхней части | `nutrition/protein_guide.md`, `faq/fitness_faq.md` |
| q03 | Что важнее новичку: идеальный сплит или регулярность тренировок? | Регулярность и простая соблюдаемая схема | `training/beginner_strength_training.md`, `faq/fitness_faq.md` |
| q04 | Помогает ли увеличение шагов повышать общий расход энергии? | Да, шаги повышают расход и поддерживают активность | `nutrition/calorie_balance.md`, `training/recovery_sleep_steps.md` |
| q05 | Почему сон влияет на восстановление и контроль аппетита? | Сон влияет на качество тренировки, голод и соблюдение плана | `training/recovery_sleep_steps.md`, `faq/fitness_faq.md` |
| q06 | Нужно ли каждый раз тренироваться до отказа для роста мышц? | Нет, отказ не обязателен; важны объём и прогрессия | `nutrition/muscle_gain_basics.md`, `training/workout_frequency.md`, `faq/fitness_faq.md` |
| q07 | Сколько тренировок в неделю достаточно новичку? | Обычно `2-4` силовых тренировки в неделю | `training/beginner_strength_training.md`, `training/workout_frequency.md`, `faq/fitness_faq.md` |
| q08 | Можно ли худеть без кардио? | Да, если есть дефицит калорий; шаги и силовые помогают | `nutrition/fat_loss_myths.md`, `faq/fitness_faq.md` |
| q09 | Почему жидкие калории могут мешать снижению веса? | Они легко увеличивают калораж и слабо насыщают | `nutrition/calorie_balance.md`, `nutrition/hydration_and_habits.md`, `faq/fitness_faq.md` |
| q10 | Что делать, если по базе знаний на вопрос нет достаточного ответа? | Честно сказать о нехватке контекста и не выдумывать факты | `faq/fitness_faq.md` |
