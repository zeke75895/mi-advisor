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
