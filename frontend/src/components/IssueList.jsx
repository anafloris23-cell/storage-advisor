// Listează problemele detectate, colorate după severitate.
export default function IssueList({ issues }) {
  if (!issues || issues.length === 0) {
    return <p className="empty">No packing issues detected.</p>
  }
  return (
    <ul className="issues">
      {issues.map((issue, i) => (
        <li key={i} className={`issue sev-${(issue.severity || '').toLowerCase()}`}>
          <div className="issue-head">
            <span className="badge">{issue.severity}</span>
            <code className="issue-code">{issue.code}</code>
            {issue.variable && <span className="issue-var">{issue.variable}</span>}
          </div>
          <p className="issue-msg">{issue.message}</p>
          {issue.recommendation && <p className="issue-rec">→ {issue.recommendation}</p>}
        </li>
      ))}
    </ul>
  )
}
