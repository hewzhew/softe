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
