import { serve } from '@hono/node-server';
import { zValidator } from '@hono/zod-validator';
import { Hono } from 'hono';
import { z } from 'zod';
import { validateDiagram, type ValidationResult } from './validator.js';

const ValidateRequestSchema = z.object({
  diagrams: z
    .array(
      z.object({
        id: z.string().min(1).optional(),
        code: z.string().min(1),
      }),
    )
    .min(1),
});

const app = new Hono();

app.get('/health', (c) => c.json({ status: 'ok' }));

app.post('/validate', zValidator('json', ValidateRequestSchema), async (c) => {
  const { diagrams } = c.req.valid('json');
  const results: ValidationResult[] = [];
  for (let i = 0; i < diagrams.length; i++) {
    const { id, code } = diagrams[i];
    results.push(await validateDiagram(id ?? String(i), code));
  }
  return c.json({ results });
});

const port = Number(process.env.PORT ?? 3000);

serve({ fetch: app.fetch, port, hostname: '0.0.0.0' }, (info) => {
  console.log(`mermaid-validator listening on :${info.port}`);
});
