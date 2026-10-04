import { createContext, useContext } from 'react'
import { modelApi } from '../api/client'
import useAsync from './useAsync'

const ModelInfoContext = createContext({ info: null, loading: false, error: null, reload: () => {} })

/** Loads /api/model-info once per session for the Responsible AI panel and the Model Insights page. */
export function ModelInfoProvider({ children }) {
  const { data, loading, error, reload } = useAsync(() => modelApi.info(), [])
  return <ModelInfoContext.Provider value={{ info: data, loading, error, reload }}>{children}</ModelInfoContext.Provider>
}

export function useModelInfo() {
  return useContext(ModelInfoContext)
}

export function recallPct(info) {
  const r = info?.metrics?.recall_at_risk
  return r == null ? null : Math.round(r * 100)
}
