import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { DisclaimerFooter } from '../components/Layout'
import Field, { Button, ErrorBanner, inputClass } from '../components/Field'

export default function LoginPage() {
  const { signedIn, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mode, setMode] = useState('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  if (signedIn) return <Navigate to="/" replace />

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await (mode === 'login' ? login(email, password) : register(email, password))
      navigate(location.state?.from ?? '/', { replace: true })
    } catch (err) {
      const fields = Object.values(err.fieldErrors ?? {})
      setError(fields.length ? `${err.message}: ${fields.join(', ')}` : err.message)
    } finally {
      setBusy(false)
    }
  }

  const tab = (m, label) => (
    <button
      type="button"
      onClick={() => {
        setMode(m)
        setError(null)
      }}
      className={`flex-1 rounded-md py-2 text-sm font-semibold ${mode === m ? 'bg-surface text-ink shadow-sm' : 'text-ink-2'}`}
    >
      {label}
    </button>
  )

  return (
    <div className="flex min-h-screen flex-col">
      <main className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">
          <h1 className="text-center text-3xl font-bold tracking-tight">
            Mi<span className="text-accent">Advisor</span>
          </h1>
          <p className="mt-2 text-center text-sm text-ink-2">
            See where each course is heading, and get a plan before it's too late.
          </p>
          <div className="mt-6 flex gap-1 rounded-lg bg-line/60 p-1">
            {tab('login', 'Log in')}
            {tab('register', 'Create account')}
          </div>
          <form onSubmit={submit} className="mt-4 space-y-4 rounded-xl border border-line bg-surface p-5">
            <Field label="Email">
              <input type="email" autoComplete="email" className={inputClass} value={email} onChange={(e) => setEmail(e.target.value)} required />
            </Field>
            <Field label="Password" hint={mode === 'register' ? 'At least 8 characters' : undefined}>
              <input
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                className={inputClass}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={mode === 'register' ? 8 : undefined}
                required
              />
            </Field>
            <ErrorBanner error={error} />
            <Button type="submit" disabled={busy} className="w-full">
              {busy ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}
            </Button>
          </form>
        </div>
      </main>
      <DisclaimerFooter />
    </div>
  )
}
