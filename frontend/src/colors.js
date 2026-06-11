// Atribuie o culoare stabilă fiecărei variabile, astfel încât aceeași variabilă
// să apară cu aceeași culoare în diagrama "before" și "after" — ceea ce face
// vizibilă rearanjarea variabilelor între sloturi.

const PALETTE = [
  '#4f86f7', '#34c38f', '#f7b34c', '#e26d8a', '#9b6dff',
  '#3fb6c4', '#f78c4c', '#6dbb5c', '#d65cc4', '#5c7cdb',
  '#c4a23f', '#5cc4a8',
]

export function buildColorMap(labels) {
  const map = {}
  let i = 0
  for (const label of labels) {
    if (!(label in map)) {
      map[label] = PALETTE[i % PALETTE.length]
      i++
    }
  }
  return map
}

export const WASTED_COLOR = '#e6e8ec'
