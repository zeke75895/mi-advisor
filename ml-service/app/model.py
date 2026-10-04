"""Loads the trained decision tree and turns raw student inputs into predictions."""

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.tree import DecisionTreeClassifier

CATEGORICAL_COLUMNS = ("course", "class_year")

# Plain-language names and value formatting for the "why" text
FEATURE_LABELS: dict[str, str] = {
    "attendance_rate": "class attendance",
    "missed_deadlines": "number of missed deadlines",
    "on_time_submission_rate": "on-time submission rate",
    "avg_practice_quiz_score": "practice quiz average",
    "midterm_score": "midterm score",
    "avg_weekly_study_hours": "weekly study time (hours)",
    "flashcards_reviewed": "number of flashcards reviewed",
    "avg_days_started_before_exam": "head start before exams (days)",
    "late_night_study_pct": "share of studying done between midnight and 4 a.m.",
    "avg_sleep_hours": "average sleep (hours)",
    "study_sessions_logged": "number of study sessions logged",
}
PERCENT_FEATURES = {"attendance_rate", "on_time_submission_rate", "late_night_study_pct"}


def _format_value(feature: str, value: float) -> str:
    if feature in PERCENT_FEATURES:
        return f"{round(value * 100, 1):g}%"
    return f"{value:g}"


@dataclass
class PathStep:
    feature: str
    value: float
    threshold: float
    direction: str  # "<=" or ">"


class RiskModel:
    def __init__(self, model_dir: Path) -> None:
        self.model: DecisionTreeClassifier = joblib.load(model_dir / "tree_v1.joblib")
        with open(model_dir / "model_config.json") as f:
            self.config: dict[str, Any] = json.load(f)

        self.features: list[str] = self.config["features"]
        self.imputation_values: dict[str, float] = self.config["imputation_values"]
        self.importances: dict[str, float] = self.config["feature_importances"]

        if list(self.model.feature_names_in_) != self.features:
            raise ValueError("model_config.json feature order does not match the trained model")

    def build_features(self, raw: dict[str, Any]) -> tuple[pd.DataFrame, list[str]]:
        """Impute and one-hot encode raw inputs into the training feature order.

        Returns the 1-row feature frame and the names of features that were imputed.
        """
        row: dict[str, float] = {}
        imputed: list[str] = []
        for feature in self.features:
            category = next((c for c in CATEGORICAL_COLUMNS if feature.startswith(f"{c}_")), None)
            if category is not None:
                row[feature] = float(raw.get(category) == feature[len(category) + 1 :])
            elif raw.get(feature) is not None:
                row[feature] = float(raw[feature])
            elif feature in self.imputation_values:
                row[feature] = float(self.imputation_values[feature])
                imputed.append(feature)
            else:
                raise ValueError(f"Missing required feature with no imputation value: {feature}")
        return pd.DataFrame([row], columns=self.features), imputed

    def decision_path(self, x: pd.DataFrame) -> list[PathStep]:
        tree = self.model.tree_
        steps = []
        for node in self.model.decision_path(x).indices:
            if tree.children_left[node] == tree.children_right[node]:  # leaf
                continue
            feature = self.features[tree.feature[node]]
            value = float(x.iloc[0][feature])
            threshold = float(tree.threshold[node])
            steps.append(PathStep(feature, value, round(threshold, 4), "<=" if value <= threshold else ">"))
        return steps

    def predict(self, raw: dict[str, Any]) -> dict[str, Any]:
        x, imputed = self.build_features(raw)
        at_risk = int(self.model.predict(x)[0])
        risk_probability = float(self.model.predict_proba(x)[0, 1])
        path = self.decision_path(x)

        path_features = list(dict.fromkeys(step.feature for step in path))
        top_features = sorted(
            ({"name": f, "importance": round(self.importances.get(f, 0.0), 4)} for f in path_features),
            key=lambda item: item["importance"],
            reverse=True,
        )
        return {
            "at_risk": at_risk,
            "risk_probability": round(risk_probability, 4),
            "top_features": top_features,
            "decision_path": [step.__dict__ for step in path],
            "explanation": self.explain(at_risk, path),
            "imputed_features": imputed,
            "model_version": self.config["model_version"],
        }

    @staticmethod
    def explain(at_risk: int, path: list[PathStep]) -> str:
        reasons = []
        for step in path:
            label = FEATURE_LABELS.get(step.feature, step.feature)
            value = _format_value(step.feature, step.value)
            threshold = _format_value(step.feature, step.threshold)
            relation = "at or below" if step.direction == "<=" else "above"
            reasons.append(f"{label} ({value}) is {relation} {threshold}")
        verdict = (
            "The model flagged this course as at risk."
            if at_risk
            else "The model did not flag this course as at risk."
        )
        return f"{verdict} It checked: " + "; ".join(reasons) + "."

    def info(self) -> dict[str, Any]:
        keys = (
            "model_version",
            "trained_on",
            "features",
            "used_features",
            "unused_features",
            "hyperparameters",
            "metrics",
            "baseline_metrics",
            "feature_importances",
            "n_train",
            "n_test",
            "leaves",
        )
        info = {k: self.config[k] for k in keys if k in self.config}
        return json.loads(
            json.dumps(info, default=lambda o: o.item() if isinstance(o, np.generic) else str(o))
        )
