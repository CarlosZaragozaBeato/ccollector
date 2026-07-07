import { RaceRow, TrainingLoadPoint } from './insights'

// One CTL/ATL/TSB cell for a single look-back point. When the point is
// unavailable we say so explicitly rather than showing a blank or a zero — a
// missing training-load row must never read as "fitness was 0".
function PointCell({ point }: { point: TrainingLoadPoint }) {
  if (!point.available || point.ctl == null) {
    return <span className="text-gray-400 italic">insufficient data</span>
  }
  const metric = (label: string, value: number | null) => (
    <span className="tabular-nums">
      {label} {value != null ? value.toFixed(1) : '–'}
    </span>
  )
  return (
    <div className="flex flex-col text-xs text-gray-600 leading-tight">
      {metric('CTL', point.ctl)}
      {metric('ATL', point.atl)}
      {metric('TSB', point.tsb)}
    </div>
  )
}

const fmtDate = (d: string) => new Date(d).toLocaleDateString()

// The per-race table shown in BOTH the below-threshold and at-threshold states,
// so any insight statement is always backed by the visible underlying data.
export default function RacePerformanceTable({ rows }: { rows: RaceRow[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="border-b border-gray-200 text-left text-xs uppercase tracking-wide text-gray-500">
            <th className="py-2 pr-4 font-medium">Race</th>
            <th className="py-2 pr-4 font-medium">Date</th>
            <th className="py-2 pr-4 font-medium">Result</th>
            <th className="py-2 pr-4 font-medium">Race day</th>
            <th className="py-2 pr-4 font-medium">7 days before</th>
            <th className="py-2 pr-4 font-medium">42 days before</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id} className="border-b border-gray-100 align-top">
              <td className="py-2 pr-4 font-medium text-gray-800">
                <span className="mr-1">🏁</span>
                {row.raceName}
              </td>
              <td className="py-2 pr-4 text-gray-600 whitespace-nowrap">{fmtDate(row.raceDate)}</td>
              <td className="py-2 pr-4 text-gray-600 whitespace-nowrap">
                {row.resultLabel ?? <span className="text-gray-400">—</span>}
              </td>
              <td className="py-2 pr-4">
                <PointCell point={row.atRaceDate} />
              </td>
              <td className="py-2 pr-4">
                <PointCell point={row.at7DaysBefore} />
              </td>
              <td className="py-2 pr-4">
                <PointCell point={row.at42DaysBefore} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
