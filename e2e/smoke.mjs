// Drives the app in headless Chromium: usage `node e2e/smoke.mjs <url>`.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const url = process.argv[2];
const out = 'out/e2e';
mkdirSync(out, { recursive: true });

const consoleErrors = new Map();
let current = null;
let step = 'start';

const track = (page) => {
  const errors = [];
  consoleErrors.set(page, errors);
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
  page.on('pageerror', (e) => errors.push(String(e)));
  return page;
};

const begin = (name, page) => { step = name; current = page; };
const done = (msg) => console.log(`ok   ${step}: ${msg}`);

const scoutReply = (page, text) =>
  page.locator('li', { has: page.locator('.author .name', { hasText: 'Scout' }) })
      .filter({ has: page.locator('.sr-only', { hasText: `Noted: ${text}` }) });

const allSettled = (reply) => reply.locator('.seg').evaluateAll(
  (segs) => segs.length > 0 && segs.every((s) => parseFloat(s.style.getPropertyValue('--p')) === 1));

async function send(page, text) {
  await page.locator('#composer-text').fill(text);
  await page.locator('#composer-text').press('Enter');
  await page.locator('li', { has: page.locator('.author .name', { hasText: 'Ben' }) })
            .filter({ has: page.locator('.sr-only', { hasText: text }) }).waitFor({ timeout: 10000 });
}

const browser = await chromium.launch();
let code = 0;
try {
  const page = track(await browser.newPage());
  begin('open and header', page);
  await page.goto(url);
  await page.locator('header h1').waitFor({ timeout: 15000 });
  const chip = await page.locator('.members .chip.agent .owner').textContent({ timeout: 15000 });
  if (chip.trim() !== 'agent of Ben') throw new Error(`agent chip reads "${chip}"`);
  done('header shown, agent chip reads "agent of Ben"');

  begin('send message', page);
  await send(page, 'Hello from the smoke test');
  done('message shown as Ben\'s');

  begin('agent reply settles', page);
  const reply = scoutReply(page, 'Hello from the smoke test');
  await reply.waitFor({ timeout: 20000 });
  for (let i = 0; i < 100 && !(await allSettled(reply)); i++) await page.waitForTimeout(100);
  if (!(await allSettled(reply))) throw new Error('segments never reached --p 1');
  await page.screenshot({ path: `${out}/conversation.png`, fullPage: true });
  done('Scout replied and every segment settled');

  begin('reduced motion', null);
  const context = await browser.newContext({ reducedMotion: 'reduce' });
  const page2 = track(await context.newPage());
  current = page2;
  await page2.goto(url);
  await page2.locator('header h1').waitFor({ timeout: 15000 });
  await send(page2, 'Second, without motion');
  const reply2 = scoutReply(page2, 'Second, without motion');
  await reply2.waitFor({ timeout: 20000 });
  if (!(await allSettled(reply2))) throw new Error('reply was not fully settled when it first appeared');
  await page2.screenshot({ path: `${out}/reduced-motion.png`, fullPage: true });
  done('reply arrived already settled');
} catch (e) {
  code = 1;
  console.error(`FAIL ${step}: ${e.message}`);
  const errors = current ? consoleErrors.get(current) : [];
  console.error(`console errors (${errors.length}):`);
  errors.forEach((m) => console.error('  ' + m));
  try { await current?.screenshot({ path: `${out}/failure.png`, fullPage: true });
        console.error(`screenshot: ${out}/failure.png`); } catch {}
} finally {
  await browser.close();
}
process.exit(code);
