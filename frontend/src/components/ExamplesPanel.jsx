import { Card, Tag, Typography, Spin, Alert } from 'antd'

const { Text, Title, Paragraph } = Typography

// Category tag color (Ant Design preset colors).
const TAG_COLOR = {
  Ordering: 'blue',
  Token: 'green',
  Struct: 'purple',
}

// Origin badge: demo examples ("synthetic") get a subtle marker;
// real contracts ("real") get a gold one that stands out.
const KIND_BADGE = {
  synthetic: { label: 'synthetic', color: 'default' },
  real: { label: 'real', color: 'gold' },
}

// Renders the preview area of a card depending on the live analysis state.
function Preview({ state }) {
  if (!state || state.status === 'loading') {
    return (
      <span className="preview-muted">
        <Spin size="small" /> computing…
      </span>
    )
  }
  if (state.status === 'error') {
    return <Text type="secondary" className="preview-muted">preview unavailable</Text>
  }
  if (state.slotsSaved > 0) {
    return (
      <>
        <Tag color="success" style={{ marginInlineEnd: 0 }}>−{state.slotsSaved} slots</Tag>
        <Text type="secondary">~{state.gasSaved.toLocaleString('en-US')} gas</Text>
      </>
    )
  }
  return <Text type="secondary" className="preview-muted">already optimal</Text>
}

// Left panel: list of examples as cards. Clicking a card loads it into the
// editor and analyzes it automatically (see App.selectExample).
export default function ExamplesPanel({ samples, previews, activeId, onSelect, error }) {
  return (
    <aside className="examples-panel">
      <Title level={4} style={{ margin: 0 }}>Examples</Title>
      <Paragraph type="secondary" style={{ fontSize: 13, margin: '4px 0 12px' }}>
        Click an example — it loads and is analyzed automatically.
      </Paragraph>

      {error && (
        <Alert type="error" showIcon message="Could not load the examples" description={error} />
      )}

      {!error && samples.length === 0 && (
        <div className="examples-loading"><Spin /> <Text type="secondary">loading examples…</Text></div>
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
