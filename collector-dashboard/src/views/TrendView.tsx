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

// Only the fields used here; the API returns more (id, position, notes, …).
interface RaceResultDto {
  id: string
  raceDate: string
  raceName: string
  distanceMeters: number
  goalFinishTime: number | null
  actualFinishTime: number | null
}

type ChartRow = TrainingLoadItem & { race: RaceResultDto | null }

const DAY_OPTIONS = [30, 60, 90] as const
type Days = (typeof DAY_OPTIONS)[number]

const pad = (n: number) => String(n).padStart(2, '0')

// Seconds → clock: "2:55:30" (with hours) or "25:30" (under an hour).
const fmtFinish = (secs: number): string => {
  const h = Math.floor(secs / 3600)
  const m = Math.floor((secs % 3600) / 60)
  const s = secs % 60
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`
}

// Prefer the actual result; fall back to the goal (labelled); omit if neither.
const fmtFinishLabel = (race: RaceResultDto): string | null => {
  if (race.actualFinishTime != null) return fmtFinish(race.actualFinishTime)
  if (race.goalFinishTime != null) return `${fmtFinish(race.goalFinishTime)} (goal)`
  return null
}

const fmtRaceDist = (m: number) => `${(m / 1000).toFixed(1)} km`

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

// Custom tooltip: the existing CTL/ATL/TSB rows, plus a race block on days that
// carry a race (merged into the data point's payload).
const TrendTooltip = (props: {
  active?: boolean
  label?: string | number
  payload?: Array<{ name?: string; value?: number | string; color?: string; payload?: ChartRow }>
}) => {
  const { active, label, payload } = props
  if (!active || !payload || payload.length === 0) return null
  const race = payload[0]?.payload?.race ?? null
  const finish = race ? fmtFinishLabel(race) : null

  return (
    <div className="rounded-md border border-gray-200 bg-white px-3 py-2 text-xs shadow-sm">
      <p className="font-medium text-gray-700 mb-1">
        {new Date(String(label)).toLocaleDateString()}
      </p>
      {payload.map((p) => (
        <p key={p.name} style={{ color: p.color }}>
          {p.name}: {p.value != null ? Number(p.value).toFixed(1) : '–'}
        </p>
      ))}
      {race && (
        <div className="mt-1 pt-1 border-t border-gray-100 text-violet-700">
          <p className="font-semibold">🏁 {race.raceName}</p>
          <p className="text-gray-600">
            {fmtRaceDist(race.distanceMeters)}
            {finish && ` · ${finish}`}
          </p>
        </div>
      )}
    </div>
  )
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
  const [races, setRaces] = useState<RaceResultDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  // Degraded state: training-load loaded but the supplementary race-results
  // fetch failed. The chart still renders — just without race markers.
  const [racesUnavailable, setRacesUnavailable] = useState(false)

  useEffect(() => {
    setLoading(true)
    setError(null)
    setRacesUnavailable(false)

    const to = new Date()
    const from = new Date()
    from.setDate(from.getDate() - days)
    const toStr = to.toISOString().split('T')[0]
    const fromStr = from.toISOString().split('T')[0]

    // allSettled (not all): race-results is supplementary (markers). Its failure
    // must not blank the primary CTL/ATL/TSB chart, which needs only training-load.
    Promise.allSettled([
      apiFetch<TrainingLoadResponse>(
        `/api/v1/athletes/${athleteId}/training-load?days=${days}`,
        apiKey
      ),
      apiFetch<RaceResultDto[]>(
        `/api/v1/athletes/${athleteId}/race-results?from=${fromStr}&to=${toStr}`,
        apiKey
      ),
    ])
      .then(([loadResult, racesResult]) => {
        // Primary data missing → keep the existing error state (chart cannot render).
        if (loadResult.status === 'rejected') {
          const reason = loadResult.reason
          setError(reason instanceof Error ? reason.message : 'Failed to load training load')
          return
        }

        setData(loadResult.value.items)

        if (racesResult.status === 'fulfilled') {
          // Belt-and-suspenders: keep only races inside the visible window
          // (ISO date strings compare lexicographically = chronologically).
          setRaces(racesResult.value.filter((r) => r.raceDate >= fromStr && r.raceDate <= toStr))
        } else {
          // Supplementary data failed → render the chart without markers.
          setRaces([])
          setRacesUnavailable(true)
        }
      })
      .finally(() => setLoading(false))
  }, [athleteId, apiKey, days])

  const raceByDate = new Map(races.map((r) => [r.raceDate, r]))
  const chartData: ChartRow[] = data.map((item) => ({
    ...item,
    race: raceByDate.get(item.date) ?? null,
  }))

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
            <span className="flex items-center gap-1">
              <span className="text-sm leading-none">🏁</span> Race
            </span>
          </div>
          {racesUnavailable && (
            <p className="text-xs text-gray-400">
              Race markers unavailable — couldn’t load race results. Showing training load only.
            </p>
          )}
          <ResponsiveContainer width="100%" height={360}>
            <ComposedChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="date" tickFormatter={fmt} tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip content={<TrendTooltip />} />
              <Legend />
              <ReferenceLine y={0} stroke="#9ca3af" strokeDasharray="4 2" />
              <ReferenceLine y={-10} stroke="#fca5a5" strokeDasharray="4 2" label={{ value: '−10', fontSize: 10, fill: '#ef4444' }} />
              {races.map((race) => (
                <ReferenceLine
                  key={race.id}
                  x={race.raceDate}
                  stroke="#8b5cf6"
                  strokeDasharray="3 3"
                  label={{ value: '🏁', position: 'top', fontSize: 12 }}
                />
              ))}
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
