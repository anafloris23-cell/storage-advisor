import React from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider } from 'antd'
import roRO from 'antd/locale/ro_RO'
import 'antd/dist/reset.css'
import App from './App.jsx'
import './index.css'

// Temă globală Ant Design: păstrăm accentul albastru al aplicației și colțurile rotunjite.
const theme = {
  token: {
    colorPrimary: '#4f86f7',
    borderRadius: 8,
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
  },
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={roRO} theme={theme}>
      <App />
    </ConfigProvider>
  </React.StrictMode>,
)
