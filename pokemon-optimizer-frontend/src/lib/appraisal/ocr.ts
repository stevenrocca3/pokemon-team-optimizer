import type { PSM, Worker } from 'tesseract.js'
import type { OcrField } from './appraise'
import type { Pixels } from './pixels'

// served from our own origin by the Vite plugin in vite.config.ts
const DIR: string = import.meta.env.VITE_TESSERACT_DIR

// the characters each field can contain; the name can be anything (nicknames, accents)
const WHITELIST: Record<OcrField, string> = { cp: 'CP0123456789', hp: '0123456789/HP ', name: '' }

let worker: Promise<{ worker: Worker; singleLine: PSM }> | null = null
let queue: Promise<unknown> = Promise.resolve()

/** Tesseract.js (about 7 MB with its English data) is only downloaded once someone reads a screenshot. */
function start() {
  worker ??= import('tesseract.js')
    .then(async ({ createWorker, OEM, PSM }) => {
      const base = new URL(DIR, location.href).href
      const w = await createWorker('eng', OEM.LSTM_ONLY, { workerPath: `${base}/worker.min.js`, corePath: base, langPath: base })
      return { worker: w, singleLine: PSM.SINGLE_LINE }
    })
    .catch((error: unknown) => {
      worker = null // let the next screenshot try again
      throw error
    })
  return worker
}

/** Starts the download early (when the player opens the screenshot picker), so the first read doesn't wait as long. */
export function warmUpOcr(): void {
  start().catch(() => {})
}

/** Frees the worker's memory (tens of MB) when the page is left. */
export function stopOcr(): void {
  const current = worker
  worker = null
  current?.then(({ worker: w }) => w.terminate()).catch(() => {})
}

/** One line of text in these pixels, read in greyscale like the prototype. Calls run one at a time on one worker. */
export function readText(p: Pixels, field: OcrField): Promise<string> {
  const run = queue.then(async () => {
    const { worker: w, singleLine } = await start()
    await w.setParameters({ tessedit_pageseg_mode: singleLine, tessedit_char_whitelist: WHITELIST[field] })
    const { data } = await w.recognize(toCanvas(p))
    return data.text
  })
  queue = run.catch(() => {})
  return run
}

function toCanvas(p: Pixels): HTMLCanvasElement {
  const grey = new ImageData(p.width, p.height)
  for (let i = 0; i < p.data.length; i += 4) {
    const v = Math.round(0.299 * p.data[i] + 0.587 * p.data[i + 1] + 0.114 * p.data[i + 2])
    grey.data[i] = grey.data[i + 1] = grey.data[i + 2] = v
    grey.data[i + 3] = 255
  }
  const canvas = document.createElement('canvas')
  canvas.width = p.width
  canvas.height = p.height
  canvas.getContext('2d')!.putImageData(grey, 0, 0)
  return canvas
}

/** The pixels of an image file, decoded by the browser. */
export async function pixelsOf(file: Blob): Promise<Pixels> {
  const bitmap = await createImageBitmap(file)
  const canvas = document.createElement('canvas')
  canvas.width = bitmap.width
  canvas.height = bitmap.height
  const context = canvas.getContext('2d', { willReadFrequently: true })!
  context.drawImage(bitmap, 0, 0)
  bitmap.close()
  const { data } = context.getImageData(0, 0, canvas.width, canvas.height)
  return { width: canvas.width, height: canvas.height, data }
}
