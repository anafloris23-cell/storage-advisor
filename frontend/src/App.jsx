import { useState, useEffect } from 'react'
import { Button, Alert, Segmented } from 'antd'
import { analyzeSource, fetchExamples, fetchDatasetSource } from './api'
import ContractResult from './components/ContractResult'
import ExamplesPanel from './components/ExamplesPanel'
import BulkReport from './components/BulkReport'

const GAS_PER_SLOT = 20000

// Economia totală estimată dintr-un răspuns de analiză, pentru preview-ul de pe
// carduri: sloturi salvate la nivel de contract + sloturi salvate din structuri.
function summarize(data) {
  let slotsSaved = 0
  for (const c of data.contracts || []) {
    const r = c.report
    slotsSaved += r.currentEstimatedSlots - r.recommendedEstimatedSlots
    for (const s of r.structOptimizations || []) {
      // valori derivate calculate aici (vezi ContractResult.jsx): backend-ul nu le serializează
      const savedPerInstance = s.currentSlots - s.optimalSlots
      if (savedPerInstance > 0) slotsSaved += savedPerInstance * Math.max(s.directInstances || 0, 0)
    }
  }
  return { slotsSaved, gasSaved: slotsSaved * GAS_PER_SLOT }
}

export default function App() {
  const [examples, setExamples] = useState([])
  const [examplesError, setExamplesError] = useState(null)
  const [source, setSource] = useState('')
  const [activeId, setActiveId] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)
  const [previews, setPreviews] = useState({}) // id -> { status, slotsSaved, gasSaved }
  const [view, setView] = useState('analyzer') // 'analyzer' | 'report'

  // La încărcare: aducem exemplele din backend, pre-completăm editorul cu primul
  // și calculăm preview-ul live (economia estimată) pentru fiecare card. Eșecul
  // unui preview lasă doar acel card cu fallback, fără să blocheze interfața.
  useEffect(() => {
    let cancelled = false
    fetchExamples()
      .then((list) => {
        if (cancelled) return
        setExamples(list)
        if (list.length) {
          setSource(list[0].source)
          setActiveId(list[0].id)
        }
        for (const s of list) {
          setPreviews((p) => ({ ...p, [s.id]: { status: 'loading' } }))
          analyzeSource(s.source, `${s.id}.sol`)
            .then((data) => {
              if (!cancelled) setPreviews((p) => ({ ...p, [s.id]: { status: 'done', ...summarize(data) } }))
            })
            .catch(() => {
              if (!cancelled) setPreviews((p) => ({ ...p, [s.id]: { status: 'error' } }))
            })
        }
      })
      .catch((e) => {
        if (!cancelled) setExamplesError(e.message)
      })
    return () => { cancelled = true }
  }, [])

  async function runAnalyze(src) {
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const data = await analyzeSource(src, 'Contract.sol')
      setResult(data)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  function selectExample(sample) {
    setActiveId(sample.id)
    setSource(sample.source)
    runAnalyze(sample.source)
  }

  function handleEdit(e) {
    setSource(e.target.value)
    setActiveId(null) // editare manuală => niciun exemplu nu mai e „activ”
  }

  // Click pe un rând din raport: aduce sursa contractului, comută pe Analizor și o analizează.
  async function openContract(file) {
    setView('analyzer')
    setActiveId(null)
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const src = await fetchDatasetSource(file)
      setSource(src)
      const data = await analyzeSource(src, 'Contract.sol')
      setResult(data)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>StorageAdvisor</h1>
        <p>Analiză statică a layout-ului de storage pentru contracte Solidity</p>
      </header>

      <div className="view-switch">
        <Segmented
          value={view}
          onChange={setView}
          size="large"
          options={[
            { label: 'Analizor', value: 'analyzer' },
            { label: 'Raport dataset', value: 'report' },
          ]}
        />
      </div>

      {view === 'report' ? (
        <BulkReport onOpenContract={openContract} />
      ) : (
      <main className="layout">
        <ExamplesPanel
          samples={examples}
          previews={previews}
          activeId={activeId}
          onSelect={selectExample}
          error={examplesError}
        />

        <section className="editor-pane">
          <textarea
            className="editor"
            value={source}
            onChange={handleEdit}
            spellCheck={false}
            placeholder="Lipește aici codul Solidity..."
          />
          <Button
            type="primary"
            block
            size="large"
            loading={loading}
            disabled={!source.trim()}
            onClick={() => runAnalyze(source)}
            style={{ marginTop: 12 }}
          >
            {loading ? 'Se analizează…' : 'Analizează'}
          </Button>
        </section>

        <section className="results-pane">
          {error && (
            <Alert type="error" showIcon message="Eroare la analiză" description={error} />
          )}

          {!error && !result && !loading && (
            <div className="placeholder">
              <p>Alege un exemplu sau lipește un contract, apoi apasă <strong>Analizează</strong>.</p>
            </div>
          )}

          {result && (
            <>
              {result.preprocessing && result.preprocessing.modified && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="Preprocesare aplicată"
                  description={
                    <ul style={{ margin: 0, paddingLeft: 18 }}>
                      {result.preprocessing.warnings.map((w, i) => <li key={i}>{w}</li>)}
                    </ul>
                  }
                />
              )}
              {result.contracts.length === 0 && (
                <div className="placeholder"><p>Niciun contract cu storage găsit.</p></div>
              )}
              {result.contracts.map((c, i) => (
                <ContractResult key={i} contract={c} />
              ))}
            </>
          )}
        </section>
      </main>
      )}
    </div>
  )
}
