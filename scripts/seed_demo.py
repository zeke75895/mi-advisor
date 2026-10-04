"""Seed a demo account for the 3-minute demo (see docs/demo-script.md).

Creates two courses:
  - CH 101 General Chemistry: struggling. Graded items + a previous check-in, so the live demo only
    needs to rate items and press "Predict risk".
  - MA 241 Calculus II: on track. Already rated and predicted, so the dashboard shows the contrast.

Usage (backend + ML service running):
    python scripts/seed_demo.py
    python scripts/seed_demo.py --api https://<backend-url> --email demo2@example.com
All data is synthetic.
"""

import argparse
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone


def call(api, method, path, body=None, token=None):
    req = urllib.request.Request(
        api + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
        headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        detail = json.loads(e.read() or b"{}").get("detail", "")
        raise SystemExit(f"{method} {path} failed ({e.code}): {detail}") from None
    except urllib.error.URLError as e:
        raise SystemExit(f"Can't reach {api}: {e.reason}. Is the backend running?") from None


def due(days, hour=23, minute=59):
    """A due date `days` from now at 11:59 pm (UTC-4, i.e. Eastern daylight time)."""
    local = datetime.now(timezone(timedelta(hours=-4))) + timedelta(days=days)
    return local.replace(hour=hour, minute=minute, second=0, microsecond=0).isoformat()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--api", default="http://localhost:8080")
    p.add_argument("--email", default="demo@coursecompass.app")
    p.add_argument("--password", default="wolfhacks-demo-2026")
    args = p.parse_args()
    api = args.api.rstrip("/")

    creds = {"email": args.email, "password": args.password}
    try:
        token = call(api, "POST", "/api/auth/register", creds)["token"]
    except SystemExit as e:
        if "409" in str(e):
            sys.exit(f"{args.email} already exists. Re-run with a new --email for a fresh demo account.")
        raise

    def post(path, body):
        return call(api, "POST", path, body, token)

    # CH 101: 75% of the grade is already graded, so the story is coherent: little room left to recover
    ch = post("/api/courses", {"courseCode": "CH 101", "courseName": "General Chemistry", "creditHours": 4, "semester": "Fall 2026"})
    items = [
        {"name": "Midterm", "category": "EXAM", "weight": 25, "pointsPossible": 100, "pointsEarned": 58, "isGraded": True, "dueDate": due(-21)},
        {"name": "Homework sets 1-6", "category": "HOMEWORK", "weight": 20, "pointsPossible": 100, "pointsEarned": 68, "isGraded": True, "dueDate": due(-7)},
        {"name": "Quizzes 1-4", "category": "QUIZ", "weight": 10, "pointsPossible": 40, "pointsEarned": 24, "isGraded": True, "dueDate": due(-10)},
        {"name": "Lab reports", "category": "PROJECT", "weight": 20, "pointsPossible": 100, "pointsEarned": 65, "isGraded": True, "dueDate": due(-3)},
        {"name": "Final Exam", "category": "EXAM", "weight": 25, "pointsPossible": 100, "dueDate": due(9, 13, 0)},
    ]
    for item in items:
        post(f"/api/courses/{ch['id']}/items", item)
    # A previous check-in so the live form is prefilled (this also creates an initial risk score)
    post(f"/api/courses/{ch['id']}/predict", {
        "attendanceRate": 0.72, "missedDeadlines": 4, "onTimeSubmissionRate": 0.65, "avgPracticeQuizScore": 61,
        "avgWeeklyStudyHours": 3, "flashcardsReviewed": 40, "avgDaysStartedBeforeExam": 1,
        "lateNightStudyPct": 0.45, "avgSleepHours": 6, "studySessionsLogged": 8,
    })

    # MA 241: on track, fully set up for the dashboard contrast
    ma = post("/api/courses", {"courseCode": "MA 241", "courseName": "Calculus II", "creditHours": 4, "semester": "Fall 2026"})
    ma_items = [
        {"name": "Midterm", "category": "EXAM", "weight": 25, "pointsPossible": 100, "pointsEarned": 88, "isGraded": True, "dueDate": due(-14)},
        {"name": "Homework", "category": "HOMEWORK", "weight": 20, "pointsPossible": 100, "pointsEarned": 92, "isGraded": True, "dueDate": due(-2)},
        {"name": "Project", "category": "PROJECT", "weight": 20, "pointsPossible": 100, "dueDate": due(5)},
        {"name": "Final Exam", "category": "EXAM", "weight": 35, "pointsPossible": 100, "dueDate": due(12, 9, 0)},
    ]
    created = [post(f"/api/courses/{ma['id']}/items", item) for item in ma_items]
    for item, rating in zip(created, [8, 9, 7, 8]):
        post(f"/api/items/{item['id']}/rating", {"rating": rating})
    post(f"/api/courses/{ma['id']}/predict", {
        "attendanceRate": 0.95, "missedDeadlines": 0, "onTimeSubmissionRate": 0.96, "avgPracticeQuizScore": 85,
        "avgWeeklyStudyHours": 7, "flashcardsReviewed": 180, "avgDaysStartedBeforeExam": 6,
        "lateNightStudyPct": 0.05, "avgSleepHours": 7.5, "studySessionsLogged": 26,
    })

    print("Demo account ready")
    print(f"  email:    {args.email}")
    print(f"  password: {args.password}")
    print(f"  CH 101 (course {ch['id']}): graded items + last check-in; rate items and predict live")
    print(f"  MA 241 (course {ma['id']}): rated and predicted (low risk)")
    print("  Syllabus to upload: docs/demo/ch101-syllabus.pdf")


if __name__ == "__main__":
    main()
