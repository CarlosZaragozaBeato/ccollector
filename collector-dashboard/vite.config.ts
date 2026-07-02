import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Builds the SPA into the Maven output directory so maven-jar-plugin
// packages the assets automatically. Quarkus serves META-INF/resources/
// from every JAR on the classpath, so the dashboard is reachable at /dashboard/.
export default defineConfig({
  plugins: [react()],
  base: '/dashboard/',
  build: {
    outDir: 'target/classes/META-INF/resources/dashboard',
    emptyOutDir: true,
    chunkSizeWarningLimit: 600,
  },
})
