/** Shown on everything Gemini wrote. */
export default function AiLabel({ disclaimer }) {
  return (
    <div className="flex flex-wrap items-start gap-2 rounded-md border border-line bg-page px-3 py-2 text-xs text-ink-2">
      <span className="rounded bg-ink px-1.5 py-0.5 font-semibold tracking-wide text-white">AI-generated</span>
      <span className="flex-1">{disclaimer ?? 'Created by AI. It can contain mistakes, so check it against your course materials.'}</span>
    </div>
  )
}
