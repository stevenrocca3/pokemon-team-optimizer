import { Link } from 'react-router'
import { SearchBar } from '../components/SearchBar'

export function NotFoundPage({ message = "That page doesn't exist." }: { message?: string }) {
  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center">
      <p className="text-6xl font-black text-brand">404</p>
      <p className="mt-4 text-lg text-ink-soft">{message}</p>
      <div className="mt-8 text-left">
        <SearchBar size="large" />
      </div>
      <Link to="/" className="mt-6 inline-block text-sm text-muted underline hover:text-ink">
        Back to home
      </Link>
    </div>
  )
}
