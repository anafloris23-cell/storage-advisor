import { WASTED_COLOR } from '../colors'

// Desenează un set de sloturi de 32 bytes. Fiecare slot e o bară orizontală
// împărțită în segmente proporționale cu dimensiunea variabilelor; spațiul
// neutilizat e marcat hașurat.
export default function SlotDiagram({ title, slots, colorMap }) {
  return (
    <div className="diagram">
      <h4 className="diagram-title">{title}</h4>
      <div className="slots">
        {slots.map((slot) => {
          const capacity = slot.usedBytes + slot.wastedBytes || 32
          return (
            <div key={slot.index} className="slot-row">
              <div className="slot-index">
                slot {slot.index}
                {slot.spanned > 1 && <span className="slot-span">×{slot.spanned}</span>}
              </div>
              <div className="slot-bar">
                {slot.segments.map((seg, i) => {
                  const pct = (seg.sizeBytes / capacity) * 100
                  return (
                    <div
                      key={i}
                      className="seg"
                      style={{ width: `${pct}%`, background: colorMap[seg.label] || '#888' }}
                      title={`${seg.typeLabel} ${seg.label} — ${seg.sizeBytes} B`}
                    >
                      <span className="seg-label">
                        {seg.label}
                        <small>{seg.sizeBytes}B</small>
                      </span>
                    </div>
                  )
                })}
                {slot.wastedBytes > 0 && (
                  <div
                    className="seg seg-wasted"
                    style={{ width: `${(slot.wastedBytes / capacity) * 100}%`, background: WASTED_COLOR }}
                    title={`${slot.wastedBytes} wasted bytes`}
                  >
                    <span className="seg-label seg-label-wasted">{slot.wastedBytes}B free</span>
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
