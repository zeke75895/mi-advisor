import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app.model import RiskModel

MODEL_DIR = Path(os.getenv("MODEL_DIR", Path(__file__).resolve().parent.parent / "models"))


def disclaimer(model: RiskModel) -> str:
    """Built from the model's measured test recall, so it stays accurate after retraining."""
    recall = model.config["metrics"]["recall_at_risk"]
    return (
        "This is guidance, not a verdict. The model was trained on synthetic data and, in testing, "
        f"missed {round((1 - recall) * 100)}% of at-risk students. "
        "Talk to your advisor before making any decision about a course."
    )


state: dict[str, RiskModel] = {}


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    state["model"] = RiskModel(MODEL_DIR)
    yield
    state.clear()


app = FastAPI(title="CourseCompass ML Service", version="1.0.0", lifespan=lifespan)

# Hackathon: allow every origin. Lock this down before any real deployment.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class PredictRequest(BaseModel):
    attendance_rate: float = Field(..., ge=0, le=1, description="Share of classes attended (0-1)")
    missed_deadlines: int = Field(..., ge=0)
    on_time_submission_rate: float = Field(..., ge=0, le=1, description="Share of deadlines met (0-1)")
    avg_practice_quiz_score: float | None = Field(
        None, ge=0, le=100, description="Blank if no quizzes taken (imputed as 0)"
    )
    midterm_score: float = Field(..., ge=0, le=100)
    avg_weekly_study_hours: float = Field(..., ge=0)
    flashcards_reviewed: int = Field(..., ge=0)
    avg_days_started_before_exam: float = Field(..., ge=0)
    late_night_study_pct: float = Field(..., ge=0, le=1, description="Share of study time 12-4 a.m. (0-1)")
    avg_sleep_hours: float | None = Field(
        None, ge=0, le=24, description="Imputed with training median if blank"
    )
    study_sessions_logged: int = Field(..., ge=0)
    # Optional context; only used if the model was trained with one-hot columns for them
    course: str | None = None
    class_year: str | None = None

    model_config = {
        "json_schema_extra": {
            "example": {
                "attendance_rate": 0.72,
                "missed_deadlines": 4,
                "on_time_submission_rate": 0.65,
                "avg_practice_quiz_score": 58.0,
                "midterm_score": 55,
                "avg_weekly_study_hours": 3.0,
                "flashcards_reviewed": 40,
                "avg_days_started_before_exam": 1.0,
                "late_night_study_pct": 0.45,
                "avg_sleep_hours": None,
                "study_sessions_logged": 8,
            }
        }
    }


class TopFeature(BaseModel):
    name: str
    importance: float


class PathStep(BaseModel):
    feature: str
    value: float
    threshold: float
    direction: str


class PredictResponse(BaseModel):
    at_risk: int
    risk_probability: float
    top_features: list[TopFeature]
    decision_path: list[PathStep]
    explanation: str
    imputed_features: list[str]
    disclaimer: str
    model_version: str


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest) -> dict[str, Any]:
    try:
        result = state["model"].predict(req.model_dump())
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return {**result, "disclaimer": disclaimer(state["model"])}


@app.get("/model-info")
def model_info() -> dict[str, Any]:
    return {**state["model"].info(), "disclaimer": disclaimer(state["model"])}
