import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Runs an async loader on mount (and when deps change) with loading/error state and a reload().
 * Ignores results from stale runs so fast navigation can't show the wrong course's data.
 */
export default function useAsync(loader, deps) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const run = useRef(0)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(loader, deps)

  const reload = useCallback(async () => {
    const id = ++run.current
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const data = await load()
      if (id === run.current) setState({ data, loading: false, error: null })
    } catch (e) {
      if (id === run.current) setState((s) => ({ ...s, loading: false, error: e.message }))
    }
  }, [load])

  useEffect(() => {
    reload()
  }, [reload])

  const setData = useCallback((data) => setState((s) => ({ ...s, data })), [])
  return { ...state, reload, setData }
}
