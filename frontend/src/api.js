// Apelul către backend-ul StorageAdvisor.
// Folosește calea relativă /api — Vite proxy spre http://localhost:8089 (vite.config.js).

// Încarcă lista de contracte exemplu expuse de backend (GET /api/examples).
export async function fetchExamples() {
  const res = await fetch('/api/examples')
  if (!res.ok) {
    throw new Error(`Could not load the examples (HTTP ${res.status})`)
  }
  return res.json()
}

// Încarcă raportul agregat pe tot dataset-ul (GET /api/bulk-report).
export async function fetchBulkReport() {
  const res = await fetch('/api/bulk-report')
  if (!res.ok) {
    throw new Error(`Could not load the report (HTTP ${res.status})`)
  }
  return res.json()
}

// Încarcă sursa unui contract din dataset, după calea relativă (GET /api/dataset-source).
export async function fetchDatasetSource(path) {
  const res = await fetch(`/api/dataset-source?path=${encodeURIComponent(path)}`)
  let payload = null
  try {
    payload = await res.json()
  } catch {
    // răspuns fără JSON
  }
  if (!res.ok) {
    const message = payload && payload.error ? payload.error : `HTTP error ${res.status}`
    throw new Error(message)
  }
  return payload.source
}

// Analizează un contract verificat de pe Etherscan, după adresă sau link (POST /api/analyze-address).
export async function analyzeAddress(url) {
  const res = await fetch('/api/analyze-address', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
  })
  let payload = null
  try {
    payload = await res.json()
  } catch {
    // no JSON body
  }
  if (!res.ok) {
    const message = payload && payload.error ? payload.error : `HTTP error ${res.status}`
    throw new Error(message)
  }
  return payload
}

export async function analyzeSource(source, filename) {
  const res = await fetch('/api/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ source, filename }),
  })

  let payload = null
  try {
    payload = await res.json()
  } catch {
    // răspuns fără JSON
  }

  if (!res.ok) {
    const message = payload && payload.error ? payload.error : `HTTP error ${res.status}`
    throw new Error(message)
  }
  return payload
}
