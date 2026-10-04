from fastapi.testclient import TestClient

from app.main import app

SAMPLE = {
    "attendance_rate": 0.72,
    "missed_deadlines": 4,
    "on_time_submission_rate": 0.65,
    "avg_practice_quiz_score": 58.0,
    "midterm_score": 55,
    "avg_weekly_study_hours": 3.0,
    "flashcards_reviewed": 40,
    "avg_days_started_before_exam": 1.0,
    "late_night_study_pct": 0.45,
    "avg_sleep_hours": 6.5,
    "study_sessions_logged": 8,
}


def test_health() -> None:
    with TestClient(app) as client:
        assert client.get("/health").json() == {"status": "ok"}


def test_predict_flags_struggling_student() -> None:
    with TestClient(app) as client:
        body = client.post("/predict", json=SAMPLE).json()
    assert body["at_risk"] == 1
    assert 0.5 < body["risk_probability"] <= 1
    assert body["top_features"][0]["name"] == "midterm_score"
    assert body["explanation"].startswith("The model flagged this course as at risk. It checked:")
    assert "not a verdict" in body["disclaimer"]
    assert "missed 23% of at-risk students" in body["disclaimer"]  # test recall 0.773


def test_predict_not_at_risk() -> None:
    strong = {**SAMPLE, "midterm_score": 88, "attendance_rate": 0.95, "on_time_submission_rate": 0.95}
    with TestClient(app) as client:
        body = client.post("/predict", json=strong).json()
    assert body["at_risk"] == 0
    assert body["risk_probability"] < 0.5


def test_predict_imputes_missing_optional_values() -> None:
    with TestClient(app) as client:
        body = client.post(
            "/predict", json={**SAMPLE, "avg_sleep_hours": None, "avg_practice_quiz_score": None}
        ).json()
    assert set(body["imputed_features"]) == {"avg_sleep_hours", "avg_practice_quiz_score"}


def test_predict_rejects_missing_required_and_out_of_range() -> None:
    with TestClient(app) as client:
        missing = {k: v for k, v in SAMPLE.items() if k != "midterm_score"}
        assert client.post("/predict", json=missing).status_code == 422
        assert client.post("/predict", json={**SAMPLE, "attendance_rate": 1.5}).status_code == 422


def test_model_info_returns_metrics() -> None:
    with TestClient(app) as client:
        body = client.get("/model-info").json()
    assert body["metrics"]["recall_at_risk"] > body["baseline_metrics"]["recall_at_risk"]
    assert body["hyperparameters"]["max_depth"] == 3
    assert body["metrics"]["confusion_matrix"] == {"tn": 93, "fp": 23, "fn": 10, "tp": 34}
    assert len(body["leaves"]) == 8
    assert sum(leaf["train_students"] for leaf in body["leaves"]) == body["n_train"]
