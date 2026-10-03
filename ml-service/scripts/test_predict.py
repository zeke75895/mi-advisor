"""Send a sample prediction to a running ML service and print the result.

Usage:
    python scripts/test_predict.py                      # http://localhost:8000
    python scripts/test_predict.py https://<railway-url>
"""

import json
import sys
import urllib.request

BASE_URL = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000").rstrip("/")

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
    "avg_sleep_hours": None,  # left blank to show imputation
    "study_sessions_logged": 8,
}


def request(method: str, path: str, body: dict | None = None) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        f"{BASE_URL}{path}", data=data, method=method, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.load(resp)


if __name__ == "__main__":
    print(f"POST {BASE_URL}/predict")
    print("Request:", json.dumps(SAMPLE, indent=2))
    result = request("POST", "/predict", SAMPLE)
    print("Response:", json.dumps(result, indent=2))

    info = request("GET", "/model-info")
    m = info["metrics"]
    print(
        f"\nModel {info['model_version']}: test recall {m['recall_at_risk']:.3f}, "
        f"accuracy {m['test_accuracy']:.3f}, ROC-AUC {m['roc_auc']:.3f}"
    )
