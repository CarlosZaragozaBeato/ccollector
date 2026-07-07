// Pure, side-effect-free reducer for the Insights tab.
//
// Kept separate from the React view (InsightsView.tsx) so the statistical
// framing can be verified in isolation — there is no component test harness in
// this project (see #34/#35/#40), so the honesty-critical logic lives here as a
// pure function and is checked by an isolated logic script + compiled-bundle
// inspection, matching the #40 verification precedent.

// ---- API response shape (subset of #44's RacePerformanceDto we consume) ----

export interface TrainingLoadPoint {
  requestedDate: string
  actualDate: string | null
  ctl: number | null
  atl: number | null
  tsb: number | null
  available: boolean
}

export interface RaceInfo {
  id: string
  raceDate: string
  raceName: string
  distanceMeters: number
  goalFinishTime: number | null
  actualFinishTime: number | null
  position: number | null
}

export interface RacePerformance {
  race: RaceInfo
  atRaceDate: TrainingLoadPoint
  at7DaysBefore: TrainingLoadPoint
  at42DaysBefore: TrainingLoadPoint
}

// ---- Reducer output ----

export interface RaceRow {
  id: string
  raceName: string
  raceDate: string
  resultLabel: string | null
  atRaceDate: TrainingLoadPoint
  at7DaysBefore: TrainingLoadPoint
  at42DaysBefore: TrainingLoadPoint
}

export interface InsightStatement {
  scoreableCount: number
  strongerCount: number
  strongerCtlMin: number
  strongerCtlMax: number
  // Undefined when the "weaker" group is empty — happens when every scoreable
  // race has the exact same goal margin (a tie), so the median split leaves no
  // race above the median. The second sentence is then omitted entirely rather
  // than rendering an empty or NaN range.
  weakerCtlMin?: number
  weakerCtlMax?: number
}

export interface InsightsResult {
  totalRaces: number
  rows: RaceRow[]
  // Non-null only when >= 3 scoreable races exist (race-day CTL + a recorded
  // goal and actual finish). Below that threshold we never assert a pattern.
  insight: InsightStatement | null
}

// The minimum number of scoreable races before we state any pattern. A
// "scoreable" race is one with complete-enough data to place it on both axes of
// the correlation: race-day CTL (chronic fitness) AND a recorded goal + actual
// finish time (to measure how the race went vs. intent). Below this we show the
// raw per-race data only, with no correlation claim.
export const INSIGHT_MIN_RACES = 3

const pad = (n: number) => String(n).padStart(2, '0')

// Seconds → clock: "2:55:30" (with hours) or "25:30" (under an hour).
// Mirrors TrendView.fmtFinish so a finish reads identically across tabs.
export function formatFinish(secs: number): string {
  const h = Math.floor(secs / 3600)
  const m = Math.floor((secs % 3600) / 60)
  const s = secs % 60
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`
}

// Prefer the actual result; fall back to the goal (labelled); else the finish
// position; else null (no recorded outcome).
function resultLabel(race: RaceInfo): string | null {
  if (race.actualFinishTime != null) return formatFinish(race.actualFinishTime)
  if (race.goalFinishTime != null) return `${formatFinish(race.goalFinishTime)} (goal)`
  if (race.position != null) return `P${race.position}`
  return null
}

function isScoreable(rp: RacePerformance): boolean {
  return (
    rp.atRaceDate.available &&
    rp.atRaceDate.ctl != null &&
    rp.race.goalFinishTime != null &&
    rp.race.actualFinishTime != null
  )
}

// Lower is better: seconds by which the actual finish beat (negative) or missed
// (positive) the goal.
function goalMargin(rp: RacePerformance): number {
  return (rp.race.actualFinishTime as number) - (rp.race.goalFinishTime as number)
}

function median(sortedAsc: number[]): number {
  const n = sortedAsc.length
  const mid = Math.floor(n / 2)
  return n % 2 === 1 ? sortedAsc[mid] : (sortedAsc[mid - 1] + sortedAsc[mid]) / 2
}

export function computeInsights(data: RacePerformance[]): InsightsResult {
  const rows: RaceRow[] = [...data]
    // Newest first (the endpoint already sorts this way; we don't rely on it).
    .sort((a, b) => (a.race.raceDate < b.race.raceDate ? 1 : a.race.raceDate > b.race.raceDate ? -1 : 0))
    .map((rp) => ({
      id: rp.race.id,
      raceName: rp.race.raceName,
      raceDate: rp.race.raceDate,
      resultLabel: resultLabel(rp.race),
      atRaceDate: rp.atRaceDate,
      at7DaysBefore: rp.at7DaysBefore,
      at42DaysBefore: rp.at42DaysBefore,
    }))

  const scoreable = data.filter(isScoreable)

  if (scoreable.length < INSIGHT_MIN_RACES) {
    return { totalRaces: data.length, rows, insight: null }
  }

  const margins = scoreable.map(goalMargin).sort((a, b) => a - b)
  const med = median(margins)

  // <= median is the "stronger" group: closest to or ahead of goal. Using <=
  // means a tie (all identical margins) puts every race in "stronger" and
  // leaves "weaker" empty — handled below by omitting the second sentence.
  const stronger = scoreable.filter((rp) => goalMargin(rp) <= med)
  const weaker = scoreable.filter((rp) => goalMargin(rp) > med)

  const ctl = (rp: RacePerformance) => rp.atRaceDate.ctl as number
  const strongerCtl = stronger.map(ctl)
  const weakerCtl = weaker.map(ctl)

  const insight: InsightStatement = {
    scoreableCount: scoreable.length,
    strongerCount: stronger.length,
    strongerCtlMin: Math.min(...strongerCtl),
    strongerCtlMax: Math.max(...strongerCtl),
  }
  if (weakerCtl.length > 0) {
    insight.weakerCtlMin = Math.min(...weakerCtl)
    insight.weakerCtlMax = Math.max(...weakerCtl)
  }

  return { totalRaces: data.length, rows, insight }
}

// Renders the insight as observational, past-tense body text. Returns an array
// of sentences so the view can lay them out; the final sentence is always the
// honesty disclaimer. Never implies causation ("came with", not "because of").
export function insightSentences(s: InsightStatement): string[] {
  const range = (min: number, max: number) =>
    min === max ? `${Math.round(min)}` : `${Math.round(min)}–${Math.round(max)}`

  const sentences: string[] = []
  sentences.push(
    `Across your ${s.scoreableCount} races with complete training data, the ` +
      `${s.strongerCount} where you finished closest to or ahead of your goal came ` +
      `with a chronic-fitness (CTL) of ${range(s.strongerCtlMin, s.strongerCtlMax)} on race day.`
  )
  if (s.weakerCtlMin !== undefined && s.weakerCtlMax !== undefined) {
    sentences.push(
      `Your other races had a race-day CTL of ${range(s.weakerCtlMin, s.weakerCtlMax)}.`
    )
  }
  sentences.push(
    'This is an observation from your own race history, not a training recommendation — ' +
      'recording more races will sharpen the picture.'
  )
  return sentences
}
