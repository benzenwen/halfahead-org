// Drives the typing lab in headless Chromium: usage `node e2e/lab.mjs <url>`.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const url = process.argv[2];
const out = 'out/lab';
const text = 'we finally shipped it! then a crash ';
// Typed after the settled step so the block wraps onto further lines.
const more = 'and we fixed it before anyone noticed, so lunch was quiet ';
// Ends a word with "merge" (a fuse) right after "a", its near neighbour.
const sentence = 'This is a merge of a ';
// A draft taller than the screen: older lines must recede, not fall off.
const credits = Array.from({ length: 14 }, (_, i) => `line ${i + 1} of the crawl`).join('\n');
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
// horizontal centre, and a little above the vertical centre (the camera
// looks at the current line's baseline, and the letters stand above it).
// Returns a summary for done().
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

  begin('crawl', null);
  const crawl = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = crawl;
  await open(crawl);
  await crawl.keyboard.type(credits, { delay: 10 });
  await crawl.waitForTimeout(2000);
  await crawl.screenshot({ path: `${out}/crawl.png` });
  const seen = await crawl.evaluate(() => {
    const lab = window.__lab;
    const { width, height } = lab.blockCentre();
    const live = lab.lastFrame().poses.filter((p) => !p['ghost?']);
    const placed = live.map((p) => ({ ...lab.project(p.x, p.y, p.z), opacity: p.opacity, char: p.char }));
    return { width, height, text: lab.liveText(), placed,
             lines: new Set(live.map((p) => p.y)).size,
             dimmest: Math.min(...live.map((p) => p.opacity)) };
  });
  if (seen.text !== credits) throw new Error(`live text "${seen.text}"`);
  if (seen.lines < 14) throw new Error(`only ${seen.lines} lines`);
  if (!(seen.dimmest < 1)) throw new Error('no line receded: every live glyph at full opacity');
  const inside = (q) => q.x >= 0 && q.x <= seen.width && q.y >= 0 && q.y <= seen.height;
  const lost = seen.placed.filter((q) => !inside(q) && q.opacity > 0.3);
  if (lost.length)
    throw new Error(`${lost.length} glyph(s) off screen at opacity > 0.3, e.g. "${lost[0].char}" at `
      + `(${lost[0].x.toFixed(0)}, ${lost[0].y.toFixed(0)}) opacity ${lost[0].opacity.toFixed(2)}`);
  const last = seen.placed[seen.placed.length - 1];
  if (!(last.y > 0.2 * seen.height && last.y < 0.8 * seen.height))
    throw new Error(`the current line is at y ${last.y.toFixed(0)} of ${seen.height} px, outside the middle 60%`);
  const offscreen = seen.placed.filter((q) => !inside(q)).length;
  done(`${seen.lines} lines; ${offscreen} glyph(s) off screen, all at opacity <= 0.3; dimmest ${seen.dimmest.toFixed(2)}; `
    + `current line at ${(100 * last.y / seen.height).toFixed(0)}% of the height`);
  await crawl.close();

  begin('steady line', null);
  const steady = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = steady;
  await open(steady);
  // Where the first glyph's place on the layout (the column's origin) lands,
  // every 100 ms while typing: the letter's own motion does not count.
  await steady.evaluate(() => {
    window.__originXs = [];
    window.__sampler = setInterval(() => window.__originXs.push(window.__lab.project(0, 0, 0).x), 100);
  });
  await steady.keyboard.type(sentence, { delay: 70 });
  const xs = await steady.evaluate(() => { clearInterval(window.__sampler); return window.__originXs; });
  await steady.screenshot({ path: `${out}/steady.png` });
  if (xs.length < 10) throw new Error(`only ${xs.length} samples while typing`);
  const drift = Math.max(...xs) - Math.min(...xs);
  if (!(drift <= 2)) throw new Error(`the line moved ${drift.toFixed(1)} px while typing "${sentence}"`);
  done(`the line's start stayed within ${drift.toFixed(2)} px over ${xs.length} samples while typing`);
  await steady.close();

  begin('contained fuse', null);
  const fusing = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = fusing;
  // The page's clock is held, so the whole fuse is checked in fixed slices
  // and the screenshot is taken mid-fuse, however slowly headless draws.
  await fusing.clock.install();
  await open(fusing);
  await fusing.clock.pauseAt(await fusing.evaluate(() => Date.now() + 1000));
  for (const ch of 'This is a merge ') {
    await fusing.keyboard.type(ch);
    await fusing.clock.runFor(70);
  }
  // How far the leftmost letter of "merge" lands right of the right edge of
  // the "a" before it, and how far any of its letters is off the line.
  const sampleFuse = () => fusing.evaluate(() => {
    const lab = window.__lab;
    const at = lab.liveText().indexOf(' a merge ');
    if (at < 0) return null;
    const poses = lab.lastFrame().poses;
    const a = poses[at + 1];
    const merge = poses.slice(at + 3, at + 8);
    const edge = lab.project(a.x + lab.advance(a.char), a.y, a.z).x;
    return {
      margin: Math.min(...merge.map((p) => lab.project(p.x, p.y, p.z).x)) - edge,
      lift: Math.max(...merge.map((p) => Math.abs(p.y))),
    };
  });
  const slices = [];
  let shot = null;
  for (let t = 0; t <= 1000; t += 50) {
    const s = await sampleFuse();
    if (!s) throw new Error(`"a merge " is not on the line ${t} ms after the space`);
    slices.push(s);
    // births move in depth too, but only the fuse moves letters off the line
    if (shot === null && t >= 100 && s.lift > 0.05) {
      await fusing.screenshot({ path: `${out}/fuse.png` });
      shot = { t, lift: s.lift };
    }
    await fusing.clock.runFor(50);
  }
  if (shot === null)
    throw new Error(`no letter of "merge" left the line by 0.05 em: lifts ${slices.map((s) => s.lift.toFixed(3)).join(' ')}`);
  const nearest = Math.min(...slices.map((s) => s.margin));
  if (!(nearest >= 0))
    throw new Error(`a letter of "merge" landed ${(-nearest).toFixed(1)} px left of the "a" before it`);
  const resting = slices[slices.length - 1].lift;
  if (!(resting < 0.01)) throw new Error(`"merge" still ${resting.toFixed(3)} em off the line after 1 s`);
  done(`"merge" checked in ${slices.length} slices of 50 ms; never nearer than ${nearest.toFixed(1)} px `
    + `to the "a" before it; fuse.png at ${shot.t} ms, a letter ${shot.lift.toFixed(2)} em off the line`);
  await fusing.close();

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
  if (!/camera target x -?\d/.test(shown)) throw new Error(`no camera target in the overlay: "${shown}"`);
  const clipped = await overlay.evaluate((el) => el.scrollWidth > el.clientWidth);
  if (clipped) throw new Error('the overlay is cut off on the right');
  done(`overlay reads "${shown.split('\n').join(' / ')}"`);
  // Its console errors are already collected; a second software-rendered
  // page left running would starve the next one.
  await debugged.close();

  begin('reduced motion', null);
  const context = await browser.newContext({ reducedMotion: 'reduce' });
  const still = track(await context.newPage());
  current = still;
  await open(still);
  // On every frame while typing: how far the frame's camera target is from
  // the current line (it must not ease), and how far the drawn aim point is
  // from the middle of the canvas (the renderer must follow it); the worst
  // of each is kept.
  await still.evaluate(() => {
    window.__worstLag = 0;
    window.__worstAim = 0;
    const sample = () => {
      const f = window.__lab.lastFrame();
      const live = f.poses.filter((p) => !p['ghost?']);
      if (live.length) {
        window.__worstLag = Math.max(window.__worstLag, Math.abs(f.camera.ty - live[live.length - 1].y));
        const c = window.__lab.blockCentre();
        window.__worstAim = Math.max(window.__worstAim, Math.hypot(c.aimX - c.width / 2, c.aimY - c.height / 2));
      }
      requestAnimationFrame(sample);
    };
    requestAnimationFrame(sample);
  });
  await still.keyboard.type(text, { delay: 70 });
  const calm = await nextFrame(still);
  if (calm.live !== text) throw new Error(`live text "${calm.live}"`);
  if (calm.unscaled !== 0) throw new Error(`${calm.unscaled} live poses not at scale 1`);
  if (calm.ghosts !== 0) throw new Error(`${calm.ghosts} ghosts with reduced motion`);
  // The camera does not glide after the line: on every frame drawn while
  // typing, its target is already on the current line, with no settle wait.
  const { lag, aim } = await still.evaluate(() => ({ lag: window.__worstLag, aim: window.__worstAim }));
  if (!(lag < 1e-9)) throw new Error(`the camera target trailed the current line by up to ${lag} em with reduced motion`);
  if (!(aim < 1.5)) throw new Error(`the drawn aim point strayed ${aim.toFixed(1)} px from the canvas centre`);
  done(`every live pose at scale 1 right after typing; camera target on the current line on every frame `
    + `(worst ${lag} em); aim within ${aim.toFixed(2)} px of the centre`);

  for (const [p, errors] of consoleErrors) {
    current = p;
    if (errors.length) throw new Error(`console errors: ${errors.join(' | ')}`);
  }
  console.log(`screenshots: ${['typing-mid', 'embers', 'settled', 'wrapped', 'crawl', 'steady', 'fuse', 'debug'].map((n) => `${out}/${n}.png`).join(', ')}`);
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
