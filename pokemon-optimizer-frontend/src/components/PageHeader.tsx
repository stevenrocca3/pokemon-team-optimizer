import type { ReactNode } from 'react'

/** The top of every main page, sitting on the scenic backdrop. */
export function PageHeader({
  eyebrow,
  title,
  children,
  actions,
}: {
  eyebrow: string
  title: ReactNode
  children?: ReactNode
  actions?: ReactNode
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4 pt-10 pb-8 sm:pt-14">
      <div className="max-w-2xl">
        <p className="eyebrow text-brand">{eyebrow}</p>
        <h1 className="mt-2 text-4xl font-black tracking-tight text-balance sm:text-5xl">{title}</h1>
        {children && <div className="mt-3 text-base text-ink-soft sm:text-lg">{children}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
    </header>
  )
}
