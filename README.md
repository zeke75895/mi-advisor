# CourseCompass

AI student hub for WolfHacks 2026 (Institute for Advanced Analytics track).

Syllabus ingest → self-ratings → decision-tree risk prediction → stay/withdraw
recommendation → study plan + flashcards.

> All data is synthetic. Risk outputs are guidance, not a verdict. Talk to your
> advisor before withdrawing from a course.

## Repo layout

| Path          | Stack                               | Deploy  |
|---------------|-------------------------------------|---------|
| `frontend/`   | React + Vite + Tailwind             | Vercel  |
| `backend/`    | Spring Boot 3, Java 17, Maven, JPA  | Railway |
| `ml-service/` | FastAPI + scikit-learn              | Railway |
| `notebooks/`  | Jupyter training + evaluation       | —       |
| `data/`       | WolfHacks synthetic dataset (xlsx)  | —       |

## Prerequisites

Node 20+, Java 17+, Maven 3.9+, Python 3.11+, Docker.

## Local development

```bash
cp .env.example .env
```

### 1. Postgres

```bash
docker compose up -d
```

Runs Postgres 16 on `localhost:5432` (db/user/password: `coursecompass`).

### 2. Backend — http://localhost:8080

```bash
cd backend
mvn spring-boot:run
curl localhost:8080/health   # {"status":"ok"}
```

Needs Postgres running. Connection settings come from `DATABASE_URL`,
`DATABASE_USERNAME`, `DATABASE_PASSWORD` and default to the docker-compose values.
Set `JWT_SECRET` (32+ bytes) in any deployed environment. Locally, a random key is
generated if it's unset, so tokens stop working after a restart.

All `/api/**` routes except auth need `Authorization: Bearer <token>`. Errors are
returned as RFC 7807 problem JSON (validation errors include an `errors` map).

| Method & path | Purpose |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | Returns a JWT |
| `GET/POST /api/courses` | List / create the user's courses |
| `GET/POST /api/courses/{id}/items` | Graded items (category, weight % of final grade, points, due date) |
| `POST /api/items/{id}/rating` | Self-rating 1-10 for an item (latest one counts) |
| `POST /api/courses/{id}/predict` | Sends study-habit features to the ML service, stores a `RiskScore`. `midtermScore` can be omitted if the course has a graded exam named "Midterm" |
| `GET /api/courses/{id}/projection` | Projected final grade from grades + self-ratings (works before any prediction) |
| `GET /api/courses/{id}/recommendation` | Stay/withdraw recommendation from model risk + grade projection + self-ratings, with a Gemini-polished explanation |

| `GET/PUT /api/courses/{id}/notes` | The course's study notes (pasted text, up to 100,000 characters) |
| `POST /api/courses/{id}/notes/pdf` | Upload a PDF (multipart `file`, ≤10 MB); its text becomes the course notes |
| `GET /api/courses/{id}/risk/latest`, `GET /api/courses/{id}/check-in/latest` | Last risk check (with explanation) and last check-in answers |
| `POST /api/materials/{courseId}/generate-flashcards` | 10 Q/A flashcards from `{"content": "..."}` or, if omitted, the saved notes. Labeled AI-generated |
| `POST /api/materials/{courseId}/generate-questions` | 5 multiple-choice questions with answers and explanations (same input) |
| `GET /api/materials/{courseId}/flashcards`, `.../questions` | Latest saved flashcard set / quiz |
| `POST /api/study-plan/generate` | 7-day plan from deadlines in the next 14 days and items rated 4/10 or lower (`{"startDate", "hoursPerDay", "courseIds", "timeZone"}`, all optional) |
| `GET /api/study-plan/latest` | Latest saved plan |

"Latest" endpoints return 204 when nothing is saved yet. Everything a student enters
or generates is stored in Postgres, so it follows them across devices.

AI generation is capped per user (`AI_USER_LIMIT` per `AI_USER_WINDOW`, default 10 per
10 minutes) and globally (`AI_GLOBAL_LIMIT_PER_MINUTE`, default 15) to protect the Gemini
free-tier quota; over the cap returns 429 with `Retry-After`. Gemini server errors are
retried once. The study endpoints need `GEMINI_API_KEY` (503 without it). Gemini output is checked
before it's returned: the item counts are enforced through the response schema, every
question needs 4 distinct options and a valid answer index, and plans are pinned to
the requested 7 dates and the daily time limit.

The projected final is the weighted average of each item's actual % (graded) or
self-rating × 10 (ungraded), using the syllabus weights. If `GEMINI_API_KEY` is set,
the reasoning bullets are rewritten by Gemini (prompts in
`backend/src/main/resources/prompts/`) and labeled "AI-generated". The rewrite is
rejected, and the template text used instead, if it changes the number of bullets or
adds any number that wasn't in the input. Only aggregate signals are sent to Gemini.

```bash
mvn test   # unit + integration tests (H2 in memory, ML service mocked)
```

### 3. ML service — http://localhost:8000

```bash
cd ml-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
curl localhost:8000/health   # {"status":"ok"}
```

| Endpoint | Purpose |
|---|---|
| `GET /health` | Liveness check |
| `POST /predict` | Risk prediction: `at_risk`, `risk_probability`, `top_features`, decision path, plain-language explanation, disclaimer |
| `GET /model-info` | Test-set metrics, baseline, hyperparameters, feature importances |
| `GET /docs` | Interactive API docs with an example request |

```bash
python scripts/test_predict.py            # sample prediction against a running service
pip install -r requirements-dev.txt && pytest   # unit tests
docker build -t coursecompass-ml . && docker run -p 8000:8000 coursecompass-ml
```

Loads `models/tree_v1.joblib` + `models/model_config.json` at startup. `scikit-learn`
is pinned to the version the model was trained with.

### 4. Frontend — http://localhost:5173

```bash
cd frontend
npm install
npm run dev
```

Pages: login/register, dashboard (course cards with risk level, projected grade and
recommendation), course detail (graded items with 1–10 confidence sliders, projected
grade meter, weekly check-in → risk check, recommendation card), flashcards (flip
cards) and practice quiz per course, a 7-day study plan, and PDF upload (text is extracted into the course notes;
deadlines and weights are still entered by hand). Everything Gemini writes is labeled "AI-generated". Set `VITE_API_BASE_URL` to point at the backend.
`vercel.json` rewrites all routes to `index.html` for client-side routing.

### 5. Train the model

```bash
cd notebooks
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
jupyter notebook
```

Run in order:

1. `01_explore_clean.ipynb`: loads `data/wolfhacks_2026_student_outcomes.xlsx`,
   makes the stratified 80/20 split, imputes, encodes, and writes
   `cleaned_data.csv` + `feature_config.json`.
2. `02_train_evaluate.ipynb`: baseline, depth selection by cross-validation,
   test-set evaluation, feature importances, and exports
   `ml-service/models/tree_v1.joblib` + `model_config.json`. Charts are saved
   to `notebooks/figures/`.
