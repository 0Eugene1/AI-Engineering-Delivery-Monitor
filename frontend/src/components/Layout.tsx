import { NavLink, Outlet } from 'react-router-dom'



export function Layout() {

  return (

    <div className="app-shell">

      <header className="topbar">

        <NavLink to="/" className="brand">

          Монитор доставки

        </NavLink>

        <nav className="nav">

          <NavLink to="/" end>

            Дашборд

          </NavLink>

          <NavLink to="/activity">Активность</NavLink>

        </nav>

      </header>

      <main className="page">

        <Outlet />

      </main>

    </div>

  )

}

