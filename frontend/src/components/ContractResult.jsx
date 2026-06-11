import SlotDiagram from './SlotDiagram'
import IssueList from './IssueList'
import { buildColorMap } from '../colors'

const GAS_PER_SLOT = 20000 // SSTORE inițializare slot (zero → non-zero)

// Normalizează un slot curent (CurrentSlotView) la modelul folosit de SlotDiagram.
function fromCurrent(s) {
  return {
    index: s.slotIndex,
    segments: s.variables.map((v) => ({
      label: v.label,
      typeLabel: v.typeLabel,
      sizeBytes: v.sizeBytes,
    })),
    usedBytes: s.usedBytes,
    wastedBytes: s.wastedBytes,
    spanned: s.slotsSpanned,
  }
}

// Normalizează un slot recomandat (RecommendedSlot) la același model.
function fromRecommended(s) {
  return {
    index: s.slotIndex,
    segments: s.variables.map((v) => ({
      label: v.label,
      typeLabel: v.typeLabel,
      sizeBytes: v.sizeBytes,
    })),
    usedBytes: s.usedBytes,
    wastedBytes: s.wastedBytes,
    spanned: 1,
  }
}

function StatCard({ label, current, recommended, unit, better }) {
  const delta = current - recommended
  return (
    <div className="stat-card">
      <div className="stat-label">{label}</div>
      <div className="stat-values">
        <span className="stat-current">{current}{unit}</span>
        <span className="stat-arrow">→</span>
        <span className="stat-recommended">{recommended}{unit}</span>
      </div>
      {delta > 0 && better && <div className="stat-delta">−{delta}{unit}</div>}
    </div>
  )
}

export default function ContractResult({ contract }) {
  const { report, currentSlots } = contract

  const slotsSaved = report.currentEstimatedSlots - report.recommendedEstimatedSlots
  const gasSaved = slotsSaved * GAS_PER_SLOT

  // Culori stabile pe variabilă, partajate între cele două diagrame.
  const labels = [
    ...report.recommendedOrder.map((v) => v.label),
    ...currentSlots.flatMap((s) => s.variables.map((v) => v.label)),
  ]
  const colorMap = buildColorMap(labels)

  const before = currentSlots.map(fromCurrent)
  const after = report.recommendedSlots.map(fromRecommended)

  const improvedStructs = (report.structOptimizations || []).filter((s) => s.hasImprovement)

  return (
    <div className="contract">
      <div className="contract-head">
        <h2>{report.contractName}</h2>
        <span className="source-unit">{report.sourceUnit}</span>
        <span className="strategy">{report.packingStrategy}</span>
      </div>

      <div className="stats">
        <StatCard label="Sloturi" current={report.currentEstimatedSlots}
                  recommended={report.recommendedEstimatedSlots} unit="" better />
        <StatCard label="Bytes irosiți" current={report.currentWastedBytes}
                  recommended={report.recommendedWastedBytes} unit="B" better />
        <div className="stat-card highlight">
          <div className="stat-label">Sloturi economisite</div>
          <div className="stat-big">{slotsSaved}</div>
        </div>
        <div className="stat-card highlight">
          <div className="stat-label">Economie gas estimată (deployment)</div>
          <div className="stat-big">~{gasSaved.toLocaleString('ro-RO')}</div>
          <div className="stat-sub">{slotsSaved} × {GAS_PER_SLOT.toLocaleString('ro-RO')} gas/slot</div>
        </div>
      </div>

      <div className="diagrams">
        <SlotDiagram title="Layout curent (declarat)" slots={before} colorMap={colorMap} />
        <SlotDiagram title="Layout recomandat (optimizat)" slots={after} colorMap={colorMap} />
      </div>

      <div className="panels">
        <section className="panel">
          <h3>Probleme detectate ({report.issues.length})</h3>
          <IssueList issues={report.issues} />
        </section>

        <section className="panel">
          <h3>Ordine recomandată în Solidity</h3>
          <pre className="code">
            {report.recommendedOrder.map((v) => `${v.typeLabel} ${v.label};`).join('\n')}
          </pre>
        </section>
      </div>

      {improvedStructs.length > 0 && (
        <section className="panel">
          <h3>Optimizări struct</h3>
          {improvedStructs.map((s, i) => (
            <div key={i} className="struct-opt">
              <div className="struct-head">
                <strong>{s.label}</strong>: {s.currentSlots} → {s.optimalSlots} sloturi/instanță
                <span className="struct-saving">
                  −{s.savedSlotsPerInstance}/instanță · {s.directInstances} instanțe → −{s.totalSavedSlots} sloturi
                </span>
              </div>
              <pre className="code">
                {s.recommendedFieldOrder.map((v) => `${v.typeLabel} ${v.label};`).join('\n')}
              </pre>
            </div>
          ))}
        </section>
      )}
    </div>
  )
}
