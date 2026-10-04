import { useEffect, useRef, useState } from 'react'
import { courseApi } from '../api/client'
import { CATEGORY_LABELS } from '../lib/format'
import ItemForm from './ItemForm'

const SAVE_DELAY_MS = 500

/** One graded item with a 1-10 confidence slider (auto-saves), plus edit and delete. */
export default function ItemRow({ item, onRated, onChanged }) {
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [rowError, setRowError] = useState(null)
  const [rating, setRating] = useState(item.latestRating ?? 5)
  const [state, setState] = useState(item.latestRating ? 'saved' : 'unrated')
  const timer = useRef(null)

  useEffect(() => () => clearTimeout(timer.current), [])

  function change(value) {
    setRating(value)
    setState('pending')
    clearTimeout(timer.current)
    timer.current = setTimeout(async () => {
      setState('saving')
      try {
        await courseApi.rate(item.id, value)
        setState('saved')
        onRated?.()
      } catch {
        setState('error')
      }
    }, SAVE_DELAY_MS)
  }

  const grade =
    item.isGraded && item.pointsPossible ? Math.round((item.pointsEarned / item.pointsPossible) * 100) : null
  const status = { unrated: 'Not rated', pending: '…', saving: 'Saving…', saved: 'Saved', error: 'Not saved, try again' }[
    state
  ]

  async function remove() {
    if (!window.confirm(`Delete "${item.name}" and its confidence ratings?`)) return
    setDeleting(true)
    setRowError(null)
    try {
      await courseApi.deleteItem(item.id)
      onChanged?.()
    } catch (e) {
      setRowError(`Couldn't delete: ${e.message}`)
      setDeleting(false)
    }
  }

  if (editing) {
    return (
      <li>
        <ItemForm
          item={item}
          submitLabel="Save changes"
          busyLabel="Saving…"
          onCancel={() => setEditing(false)}
          onSubmit={async (updated) => {
            await courseApi.updateItem(item.id, updated)
            setEditing(false)
            onChanged?.()
          }}
        />
      </li>
    )
  }

  return (
    <li className={`rounded-lg border border-line bg-surface p-4 ${deleting ? 'opacity-50' : ''}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-medium">{item.name}</p>
          <p className="text-xs text-ink-2">
            {CATEGORY_LABELS[item.category]} · {item.weight}% of grade
            {item.dueDate && ` · due ${new Date(item.dueDate).toLocaleDateString()}`}
          </p>
        </div>
        <div className="flex items-center gap-1">
          <span
            className={`rounded-md px-2 py-1 text-sm font-semibold tabular-nums ${grade != null ? 'bg-accent-soft' : 'bg-line/50 text-ink-2'}`}
          >
            {grade != null ? `${grade}%` : 'Not graded'}
          </span>
          <button
            type="button"
            onClick={() => setEditing(true)}
            className="rounded-md px-2 py-1 text-xs font-semibold text-accent-strong hover:bg-accent-soft"
            aria-label={`Edit ${item.name}`}
          >
            Edit
          </button>
          <button
            type="button"
            onClick={remove}
            disabled={deleting}
            className="rounded-md px-2 py-1 text-xs font-semibold text-ink-2 hover:bg-critical/10"
            aria-label={`Delete ${item.name}`}
          >
            Delete
          </button>
        </div>
      </div>
      {rowError && <p role="alert" className="mt-2 text-xs font-medium text-critical">{rowError}</p>}
      <div className="mt-3">
        <div className="flex items-center justify-between text-xs text-ink-2">
          <label htmlFor={`rating-${item.id}`}>{item.isGraded ? 'How confident did it feel?' : 'How confident do you feel?'}</label>
          <span className={state === 'error' ? 'font-medium text-critical' : ''}>{status}</span>
        </div>
        <div className="mt-1 flex items-center gap-3">
          <input
            id={`rating-${item.id}`}
            type="range"
            min="1"
            max="10"
            step="1"
            value={rating}
            onChange={(e) => change(Number(e.target.value))}
            className={`h-2 w-full cursor-pointer ${state === 'unrated' ? 'opacity-40' : ''}`}
            aria-valuetext={`${rating} out of 10`}
          />
          <span className={`w-10 text-right text-sm font-semibold tabular-nums ${state === 'unrated' ? 'text-muted' : ''}`}>
            {state === 'unrated' ? '–' : `${rating}/10`}
          </span>
        </div>
      </div>
    </li>
  )
}
