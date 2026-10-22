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

  begin('credits', page);
  await page.getByRole('button', { name: 'End scene' }).click();
  await page.waitForTimeout(5500);
  const dialog = page.locator('[role=dialog][aria-label=Credits]');
  if ((await dialog.count()) !== 1) throw new Error('credits dialog did not appear');
  if (!(await dialog.evaluate((d) => d.contains(document.activeElement))))
    throw new Error('focus is not inside the credits dialog');
  const rowY = async (text) => {
    const box = await dialog.locator('.seg', { hasText: new RegExp(`^\\s*${text}\\s*$`, "i") }).first().boundingBox();
    if (!box) throw new Error(`no credits line "${text}"`);
    return box.y;
  };
  const gap = Math.abs((await rowY('HALF AHEAD')) - (await rowY('BEN')));
  if (gap <= 40) throw new Error(`credits lines run together (y differs by ${gap}px)`);
  await page.screenshot({ path: `${out}/credits.png`, fullPage: true });
  await page.keyboard.press('Escape');
  await dialog.waitFor({ state: 'detached', timeout: 3000 })
    .catch(() => { throw new Error('Escape did not close the credits'); });
  done('credits rolled on separate rows, focus inside, Escape closed them');

  begin('scene', page);
  await page.keyboard.press('Escape');
  const rendered = () => page.locator('ol.transcript > li').count();
  for (let i = await rendered(); i < 4; i++) await send(page, `Scene line ${i}`);
  const before = await rendered();
  await page.getByRole('button', { name: 'Replay scene' }).click();
  await page.waitForTimeout(300);
  const early = await rendered();
  if (!(early < before)) throw new Error(`replay did not hide messages (${early} of ${before})`);
  await page.getByRole('button', { name: 'Pause' }).click();
  const frozen = await rendered();
  await page.waitForTimeout(1000);
  if ((await rendered()) !== frozen) throw new Error('pause did not freeze the scene');
  await page.screenshot({ path: `${out}/scene.png`, fullPage: true });
  await page.getByRole('button', { name: 'Resume' }).click();
  const bar = page.locator('[role=toolbar][aria-label=Scene]');
  await bar.waitFor({ state: 'detached', timeout: 35000 })
    .catch(() => { throw new Error('scene did not finish within 35 s'); });
  if ((await rendered()) !== before) throw new Error('not all messages rendered after replay');
  const settled = await page.locator('ol.transcript .seg').evaluateAll(
    (segs) => segs.every((s) => parseFloat(s.style.getPropertyValue('--p')) === 1));
  if (!settled) throw new Error('messages not settled after replay');
  await page.getByRole('button', { name: 'Replay scene' }).click();
  await page.getByRole('button', { name: 'Stop' }).click();
  if ((await rendered()) !== before) throw new Error('Stop did not show every message');
  done('replay hid messages, pause froze it, resume finished, Stop showed all');
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
