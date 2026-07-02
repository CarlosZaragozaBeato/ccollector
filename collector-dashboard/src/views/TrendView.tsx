import { useEffect, useState } from 'react'
import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'
import { apiFetch } from '../api/client'

interface TrainingLoadItem {
  date: string
  tssDay: number | null
  ctl: number | null
  atl: number | null
  tsb: number | null
}
interface TrainingLoadResponse {
  days: number
  items: TrainingLoadItem[]
}

const DAY_OPTIONS = [30, 60, 90] as const
type Days = (typeof DAY_OPTIONS)[number]

// Dot color driven by TSB value: green = fresh (>0), red = overreached (<-10), amber = neutral.
const TsbDot = (props: {
  cx?: number
  cy?: number
  payload?: TrainingLoadItem
}) => {
  const { cx, cy, payload } = props
  if (cx == null || cy == null || payload?.tsb == null) return null
  const tsb = payload.tsb
  const color = tsb > 0 ? '#22c55e' : tsb < -10 ? '#ef4444' : '#f59e0b'
  return <circle cx={cx} cy={cy} r={3} fill={color} stroke="white" strokeWidth={1} />
}

const fmt = (d: string) => {
  const dt = new Date(d)
  return `${dt.getMonth() + 1}/${dt.getDate()}`
}

export default function TrendView({
  athleteId,
  apiKey,
}: {
  athleteId: string
  apiKey: string
}) {
  const [days, setDays] = useState<Days>(90)
  const [data, setData] = useState<TrainingLoadItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    apiFetch<TrainingLoadResponse>(
      `/api/v1/athletes/${athleteId}/training-load?days=${days}`,
      apiKey
    )
      .then((r) => setData(r.items))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [athleteId, apiKey, days])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-800">Fitness Trends (CTL / ATL / TSB)</h2>
        <div className="flex gap-2">
          {DAY_OPTIONS.map((d) => (
            <button
              key={d}
              onClick={() => setDays(d)}
              className={`px-3 py-1 rounded text-sm font-medium transition-colors ${
                days === d
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {d}d
            </button>
          ))}
        </div>
      </div>

      {loading && (
        <div className="flex items-center justify-center h-64 text-gray-500">
          <svg className="animate-spin h-6 w-6 mr-2" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
          </svg>
          Loading…
        </div>
      )}

      {error && (
        <div className="rounded-md bg-red-50 border border-red-200 p-4 text-red-700 text-sm">
          <strong>Error:</strong> {error}
        </div>
      )}

      {!loading && !error && (
        <>
          <div className="flex gap-4 text-xs text-gray-500">
            <span className="flex items-center gap-1">
              <span className="inline-block w-3 h-3 rounded-full bg-green-500" /> TSB &gt; 0 (fresh)
            </span>
            <span className="flex items-center gap-1">
              <span className="inline-block w-3 h-3 rounded-full bg-amber-400" /> TSB −10 to 0
            </span>
            <span className="flex items-center gap-1">
              <span className="inline-block w-3 h-3 rounded-full bg-red-500" /> TSB &lt; −10 (overreached)
            </span>
          </div>
          <ResponsiveContainer width="100%" height={360}>
            <ComposedChart data={data} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="date" tickFormatter={fmt} tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip
                labelFormatter={(l: string) => new Date(l).toLocaleDateString()}
                formatter={(v: number, name: string) => [
                  v != null ? v.toFixed(1) : '–',
                  name.toUpperCase(),
                ]}
              />
              <Legend />
              <ReferenceLine y={0} stroke="#9ca3af" strokeDasharray="4 2" />
              <ReferenceLine y={-10} stroke="#fca5a5" strokeDasharray="4 2" label={{ value: '−10', fontSize: 10, fill: '#ef4444' }} />
              <Line
                dataKey="ctl"
                name="CTL (fitness)"
                stroke="#3b82f6"
                strokeWidth={2}
                dot={false}
              />
              <Line
                dataKey="atl"
                name="ATL (fatigue)"
                stroke="#f97316"
                strokeWidth={2}
                dot={false}
              />
              <Line
                dataKey="tsb"
                name="TSB (form)"
                stroke="#6b7280"
                strokeWidth={1.5}
                dot={<TsbDot />}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </>
      )}
    </div>
  )
}
