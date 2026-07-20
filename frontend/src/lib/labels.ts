/** Human labels and icons for risk codes / activity event types (UI only). */

const RISK_LABELS: Record<string, string> = {
  OPEN_MR_STALE: 'Просроченный открытый Merge Request',
  STALE_ACTIVITY: 'Нет активности',
  JIRA_ACTIVE_NO_GIT: 'Jira без Git-активности',
  NO_MR: 'Нет Merge Request',
}

const RISK_MARKERS: Record<string, string> = {
  OPEN_MR_STALE: '🔴',
  STALE_ACTIVITY: '🟡',
  JIRA_ACTIVE_NO_GIT: '🟠',
  NO_MR: '🟠',
}

const EVENT_ICONS: Record<string, string> = {
  BRANCH_CREATED: '🌱',
  COMMIT: '💻',
  MR_OPENED: '🔀',
  MR_APPROVED: '👍',
  MR_MERGED: '✅',
}

const EVENT_LABELS: Record<string, string> = {
  BRANCH_CREATED: 'Создана ветка',
  COMMIT: 'Коммит',
  MR_OPENED: 'Открыт Merge Request',
  MR_APPROVED: 'Одобрен Merge Request',
  MR_MERGED: 'Merge Request смержен',
}

/** Known Workstream Type codes → Russian display (API displayName may stay English). */
const WORKSTREAM_LABELS: Record<string, string> = {
  backend: 'Бэкенд',
  frontend: 'Фронтенд',
  oracle: 'Oracle',
  qa: 'QA',
}

export function riskLabel(code: string): string {
  return RISK_LABELS[code] ?? code
}

export function riskMarker(code: string): string {
  return RISK_MARKERS[code] ?? '⚪'
}

export function eventIcon(type: string): string {
  return EVENT_ICONS[type] ?? '•'
}

export function eventLabel(type: string): string {
  return EVENT_LABELS[type] ?? type
}

export function workstreamLabel(
  code: string | null | undefined,
  fallbackDisplayName?: string | null,
): string {
  if (code && WORKSTREAM_LABELS[code]) {
    return WORKSTREAM_LABELS[code]
  }
  return fallbackDisplayName ?? code ?? '—'
}
