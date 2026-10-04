# MiAdvisor

AI student hub for WolfHacks 2026 (Institute for Advanced Analytics track).

Syllabus ingest → self-ratings → decision-tree risk prediction → stay/withdraw
recommendation → study plan + flashcards.

> All data is synthetic. Risk outputs are guidance, not a verdict. Talk to your
> advisor before withdrawing from a course.

## The model at a glance

Decision tree (depth 3) trained on the WolfHacks synthetic dataset (800 students, 28% at risk),
evaluated on a stratified 20% hold-out (160 students):

| | Majority-class baseline | Decision tree |
|---|---|---|
| Accuracy | 72.5% | 79.4% |
| Recall on at-risk | 0% | **77.3%** (34 of 44) |
| ROC-AUC | 0.50 | 0.87 |

Depth chosen by repeated cross-validation on training data only; train vs test accuracy
86.1% vs 79.4%. Top features: midterm score, on-time submissions, attendance, late-night
studying. Full walkthrough with confusion matrix, importances, the tree diagram and an
overfitting discussion: [`notebooks/02_train_evaluate.ipynb`](notebooks/02_train_evaluate.ipynb).
The app's Model Insights page shows the same numbers live.

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
| `PUT/DELETE /api/items/{id}` | Edit a graded item (e.g. enter the score when it's back) or delete it with its ratings |
| `POST /api/items/{id}/rating` | Self-rating 1-10 for an item (latest one counts) |
| `POST /api/courses/{id}/predict` | Sends study-habit features to the ML service, stores a `RiskScore`. `midtermScore` can be omitted if the course has a graded exam named "Midterm" |
| `GET /api/courses/{id}/projection` | Projected final grade from grades + self-ratings (works before any prediction) |
| `GET /api/courses/{id}/recommendation` | Stay/withdraw recommendation from model risk + grade projection + self-ratings, with a Gemini-polished explanation. 204 until the course has a risk check and a graded or rated item |

| `GET/PUT /api/courses/{id}/notes` | The course's study notes (pasted text, up to 100,000 characters) |
| `POST /api/courses/{id}/notes/pdf` | Upload a PDF (multipart `file`, ≤10 MB); its text becomes the course notes |
| `POST /api/courses/{id}/notes/extract-items` | Suggested graded items (name, category, weight, date) from the syllabus text, via Gemini or a pattern-match fallback; the student reviews them before adding |
| `POST/GET /api/courses/{id}/study-sessions` | Log study time / list sessions with weekly totals (`?today=YYYY-MM-DD`) |
| `GET /api/study-sessions/summary`, `DELETE /api/study-sessions/{id}` | Last-7-days totals across courses / remove a session |
| `GET /api/courses/{id}/risk/latest`, `GET /api/courses/{id}/check-in/latest` | Last risk check (with explanation) and last check-in answers |
| `POST /api/materials/{courseId}/generate-flashcards` | 10 Q/A flashcards from `{"content": "..."}` or, if omitted, the saved notes. Labeled AI-generated |
| `POST /api/materials/{courseId}/generate-questions` | 5 multiple-choice questions with answers and explanations (same input) |
| `GET /api/materials/{courseId}/flashcards`, `.../questions` | Latest saved flashcard set / quiz |
| `POST /api/study-plan/generate` | 7-day plan from deadlines in the next 14 days and items rated 4/10 or lower (`{"startDate", "hoursPerDay", "courseIds", "timeZone"}`, all optional) |
| `GET /api/study-plan/latest` | Latest saved plan |
| `GET /api/model-info` | Model metrics, confusion matrix, importances and tree rules (from the ML service, cached for `ML_MODEL_INFO_TTL`, default 5 minutes) |

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
self-rating × 10 (ungraded), using the syllabus weights. The recommendation also uses the
score needed to pass: the average required on all remaining work to finish with a C,
computed from actual grades (over 85% is a steep climb; over 100% means a C is out of
reach). The reasons are worded to match the final recommendation. If `GEMINI_API_KEY` is set,
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
docker build -t miadvisor-ml . && docker run -p 8000:8000 miadvisor-ml
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
deadlines and weights are still entered by hand). Everything Gemini writes is labeled "AI-generated". A Model Insights page shows
the confusion matrix, feature importances and the tree's rules in plain English, and every
risk and recommendation card carries a Responsible AI panel with the model's live recall. Set `VITE_API_BASE_URL` to point at the backend.
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

## Demo

- Script: [`docs/demo-script.md`](docs/demo-script.md) (3 minutes, with a numbers cheat sheet and fallbacks)
- Seed a demo account: `python scripts/seed_demo.py` (prints the login; all data is synthetic)
- Sample syllabus to upload: [`docs/demo/ch101-syllabus.pdf`](docs/demo/ch101-syllabus.pdf)

## Deploying

| Service | Where | Setup |
|---|---|---|
| Postgres | Tiger Data or Railway Postgres | Create a database and copy its host, port, name, user and password |
| ML service | Railway, from `ml-service/Dockerfile` | No env vars needed; Railway sets `PORT` |
| Backend | Railway, from `backend/Dockerfile` | Env vars below |
| Frontend | Vercel, root directory `frontend` | `VITE_API_BASE_URL=https://<backend-url>` |

Backend environment variables:

- `DATABASE_URL` must be a **JDBC** URL: `jdbc:postgresql://<host>:<port>/<db>?sslmode=require`.
  Providers usually show `postgres://user:pass@host:port/db`; convert it and put the user and
  password in `DATABASE_USERNAME` and `DATABASE_PASSWORD`.
- `JWT_SECRET`: required (`openssl rand -base64 48`). Without it, logins reset on every restart.
- `ML_SERVICE_URL`: the ML service's Railway URL.
- `CORS_ALLOWED_ORIGINS`: your Vercel URL, e.g. `https://miadvisor.vercel.app`.
- `GEMINI_API_KEY` (and optionally `GEMINI_MODEL`, `AI_USER_LIMIT`).

Tables are created automatically on first start (`spring.jpa.hibernate.ddl-auto=update`).

