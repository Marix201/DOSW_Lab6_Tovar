import { useEffect, useState } from 'react'
import TasksPage from './features/tasks/pages/TasksPage'
import {
  clearAccessToken,
  getAdminSummary,
  getCurrentUser,
  loginUser,
  registerUser,
  setAccessToken,
  setUnauthorizedHandler,
} from './api/authApi'

const inputStyle = {
  display: 'block',
  width: '100%',
  boxSizing: 'border-box',
  padding: '10px',
  marginTop: '6px',
  marginBottom: '14px',
}

const buttonStyle = {
  padding: '10px 14px',
  cursor: 'pointer',
  marginRight: '8px',
  marginTop: '8px',
}

export default function App() {
  const [mode, setMode] = useState('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [user, setUser] = useState(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [adminSummary, setAdminSummary] = useState(null)

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null)
      setAdminSummary(null)
      setPassword('')
      setError('Tu sesión expiró. Inicia sesión nuevamente.')
    })
  }, [])

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setMessage('')
    setLoading(true)

    try {
      if (mode === 'register') {
        await registerUser({ name: name.trim(), email: email.trim(), password })
        setMode('login')
        setPassword('')
        setMessage('Registro exitoso. Ahora puedes iniciar sesión.')
        return
      }

      const session = await loginUser({ email: email.trim(), password })

      if (!session.accessToken) {
        throw new Error('La respuesta del servidor no contiene un token de acceso.')
      }

      setAccessToken(session.accessToken)

      try {
        const currentUser = await getCurrentUser()
        setUser(currentUser)
        setPassword('')
        setMessage('')
      } catch (requestError) {
        clearAccessToken()
        throw requestError
      }
    } catch (requestError) {
      setError(requestError.message || 'Ocurrió un error. Inténtalo de nuevo.')
    } finally {
      setLoading(false)
    }
  }

  function handleLogout() {
    clearAccessToken()
    setUser(null)
    setAdminSummary(null)
    setPassword('')
    setError('')
    setMessage('Sesión cerrada.')
  }

  async function handleAdminSummary() {
    setError('')
    try {
      const result = await getAdminSummary()
      setAdminSummary(JSON.stringify(result, null, 2))
    } catch (requestError) {
      setError(requestError.message || 'No fue posible consultar el resumen.')
    }
  }

  if (user) {
    return (
      <main style={{ maxWidth: 900, margin: '30px auto', padding: '0 18px' }}>
        <header style={{ marginBottom: 24 }}>
          <h1>App ToDo</h1>
          <p>
            Sesión iniciada como <strong>{user.name}</strong> ({user.email})
          </p>
          <p>Rol: {user.role}</p>
          <button style={buttonStyle} onClick={handleLogout}>
            Cerrar sesión
          </button>
          {user.role === 'ADMIN' && (
            <button style={buttonStyle} onClick={handleAdminSummary}>
              Consultar resumen administrativo
            </button>
          )}
        </header>

        {error && <p role="alert">{error}</p>}
        {adminSummary && (
          <pre style={{ whiteSpace: 'pre-wrap' }}>{adminSummary}</pre>
        )}

        <TasksPage />
      </main>
    )
  }

  return (
    <main style={{ maxWidth: 420, margin: '50px auto', padding: '24px' }}>
      <h1>App ToDo</h1>
      <h2>{mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</h2>

      {message && <p role="status">{message}</p>}
      {error && <p role="alert">{error}</p>}

      <form onSubmit={handleSubmit}>
        {mode === 'register' && (
          <label>
            Nombre
            <input
              style={inputStyle}
              value={name}
              onChange={(event) => setName(event.target.value)}
              autoComplete="name"
              required
            />
          </label>
        )}

        <label>
          Correo electrónico
          <input
            style={inputStyle}
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
          />
        </label>

        <label>
          Contraseña
          <input
            style={inputStyle}
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            minLength={mode === 'register' ? 8 : undefined}
            required
          />
        </label>

        <button style={buttonStyle} type="submit" disabled={loading}>
          {loading
            ? 'Procesando...'
            : mode === 'login'
              ? 'Iniciar sesión'
              : 'Registrarme'}
        </button>
      </form>

      <p>
        {mode === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?'}{' '}
        <button
          type="button"
          style={buttonStyle}
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login')
            setError('')
            setMessage('')
          }}
        >
          {mode === 'login' ? 'Registrarme' : 'Iniciar sesión'}
        </button>
      </p>
    </main>
  )
}
