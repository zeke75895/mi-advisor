import { useEffect, useState } from 'react'
import useAsync from '../lib/useAsync'
import { useParams } from 'react-router'
import { studyApi } from '../api/client'
import AiLabel from '../components/AiLabel'
import { Button, ErrorBanner, Loading } from '../components/Field'
import NotesInput from '../components/NotesInput'
import StudyHeader from '../components/StudyHeader'
import useCourse from '../components/useCourse'

const LETTERS = ['A', 'B', 'C', 'D']

export default function QuizPage() {
  const { courseId } = useParams()
  const { course, error: courseError } = useCourse(courseId)
  const saved = useAsync(() => studyApi.latestQuestions(courseId), [courseId])
  const [quiz, setQuiz] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [index, setIndex] = useState(0)
  const [answers, setAnswers] = useState({}) // question index -> chosen option
  const [checked, setChecked] = useState({}) // question index -> true once checked

  useEffect(() => {
    if (saved.data) restart(saved.data)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [saved.data])

  function restart(next = quiz) {
    setQuiz(next)
    setIndex(0)
    setAnswers({})
    setChecked({})
  }

  async function generate() {
    setBusy(true)
    setError(null)
    try {
      restart(await studyApi.questions(courseId))
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const total = quiz?.questions.length ?? 0
  const finished = quiz && index >= total
  const score = quiz ? quiz.questions.filter((q, i) => checked[i] && answers[i] === q.correctOptionIndex).length : 0

  return (
    <div className="space-y-6">
      <StudyHeader course={course} courseId={courseId} title="Practice quiz" />
      <ErrorBanner error={courseError} />
      {saved.loading && !quiz && <Loading label="Loading your saved quiz…" />}
      <ErrorBanner error={saved.error && `Couldn't load your saved quiz: ${saved.error}`} onRetry={saved.reload} />
      <NotesInput courseId={courseId} actionLabel={quiz ? 'Make a new quiz' : 'Make a 5-question quiz'} busy={busy} error={error} onGenerate={generate} />

      {quiz && (
        <section className="space-y-4">
          <AiLabel disclaimer={quiz.disclaimer} />
          {finished ? (
            <div className="rounded-xl border border-line bg-surface p-6 text-center">
              <p className="text-sm font-semibold uppercase tracking-wide text-muted">Your score</p>
              <p className="mt-1 text-4xl font-bold tabular-nums">
                {score} / {total}
              </p>
              <p className="mt-2 text-ink-2">
                {score === total ? 'Perfect. You know this material.' : score >= total / 2 ? 'Solid. Review the ones you missed.' : 'Worth another pass through your notes, then try again.'}
              </p>
              <Button className="mt-4" onClick={() => restart()}>
                Try again
              </Button>
            </div>
          ) : (
            <QuestionCard
              q={quiz.questions[index]}
              number={index + 1}
              total={total}
              chosen={answers[index]}
              checked={Boolean(checked[index])}
              onChoose={(o) => setAnswers((a) => ({ ...a, [index]: o }))}
              onCheck={() => setChecked((c) => ({ ...c, [index]: true }))}
              onNext={() => setIndex((i) => i + 1)}
              last={index === total - 1}
            />
          )}
        </section>
      )}
    </div>
  )
}

function QuestionCard({ q, number, total, chosen, checked, onChoose, onCheck, onNext, last }) {
  const correct = chosen === q.correctOptionIndex
  return (
    <div className="rounded-xl border border-line bg-surface p-5">
      <div className="flex items-center justify-between text-xs text-muted">
        <span className="font-semibold uppercase tracking-wide">
          Question {number} of {total}
        </span>
        <div className="h-1.5 w-24 rounded-full bg-accent-soft" aria-hidden>
          <div className="h-1.5 rounded-full bg-accent" style={{ width: `${(number / total) * 100}%` }} />
        </div>
      </div>
      <fieldset className="mt-3">
        <legend className="text-lg font-semibold leading-snug">{q.question}</legend>
        <div className="mt-4 space-y-2">
          {q.options.map((option, i) => {
            const isAnswer = i === q.correctOptionIndex
            const isChosen = i === chosen
            let style = 'border-line hover:border-accent'
            let mark = null
            if (checked && isAnswer) {
              style = 'border-good bg-good/5'
              mark = <span className="font-semibold text-ink">✓ Correct answer</span>
            } else if (checked && isChosen) {
              style = 'border-critical bg-critical/5'
              mark = <span className="font-semibold text-ink">✕ Your answer</span>
            } else if (isChosen) {
              style = 'border-accent bg-accent-soft'
            }
            return (
              <label key={option} className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm ${style} ${checked ? 'cursor-default' : ''}`}>
                <input
                  type="radio"
                  name={`q-${number}`}
                  className="mt-0.5"
                  checked={isChosen}
                  disabled={checked}
                  onChange={() => onChoose(i)}
                />
                <span className="flex-1">
                  <span className="font-semibold">{LETTERS[i]}.</span> {option}
                </span>
                {mark && <span className="text-xs">{mark}</span>}
              </label>
            )
          })}
        </div>
      </fieldset>
      {checked && (
        <div role="status" className="mt-4 rounded-md bg-page p-3 text-sm">
          <p className="font-semibold">{correct ? '✓ Correct!' : `✕ Not quite. The answer is ${LETTERS[q.correctOptionIndex]}.`}</p>
          <p className="mt-1 text-ink-2">{q.explanation}</p>
        </div>
      )}
      <div className="mt-4 flex justify-end">
        {checked ? (
          <Button onClick={onNext}>{last ? 'See score' : 'Next question →'}</Button>
        ) : (
          <Button onClick={onCheck} disabled={chosen == null}>
            Check answer
          </Button>
        )}
      </div>
    </div>
  )
}
