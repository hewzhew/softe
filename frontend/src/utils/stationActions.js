export async function executeStationDispatch({ api, refresh, notify }) {
  if (!api?.dispatch) {
    throw new Error('dispatch api required')
  }

  const assignments = await api.dispatch()

  if (refresh) {
    await refresh()
  }
  if (notify) {
    notify('dispatch')
  }

  return assignments
}

export async function executeStationDispatchOne({ api, payload = {}, refresh, notify }) {
  if (!api?.dispatchOne) {
    throw new Error('dispatchOne api required')
  }

  const assignment = await api.dispatchOne(payload)

  if (refresh) {
    await refresh()
  }
  if (notify) {
    notify('dispatch-one')
  }

  return assignment
}
