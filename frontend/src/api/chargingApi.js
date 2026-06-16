import { http, unwrap } from './client.js'

export const api = {
  login: (payload) => unwrap(http.post('/auth/login', payload)),
  currentUser: () => unwrap(http.get('/auth/me')),
  logout: () => unwrap(http.post('/auth/logout')),

  getConfig: () => unwrap(http.get('/config')),
  updateConfig: (payload) => unwrap(http.put('/config', payload)),
  resetDemo: () => unwrap(http.post('/demo/reset')),
  seedDemo: () => unwrap(http.post('/demo/seed')),
  getSnapshot: () => unwrap(http.get('/demo/snapshot')),

  createAccount: (payload) => unwrap(http.post('/accounts', payload)),
  getAccount: (carId) => unwrap(http.get(`/accounts/${carId}`)),
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
  dispatchOne: (payload = {}) => unwrap(http.post('/scheduler/dispatch-one', payload)),

  createFault: (payload) => unwrap(http.post('/faults', payload)),
  recoverPile: (pileId) => unwrap(http.post(`/faults/${pileId}/recover`)),

  runAcceptanceScenario: () => unwrap(http.get('/acceptance/scenario')),
  getCourseScenario: () => unwrap(http.get('/scenarios/course-sample')),
  runCourseScenario: () => unwrap(http.post('/scenarios/course-sample/run')),
  getStationSnapshot: () => unwrap(http.get('/station/snapshot')),
  getStationClock: () => unwrap(http.get('/station/clock')),
  setStationClock: (payload) => unwrap(http.patch('/station/clock', payload)),
  playStationClock: () => unwrap(http.post('/station/clock/play')),
  pauseStationClock: () => unwrap(http.post('/station/clock/pause')),
  advanceStation: (payload) => unwrap(http.post('/station/advance', payload)),
  getStationEvents: () => unwrap(http.get('/station/events')),
  addStationEvent: (payload) => unwrap(http.post('/station/events', payload)),
  importStationEvents: (payload) => unwrap(http.post('/station/events/import', payload))
}
