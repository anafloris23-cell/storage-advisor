import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Frontend-ul rulează pe :5173 și proxază apelurile /api către backend-ul Spring Boot pe :8089.
// Astfel codul din aplicație folosește căi relative (/api/analyze), fără URL-uri hardcodate.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8089',
        changeOrigin: true,
      },
    },
  },
})
