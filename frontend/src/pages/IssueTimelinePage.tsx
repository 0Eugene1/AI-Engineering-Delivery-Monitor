import { useEffect, useMemo, useState } from 'react'

import { Link, useParams } from 'react-router-dom'

import { fetchIssue, fetchTimeline } from '../api/client'

import type { IssueResponse, TimelineEvent } from '../api/types'

import { EmptyState, ErrorState, Loading } from '../components/Status'

import { eventDetail, formatDay } from '../lib/format'

import { eventIcon, eventLabel, workstreamLabel } from '../lib/labels'



function detailLine(event: TimelineEvent): string | null {

  return eventDetail(event.type, event.payload)

}



export function IssueTimelinePage() {

  const { key = '' } = useParams()

  const issueKey = decodeURIComponent(key)

  const [issue, setIssue] = useState<IssueResponse | null | undefined>(undefined)

  const [events, setEvents] = useState<TimelineEvent[] | null>(null)

  const [error, setError] = useState<string | null>(null)



  useEffect(() => {

    if (!issueKey) return

    let cancelled = false

    ;(async () => {

      try {

        const [timeline, issueResult] = await Promise.all([

          fetchTimeline(issueKey),

          fetchIssue(issueKey),

        ])

        if (cancelled) return

        setEvents(timeline.events)

        setIssue(issueResult)

        setError(null)

      } catch (err) {

        if (!cancelled) {

          setError(err instanceof Error ? err.message : 'Не удалось загрузить историю задачи')

        }

      }

    })()

    return () => {

      cancelled = true

    }

  }, [issueKey])



  const chronological = useMemo(

    () => (events ? [...events].reverse() : []),

    [events],

  )



  if (error) return <ErrorState message={error} />

  if (events === null || issue === undefined) return <Loading />



  return (

    <div className="timeline-page">

      <p className="breadcrumb">

        <Link to="/">Дашборд</Link>

        <span aria-hidden> / </span>

        <span>{issueKey}</span>

      </p>



      <h1 className="issue-title">{issueKey}</h1>



      <section className="panel">

        <h2>Jira</h2>

        <div className="jira-block">

          <div className="field-label">Статус</div>

          <div>{issue?.status ?? '—'}</div>

          {issue?.summary ? <p className="issue-summary">{issue.summary}</p> : null}

          {!issue ? (

            <p className="muted">

              Задача отсутствует в БД Monitor (только Git-ключ). История всё равно показана.

            </p>

          ) : null}

        </div>

      </section>



      <section className="panel">

        <h2>История задачи</h2>

        {chronological.length === 0 ? (

          <EmptyState>Нет событий</EmptyState>

        ) : (

          <ol className="timeline-list">

            {chronological.map((event) => {

              const detail = detailLine(event)

              return (

                <li key={event.id} className="timeline-item">

                  <div className="timeline-day">{formatDay(event.occurredAt)}</div>

                  <div className="timeline-body">

                    <div className="timeline-headline">

                      <span aria-hidden>{eventIcon(event.type)}</span>

                      <span>{eventLabel(event.type)}</span>

                      {event.workstreamType ? (

                        <span className="pill">

                          {workstreamLabel(

                            event.workstreamType.code,

                            event.workstreamType.displayName,

                          )}

                        </span>

                      ) : null}

                    </div>

                    {detail ? <div className="timeline-detail">{detail}</div> : null}

                  </div>

                </li>

              )

            })}

          </ol>

        )}

      </section>

    </div>

  )

}

