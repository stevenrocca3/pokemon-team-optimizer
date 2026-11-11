import { useState, type DragEvent } from 'react'
import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'
import { usePageTitle } from '../lib/page-title'
import { useAnalysisJob } from '../analysis/analysis-job'
import { ScanVisualizer } from '../analysis/ScanVisualizer'
import { AnalyzeDemoPhone } from '../components/AnalyzeDemoPhone'

/** /analyze: drop a GO Battle League screen recording; the analysis then runs in the background (see the widget). */
export function AnalyzePage() {
  usePageTitle('Battle Analysis')
  const { job, start } = useAnalysisJob()
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [dragging, setDragging] = useState(false)
  const running = job !== null && job.status !== 'complete' && job.status !== 'failed'

  async function send(file: File | undefined) {
    if (!file) return
    setError(null)
    setSending(true)
    try {
      await start(file)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSending(false)
    }
  }

  function drop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    if (!sending) send(e.dataTransfer.files[0])
  }

  return (
    <div className="mx-auto max-w-6xl px-4 pb-24">
      <div className="grid items-start gap-10 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-14">
        <div className="min-w-0">
          <PageHeader eyebrow="Battle analysis" title="Grade your GO Battle League set">
            Upload a screen recording of one battle. DexPresso rebuilds it turn by turn and scores your fast move
            timing, shields, switches and charged moves.{' '}
            <Link to="/analytics" className="text-brand underline-offset-2 hover:underline">
              See your trends and top teams
            </Link>
            .
          </PageHeader>

          {/* once a video is sent, the scan takes the drop zone's place until the player starts another */}
          {job ? (
            <ScanVisualizer />
          ) : (
            <label
              onDragOver={(e) => {
                e.preventDefault()
                setDragging(true)
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={drop}
              className={`card flex cursor-pointer flex-col items-center gap-3 border-2 border-dashed px-6 py-14 text-center transition ${
                dragging ? 'border-brand bg-raised' : 'border-line-strong'
              } ${sending ? 'pointer-events-none opacity-60' : 'hover:border-brand'}`}
            >
              <span className="font-display text-xl text-ink">
                {sending ? 'Uploading...' : 'Drop a recording here, or choose one'}
              </span>
              <span className="text-sm text-muted">
                MP4 or MOV, one battle, up to 400 MB. The video is deleted once it’s read.
              </span>
              <input
                type="file"
                accept="video/mp4,video/quicktime,.mp4,.mov"
                aria-label="Battle recording"
                disabled={sending}
                className="sr-only"
                onChange={(e) => {
                  send(e.target.files?.[0])
                  e.target.value = ''
                }}
              />
            </label>
          )}

          {error && (
            <p role="alert" className="mt-4 rounded-lg bg-raised px-4 py-3 text-sm text-ink ring-1 ring-danger">
              {error}
            </p>
          )}
          {running && (
            <p className="mt-4 text-sm text-ink-soft">
              You can leave this page: the analysis keeps going, and you’ll get a note when it’s done.{' '}
              <Link to="/team-builder" className="text-brand underline-offset-2 hover:underline">
                Build teams while you wait
              </Link>
              .
            </p>
          )}
        </div>

        {/* a live preview of what the analysis does: beside the upload on wide screens, under it on phones */}
        {!job && (
          <aside aria-label="What you get" className="lg:sticky lg:top-24 lg:pt-10">
            <AnalyzeDemoPhone />
          </aside>
        )}
      </div>
    </div>
  )
}
