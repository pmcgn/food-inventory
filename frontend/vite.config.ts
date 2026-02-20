import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import basicSsl from '@vitejs/plugin-basic-ssl';

export default defineConfig({
  plugins: [sveltekit(), basicSsl()],
  server: {
    https: true,
    host: true,   // listen on 0.0.0.0 so your phone can reach it
    proxy: {
      '/inventory': 'http://localhost:8080',
      '/alerts':    'http://localhost:8080',
      '/settings':  'http://localhost:8080'
    }
  }
});
