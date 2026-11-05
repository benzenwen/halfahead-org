// Drives the typing lab in headless Chromium: usage `node e2e/lab.mjs <url>`.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const url = process.argv[2];
const out = 'out/lab';
const text = 'we finally shipped it! then a crash ';
// Typed after the settled step so the block wraps onto further lines.
const more = 'and we fixed it before anyone noticed, so lunch was quiet ';
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

// Where the block sits: its drawn centre within 12% of the width of the
// horizontal centre, and a little above the vertical centre (the lens shift
// lifts it 8% of the height). Returns a summary for done().
const assertCentred = (c) => {
  const off = c.x - c.width / 2;
  if (!(Math.abs(off) < 0.12 * c.width))
    throw new Error(`block centre at x ${c.x.toFixed(1)} is ${off.toFixed(1)} px off a ${c.width} px centre`);
  const above = c.height / 2 - c.y;
  if (!(above > 0 && above < 0.2 * c.height))
    throw new Error(`block centre at y ${c.y.toFixed(1)} is not a little above the middle of ${c.height} px`);
  return `block centre ${off.toFixed(1)} px across, ${above.toFixed(1)} px above the middle`;
};

// The frame drawn after everything typed so far, read in the page.
const nextFrame = (page) => page.evaluate(() => new Promise((resolve) =>
  requestAnimationFrame(() => requestAnimationFrame(() => {
    const f = window.__lab.lastFrame();
    resolve({
      value: document.getElementById('type').value,
      live: window.__lab.liveText(),
      drawn: window.__lab.drawn(),
      ghosts: f.poses.filter((p) => p['ghost?']).length,
      unscaled: f.poses.filter((p) => !p['ghost?'] && p.scale !== 1).length,
      lines: new Set(f.poses.filter((p) => !p['ghost?']).map((p) => p.y)).size,
      centre: window.__lab.blockCentre(),
    });
  }))));

async function open(page, at = url) {
  await page.goto(at);
  await page.waitForFunction(() => window.__lab?.backend, null, { timeout: 30000 });
  return page.evaluate(() => window.__lab.backend);
}

const browser = await chromium.launch({
  args: ['--enable-unsafe-webgpu', '--enable-unsafe-swiftshader', '--use-angle=swiftshader'],
});
let code = 0;
try {
  const page = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  begin('open', page);
  const backend = await open(page);
  if (!['WebGPU', 'WebGL2'].includes(backend)) throw new Error(`backend is "${backend}"`);
  const status = await page.locator('#status').textContent();
  if (!status.includes(backend)) throw new Error(`status line reads "${status}"`);
  if (await page.locator('#debug').isVisible()) throw new Error('the debug overlay shows without ?debug');
  console.log(`backend: ${backend}`);
  done(`renderer started with ${backend}; status reads "${status}"; no debug overlay`);

  begin('accessibility', page);
  if ((await page.locator('canvas[aria-hidden="true"]').count()) !== 1)
    throw new Error('the canvas is not aria-hidden');
  const input = page.getByLabel('Type here');
  if (!(await input.evaluate((el) => el.tagName === 'TEXTAREA' && el === document.activeElement)))
    throw new Error('the focused element is not the textarea labelled "Type here"');
  done('canvas aria-hidden; the focused element is the textarea labelled "Type here"');

  begin('typing', page);
  await page.keyboard.type(text, { delay: 70 });
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${out}/typing-mid.png` });
  const typed = await nextFrame(page);
  if (typed.value !== text) throw new Error(`textarea holds "${typed.value}"`);
  if (typed.live !== typed.value) throw new Error(`live text "${typed.live}" != "${typed.value}"`);
  if (typed.drawn < typed.value.length) throw new Error(`drew ${typed.drawn} of ${typed.value.length}`);
  done(`live text matches the textarea; ${typed.drawn} glyphs drawn`);

  begin('embers', page);
  for (let i = 0; i < 3; i++) await page.keyboard.press('Backspace');
  await page.waitForTimeout(100);
  // Read before the screenshot: embers live 600 ms, and a software-rendered
  // screenshot can take most of that.
  const burning = await nextFrame(page);
  await page.screenshot({ path: `${out}/embers.png` });
  if (burning.ghosts === 0) throw new Error('no ghost poses after Backspace');
  if (burning.live !== burning.value) throw new Error(`live text "${burning.live}" != "${burning.value}"`);
  done(`${burning.ghosts} ember(s) in flight`);

  begin('settled', page);
  await page.waitForTimeout(2000);
  await page.screenshot({ path: `${out}/settled.png` });
  const settled = await nextFrame(page);
  if (settled.ghosts !== 0) throw new Error(`${settled.ghosts} ghosts after 2 s`);
  if (settled.unscaled !== 0) throw new Error(`${settled.unscaled} live poses not at scale 1`);
  // Upright at rest: every live glyph's projected left edge within 2 degrees
  // of the screen vertical, and the block centred under the camera.
  const stage = await page.evaluate(() => {
    const leans = window.__lab.lastFrame().poses
      .map((p, i) => ({ i, char: p.char, ghost: p['ghost?'] }))
      .filter((g) => !g.ghost)
      .map((g) => ({ ...g, lean: window.__lab.lean(g.i) }));
    return { leans, centre: window.__lab.blockCentre() };
  });
  if (stage.leans.length !== settled.live.length)
    throw new Error(`measured ${stage.leans.length} glyphs of ${settled.live.length}`);
  const worst = stage.leans.reduce((a, b) => (Math.abs(b.lean) > Math.abs(a.lean) ? b : a));
  if (!(Math.abs(worst.lean) < 2))
    throw new Error(`glyph ${worst.i} ("${worst.char}") leans ${worst.lean.toFixed(2)} degrees`);
  done(`no ghosts; every live pose at scale 1; worst lean ${worst.lean.toFixed(2)} deg `
    + `("${worst.char}"); ${assertCentred(stage.centre)}`);

  begin('wrapped', page);
  await page.keyboard.type(more, { delay: 30 });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: `${out}/wrapped.png` });
  const wrapped = await nextFrame(page);
  if (wrapped.live !== wrapped.value) throw new Error(`live text "${wrapped.live}" != "${wrapped.value}"`);
  if (wrapped.lines < 2) throw new Error(`the block did not wrap: ${wrapped.lines} line(s)`);
  done(`${wrapped.lines} lines; ${assertCentred(wrapped.centre)}`);

  begin('debug', null);
  const debugged = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = debugged;
  const debugUrl = new URL(url);
  debugUrl.searchParams.set('debug', '');
  const debugBackend = await open(debugged, debugUrl.href);
  await debugged.keyboard.type('we shipped it ', { delay: 70 });
  await debugged.waitForTimeout(400);
  await debugged.screenshot({ path: `${out}/debug.png` });
  const overlay = debugged.locator('#debug');
  if (!(await overlay.isVisible())) throw new Error('no visible overlay with ?debug');
  const shown = await overlay.textContent();
  const frameNo = Number(/frame (\d+)/.exec(shown)?.[1]);
  if (!(frameNo > 10)) throw new Error(`overlay frame number is ${frameNo}: "${shown}"`);
  if (!/\d+ fps/.test(shown)) throw new Error(`no fps in the overlay: "${shown}"`);
  if (!shown.includes(debugBackend)) throw new Error(`overlay does not name ${debugBackend}: "${shown}"`);
  if (!shown.split('\n').some((l) => l.includes('shipped -> launch')))
    throw new Error(`no "shipped -> launch" line in the overlay: "${shown}"`);
  done(`overlay reads "${shown.split('\n').join(' / ')}"`);
  // Its console errors are already collected; a second software-rendered
  // page left running would starve the next one.
  await debugged.close();

  begin('reduced motion', null);
  const context = await browser.newContext({ reducedMotion: 'reduce' });
  const still = track(await context.newPage());
  current = still;
  await open(still);
  // On every frame while typing, how far the camera's aim trails the
  // block's centre (lifted by the lens shift); the worst is kept.
  await still.evaluate(() => {
    window.__worstLag = 0;
    const sample = () => {
      const c = window.__lab.blockCentre();
      const lag = Math.hypot(c.aimX - c.width / 2, c.aimY - c.height * (0.5 - 0.08));
      window.__worstLag = Math.max(window.__worstLag, lag);
      requestAnimationFrame(sample);
    };
    requestAnimationFrame(sample);
  });
  await still.keyboard.type(text, { delay: 70 });
  const calm = await nextFrame(still);
  if (calm.live !== text) throw new Error(`live text "${calm.live}"`);
  if (calm.unscaled !== 0) throw new Error(`${calm.unscaled} live poses not at scale 1`);
  if (calm.ghosts !== 0) throw new Error(`${calm.ghosts} ghosts with reduced motion`);
  // The camera does not glide after the block: on every frame drawn while
  // typing, its aim is already on the block, with no settle wait.
  const lag = await still.evaluate(() => window.__worstLag);
  if (!(lag < 1.5)) throw new Error(`the camera trailed the block by up to ${lag.toFixed(1)} px with reduced motion`);
  done(`every live pose at scale 1 right after typing; camera never more than ${lag.toFixed(2)} px off the block`);

  for (const [p, errors] of consoleErrors) {
    current = p;
    if (errors.length) throw new Error(`console errors: ${errors.join(' | ')}`);
  }
  console.log(`screenshots: ${['typing-mid', 'embers', 'settled', 'wrapped', 'debug'].map((n) => `${out}/${n}.png`).join(', ')}`);
} catch (e) {
  code = 1;
  console.error(`FAIL ${step}: ${e.message}`);
  const errors = current ? consoleErrors.get(current) : [];
  console.error(`console errors (${errors.length}):`);
  errors.forEach((m) => console.error('  ' + m));
  try { await current?.screenshot({ path: `${out}/failure.png` });
        console.error(`screenshot: ${out}/failure.png`); } catch {}
} finally {
  await browser.close();
}
process.exit(code);
