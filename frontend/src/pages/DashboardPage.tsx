import { useEffect, useState } from 'react'

import { Link } from 'react-router-dom'

import {

  fetchActivity,

  fetchRisks,

  fetchWorkstreamProgress,

} from '../api/client'

import type { ActivityEvent, RiskItem, WorkstreamProgressItem } from '../api/types'

import { ProgressBar } from '../components/ProgressBar'

import { EmptyState, ErrorState, Loading } from '../components/Status'

import { eventLabel, riskLabel, riskMarker, workstreamLabel } from '../lib/labels'



type RiskAggregate = {

  code: string

  count: number

}



function aggregateRisks(risks: RiskItem[]): RiskAggregate[] {

  const counts = new Map<string, number>()

  for (const risk of risks) {

    counts.set(risk.code, (counts.get(risk.code) ?? 0) + 1)

  }

  return [...counts.entries()]

    .map(([code, count]) => ({ code, count }))

    .sort((a, b) => b.count - a.count)

}



function groupLatestByIssue(events: ActivityEvent[], limit = 8) {

  const byIssue = new Map<string, ActivityEvent[]>()

  for (const event of events) {

    if (!event.issueKey) continue

    if (!byIssue.has(event.issueKey) && byIssue.size >= limit) {

      continue

    }

    const list = byIssue.get(event.issueKey) ?? []

    if (list.length < 3) {

      list.push(event)

      byIssue.set(event.issueKey, list)

    }

  }

  return [...byIssue.entries()]

}



export function DashboardPage() {

  const [projects, setProjects] = useState<WorkstreamProgressItem[] | null>(null)

  const [riskAggs, setRiskAggs] = useState<RiskAggregate[] | null>(null)

  const [latest, setLatest] = useState<[string, ActivityEvent[]][] | null>(null)

  const [error, setError] = useState<string | null>(null)



  useEffect(() => {

    let cancelled = false

    ;(async () => {

      try {

        const [progress, risks, activity] = await Promise.all([

          fetchWorkstreamProgress(),

          fetchRisks({ limit: 200 }),

          fetchActivity({ limit: 50 }),

        ])

        if (cancelled) return

        setProjects(progress.items)

        setRiskAggs(aggregateRisks(risks.risks))

        setLatest(groupLatestByIssue(activity.events))

        setError(null)

      } catch (err) {

        if (!cancelled) {

          setError(err instanceof Error ? err.message : 'Не удалось загрузить дашборд')

        }

      }

    })()

    return () => {

      cancelled = true

    }

  }, [])



  if (error) return <ErrorState message={error} />

  if (!projects || !riskAggs || !latest) return <Loading />



  return (

    <div className="dashboard">

      <h1>Монитор доставки</h1>



      <section className="panel">

        <h2>Проекты</h2>

        {projects.length === 0 ? (

          <EmptyState>Типы workstream не настроены.</EmptyState>

        ) : (

          <ul className="project-list">

            {projects.map((item) => (

              <li key={item.workstreamType.code}>

                <div className="project-name">

                  {workstreamLabel(

                    item.workstreamType.code,

                    item.workstreamType.displayName,

                  )}

                </div>

                <ProgressBar percent={item.percent} />

              </li>

            ))}

          </ul>

        )}

      </section>



      <section className="panel">

        <h2>Риски</h2>

        {riskAggs.length === 0 ? (

          <EmptyState>Рисков не обнаружено.</EmptyState>

        ) : (

          <ul className="risk-list">

            {riskAggs.map((agg) => (

              <li key={agg.code}>

                <span className="risk-marker" aria-hidden>

                  {riskMarker(agg.code)}

                </span>

                <span className="risk-count">{agg.count}</span>

                <span>{riskLabel(agg.code)}</span>

              </li>

            ))}

          </ul>

        )}

      </section>



      <section className="panel">

        <div className="panel-head">

          <h2>Последняя активность</h2>

          <Link to="/activity" className="subtle-link">

            Открыть ленту

          </Link>

        </div>

        {latest.length === 0 ? (

          <EmptyState>Нет недавней активности.</EmptyState>

        ) : (

          <ul className="latest-list">

            {latest.map(([issueKey, events]) => (

              <li key={issueKey}>

                <Link to={`/issues/${encodeURIComponent(issueKey)}`} className="issue-key">

                  {issueKey}

                </Link>

                <ul className="latest-events">

                  {events.map((event) => (

                    <li key={event.id}>{eventLabel(event.type)}</li>

                  ))}

                </ul>

              </li>

            ))}

          </ul>

        )}

      </section>

    </div>

  )

}

