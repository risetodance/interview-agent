import React from 'react'
import ReactDOM from 'react-dom/client'
import { createPinia } from 'pinia'
import App from './App'
import './index.css'

// 创建 pinia 实例
const pinia = createPinia()

// 直接修改 window 对象挂载 pinia
// @ts-ignore
window.__PINIA__ = pinia

const root = ReactDOM.createRoot(document.getElementById('root')!)

root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
