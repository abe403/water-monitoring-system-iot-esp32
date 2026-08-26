import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 4173,
    proxy: {
      "/api": "http://localhost:8084",
      "/ws": {
        target: "http://localhost:8084",
        ws: true,
      },
    },
  },
  preview: { port: 4173 },
  build: {
    sourcemap: process.env.SOURCE_MAPS === "true",
    chunkSizeWarningLimit: 750,
  },
});
