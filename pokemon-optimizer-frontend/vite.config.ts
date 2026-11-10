import { readFileSync } from 'node:fs'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const moduleFile = (path: string) => new URL(`./node_modules/${path}`, import.meta.url)
const versionOf = (pkg: string) => (JSON.parse(readFileSync(moduleFile(`${pkg}/package.json`), 'utf8')) as { version: string }).version

/**
 * The screenshot reader's OCR (Tesseract.js) loads a worker, a WebAssembly core and English text data at runtime. By
 * default those come from a CDN; this serves them from our own origin instead. The folder name carries every version, so
 * it can sit under /assets/ (cached for a year) and an upgrade is a new URL. Only one core is downloaded per browser:
 * Tesseract.js picks the fastest the browser supports.
 */
const TESSERACT_DIR = `assets/tesseract-${versionOf('tesseract.js')}-${versionOf('tesseract.js-core')}-${versionOf('@tesseract.js-data/eng')}`
const TESSERACT_FILES: Record<string, URL> = {
  'worker.min.js': moduleFile('tesseract.js/dist/worker.min.js'),
  'tesseract-core-relaxedsimd-lstm.wasm.js': moduleFile('tesseract.js-core/tesseract-core-relaxedsimd-lstm.wasm.js'),
  'tesseract-core-simd-lstm.wasm.js': moduleFile('tesseract.js-core/tesseract-core-simd-lstm.wasm.js'),
  'tesseract-core-lstm.wasm.js': moduleFile('tesseract.js-core/tesseract-core-lstm.wasm.js'),
  'eng.traineddata.gz': moduleFile('@tesseract.js-data/eng/4.0.0_best_int/eng.traineddata.gz'),
}

function selfHostedTesseract(): Plugin {
  return {
    name: 'self-hosted-tesseract',
    configureServer(server) {
      server.middlewares.use(`/${TESSERACT_DIR}`, (req, res, next) => {
        const file = TESSERACT_FILES[(req.url ?? '').replace(/^\//, '').split('?')[0]]
        if (!file) return next()
        res.setHeader('Content-Type', file.pathname.endsWith('.gz') ? 'application/gzip' : 'text/javascript')
        res.end(readFileSync(file))
      })
    },
    generateBundle() {
      for (const [name, file] of Object.entries(TESSERACT_FILES)) {
        this.emitFile({ type: 'asset', fileName: `${TESSERACT_DIR}/${name}`, source: readFileSync(file) })
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), selfHostedTesseract()],
  define: {
    'import.meta.env.VITE_TESSERACT_DIR': JSON.stringify(`/${TESSERACT_DIR}`),
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
      },
    },
  },
})
