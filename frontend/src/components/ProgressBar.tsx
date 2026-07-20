export function ProgressBar({ percent }: { percent: number }) {
  const clamped = Math.max(0, Math.min(100, percent))
  const filled = Math.round(clamped / 10)
  const bar = '█'.repeat(filled) + '░'.repeat(10 - filled)
  return (
    <div className="progress" aria-label={`${clamped}%`}>
      <span className="progress-bar" aria-hidden>
        {bar}
      </span>
      <span className="progress-pct">{clamped}%</span>
    </div>
  )
}
