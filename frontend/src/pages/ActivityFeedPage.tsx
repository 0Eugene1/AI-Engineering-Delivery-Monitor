import { useEffect, useMemo, useState } from 'react'

import { Link } from 'react-router-dom'

import { fetchActivity, fetchWorkstreamTypes } from '../api/client'

import type { ActivityEvent, WorkstreamType } from '../api/types'

import { EmptyState, ErrorState, Loading } from '../components/Status'

import { feedDayHeading } from '../lib/format'

import { eventIcon, eventLabel, workstreamLabel } from '../lib/labels'



type DayGroup = {

  heading: string

  key: string

  events: ActivityEvent[]

}



function groupByDay(events: ActivityEvent[]): DayGroup[] {

  const groups: DayGroup[] = []

  const index = new Map<string, DayGroup>()

  for (const event of events) {

    const heading = feedDayHeading(event.occurredAt)

    let group = index.get(heading)

    if (!group) {

      group = { heading, key: heading, events: [] }

      index.set(heading, group)

      groups.push(group)

    }

    group.events.push(event)

  }

  return groups

}



export function ActivityFeedPage() {

  const [events, setEvents] = useState<ActivityEvent[] | null>(null)

  const [types, setTypes] = useState<WorkstreamType[]>([])

  const [filter, setFilter] = useState('')

  const [error, setError] = useState<string | null>(null)



  useEffect(() => {

    let cancelled = false

    ;(async () => {

      try {

        const [activity, workstreamTypes] = await Promise.all([

          fetchActivity({

            limit: 100,

            workstreamType: filter || undefined,

          }),

          fetchWorkstreamTypes(),

        ])

        if (cancelled) return

        setEvents(activity.events)

        setTypes(workstreamTypes)

        setError(null)

      } catch (err) {

        if (!cancelled) {

          setError(err instanceof Error ? err.message : 'Не удалось загрузить активность')

        }

      }

    })()

    return () => {

      cancelled = true

    }

  }, [filter])



  const groups = useMemo(() => (events ? groupByDay(events) : []), [events])



  if (error) return <ErrorState message={error} />

  if (!events) return <Loading />



  return (

    <div className="feed-page">

      <div className="panel-head">

        <h1>Лента активности</h1>

        <label className="filter">

          <span className="sr-only">Тип workstream</span>

          <select value={filter} onChange={(e) => setFilter(e.target.value)}>

            <option value="">Все</option>

            {types.map((type) => (

              <option key={type.code} value={type.code}>

                {workstreamLabel(type.code, type.displayName)}

              </option>

            ))}

          </select>

        </label>

      </div>



      {groups.length === 0 ? (

        <EmptyState>Нет событий</EmptyState>

      ) : (

        groups.map((group) => (

          <section key={group.key} className="feed-day">

            <h2>{group.heading}</h2>

            <ul className="feed-list">

              {group.events.map((event) => (

                <li key={event.id} className="feed-item">

                  <div className="feed-main">

                    <span className="event-icon" aria-hidden>

                      {eventIcon(event.type)}

                    </span>

                    {event.issueKey ? (

                      <Link

                        to={`/issues/${encodeURIComponent(event.issueKey)}`}

                        className="issue-key"

                      >

                        {event.issueKey}

                      </Link>

                    ) : (

                      <span className="muted">без задачи</span>

                    )}

                    <span>{eventLabel(event.type)}</span>

                  </div>

                  <div className="feed-meta">

                    <span>

                      👤 {event.actor?.name ?? event.actor?.id ?? '—'}

                    </span>

                    <span className="pill">

                      {workstreamLabel(

                        event.workstreamType?.code,

                        event.workstreamType?.displayName,

                      )}

                    </span>

                  </div>

                </li>

              ))}

            </ul>

          </section>

        ))

      )}

    </div>

  )

}

