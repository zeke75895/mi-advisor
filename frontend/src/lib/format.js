// Same thresholds as the backend's FusionService.riskLevel. The tree's probability isn't
// calibrated, so the UI only ever shows the level, never the percentage.
export function riskLevel(probability) {
  if (probability == null) return null
  if (probability > 0.7) return 'high'
  if (probability > 0.4) return 'moderate'
  return 'low'
}

export const FEATURE_LABELS = {
  attendance_rate: 'Class attendance',
  missed_deadlines: 'Missed deadlines',
  on_time_submission_rate: 'On-time submissions',
  avg_practice_quiz_score: 'Practice quiz average',
  midterm_score: 'Midterm score',
  avg_weekly_study_hours: 'Weekly study hours',
  flashcards_reviewed: 'Flashcards reviewed',
  avg_days_started_before_exam: 'Days started before exams',
  late_night_study_pct: 'Late-night studying (12–4 a.m.)',
  avg_sleep_hours: 'Average sleep',
  study_sessions_logged: 'Study sessions logged',
}

export const CATEGORY_LABELS = { EXAM: 'Exam', HOMEWORK: 'Homework', QUIZ: 'Quiz', PROJECT: 'Project' }

export function letterGrade(score) {
  if (score == null) return null
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  if (score >= 60) return 'D'
  return 'F'
}

export const pct = (fraction) => `${Math.round(fraction * 100)}%`

export const RECOMMENDATION_TONE = {
  strong_stay: 'good',
  lean_stay: 'good',
  uncertain: 'warning',
  lean_withdraw: 'serious',
  strong_withdraw: 'critical',
}
