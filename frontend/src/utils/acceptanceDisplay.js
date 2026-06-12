export function sampleCheckLabel(check) {
  return check?.matched ? '通过' : '需复核'
}

export function sampleCheckType(check) {
  return check?.matched ? 'success' : 'warning'
}

export function flattenScenarioRows(rows = []) {
  return rows.flatMap((row) => {
    const slotCount = Math.max(
      row.fast1?.length || 0,
      row.fast2?.length || 0,
      row.slow1?.length || 0,
      row.slow2?.length || 0,
      row.slow3?.length || 0,
      3
    )

    return Array.from({ length: slotCount }, (_, index) => ({
      key: `${row.time}-${index}`,
      time: index === 0 ? row.time : '',
      event: index === 0 ? row.event : '',
      slot: index + 1,
      fast1: row.fast1?.[index] || '-',
      fast2: row.fast2?.[index] || '-',
      slow1: row.slow1?.[index] || '-',
      slow2: row.slow2?.[index] || '-',
      slow3: row.slow3?.[index] || '-',
      waitingAreaText: index === 0 ? row.waitingAreaText || '-' : '',
      notes: index === 0 ? row.notes || '' : ''
    }))
  })
}
