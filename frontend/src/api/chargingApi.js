import { http, unwrap } from './client'

export const api = {
  getConfig: () => unwrap(http.get('/config')),
  updateConfig: (payload) => unwrap(http.put('/config', payload)),
  resetDemo: () => unwrap(http.post('/demo/reset')),
  seedDemo: () => unwrap(http.post('/demo/seed')),
  getSnapshot: () => unwrap(http.get('/demo/snapshot')),

  createAccount: (payload) => unwrap(http.post('/accounts', payload)),
  setPassword: (carId, payload) => unwrap(http.post(`/accounts/${carId}/password`, payload)),

  submitRequest: (payload) => unwrap(http.post('/charging/requests', payload)),
  modifyAmount: (carId, payload) => unwrap(http.patch(`/charging/requests/${carId}/amount`, payload)),
  modifyMode: (carId, payload) => unwrap(http.patch(`/charging/requests/${carId}/mode`, payload)),
  getCarState: (carId) => unwrap(http.get(`/charging/cars/${carId}/state`)),
  startCharging: (carId, payload) => unwrap(http.post(`/charging/${carId}/start`, payload)),
  getChargingState: (carId) => unwrap(http.get(`/charging/${carId}/state`)),
  endCharging: (carId, payload) => unwrap(http.post(`/charging/${carId}/end`, payload)),

  queryBills: (carId, date) => unwrap(http.get('/bills', { params: { carId, date } })),
  queryDetails: (billId) => unwrap(http.get(`/bills/${billId}/details`)),

  getPiles: () => unwrap(http.get('/piles')),
  powerOn: (pileId) => unwrap(http.post(`/piles/${pileId}/power-on`)),
  startPile: (pileId) => unwrap(http.post(`/piles/${pileId}/start`)),
  powerOff: (pileId) => unwrap(http.post(`/piles/${pileId}/power-off`)),

  getQueues: () => unwrap(http.get('/queues')),
  dispatch: () => unwrap(http.post('/scheduler/dispatch')),

  createFault: (payload) => unwrap(http.post('/faults', payload)),
  recoverPile: (pileId) => unwrap(http.post(`/faults/${pileId}/recover`))
}
