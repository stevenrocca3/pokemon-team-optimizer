import type { ReactNode } from 'react'
import { CreamerRule } from './Creamer'

/** The top of every main page, sitting on the scenic backdrop. */
export function PageHeader({
  eyebrow,
  title,
  children,
  actions,
}: {
  /** the small label above the title; the legal pages go without */
  eyebrow?: string
  title: ReactNode
  children?: ReactNode
  actions?: ReactNode
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4 pt-10 pb-8 sm:pt-14">
      {/* centred on phones, left-aligned beside the actions from sm up */}
      <div className="mx-auto max-w-2xl text-center sm:mx-0 sm:text-left">
        {eyebrow && <p className="eyebrow mb-2 text-brand">{eyebrow}</p>}
        <h1 className="text-4xl font-black tracking-tight text-balance sm:text-5xl">{title}</h1>
        <CreamerRule align="responsive" className="mt-3" />
        {children && <div className="mt-3 text-base text-ink-soft sm:text-lg">{children}</div>}
      </div>
      {/* on phones the actions share the full width, so they don't sit lopsided on the left */}
      {actions && (
        <div className="flex w-full flex-wrap items-center gap-3 sm:w-auto [&>*]:flex-1 sm:[&>*]:flex-none">
          {actions}
        </div>
      )}
    </header>
  )
}
