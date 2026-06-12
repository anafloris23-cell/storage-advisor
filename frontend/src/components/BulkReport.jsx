import { useEffect, useState } from 'react'
import { Statistic, Card, Table, Tag, Alert, Spin, Typography } from 'antd'
import { fetchBulkReport } from '../api'

const { Title, Paragraph, Text } = Typography
const GAS_PER_SLOT = 20000

const KIND_TAG = {
  real: { label: 'real', color: 'gold' },
  synthetic: { label: 'sintetic', color: 'blue' },
  other: { label: 'alt', color: 'default' },
}

const columns = (onOpen) => [
  {
    title: 'Contract',
    dataIndex: 'contract',
    key: 'contract',
    render: (v) => <Text strong>{v || '(anonim)'}</Text>,
  },
  {
    title: 'Tip',
    dataIndex: 'kind',
    key: 'kind',
    filters: [
      { text: 'real', value: 'real' },
      { text: 'sintetic', value: 'synthetic' },
    ],
    onFilter: (val, r) => r.kind === val,
    render: (k) => {
      const t = KIND_TAG[k] || KIND_TAG.other
      return <Tag color={t.color}>{t.label}</Tag>
    },
  },
  {
    title: 'Strategie',
    dataIndex: 'strategy',
    key: 'strategy',
    render: (s) => <Tag>{s && s.startsWith('FFD') ? 'FFD' : 'DP'}</Tag>,
  },
  {
    title: '−Sloturi',
    dataIndex: 'savedSlots',
    key: 'savedSlots',
    align: 'right',
    sorter: (a, b) => a.savedSlots - b.savedSlots,
    render: (v) => (v > 0 ? <Text type="success">−{v}</Text> : '—'),
  },
  {
    title: '−Bytes',
    dataIndex: 'savedBytes',
    key: 'savedBytes',
    align: 'right',
    render: (v) => (v > 0 ? `−${v}` : '—'),
  },
  {
    title: 'Struct',
    dataIndex: 'structSavedSlots',
    key: 'structSavedSlots',
    align: 'right',
    render: (v) => (v > 0 ? <Text type="success">−{v}</Text> : '—'),
  },
]

export default function BulkReport({ onOpenContract }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    fetchBulkReport()
      .then((d) => { if (!cancelled) { setData(d); setLoading(false) } })
      .catch((e) => { if (!cancelled) { setError(e.message); setLoading(false) } })
    return () => { cancelled = true }
  }, [])

  if (loading) {
    return <div className="report-pane"><Spin /> <Text type="secondary">se încarcă raportul…</Text></div>
  }
  if (error) {
    return <div className="report-pane"><Alert type="error" showIcon message="Eroare la încărcarea raportului" description={error} /></div>
  }
  if (!data || !data.generated) {
    return (
      <div className="report-pane">
        <Alert
          type="info"
          showIcon
          message="Raportul nu a fost generat încă"
          description="Rulează BulkAnalyzer pe dataset ca să generezi reports/bulk-report.csv, apoi reîncarcă pagina."
        />
      </div>
    )
  }

  const pct = data.contractsOk ? Math.round((data.winnersCount / data.contractsOk) * 1000) / 10 : 0
  const gas = data.totalSavedSlots * GAS_PER_SLOT

  return (
    <div className="report-pane">
      <Title level={3} style={{ marginTop: 0 }}>Raport dataset</Title>
      <Paragraph type="secondary" style={{ maxWidth: 900 }}>
        Am analizat <b>{data.contractsTotal.toLocaleString('ro-RO')}</b> contracte
        ({data.contractsOk.toLocaleString('ro-RO')} cu succes, {data.contractsError.toLocaleString('ro-RO')} cu erori).
        <b> {data.winnersCount.toLocaleString('ro-RO')}</b> contracte (<b>{pct}%</b>) aveau layout suboptim,
        însumând <b>{data.totalSavedSlots.toLocaleString('ro-RO')} sloturi</b>
        {' '}(~{gas.toLocaleString('ro-RO')} gas) ce pot fi economisite la deploy.
      </Paragraph>

      <div className="report-stats">
        <Card size="small"><Statistic title="Contracte analizate" value={data.contractsOk} /></Card>
        <Card size="small"><Statistic title="Contracte cu câștig" value={data.winnersCount} suffix={`(${pct}%)`} /></Card>
        <Card size="small"><Statistic title="Sloturi economisite" value={data.totalSavedSlots} /></Card>
        <Card size="small"><Statistic title="Bytes recuperați" value={data.totalSavedBytes} /></Card>
        <Card size="small"><Statistic title="Optim absolut (DP)" value={data.dpCount} suffix={`/ ${data.dpCount + data.ffdCount}`} /></Card>
        <Card size="small"><Statistic title="Erori (necompilate)" value={data.contractsError} /></Card>
      </div>

      <div className="report-split">
        <Card size="small">
          <Statistic title="Subset real — sloturi" value={data.realSavedSlots} />
          <Text type="secondary" style={{ fontSize: 12 }}>{data.realCount.toLocaleString('ro-RO')} contracte</Text>
        </Card>
        <Card size="small">
          <Statistic title="Subset sintetic — sloturi" value={data.syntheticSavedSlots} />
          <Text type="secondary" style={{ fontSize: 12 }}>{data.syntheticCount} contracte</Text>
        </Card>
        <Card size="small">
          <Statistic title="Optimizări struct" value={data.structSavedSlots} suffix="sloturi" />
          <Text type="secondary" style={{ fontSize: 12 }}>{data.structsImproved} structuri</Text>
        </Card>
      </div>

      <Title level={5}>Top contracte după câștig</Title>
      <Table
        size="small"
        rowKey={(r, i) => `${r.file}#${i}`}
        columns={columns(onOpenContract)}
        dataSource={data.top}
        pagination={{ pageSize: 15, showSizeChanger: false }}
        onRow={(r) => ({ onClick: () => onOpenContract(r.file), style: { cursor: 'pointer' } })}
      />
      <Text type="secondary" style={{ fontSize: 12 }}>
        Click pe un rând → deschide contractul în Analizor cu diagrama before/after.
      </Text>
    </div>
  )
}
