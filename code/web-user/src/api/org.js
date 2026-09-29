import request from './request'

export const orgApi = {
  create: (name) => request.post('/orgs', { name }),
  my: () => request.get('/orgs/my'),
  detail: (id) => request.get('/orgs/' + id),
  join: (inviteCode) => request.post('/orgs/join', { inviteCode }),
  kick: (id, userId) => request.delete('/orgs/' + id + '/members/' + userId),
  exit: (id) => request.delete('/orgs/' + id + '/exit'),
  addCollab: (id, mindMapId) => request.post('/orgs/' + id + '/collab', { mindMapId }),
  collabList: (id) => request.get('/orgs/' + id + '/collab'),
  removeCollab: (id, mindMapId) => request.delete('/orgs/' + id + '/collab/' + mindMapId)
}
