import { useEffect, useState } from 'react'
import { courseApi, featuresApi } from '../api/client'
import AiLabel from './AiLabel'
import { Button } from './Field'

// One features check per page load; a failed check is retried next time.
let voicePromise = null
function voiceEnabled() {
  voicePromise ??= featuresApi.get().then((f) => Boolean(f?.voiceBriefing)).catch(() => {
    voicePromise = null
    return false
  })
  return voicePromise
}

function toAudioUrl(base64, mimeType) {
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))
  return URL.createObjectURL(new Blob([bytes], { type: mimeType }))
}

/** "Listen to your briefing": Gemini writes a short script from the recommendation, ElevenLabs reads it. */
export default function BriefingPlayer({ courseId }) {
  const [enabled, setEnabled] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [briefing, setBriefing] = useState(null)
  const [audioUrl, setAudioUrl] = useState(null)

  useEffect(() => {
    let alive = true
    voiceEnabled().then((on) => alive && setEnabled(on))
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => () => audioUrl && URL.revokeObjectURL(audioUrl), [audioUrl])

  if (!enabled) return null

  async function listen() {
    setBusy(true)
    setError(null)
    try {
      const b = await courseApi.briefing(courseId)
      setBriefing(b)
      setAudioUrl(toAudioUrl(b.audioBase64, b.mimeType))
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mt-4 rounded-md border border-line bg-page p-3">
      {!audioUrl ? (
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="secondary" onClick={listen} disabled={busy}>
            {busy ? 'Preparing your briefing…' : '🔊 Listen to your briefing'}
          </Button>
          <span className="text-xs text-ink-2">A 30-second spoken summary of this course.</span>
        </div>
      ) : (
        <div className="space-y-2">
          <p className="text-sm font-semibold">🔊 Your briefing</p>
          <audio src={audioUrl} controls autoPlay className="w-full" />
          <details className="text-sm">
            <summary className="cursor-pointer text-ink-2">Read the transcript</summary>
            <p className="mt-2 leading-relaxed">{briefing.script}</p>
          </details>
          <AiLabel disclaimer={briefing.disclaimer} />
        </div>
      )}
      {error && <p className="mt-2 text-sm text-critical">{error}</p>}
    </div>
  )
}
