import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'
import { archiveData, members, workspaces } from '../mock/boardMock'

const state = reactive({
  workspaces: structuredClone(workspaces),
  members: structuredClone(members),
  archives: structuredClone(archiveData)
})

export const boardStore = {
  state,
  async load() {
    const [dashboard, loadedMembers, ...loadedArchives] = await Promise.all([
      dataSource.board.getDashboard(),
      dataSource.board.getMembers(),
      ...['documents', 'minutes', 'summary', 'feedback'].map(async section => [
        section,
        await dataSource.board.getArchive(section)
      ])
    ])
    state.workspaces = dashboard?.workspaces || state.workspaces
    state.members = loadedMembers || state.members
    loadedArchives.forEach(([section, rows]) => {
      state.archives[section] = rows
    })
    return state
  },
  createWorkspace: data => dataSource.board.createWorkspace(data),
  createMeeting: data => dataSource.board.createMeeting(data),
  createEvent: data => dataSource.board.createEvent(data)
}
