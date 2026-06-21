import { useEffect, useState } from 'react'
import { Statistic, Card, Table, Tag, Alert, Spin, Typography } from 'antd'
import { fetchBulkReport } from '../api'

const { Title, Paragraph, Text } = Typography
const GAS_PER_SLOT = 20000

const KIND_TAG = {
  real: { label: 'real', color: 'gold' },
  synthetic: { label: 'synthetic', color: 'blue' },
  other: { label: 'other', color: 'default' },
}

const columns = (onOpen) => [
  {
    title: 'Contract',
    dataIndex: 'contract',
    key: 'contract',
    render: (v) => <Text strong>{v || '(anonymous)'}</Text>,
  },
  {
    title: 'Type',
    dataIndex: 'kind',
    key: 'kind',
    filters: [
      { text: 'real', value: 'real' },
      { text: 'synthetic', value: 'synthetic' },
    ],
    onFilter: (val, r) => r.kind === val,
    render: (k) => {
      const t = KIND_TAG[k] || KIND_TAG.other
      return <Tag color={t.color}>{t.label}</Tag>
    },
  },
  {
    title: 'Strategy',
    dataIndex: 'strategy',
    key: 'strategy',
    render: (s) => <Tag>{s && s.startsWith('FFD') ? 'FFD' : 'DP'}</Tag>,
  },
  {
    title: '−Slots',
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
    return <div className="report-pane"><Spin /> <Text type="secondary">loading report…</Text></div>
  }
  if (error) {
    return <div className="report-pane"><Alert type="error" showIcon message="Error loading the report" description={error} /></div>
  }
  if (!data || !data.generated) {
    return (
      <div className="report-pane">
        <Alert
          type="info"
          showIcon
          message="The report has not been generated yet"
          description="Run BulkAnalyzer on the dataset to generate reports/bulk-report.csv, then reload the page."
        />
      </div>
    )
  }

  const pct = data.contractsOk ? Math.round((data.winnersCount / data.contractsOk) * 1000) / 10 : 0
  const gas = data.totalSavedSlots * GAS_PER_SLOT

  return (
    <div className="report-pane">
      <Title level={3} style={{ marginTop: 0 }}>Dataset report</Title>
      <Paragraph type="secondary" style={{ maxWidth: 1200 }}>
        The analysis processed <b>{data.contractsTotal.toLocaleString('en-US')}</b> contracts
        ({data.contractsOk.toLocaleString('en-US')} successfully, {data.contractsError.toLocaleString('en-US')} with errors).
        <b> {data.winnersCount.toLocaleString('en-US')}</b> contracts ({pct}%) had a suboptimal layout,
        totaling <b>{data.totalSavedSlots.toLocaleString('en-US')} slots</b>
        {' '}(~{gas.toLocaleString('en-US')} gas) that can be saved on deployment.
      </Paragraph>

      <div className="report-stats">
        <Card size="small"><Statistic title="Contracts analyzed" value={data.contractsOk} /></Card>
        <Card size="small"><Statistic title="Contracts with savings" value={data.winnersCount} /></Card>
        <Card size="small"><Statistic title="Saved slots" value={data.totalSavedSlots} /></Card>
        <Card size="small"><Statistic title="Recovered bytes" value={data.totalSavedBytes} /></Card>
        <Card size="small"><Statistic title="Exact optimum (DP)" value={data.dpCount} suffix={`/ ${data.dpCount + data.ffdCount}`} /></Card>
        <Card size="small"><Statistic title="Errors (not compiled)" value={data.contractsError} /></Card>
      </div>

      <div className="report-split">
        <Card size="small">
          <Statistic title="Real subset — slots" value={data.realSavedSlots} />
          <Text type="secondary" style={{ fontSize: 12 }}>{data.realCount.toLocaleString('en-US')} contracts</Text>
        </Card>
        <Card size="small">
          <Statistic title="Synthetic subset — slots" value={data.syntheticSavedSlots} />
          <Text type="secondary" style={{ fontSize: 12 }}>{data.syntheticCount} contracts</Text>
        </Card>
        <Card size="small">
          <Statistic title="Struct optimizations" value={data.structSavedSlots} suffix="slots" />
          <Text type="secondary" style={{ fontSize: 12 }}>{data.structsImproved} structures</Text>
        </Card>
      </div>

      <Title level={5}>Top contracts by savings</Title>
      <Table
        size="small"
        rowKey={(r, i) => `${r.file}#${i}`}
        columns={columns(onOpenContract)}
        dataSource={data.top}
        pagination={{ pageSize: 15, showSizeChanger: false }}
        onRow={(r) => ({ onClick: () => onOpenContract(r.file), style: { cursor: 'pointer' } })}
      />
      <Text type="secondary" style={{ fontSize: 12 }}>
        Click a row to open the contract in the Analyzer with the before/after diagram.
      </Text>
    </div>
  )
}
