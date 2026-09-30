import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // During development the Kotlin server runs separately (./gradlew :server:run).
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
