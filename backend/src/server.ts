import { createApp } from './app';
import { config } from './config';

const app = createApp();

app.listen(config.port, '0.0.0.0', () => {
  console.log(`====================================================`);
  console.log(`SMART EXPENSES TRACKER - BACKEND API`);
  console.log(`Listening on http://0.0.0.0:${config.port}`);
  console.log(`Environment: ${config.nodeEnv}`);
  console.log(`Health check: http://localhost:${config.port}/health`);
  console.log(`====================================================`);
});
