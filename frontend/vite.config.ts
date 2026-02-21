import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import basicSsl from '@vitejs/plugin-basic-ssl';

export default defineConfig({
  plugins: [sveltekit(), basicSsl()],
  server: {
    host: true,   // listen on 0.0.0.0 so your phone can reach it
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
});
