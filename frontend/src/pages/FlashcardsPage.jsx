import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { studyApi } from '../api/client'
import AiLabel from '../components/AiLabel'
import { Button, ErrorBanner } from '../components/Field'
import NotesInput from '../components/NotesInput'
import StudyHeader from '../components/StudyHeader'
import useCourse from '../components/useCourse'

export default function FlashcardsPage() {
  const { courseId } = useParams()
  const { course, error: courseError } = useCourse(courseId)
  const [set, setSet] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [flipped, setFlipped] = useState({})

  useEffect(() => {
    studyApi.latestFlashcards(courseId).then(setSet).catch(() => {})
  }, [courseId])

  async function generate() {
    setBusy(true)
    setError(null)
    try {
      setSet(await studyApi.flashcards(courseId))
      setFlipped({})
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  function shuffle() {
    const cards = [...set.flashcards]
    for (let i = cards.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[cards[i], cards[j]] = [cards[j], cards[i]]
    }
    setSet({ ...set, flashcards: cards })
    setFlipped({})
  }

  return (
    <div className="space-y-6">
      <StudyHeader course={course} courseId={courseId} title="Flashcards" />
      <ErrorBanner error={courseError} />
      <NotesInput courseId={courseId} actionLabel={set ? 'Make new flashcards' : 'Make 10 flashcards'} busy={busy} error={error} onGenerate={generate} />

      {set && (
        <section className="space-y-4">
          <AiLabel disclaimer={set.disclaimer} />
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm text-ink-2">
              {set.flashcards.length} cards · tap a card to flip it ·{' '}
              {Object.values(flipped).filter(Boolean).length} flipped
            </p>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={shuffle}>
                Shuffle
              </Button>
              <Button variant="secondary" onClick={() => setFlipped({})}>
                Reset
              </Button>
            </div>
          </div>
          <ul className="grid gap-4 sm:grid-cols-2">
            {set.flashcards.map((card, i) => (
              <li key={`${i}-${card.question}`}>
                <FlipCard
                  index={i + 1}
                  card={card}
                  flipped={Boolean(flipped[i])}
                  onFlip={() => setFlipped((f) => ({ ...f, [i]: !f[i] }))}
                />
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

function FlipCard({ index, card, flipped, onFlip }) {
  const face = 'absolute inset-0 flex flex-col rounded-xl border border-line p-5 backface-hidden'
  return (
    <button
      type="button"
      onClick={onFlip}
      aria-pressed={flipped}
      aria-label={flipped ? `Card ${index} answer: ${card.answer}` : `Card ${index} question: ${card.question}. Show answer`}
      className="group h-48 w-full perspective-distant text-left focus:outline-none"
    >
      <div
        className={`relative h-full w-full rounded-xl transition-transform duration-500 transform-3d group-focus-visible:ring-2 group-focus-visible:ring-accent/50 ${flipped ? 'rotate-y-180' : ''}`}
      >
        <div className={`${face} bg-surface`}>
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">Question {index}</span>
          <p className="mt-2 flex-1 overflow-auto font-medium leading-snug">{card.question}</p>
          <span className="text-xs text-accent-strong">Tap to see answer ↻</span>
        </div>
        <div className={`${face} rotate-y-180 bg-accent-soft`}>
          <span className="text-xs font-semibold uppercase tracking-wide text-accent-strong">Answer</span>
          <p className="mt-2 flex-1 overflow-auto text-sm leading-relaxed">{card.answer}</p>
          <span className="text-xs text-ink-2">Tap to flip back ↻</span>
        </div>
      </div>
    </button>
  )
}
