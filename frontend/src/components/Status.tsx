import type { ReactNode } from 'react'



export function Loading({ label = 'Загрузка…' }: { label?: string }) {

  return <p className="muted status-line">{label}</p>

}



export function EmptyState({ children }: { children: ReactNode }) {

  return <p className="muted empty">{children}</p>

}



export function ErrorState({ message }: { message: string }) {

  return <p className="error status-line">{message}</p>

}

