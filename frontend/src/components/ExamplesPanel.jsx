import { Card, Tag, Typography, Spin, Alert } from 'antd'

const { Text, Title, Paragraph } = Typography

// Culoarea tag-ului de categorie (Ant Design preset colors).
const TAG_COLOR = {
  Ordonare: 'blue',
  Token: 'green',
  Struct: 'purple',
}

// Badge de proveniență: exemplele de demo („synthetic”) au un marcaj discret;
// contractele reale („real”, viitor) primesc unul auriu, care iese în evidență.
const KIND_BADGE = {
  synthetic: { label: 'sintetic', color: 'default' },
  real: { label: 'real', color: 'gold' },
}

// Randează zona de preview a unui card în funcție de starea analizei live.
function Preview({ state }) {
  if (!state || state.status === 'loading') {
    return (
      <span className="preview-muted">
        <Spin size="small" /> se calculează…
      </span>
    )
  }
  if (state.status === 'error') {
    return <Text type="secondary" className="preview-muted">preview indisponibil</Text>
  }
  if (state.slotsSaved > 0) {
    return (
      <>
        <Tag color="success" style={{ marginInlineEnd: 0 }}>−{state.slotsSaved} sloturi</Tag>
        <Text type="secondary">~{state.gasSaved.toLocaleString('ro-RO')} gas</Text>
      </>
    )
  }
  return <Text type="secondary" className="preview-muted">deja optim</Text>
}

// Panoul din stânga: listă de exemple sub formă de carduri. Click pe card =>
// se încarcă în editor și se analizează automat (vezi App.selectExample).
export default function ExamplesPanel({ samples, previews, activeId, onSelect, error }) {
  return (
    <aside className="examples-panel">
      <Title level={4} style={{ margin: 0 }}>Exemple</Title>
      <Paragraph type="secondary" style={{ fontSize: 13, margin: '4px 0 12px' }}>
        Click pe un exemplu — se încarcă și se analizează automat.
      </Paragraph>

      {error && (
        <Alert type="error" showIcon message="Nu am putut încărca exemplele" description={error} />
      )}

      {!error && samples.length === 0 && (
        <div className="examples-loading"><Spin /> <Text type="secondary">se încarcă exemplele…</Text></div>
      )}

      <div className="example-list">
        {samples.map((s) => {
          const kind = KIND_BADGE[s.kind] || KIND_BADGE.synthetic
          return (
            <Card
              key={s.id}
              size="small"
              hoverable
              className={`example-card${activeId === s.id ? ' active' : ''}`}
              onClick={() => onSelect(s)}
            >
              <div className="example-card-head">
                <Text strong>{s.title}</Text>
                <Tag color={TAG_COLOR[s.tag]} style={{ marginInlineEnd: 0 }}>{s.tag}</Tag>
              </div>
              <div className="example-kind">
                <Tag color={kind.color} bordered style={{ marginInlineEnd: 0 }}>{kind.label}</Tag>
              </div>
              <Text type="secondary" className="example-desc">{s.description}</Text>
              <div className="example-preview">
                <Preview state={previews[s.id]} />
              </div>
            </Card>
          )
        })}
      </div>
    </aside>
  )
}
