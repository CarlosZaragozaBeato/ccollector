import { useState } from 'react'

export interface DiaryEntry {
  date: string
  perceivedEffort: number | null
  subjectiveState: string | null
  notes: string | null
  weightKg: number | null
}

const NOTES_EXCERPT_LIMIT = 140

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })

const rpeBadgeClass = (rpe: number): string => {
  if (rpe <= 3) return 'bg-green-100 text-green-800'
  if (rpe <= 6) return 'bg-amber-100 text-amber-800'
  if (rpe <= 8) return 'bg-orange-100 text-orange-800'
  return 'bg-red-100 text-red-800'
}

const STATE_PILL: Record<string, string> = {
  EXCELLENT: 'bg-green-100 text-green-800',
  GOOD: 'bg-emerald-100 text-emerald-800',
  NEUTRAL: 'bg-gray-100 text-gray-700',
  POOR: 'bg-orange-100 text-orange-800',
  BAD: 'bg-red-100 text-red-800',
}

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()

export default function DiaryEntryCard({ entry }: { entry: DiaryEntry }) {
  const [expanded, setExpanded] = useState(false)

  const hasDetails =
    entry.perceivedEffort != null ||
    entry.subjectiveState != null ||
    (entry.notes != null && entry.notes.length > 0) ||
    entry.weightKg != null

  const notesTooLong = entry.notes != null && entry.notes.length > NOTES_EXCERPT_LIMIT
  const notesToShow =
    entry.notes == null
      ? null
      : notesTooLong && !expanded
      ? `${entry.notes.slice(0, NOTES_EXCERPT_LIMIT)}…`
      : entry.notes

  return (
    <div className="rounded-lg border border-gray-200 p-4 hover:bg-gray-50 transition-colors">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h3 className="text-sm font-semibold text-gray-800">{fmtDate(entry.date)}</h3>
        <div className="flex items-center gap-2">
          {entry.perceivedEffort != null && (
            <span
              className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${rpeBadgeClass(
                entry.perceivedEffort
              )}`}
            >
              RPE {entry.perceivedEffort}
            </span>
          )}
          {entry.subjectiveState != null && (
            <span
              className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                STATE_PILL[entry.subjectiveState] ?? 'bg-gray-100 text-gray-700'
              }`}
            >
              {capitalize(entry.subjectiveState)}
            </span>
          )}
          {entry.weightKg != null && (
            <span className="text-xs text-gray-500 font-mono">{entry.weightKg.toFixed(1)} kg</span>
          )}
        </div>
      </div>

      {notesToShow != null && notesToShow.length > 0 && (
        <p className="mt-2 text-sm text-gray-600 whitespace-pre-wrap">
          {notesToShow}
          {notesTooLong && (
            <button
              onClick={() => setExpanded((v) => !v)}
              className="ml-1 text-blue-600 hover:text-blue-800 text-xs font-medium"
            >
              {expanded ? 'Show less' : 'Show more'}
            </button>
          )}
        </p>
      )}

      {!hasDetails && <p className="mt-2 text-sm text-gray-400 italic">No details recorded.</p>}
    </div>
  )
}
