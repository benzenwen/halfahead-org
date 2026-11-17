// Drives the typing lab in headless Chromium: usage `node e2e/lab.mjs <url>`.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { inflateSync } from 'node:zlib';

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

// Every step but the reveal's runs on the full stage (?reveal=3) unless its
// URL names a level; { plain: true } opens the URL as given, at level 0.
async function open(page, at = url, { plain = false } = {}) {
  const u = new URL(at);
  if (!plain && !u.searchParams.has('reveal')) u.searchParams.set('reveal', '3');
  await page.goto(u.href);
  await page.waitForFunction(() => window.__lab?.backend, null, { timeout: 30000 });
  return page.evaluate(() => window.__lab.backend);
}

// A screenshot's pixels: { width, height, pixel(x, y) -> [r, g, b] }, from
// an 8-bit RGB or RGBA PNG that is not interlaced (what Chromium writes).
// Screenshots are decoded here because reading a WebGPU canvas back is
// unreliable.
function decodePng(png) {
  const idat = [];
  let width = 0, height = 0, colorType = null;
  for (let i = 8; i < png.length;) {
    const len = png.readUInt32BE(i);
    const type = png.toString('ascii', i + 4, i + 8);
    const data = png.subarray(i + 8, i + 8 + len);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      if (data[8] !== 8) throw new Error(`a screenshot PNG of bit depth ${data[8]}`);
      colorType = data[9];
      if (data[12] !== 0) throw new Error('an interlaced screenshot PNG');
    }
    if (type === 'IDAT') idat.push(data);
    i += 12 + len;
  }
  if (colorType !== 2 && colorType !== 6) throw new Error(`a screenshot PNG of colour type ${colorType}`);
  const bpp = colorType === 6 ? 4 : 3;
  const stride = width * bpp;
  const raw = inflateSync(Buffer.concat(idat));
  const px = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const from = y * (stride + 1) + 1;
    const to = y * stride;
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? px[to + i - bpp] : 0;
      const b = y > 0 ? px[to - stride + i] : 0;
      const c = i >= bpp && y > 0 ? px[to - stride + i - bpp] : 0;
      let predicted = 0;
      if (filter === 1) predicted = a;
      else if (filter === 2) predicted = b;
      else if (filter === 3) predicted = (a + b) >> 1;
      else if (filter === 4) {
        const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
        predicted = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      } else if (filter !== 0) throw new Error(`a screenshot PNG with filter ${filter}`);
      px[to + i] = (raw[from + i] + predicted) & 255;
    }
  }
  return { width, height, pixel: (x, y) => [px[y * stride + x * bpp], px[y * stride + x * bpp + 1], px[y * stride + x * bpp + 2]] };
}

// The luminance (0 black to 1 white) of one pixel of the page as shown, at
// the left edge half way down, where only the stage's background is drawn.
async function backgroundLuminance(page) {
  const [r, g, b] = decodePng(await page.screenshot({ clip: { x: 4, y: 320, width: 1, height: 1 } })).pixel(0, 0);
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
}

// --- measuring a still ---------------------------------------------------------
// How much ink each pixel of a box holds, 0 (background) to 1 (the ink's
// colour): where the pixel lies on the line from bg to ink, in linear light,
// because that is where the renderer blends. Colours are 0-255 sRGB triples.
const toLinear = (v) => { const c = v / 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };

function inkMap(img, box, bg, ink) {
  const x0 = Math.max(0, Math.floor(box.x0)), y0 = Math.max(0, Math.floor(box.y0));
  const w = Math.min(img.width, Math.ceil(box.x1)) - x0, h = Math.min(img.height, Math.ceil(box.y1)) - y0;
  if (!(w > 4 && h > 4)) throw new Error(`a sample's box is ${w} x ${h} px on a ${img.width} x ${img.height} screenshot`);
  const from = bg.map(toLinear);
  const along = ink.map((v, i) => toLinear(v) - from[i]);
  const length2 = along.reduce((sum, v) => sum + v * v, 0);
  const cov = new Float32Array(w * h);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const p = img.pixel(x0 + x, y0 + y);
      const c = p.reduce((sum, v, i) => sum + (toLinear(v) - from[i]) * along[i], 0) / length2;
      cov[y * w + x] = Math.max(0, Math.min(1, c));
    }
  return { x0, y0, w, h, cov };
}

// A pixel counts as inked above this: the dark stage's floor lines, far
// fainter, pass behind the lower rows.
const inked = 0.15;

// The summed ink of the columns [xa, xb) and rows [ya, yb) of a map.
function inkSum(m, xa = 0, xb = m.w, ya = 0, yb = m.h) {
  let sum = 0;
  for (let y = Math.floor(ya); y < Math.floor(yb); y++)
    for (let x = Math.floor(xa); x < Math.floor(xb); x++) sum += m.cov[y * m.w + x];
  return sum;
}

const softPixels = (m) => m.cov.reduce((n, c) => n + (c > inked && c < 0.85 ? 1 : 0), 0);

// The width and height of the box around a map's pixels that are mostly ink.
function inkedExtent(m) {
  let xa = m.w, xb = -1, ya = m.h, yb = -1;
  for (let y = 0; y < m.h; y++)
    for (let x = 0; x < m.w; x++)
      if (m.cov[y * m.w + x] > 0.5) { xa = Math.min(xa, x); xb = Math.max(xb, x); ya = Math.min(ya, y); yb = Math.max(yb, y); }
  if (xb < 0) throw new Error('a sample with no pixel mostly ink');
  return { width: xb - xa + 1, height: yb - ya + 1 };
}

// The ink-weighted mean x of the inked pixels in rows [ya, yb) of a map.
function inkCentreX(m, ya, yb) {
  let sum = 0, weight = 0;
  for (let y = Math.floor(ya); y < Math.floor(yb); y++)
    for (let x = 0; x < m.w; x++) {
      const c = m.cov[y * m.w + x];
      if (c > inked) { sum += c * (m.x0 + x); weight += c; }
    }
  if (!(weight > 0)) throw new Error('no ink in a third of a sample');
  return sum / weight;
}

// How far right the ink in the top third of a map is of that in the bottom third.
const lean = (m) => inkCentreX(m, 0, m.h / 3) - inkCentreX(m, 2 * m.h / 3, m.h);

// The colour most pixels of the box share among those mostly ink.
function commonInk(img, m) {
  const counts = new Map();
  for (let y = 0; y < m.h; y++)
    for (let x = 0; x < m.w; x++)
      if (m.cov[y * m.w + x] > 0.5) {
        const key = img.pixel(m.x0 + x, m.y0 + y).join(',');
        counts.set(key, (counts.get(key) ?? 0) + 1);
      }
  if (!counts.size) throw new Error('a sample with no pixel mostly ink');
  return [...counts].reduce((a, b) => (b[1] > a[1] ? b : a))[0].split(',').map(Number);
}

// The colour most pixels of a screenshot have: its background.
function commonColour(img) {
  const counts = new Map();
  for (let y = 0; y < img.height; y += 3)
    for (let x = 0; x < img.width; x += 3) {
      const key = img.pixel(x, y).join(',');
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
  return [...counts].reduce((a, b) => (b[1] > a[1] ? b : a))[0].split(',').map(Number);
}

// The specimen's still, measured: throws on the first channel that is not
// drawn as the design says, and returns a summary for done().
function measureSpecimen(img, samples, inkOf, lights) {
  const bg = commonColour(img);
  const expectedBg = lights === 1 ? [0x14, 0x12, 0x10] : [0xf6, 0xf1, 0xe7];
  if (bg.some((v, i) => Math.abs(v - expectedBg[i]) > 4))
    throw new Error(`the background is rgb(${bg}), not rgb(${expectedBg})`);
  const row = (name) => {
    const found = samples.filter((s) => s.row === name)
      .map((s) => ({ ...s, map: inkMap(img, s.box, bg, inkOf(name === 'ink' ? s.value : 'text')) }));
    if (!found.length) throw new Error(`no ${name} samples`);
    return found;
  };
  const at = (found, pred, what) => {
    const s = found.find((x) => pred(x.value));
    if (!s) throw new Error(`no sample at ${what}`);
    return s.map;
  };
  const is = (v) => (x) => x === v;
  const fx = (n) => n.toFixed(0);

  const weight = row('weight');
  const areas = weight.map((s) => inkSum(s.map));
  const [light, regular, bold] = [-1, 0, 1].map((v) => inkSum(at(weight, is(v), `weight ${v}`)));
  if (!(regular >= 1.15 * light)) throw new Error(`weight 0 inks ${fx(regular)} px, not 15% more than weight -1's ${fx(light)}`);
  if (!(bold >= 1.15 * regular)) throw new Error(`weight +1 inks ${fx(bold)} px, not 15% more than weight 0's ${fx(regular)}`);
  if (!(light >= 0.25 * regular)) throw new Error(`weight -1 inks ${fx(light)} px of weight 0's ${fx(regular)}: the letter is all but gone`);
  if (!areas.every((a, i) => i === 0 || a > areas[i - 1])) throw new Error(`the inked area does not grow with weight: ${areas.map(fx)}`);

  const softs = row('blur').map((s) => softPixels(s.map));
  if (!softs.every((n, i) => i === 0 || n > softs[i - 1])) throw new Error(`the soft pixels do not grow with blur: ${softs}`);

  const skew = row('skew');
  const upright = lean(at(skew, is(0), 'skew 0'));
  const right = lean(at(skew, is(0.3), 'skew 0.3')) - upright;
  const left = lean(at(skew, is(-0.3), 'skew -0.3')) - upright;
  if (!(right > 1)) throw new Error(`at skew +0.3 the top third's ink is ${right.toFixed(1)} px right of the bottom third's (less the upright sample's)`);
  if (!(left < -1)) throw new Error(`at skew -0.3 the top third's ink is ${left.toFixed(1)} px right of the bottom third's (less the upright sample's)`);

  const stretch = row('stretch');
  const rest = inkedExtent(at(stretch, (v) => v.sx === 1 && v.sy === 1, 'stretch rest'));
  const wide = inkedExtent(at(stretch, (v) => v.sx > 1, 'sx 1.4'));
  const tall = inkedExtent(at(stretch, (v) => v.sy > 1, 'sy 1.4'));
  if (!(wide.width >= rest.width + 2)) throw new Error(`sx 1.4 is ${wide.width} px wide, at rest ${rest.width}`);
  if (!(tall.height >= 1.2 * rest.height)) throw new Error(`sy 1.4 is ${tall.height} px tall, at rest ${rest.height}`);
  if (!(Math.abs(wide.height - rest.height) <= 3)) throw new Error(`sx 1.4 changed the height: ${wide.height} px, at rest ${rest.height}`);
  if (!(Math.abs(tall.width - rest.width) <= 3)) throw new Error(`sy 1.4 changed the width: ${tall.width} px, at rest ${rest.width}`);

  const fill = row('fill');
  const halves = (m) => [inkSum(m, 0, m.w / 2), inkSum(m, m.w / 2, m.w)];
  const [halfL, halfR] = halves(at(fill, is(0.5), 'fill 0.5'));
  if (!(halfL >= 2 * halfR)) throw new Error(`at fill 0.5 the left half inks ${fx(halfL)} px, not twice the right half's ${fx(halfR)}`);
  const [dimL, dimR] = halves(at(fill, is(0), 'fill 0'));
  const [fullL, fullR] = halves(at(fill, is(1), 'fill 1'));
  const even = (a, b) => Math.abs(a - b) <= 0.15 * Math.max(a, b);
  if (!even(dimL, dimR)) throw new Error(`fill 0 is not uniform: halves ${fx(dimL)} and ${fx(dimR)} px`);
  if (!even(fullL, fullR)) throw new Error(`fill 1 is not uniform: halves ${fx(fullL)} and ${fx(fullR)} px`);
  const dimShare = (dimL + dimR) / (fullL + fullR);
  if (!(dimShare > 0.25 && dimShare < 0.45)) throw new Error(`fill 0 holds ${dimShare.toFixed(2)} of fill 1's ink, not about 0.35`);
  const filled = fill.map((s) => inkSum(s.map));
  if (!filled.every((a, i) => i === 0 || a > filled[i - 1])) throw new Error(`the ink does not grow with fill: ${filled.map(fx)}`);

  const inks = row('ink');
  if (inks.length !== 5) throw new Error(`${inks.length} inks in the specimen`);
  const off = inks.map((s) => {
    const want = inkOf(s.value), got = commonInk(img, s.map);
    const worst = Math.max(...got.map((v, i) => Math.abs(v - want[i])));
    if (!(worst <= 12)) throw new Error(`the ${s.value} sample is mostly rgb(${got}), not rgb(${want})`);
    return worst;
  });

  return `weight ${areas.map(fx).join(' < ')} px of ink; blur ${softs.join(' < ')} soft px; `
    + `skew ${left.toFixed(1)} / +${right.toFixed(1)} px; stretch ${rest.width}x${rest.height} -> sx ${wide.width}x${wide.height}, sy ${tall.width}x${tall.height}; `
    + `fill 0.5 ${(halfL / halfR).toFixed(1)}:1, fill 0 at ${dimShare.toFixed(2)} of full; inks within ${Math.max(...off)}/255`;
}

// The reveal level of the frame drawn now, waiting up to 5 s for it to reach
// at least `atLeast` (software rendering can lag behind the keys).
async function revealLevel(page, atLeast = 0) {
  await page.waitForFunction((n) => window.__lab.lastFrame().reveal.level >= n, atLeast, { timeout: 5000 })
    .catch(() => {});
  return page.evaluate(() => window.__lab.lastFrame().reveal.level);
}

const browser = await chromium.launch({
  args: ['--enable-unsafe-webgpu', '--enable-unsafe-swiftshader', '--use-angle=swiftshader'],
});
// The second browser, of the webgpu step; closed with the first.
let gpuBrowser = null;
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

  // The reveal: a plain /lab/ starts as a familiar flat box on paper and
  // opens up as it is used.
  begin('reveal 0', null);
  const revealing = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = revealing;
  await open(revealing, url, { plain: true });
  let lv = await revealLevel(revealing);
  let lum = await backgroundLuminance(revealing);
  await revealing.screenshot({ path: `${out}/reveal-0.png` });
  if (lv !== 0) throw new Error(`a plain /lab/ starts at reveal level ${lv}`);
  if (!(lum > 0.8)) throw new Error(`the background at level 0 has luminance ${lum.toFixed(3)}, not paper`);
  done(`level 0; background luminance ${lum.toFixed(3)} (paper)`);

  begin('reveal 1', revealing);
  await revealing.keyboard.type('one two three ', { delay: 70 });
  lv = await revealLevel(revealing, 1);
  await revealing.screenshot({ path: `${out}/reveal-1.png` });
  if (lv !== 1) throw new Error(`after three words the level is ${lv}`);
  lum = await backgroundLuminance(revealing);
  if (!(lum > 0.8)) throw new Error(`the lights went down at level 1: luminance ${lum.toFixed(3)}`);
  done(`level 1 after three words; still on paper (${lum.toFixed(3)})`);

  begin('reveal 2', revealing);
  await revealing.keyboard.type('deploy crash merge finally ', { delay: 70 });
  lv = await revealLevel(revealing, 2);
  if (lv !== 2) throw new Error(`after four glossary words the level is ${lv}`);
  await revealing.waitForTimeout(1500);
  lum = await backgroundLuminance(revealing);
  await revealing.screenshot({ path: `${out}/reveal-2.png` });
  if (!(lum < 0.2)) throw new Error(`1.5 s into level 2 the background has luminance ${lum.toFixed(3)}, not the dark stage`);
  const lights = await revealing.evaluate(() => window.__lab.lastFrame().reveal.lights);
  if (lights !== 1) throw new Error(`1.5 s into level 2 the lights are at ${lights}`);
  done(`level 2 after "deploy crash merge finally"; 1.5 s later lights ${lights}, background ${lum.toFixed(3)}`);

  begin('reveal 3', revealing);
  await revealing.keyboard.type('WHAT A DAY! ', { delay: 70 });
  lv = await revealLevel(revealing, 3);
  if (lv !== 3) throw new Error(`after "WHAT A DAY! " the level is ${lv}`);
  const cardScale = await revealing.waitForFunction(() => {
    const lab = window.__lab;
    const card = lab.directions().find((d) => d.shot === 'card');
    if (!card) return null;
    const on = new Set(card['glyph-ids']);
    const big = Math.max(...lab.lastFrame().poses.filter((p) => on.has(p.id)).map((p) => p.scale));
    return big > 1.5 ? big : null;
  }, null, { timeout: 5000 }).then((h) => h.jsonValue()).catch(() => null);
  await revealing.screenshot({ path: `${out}/reveal-3.png` });
  if (!cardScale) throw new Error('at level 3 no card was performed (no glyph of a card direction at scale > 1.5)');
  done(`level 3 after "WHAT A DAY! "; the card performed, a glyph at scale ${cardScale.toFixed(2)}`);
  await revealing.close();

  begin('reveal wands', null);
  const waving = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = waving;
  await open(waving, url, { plain: true });
  if ((await revealLevel(waving)) !== 0) throw new Error('a fresh page is not at level 0');
  await waving.keyboard.press('Control+.');
  lv = await revealLevel(waving, 1);
  if (lv !== 1) throw new Error(`Ctrl+. raised the level to ${lv}, not 1`);
  // slow pointing is no wand: 750 px over about a second
  await waving.mouse.move(100, 300);
  for (let k = 1; k <= 10; k++) {
    await waving.mouse.move(100 + 75 * k, 300);
    await waving.waitForTimeout(100);
  }
  if ((await revealLevel(waving)) !== 1) throw new Error('a slow sweep of the mouse raised the level');
  // a flick: 750 px back in five moves dispatched at once, since a
  // software-rendered page can take longer than the flick itself between
  // two of Playwright's mouse moves
  await waving.evaluate(() => {
    const stage = document.getElementById('type');
    for (let k = 1; k <= 5; k++)
      stage.dispatchEvent(new PointerEvent('pointermove', {
        bubbles: true, pointerType: 'mouse', clientX: 850 - 150 * k, clientY: 320 }));
  });
  lv = await revealLevel(waving, 2);
  if (lv !== 2) throw new Error(`a flick of the mouse left the level at ${lv}`);
  done('Ctrl+. raised level 0 to 1; a slow sweep of the mouse did nothing; a synthesized flick raised it to 2');
  await waving.close();

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
  if (!/reveal level 3 · lights 1\.00/.test(shown)) throw new Error(`no reveal level in the overlay: "${shown}"`);
  if (!/style (plain|elegant|caffeinated|drunken|coarse) \(auto\)/.test(shown))
    throw new Error(`the overlay does not name the style in force: "${shown}"`);
  const clipped = await overlay.evaluate((el) => el.scrollWidth > el.clientWidth);
  if (clipped) throw new Error('the overlay is cut off on the right');
  done(`overlay reads "${shown.split('\n').join(' / ')}"`);
  // Its console errors are already collected; a second software-rendered
  // page left running would starve the next one.
  await debugged.close();

  // The stage behind the letters hears each moment: "crash" is a :quake
  // cue, and the grid floor's lines flash with it, then settle back.
  begin('stage', null);
  const staged = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = staged;
  const stageUrl = new URL(url);
  stageUrl.searchParams.set('debug', '');
  stageUrl.searchParams.set('stage', 'grid');
  await open(staged, stageUrl.href);
  const atRest = await staged.evaluate(() => ({ name: window.__lab.stage, state: window.__lab.stageState() }));
  if (atRest.name !== 'grid') throw new Error(`?stage=grid shows the stage "${atRest.name}"`);
  if (atRest.state.brightness !== 0) throw new Error(`the grid starts at brightness ${atRest.state.brightness}`);
  if (!(atRest.state.visibility > 0.99)) throw new Error(`the grid floor is at visibility ${atRest.state.visibility} at level 3`);
  // The floor's light, sampled on every frame. The flash is a function of
  // time, so each frame is checked against the curve at its own t: software
  // rendering draws a frame every few hundred ms, and how near one lands to
  // the flash's peak depends on that phase, not on the grid.
  await staged.evaluate(() => {
    window.__samples = [];
    const sample = () => {
      const s = window.__lab.stageState();
      window.__samples.push({
        t: s.t, brightness: s.brightness,
        flashAt: s['flash-at'] ?? null, flashIntensity: s['flash-intensity'] ?? null,
      });
      requestAnimationFrame(sample);
    };
    requestAnimationFrame(sample);
  });
  await staged.keyboard.type('crash ', { delay: 70 });
  // Read at once: the pause after "crash" places a :line shot, a newer moment.
  const sawQuake = await staged.waitForFunction(
    () => /stage grid · moment cue quake 1\.0 at \d/.test(document.getElementById('debug').textContent),
    null, { polling: 'raf', timeout: 5000 }).then(() => true).catch(() => false);
  if (!sawQuake)
    throw new Error(`the overlay never showed the quake moment: "${await staged.locator('#debug').textContent()}"`);
  const settledFlash = await staged.waitForFunction(
    () => window.__samples.some((s) => s.flashAt !== null) && window.__lab.stageState().brightness < 0.01,
    null, { polling: 'raf', timeout: 4000 }).then(() => true).catch(() => false);
  const samples = await staged.evaluate(() => window.__samples);
  const first = samples.find((s) => s.flashAt !== null);
  if (!first) throw new Error('the grid never received the quake (no flash-at in its state)');
  const { flashAt, flashIntensity } = first;
  if (flashIntensity !== 1) throw new Error(`"crash" flashed the grid at intensity ${flashIntensity}, not 1.0`);
  // one sample per update: our frame callback can read the same update twice
  const frames = samples.filter((s, i) => typeof s.t === 'number' && (i === 0 || s.t !== samples[i - 1].t));
  const gaps = frames.slice(1).map((s, i) => s.t - frames[i].t).filter((d) => d > 0).sort((a, b) => a - b);
  const interval = gaps.length
    ? `median ${gaps[Math.floor(gaps.length / 2)].toFixed(0)} ms (${gaps[0].toFixed(0)}-${gaps[gaps.length - 1].toFixed(0)})`
    : 'unknown';
  const inFlash = frames.filter((s) => s.flashAt === flashAt && (s.t - flashAt) / 900 >= 0 && (s.t - flashAt) / 900 < 1);
  if (!inFlash.some((s) => s.brightness > 0))
    throw new Error(`no frame inside the 900 ms flash was lit: ${inFlash.length} frames inside it; frame interval ${interval}`);
  for (const s of inFlash) {
    const u = (s.t - flashAt) / 900;
    const expected = flashIntensity * (1 - u) ** 2;
    if (!(Math.abs(s.brightness - expected) <= 1e-6))
      throw new Error(`at t ${s.t} (u ${u.toFixed(3)}) the grid's brightness is ${s.brightness}, not ${expected.toFixed(6)} on its flash curve`);
  }
  if (!settledFlash) {
    const now = await staged.evaluate(() => window.__lab.stageState().brightness);
    throw new Error(`the grid's brightness is still ${now} 4 s after the quake`);
  }
  // at rest again, so the screenshot is the plain floor
  await staged.waitForTimeout(1000);
  await staged.screenshot({ path: `${out}/stage-grid.png` });
  const rested = await staged.evaluate(() => window.__lab.stageState());
  if (rested.brightness !== 0 || rested.pulse !== 0)
    throw new Error(`the grid is not at rest: ${JSON.stringify(rested)}`);
  done(`?stage=grid; "crash" -> quake in overlay; ${inFlash.length} frames inside the 900 ms flash, `
    + `each on intensity x (1-u)^2; frame interval ${interval}; brightness -> 0`);
  await staged.close();

  begin('style auto', null);
  const auto = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = auto;
  // Held clock, so the typing rhythm is exactly what the step says.
  await auto.clock.install();
  await open(auto);
  await auto.bringToFront();
  await auto.clock.pauseAt(await auto.evaluate(() => Date.now() + 1000));
  // pauseAt can draw a frame at the time it jumps to and still leave the
  // page's clock behind it, which stalls the animation loop until the clock
  // catches up; run it past that frame first.
  const lead = await auto.evaluate(() => window.__lab.lastFrame()['t-ms'] - performance.now());
  await auto.clock.runFor(Math.max(0, Math.ceil(lead)) + 100);
  const picker = await auto.locator('#style').inputValue();
  if (picker !== 'auto') throw new Error(`the picker starts on "${picker}", not Auto`);
  // fastForward fires a due animation frame once per key, not every 16 ms:
  // seconds of software-rendered frames would back up behind a screenshot.
  const typeAt = async (chars, ms) => {
    for (const ch of chars) {
      await auto.keyboard.type(ch);
      await auto.clock.fastForward(ms);
    }
    return auto.evaluate(() => window.__lab.lastFrame().style);
  };
  const fast = await typeAt('quick brown fox', 40);
  await auto.screenshot({ path: `${out}/style-auto-fast.png` });
  if (fast !== 'caffeinated') throw new Error(`typing at 40 ms a key reads as "${fast}"`);
  const slow = await typeAt(' and a slow dog', 450);
  await auto.screenshot({ path: `${out}/style-auto-slow.png` });
  if (slow !== 'elegant') throw new Error(`typing at 450 ms a key then reads as "${slow}"`);
  done(`40 ms a key reads ${fast}; 450 ms a key then reads ${slow}`);
  await auto.close();

  // Each explicit style, picked one way or the other, on one page: the
  // textarea keeps the focus, and the frames are performed in that style.
  const styled = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = styled;
  await open(styled);
  const picks = [['elegant', 'picker'], ['caffeinated', 'Control+4'], ['drunken', 'picker'],
                 ['coarse', 'Control+6'], ['plain', 'picker']];
  for (const [name, how] of picks) {
    begin(`style ${name}`, styled);
    if (how === 'picker') await styled.selectOption('#style', name);
    else await styled.keyboard.press(how);
    const picked = await styled.evaluate(() => ({
      value: document.getElementById('style').value,
      focused: document.activeElement === document.getElementById('type'),
    }));
    if (picked.value !== name) throw new Error(`the picker shows "${picked.value}" after ${how}`);
    if (!picked.focused) throw new Error(`the textarea lost the focus after ${how}`);
    await styled.locator('#type').fill('');
    await styled.keyboard.type('we shipped it ', { delay: 70 });
    await styled.waitForTimeout(150);
    const f = await styled.evaluate(() => new Promise((resolve) =>
      requestAnimationFrame(() => requestAnimationFrame(() => resolve({
        style: window.__lab.lastFrame().style,
        live: window.__lab.liveText(),
        value: document.getElementById('type').value,
      })))));
    await styled.screenshot({ path: `${out}/style-${name}.png` });
    if (f.style !== name) throw new Error(`the frame's style is "${f.style}"`);
    if (f.live !== f.value || f.value !== 'we shipped it ') throw new Error(`live text "${f.live}" for "${f.value}"`);
    done(`picked with ${how}; textarea kept the focus; frames performed ${f.style}`);
  }
  await styled.close();

  begin('sound', null);
  const sounding = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = sounding;
  const soundUrl = new URL(url);
  soundUrl.searchParams.set('debug', '');
  await open(sounding, soundUrl.href);
  // The overlay is redrawn on the next frame, which software rendering can
  // take a few hundred ms to reach.
  const overlayReads = (words) => sounding.waitForFunction(
    (w) => document.getElementById('debug').textContent.includes(w), words, { timeout: 5000 })
    .catch(() => {});
  const soundState = () => sounding.evaluate(() => ({
    checked: document.getElementById('sound').checked,
    overlay: document.getElementById('debug').textContent,
    focused: document.activeElement === document.getElementById('type'),
    log: window.__lab.soundLog(),
    bounds: window.__lab.bounds(),
  }));
  // Every voice within the bounds the pure core enforces, read from the page.
  const assertBounded = (entry, b) => {
    const vs = entry.sound.voices;
    if (!vs.length) throw new Error(`a sound with no voices: ${JSON.stringify(entry)}`);
    const sum = vs.reduce((a, v) => a + v.gain, 0);
    if (!(sum <= b['max-sum-gain'] + 1e-9)) throw new Error(`summed gain ${sum} over ${b['max-sum-gain']}`);
    for (const v of vs) {
      if (!(v['at-ms'] >= 0 && v['at-ms'] + v.ms <= b['max-ms'] + 1e-9)) throw new Error(`voice outside ${b['max-ms']} ms: ${JSON.stringify(v)}`);
      if (!(v.gain >= 0 && v.gain <= b['max-voice-gain'] + 1e-9)) throw new Error(`voice gain over ${b['max-voice-gain']}: ${JSON.stringify(v)}`);
      if (!(v.hz >= b.hz[0] && v.hz <= b.hz[1])) throw new Error(`voice hz outside ${b.hz}: ${JSON.stringify(v)}`);
      if (!(v.pan >= b.pan[0] && v.pan <= b.pan[1])) throw new Error(`voice pan outside ${b.pan}: ${JSON.stringify(v)}`);
    }
    return vs.length;
  };
  await overlayReads('sound off');
  let heard = await soundState();
  if (heard.checked) throw new Error('Sound starts on');
  if (!heard.overlay.includes('sound off')) throw new Error(`the overlay does not read "sound off": "${heard.overlay}"`);
  await sounding.keyboard.type('crash ', { delay: 70 });
  await sounding.waitForTimeout(1200);
  heard = await soundState();
  if (heard.log.length) throw new Error(`with Sound off, ${heard.log.length} sound(s) logged`);
  await sounding.locator('#sound').click();
  await overlayReads('sound on');
  heard = await soundState();
  if (!heard.checked || !heard.overlay.includes('sound on'))
    throw new Error(`after the click Sound is ${heard.checked}; overlay "${heard.overlay}"`);
  if (!heard.focused) throw new Error('the textarea lost the focus to the Sound checkbox');
  await sounding.locator('#type').fill('');
  await sounding.keyboard.type('crash ', { delay: 70 });
  // past the pause, so the phrase is directed too: a :line, which is silent
  await sounding.waitForTimeout(1200);
  heard = await soundState();
  if (heard.log.length !== 1 || heard.log[0].kind !== 'cue' || heard.log[0].effect !== 'quake')
    throw new Error(`after "crash " the log is ${JSON.stringify(heard.log)}`);
  const crashVoices = assertBounded(heard.log[0], heard.bounds);
  await sounding.keyboard.type('WHAT A DAY! ', { delay: 70 });
  await sounding.waitForTimeout(1200);
  heard = await soundState();
  const hit = heard.log.find((e) => e.kind === 'shot' && e.shot === 'card');
  if (!hit) throw new Error(`no card hit after "WHAT A DAY! ": ${JSON.stringify(heard.log)}`);
  assertBounded(hit, heard.bounds);
  if (!hit.sound.voices.some((v) => v.wave === 'noise') || !hit.sound.voices.some((v) => v.wave !== 'noise' && v.hz < 100))
    throw new Error(`the card hit has no noise swell or no low thump: ${JSON.stringify(hit.sound)}`);
  heard.log.forEach((e) => assertBounded(e, heard.bounds));
  await sounding.reload();
  await sounding.waitForFunction(() => window.__lab?.backend, null, { timeout: 30000 });
  await overlayReads('sound on');
  heard = await soundState();
  if (!heard.checked || !heard.overlay.includes('sound on'))
    throw new Error(`after a reload Sound is ${heard.checked}; overlay "${heard.overlay}"`);
  // leave nothing remembered behind
  await sounding.locator('#sound').click();
  await sounding.reload();
  await sounding.waitForFunction(() => window.__lab?.backend, null, { timeout: 30000 });
  heard = await soundState();
  if (heard.checked) throw new Error('Sound still on after turning it off and reloading');
  done(`off by default and silent; on: "crash" -> quake in ${crashVoices} bounded voice(s), the :line after it silent; `
    + `"WHAT A DAY!" -> card hit of ${hit.sound.voices.length} voices; remembered on, then off, across reloads`);
  await sounding.close();

  // A held letter swells: each letter of "loooove" lifts in turn and comes
  // back. Read from every frame drawn, whatever the frame rate: the lift is
  // one hump per letter, each a copy of the one before starting later, so
  // among any samples a later letter's highest sample is never earlier.
  begin('swell', null);
  {
    const swelling = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
    current = swelling;
    const swellUrl = new URL(url);
    swellUrl.searchParams.set('debug', '');
    swellUrl.searchParams.set('reveal', '3');
    await open(swelling, swellUrl.href);
    // Plain, so no style adds a sway of its own to y.
    await swelling.selectOption('#style', 'plain');
    const held = 'loooove';
    const swellLine = `${held} -> swell 0.7 (elongation)`;
    await swelling.evaluate(({ line, from, count }) => {
      const samples = [];
      window.__swell = { samples, shownAt: null };
      const sample = () => {
        const f = window.__lab.lastFrame();
        const live = f ? f.poses.filter((p) => !p['ghost?']) : [];
        const last = samples[samples.length - 1];
        if (f && live.length >= from + count && (!last || last.t !== f['t-ms'])) {
          // the space before the word is in no cue: its y is the line's rest
          samples.push({ t: f['t-ms'], rest: live[from - 1].y, ys: live.slice(from, from + count).map((p) => p.y) });
          if (window.__swell.shownAt === null && document.getElementById('debug').textContent.includes(line))
            window.__swell.shownAt = f['t-ms'];
        }
        requestAnimationFrame(sample);
      };
      requestAnimationFrame(sample);
    }, { line: swellLine, from: 2, count: held.length });
    await swelling.keyboard.type(`I ${held} it `, { delay: 70 });
    await swelling.waitForFunction(() => window.__swell.shownAt !== null, null, { timeout: 5000 })
      .catch(() => {});
    const swellOverlay = await swelling.locator('#debug').textContent();
    if (!swellOverlay.includes(swellLine))
      throw new Error(`the overlay does not show "${swellLine}": "${swellOverlay}"`);
    await swelling.waitForTimeout(800);
    await swelling.screenshot({ path: `${out}/swell.png` });
    // The cue fired no later than the overlay showed it, so 5.5 s after the
    // showing is at least 5.5 s after the cue.
    await swelling.waitForFunction(() => {
      const { samples, shownAt } = window.__swell;
      return samples.length && samples[samples.length - 1].t >= shownAt + 6000;
    }, null, { timeout: 20000 });
    const swell = await swelling.evaluate(() => window.__swell);
    const lifts = swell.samples.map((m) => ({ t: m.t, lifts: m.ys.map((y) => y - m.rest) }));
    const highest = Math.max(...lifts.flatMap((m) => m.lifts));
    const swellCap = 0.25 * 0.7 + 0.001;
    if (!(highest > 1e-6)) throw new Error(`no letter of "${held}" lifted in ${lifts.length} sampled frames`);
    if (!(highest <= swellCap)) throw new Error(`a letter lifted ${highest} em, above ${swellCap}`);
    const dipped = Math.min(...lifts.flatMap((m) => m.lifts));
    if (!(dipped >= -1e-9)) throw new Error(`a letter sank ${-dipped} em below the line`);
    const peakTimes = [...held].map((_, i) =>
      lifts.reduce((best, m) => (m.lifts[i] > best.lift ? { t: m.t, lift: m.lifts[i] } : best), { t: null, lift: 0 }).t);
    peakTimes.forEach((t, i) => {
      if (t === null) throw new Error(`letter ${i} of "${held}" never lifted in ${lifts.length} sampled frames`);
      if (i > 0 && t < peakTimes[i - 1])
        throw new Error(`letter ${i} peaked at ${t} ms, before letter ${i - 1} at ${peakTimes[i - 1]} ms`);
    });
    const atRest = lifts.filter((m) => m.t >= swell.shownAt + 5500);
    if (!atRest.length) throw new Error("no frame was sampled 5.5 s after the cue");
    const stillLifted = atRest.find((m) => m.lifts.some((l) => l !== 0));
    if (stillLifted)
      throw new Error(`${stillLifted.t - swell.shownAt} ms after the cue the lifts are ${JSON.stringify(stillLifted.lifts)}`);
    done(`"${swellLine}"; over ${lifts.length} frames the highest lift was ${highest.toFixed(3)} em (cap ${swellCap}); `
      + `peaks in order over ${Math.round(peakTimes[held.length - 1] - peakTimes[0])} ms; at rest in all ${atRest.length} frames from 5.5 s on`);
    await swelling.close();
  }

  begin('director', null);
  const directed = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
  current = directed;
  // Held clock, as in "style auto", so the shots are read at exact times.
  await directed.clock.install();
  await open(directed);
  await directed.bringToFront();
  await directed.clock.pauseAt(await directed.evaluate(() => Date.now() + 1000));
  const lead2 = await directed.evaluate(() => window.__lab.lastFrame()['t-ms'] - performance.now());
  await directed.clock.runFor(Math.max(0, Math.ceil(lead2)) + 100);
  const typeKeys = async (chars, ms = 70) => {
    for (const ch of chars) {
      await directed.keyboard.type(ch);
      await directed.clock.fastForward(ms);
    }
  };
  // The frame drawn now, and every direction with the text of its glyphs.
  const look = () => directed.evaluate(() => {
    const lab = window.__lab;
    const f = lab.lastFrame();
    const chars = new Map(f.poses.filter((p) => !p['ghost?']).map((p) => [p.id, p.char]));
    return {
      t: f['t-ms'],
      frame: f,
      directions: lab.directions().map((d) => ({ ...d, text: d['glyph-ids'].map((id) => chars.get(id)).join('') })),
    };
  });
  const directionOf = (onStage, text) => onStage.directions.find((d) => d.text === text);
  const distance = (c) => Math.hypot(c.x - c.tx, c.y - c.ty, c.z - c.tz);
  // A pause ends "hello there" in the line, so the card is only its phrase.
  await typeKeys('hello there ');
  await directed.clock.fastForward(1500);
  await typeKeys('WHAT A DAY! ');
  let onStage = await look();
  const card = directionOf(onStage, 'WHATADAY!');
  if (card?.shot !== 'card') throw new Error(`"WHAT A DAY!" was directed ${JSON.stringify(card)}`);
  await directed.clock.fastForward(card['at-ms'] + 900 - onStage.t);
  onStage = await look();
  const onCard = new Set(card['glyph-ids']);
  const live = onStage.frame.poses.filter((p) => !p['ghost?']);
  const biggest = Math.max(...live.filter((p) => onCard.has(p.id)).map((p) => p.scale));
  const others = live.filter((p) => !onCard.has(p.id));
  await directed.screenshot({ path: `${out}/card.png` });
  if (!(biggest > 2)) throw new Error(`900 ms into the card its largest glyph is at scale ${biggest}`);
  const undimmed = others.filter((p) => p.opacity > 0.08 + 1e-6);
  if (undimmed.length)
    throw new Error(`${undimmed.length} glyph(s) off the card above opacity 0.08, e.g. "${undimmed[0].char}" at ${undimmed[0].opacity}`);
  await directed.clock.fastForward(5000);
  onStage = await look();
  const unsettled = onStage.frame.poses.filter((p) => !p['ghost?'] && p.scale !== 1);
  if (unsettled.length) throw new Error(`${unsettled.length} glyph(s) not back at scale 1 5 s after the card`);
  // "hello there" was at rest before the card and is again now, so where it
  // settles is where it would have been without the card.
  const settledZ = new Map(onStage.frame.poses.filter((p) => !p['ghost?']).map((p) => [p.id, p.z]));
  const behind = others.filter((p) => p.char.trim() && p.id < card['glyph-ids'][0]);
  const unreceded = behind.filter((p) => !(p.z <= settledZ.get(p.id) - 1.9));
  if (behind.length !== 10 || unreceded.length)
    throw new Error(`of ${behind.length} letters of "hello there", ${unreceded.length} not 1.9 em behind where they settle, e.g. `
      + `"${unreceded[0]?.char}" at z ${unreceded[0]?.z} (settles at ${settledZ.get(unreceded[0]?.id)})`);
  // Within 10 s of the card the budget is spent: a question stays in the line.
  await typeKeys('why not? ');
  onStage = await look();
  const spent = directionOf(onStage, 'whynot?');
  if (!(onStage.t - card['at-ms'] < 10000)) throw new Error(`"why not?" typed ${onStage.t - card['at-ms']} ms after the card`);
  if (spent?.shot !== 'line') throw new Error(`"why not?" within 10 s of the card was directed ${JSON.stringify(spent)}`);
  await directed.clock.fastForward(card['at-ms'] + 11000 - onStage.t);
  onStage = await look();
  const restDistance = distance(onStage.frame.camera);
  await typeKeys('is it? ');
  onStage = await look();
  const push = directionOf(onStage, 'isit?');
  if (push?.shot !== 'push') throw new Error(`"is it?" 11 s after the card was directed ${JSON.stringify(push)}`);
  await directed.clock.fastForward(push['at-ms'] + 800 - onStage.t);
  onStage = await look();
  const pushed = distance(onStage.frame.camera);
  await directed.screenshot({ path: `${out}/push.png` });
  if (!(pushed <= 0.75 * restDistance))
    throw new Error(`800 ms into the push the camera is ${pushed.toFixed(2)} em from its target, at rest ${restDistance.toFixed(2)}`);
  done(`card: a glyph at scale ${biggest.toFixed(2)}, ${others.length} others at opacity <= 0.08 and "hello there" receded, all back at scale 1 5 s later; `
    + `"why not?" ${spent.shot} within 10 s; "is it?" ${push.shot}: camera ${restDistance.toFixed(2)} -> ${pushed.toFixed(2)} em`);
  await directed.close();

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

  // Its console errors are kept; a software-rendered page left running would
  // starve the specimen's.
  await context.close();

  // The channels, each at fixed values on the type specimen, measured in the
  // two stills the foreman is shown: no motion, so no timing.
  begin('specimen', null);
  {
    const lines = [];
    for (const [name, lights] of [['dark', 1], ['paper', 0]]) {
      const specimen = track(await browser.newPage({ viewport: { width: 1280, height: 900 } }));
      current = specimen;
      const specimenUrl = new URL(url);
      specimenUrl.searchParams.set('specimen', '');
      if (lights === 0) specimenUrl.searchParams.set('reveal', '0');
      await open(specimen, specimenUrl.href, { plain: true });
      await specimen.waitForFunction(() => window.__lab.specimen()?.length, null, { timeout: 10000 });
      const twoFrames = () => specimen.evaluate(() => new Promise((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve(JSON.stringify(window.__lab.lastFrame()))))));
      const before = await twoFrames();
      await specimen.keyboard.type('typed ', { delay: 20 });
      if ((await twoFrames()) !== before) throw new Error('typing changed the specimen');
      const shown = await specimen.evaluate(() => {
        const lab = window.__lab;
        const f = lab.lastFrame();
        const inks = {};
        for (const k of ['text', 'signal', 'gold', 'cool', 'muted']) inks[k] = lab.inkColour(k, f.reveal.lights);
        return { samples: lab.specimen(), inks, lights: f.reveal.lights, drawn: lab.drawn(),
                 page: document.documentElement.dataset.lights };
      });
      if (shown.lights !== lights || shown.page !== (lights === 1 ? 'down' : 'up'))
        throw new Error(`${specimenUrl.search} shows lights ${shown.lights}, the page "${shown.page}"`);
      const img = decodePng(await specimen.screenshot({ path: `${out}/specimen-${name}.png` }));
      lines.push(`${name}: ${shown.samples.length} samples, ${shown.drawn} glyphs; `
        + measureSpecimen(img, shown.samples, (k) => shown.inks[k], lights));
      await specimen.close();
    }

    // H3 (docs/product/lab-notebook.md): the mean frame time with a 200
    // character paragraph at rest on the ordinary page. Reported only:
    // headless numbers are for comparing one run with another.
    const timed = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
    current = timed;
    const timedUrl = new URL(url);
    timedUrl.searchParams.set('debug', '');
    const timedBackend = await open(timed, timedUrl.href);
    const paragraph = 'the quick brown fox jumps over the lazy dog and '.repeat(5).slice(0, 200);
    await timed.keyboard.type(paragraph, { delay: 10 });
    // every cue and shot over (5 s at most), then a full second of frames
    await timed.waitForTimeout(6500);
    const timing = await timed.evaluate(() => ({
      overlay: document.getElementById('debug').textContent,
      live: window.__lab.liveText().length,
      drawn: window.__lab.drawn(),
    }));
    const mean = /mean ([\d.]+) ms/.exec(timing.overlay)?.[1];
    if (timing.live !== 200 || !mean) throw new Error(`${timing.live} characters live; overlay "${timing.overlay}"`);
    await timed.close();
    done(`${lines.join(' | ')} | H3: mean frame time ${mean} ms with ${timing.drawn} glyphs at rest on ${timedBackend}`);
  }

  // Reading back: Ctrl+Enter reads the draft to its reader. Everything is
  // read from the frames drawn and from __lab.reading(), sampled on every
  // animation frame, and each sampled frame is checked against the head at
  // that frame's own time, so nothing depends on the frame rate.
  begin('reading', null);
  {
    const page = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
    current = page;
    const readingUrl = new URL(url);
    readingUrl.searchParams.set('debug', '');
    readingUrl.searchParams.set('reveal', '3');
    await open(page, readingUrl.href);
    // Elegant performs four of the sentence's marks and has no sway; Plain,
    // its flourish being 0, would perform none.
    await page.selectOption('#style', 'elegant');
    const setPace = async (wpm) => {
      const shown = await page.evaluate((v) => {
        const slider = document.getElementById('pace');
        slider.value = String(v);
        slider.dispatchEvent(new Event('input', { bubbles: true }));
        return document.getElementById('pace-value').textContent;
      }, wpm);
      if (shown !== String(wpm)) throw new Error(`the pace reads "${shown}" after setting ${wpm}`);
    };
    await setPace(250);
    const acceptance = 'I loooove this (honestly). But maybe not. Ship it!';
    const held = { from: 2, to: 9 };
    await page.evaluate(() => {
      const samples = [];
      window.__read = { samples };
      const offRest = ['sx', 'sy', 'skew', 'blur', 'fill'];
      let place = null, restSince = null;
      const sample = () => {
        const lab = window.__lab;
        const f = lab.lastFrame();
        const last = samples[samples.length - 1];
        if (f && (!last || last.t !== f['t-ms'])) {
          const t = f['t-ms'];
          const live = f.poses.filter((p) => !p['ghost?']);
          // where every letter stands and how it looks: at rest it stops
          // changing. Elegant dresses the aside (smaller, lighter), so size,
          // glow and weight rest wherever the dress puts them; the channels
          // only motion uses rest at their own values.
          const here = JSON.stringify(live.map((p) => [p.x, p.y, p.scale, p.glow, p.weight ?? 0]));
          const restful = live.length === f.poses.length && here === place && live.every((p) =>
            p.z === 0 && p.rx === 0 && p.ry === 0 && p.rz === 0
            && p.opacity === 1 && p.wobble === 0 && !offRest.some((k) => k in p));
          restSince = restful ? (restSince ?? last.t) : null;
          place = here;
          const r = lab.reading();
          samples.push({
            t,
            restFor: restSince === null ? 0 : t - restSince,
            text: live.map((p) => p.char).join(''),
            ys: live.map((p) => p.y),
            lengths: live.map((p) => p.char.length),
            fills: live.map((p) => ('fill' in p ? p.fill : null)),
            reading: r && { t0: r.t0, durationMs: r.durationMs, stops: r.stops, head: r.headAt(t) },
            overlay: /^reading .*$/m.exec(document.getElementById('debug').textContent)?.[0] ?? null,
          });
        }
        requestAnimationFrame(sample);
      };
      requestAnimationFrame(sample);
    });
    const samplesFrom = (i) => page.evaluate((from) => window.__read.samples.slice(from), i);
    const sampleCount = () => page.evaluate(() => window.__read.samples.length);
    // Wait on the data: a sampled frame at or after index `from` for which
    // the predicate (run in the page, given the sample and arg) holds.
    const waitForSample = (from, predicate, arg, what, timeout = 30000) =>
      page.waitForFunction(({ from, source, arg }) => {
        const holds = new Function(`return (${source})`)();
        return window.__read.samples.slice(from).some((m) => holds(m, arg));
      }, { from, source: predicate.toString(), arg }, { timeout })
        .catch(() => { throw new Error(`no sampled frame in ${timeout} ms where ${what}`); });
    const currentReading = () => page.evaluate(() => {
      const r = window.__lab.reading();
      return r && { t0: r.t0, durationMs: r.durationMs, beatMs: r.beatMs, stops: r.stops,
                    entries: r.entries, performing: r.performing };
    });

    await page.keyboard.type(acceptance, { delay: 40 });
    // The typing's own cues and any shot are over when every letter has
    // stood at its resting place for 500 ms (in Elegant a swell takes up to
    // 5 s, so this waits on the frames, not on a clock).
    let mark = await sampleCount();
    await waitForSample(mark, (m, text) => m.text === text && m.restFor >= 500, acceptance,
      'every letter has been at rest for 500 ms');
    const settled = (await samplesFrom(mark)).find((m) => m.text === acceptance && m.restFor >= 500);
    const restYs = settled.ys;
    const atRest = (m) => m.restFor > 0 && m.ys.every((y, i) => y === restYs[i]);

    mark = await sampleCount();
    await page.keyboard.press('Control+Enter');
    const first = await currentReading();
    if (!first) throw new Error('Ctrl+Enter started no reading');
    if (first.stops.length !== 9) throw new Error(`the reading at 250 wpm has ${first.stops.length} stops, not the sentence's 9 words`);
    const gestures = first.performing.map((g) => g.effect).join(' ');
    if (gestures !== 'swell hush nod glow') throw new Error(`Elegant performs "${gestures}"`);
    await waitForSample(mark, (m, t0) => m.reading === null && m.t > t0, first.t0, 'the reading is over');
    const read = (await samplesFrom(mark)).filter((m) => m.t >= first.t0);
    const stopsEnd = first.stops[8]['at-ms'] + first.stops[8].ms;
    const during = read.filter((m) => m.reading && m.t < first.t0 + stopsEnd);
    if (!during.length) throw new Error(`no frame was sampled during the ${stopsEnd} ms of the stops`);
    // (a) a lit prefix, at most one letter part filled, a dim suffix;
    // (b) the boundary is where the head is at that frame's time
    let worstGap = 0;
    for (const m of during) {
      const fills = m.fills.map((v) => v ?? 1);
      const at = `${m.t - first.t0} ms into the reading`;
      fills.forEach((v, i) => {
        if (i > 0 && v > fills[i - 1]) throw new Error(`${at} letter ${i} is filled ${v}, the one before it ${fills[i - 1]}`);
      });
      const partial = fills.filter((v) => v > 0 && v < 1).length;
      if (partial > 1) throw new Error(`${at} ${partial} letters are part filled`);
      const lit = fills.reduce((sum, v, i) => sum + v * m.lengths[i], 0);
      worstGap = Math.max(worstGap, Math.abs(lit - m.reading.head));
      if (!(Math.abs(lit - m.reading.head) <= 1.5))
        throw new Error(`${at} ${lit.toFixed(2)} characters are lit and the head is at ${m.reading.head.toFixed(2)}`);
    }
    if (!during.some((m) => m.fills.some((v) => v !== null && v < 1)))
      throw new Error('no sampled frame during the stops has a dim letter');
    if (!read.some((m) => m.overlay?.startsWith('reading paced · ')))
      throw new Error(`the overlay never showed the reading: ${JSON.stringify(read.map((m) => m.overlay))}`);
    // (c) "loooove" waits for the head, then swells
    const reached = first.t0 + first.stops.find((s) => s.span[0] === held.from && s.span[1] === held.to)['at-ms'];
    const lifts = (m) => m.ys.slice(held.from, held.to).map((y, i) => y - restYs[held.from + i]);
    const early = read.filter((m) => m.t < reached).find((m) => lifts(m).some((l) => Math.abs(l) > 1e-9));
    if (early) throw new Error(`${reached - early.t} ms before the head reached "loooove" its lifts were ${JSON.stringify(lifts(early))}`);
    const highest = Math.max(...read.filter((m) => m.t >= reached).flatMap(lifts));
    if (!(highest > 1e-6)) throw new Error(`no letter of "loooove" lifted in ${read.length} frames once the head had reached it`);
    // (d) over: no ink left to fill, and 5.5 s later every letter at rest
    const overAt = read.find((m) => m.reading === null).t;
    await waitForSample(mark, (m, t) => m.t >= t, overAt + 5500, 'it is 5.5 s after the reading');
    const after = (await samplesFrom(mark)).filter((m) => m.t >= overAt);
    const inked = after.find((m) => m.fills.some((v) => v !== null));
    if (inked) throw new Error(`${inked.t - overAt} ms after the reading a pose still has a fill`);
    const late = after.filter((m) => m.t >= overAt + 5500);
    const unsettled = late.find((m) => !atRest(m));
    if (unsettled) throw new Error(`${unsettled.t - overAt} ms after the reading a letter is not at rest`);

    // (e) a faster reader gets a shorter reading; the button starts one too
    await setPace(400);
    await page.keyboard.press('Control+Enter');
    const fast = await currentReading();
    await page.keyboard.press('Escape');
    if (!fast || (await currentReading()) !== null) throw new Error('Escape did not end the reading at 400 wpm');
    await setPace(150);
    await page.locator('#read').click();
    const slow = await currentReading();
    if (!slow) throw new Error('the Read back button started no reading');
    if (slow.stops.length !== 9) throw new Error(`at 150 wpm the reading has ${slow.stops.length} stops: not paced`);
    if (!(fast.durationMs < slow.durationMs))
      throw new Error(`the reading lasts ${fast.durationMs} ms at 400 wpm and ${slow.durationMs} ms at 150`);
    if (!(fast.beatMs === 150 && slow.beatMs === 400)) throw new Error(`beats of ${fast.beatMs} and ${slow.beatMs} ms`);
    // the still for the foreman: about 40% of the way through, at 150 wpm
    mark = await sampleCount();
    await waitForSample(mark, (m, t) => m.reading && m.t >= t, slow.t0 + 0.4 * slow.durationMs,
      'the reading at 150 wpm is 40% through');
    await page.screenshot({ path: `${out}/reading.png` });
    // (f) Escape lights everything at once
    await page.keyboard.press('Control+Enter');
    const stopped = await currentReading();
    mark = await sampleCount();
    await waitForSample(mark, (m, t0) => m.reading?.t0 === t0 && m.fills.some((v) => v !== null && v < 1), stopped.t0,
      'the new reading has dimmed a letter');
    await page.keyboard.press('Escape');
    const escapedAt = await page.evaluate(() => Math.round(performance.now()));
    mark = await sampleCount();
    await waitForSample(mark, (m, t) => m.t >= t, escapedAt, 'a frame was drawn after Escape');
    const afterEscape = (await samplesFrom(mark)).filter((m) => m.t >= escapedAt);
    const stillRead = afterEscape.find((m) => m.reading !== null || m.fills.some((v) => v !== null));
    if (stillRead) throw new Error(`${stillRead.t - escapedAt} ms after Escape a frame still has a reading or a fill`);
    if ((await page.inputValue('#type')) !== acceptance) throw new Error('reading back changed the text');
    done(`Elegant at 250 wpm performs ${gestures}; ${during.length} frames during the ${stopsEnd} ms of stops: a lit prefix, `
      + `at most one part-filled letter, a dim suffix, within ${worstGap.toFixed(2)} characters of the head; "loooove" still until the head `
      + `reached it, then lifted ${highest.toFixed(3)} em; no fill once over and at rest in all ${late.length} frames from 5.5 s on; `
      + `${fast.durationMs} ms at 400 wpm < ${slow.durationMs} ms at 150; no fill in ${afterEscape.length} frame(s) after Escape`);
    await page.close();
  }

  begin('dress', null);
  {
    // Elegant (warm, some flourish) gilds the starred phrase and whispers
    // the aside; it is too spare for a versal.
    const dressed = 'This is *the one thing* that matters (trust me).';
    // Caffeinated is lavish enough to open a message of 12 words or more
    // with a versal.
    const fourteen = 'Now we can ship the new build before lunch and have time for tea';
    const openDressed = async (level) => {
      const page = track(await browser.newPage({ viewport: { width: 1000, height: 640 } }));
      current = page;
      const dressUrl = new URL(url);
      dressUrl.searchParams.set('debug', '');
      dressUrl.searchParams.set('reveal', String(level));
      await open(page, dressUrl.href);
      return page;
    };
    // Wait on the data, not a clock: the frame spells `text`, has no ghost,
    // and its poses have not changed for 700 ms of the lab's own time (a
    // dress eases in over 500 ms, changing every frame as it does).
    const atRest = (page, text) => page.waitForFunction((text) => {
      const f = window.__lab.lastFrame();
      const here = JSON.stringify(f.poses);
      const w = (window.__still ??= {});
      if (here !== w.here) { w.here = here; w.since = f['t-ms']; }
      return f.poses.map((p) => p.char).join('') === text && f.poses.every((p) => !p['ghost?'])
        && f['t-ms'] - w.since >= 700;
    }, text, { timeout: 45000 })
      .catch(() => { throw new Error(`the letters of "${text}" did not come to rest in 45 s`); });
    const retype = async (page, style, text) => {
      await page.selectOption('#style', style);
      await page.fill('#type', '');
      await page.keyboard.type(text, { delay: 30 });
      await atRest(page, text);
    };
    // The frame and its dress as data: each placed dress with its poses and
    // where its first and last letters were drawn.
    const worn = (page) => page.evaluate(() => {
      const lab = window.__lab;
      const f = lab.lastFrame();
      const byId = new Map(f.poses.filter((p) => !p['ghost?']).map((p) => [p.id, p]));
      return {
        level: f.reveal.level,
        lights: f.reveal.lights,
        poses: f.poses,
        overlay: document.getElementById('debug').textContent,
        gold: lab.inkColour('gold', f.reveal.lights),
        dress: lab.dress().map((d) => {
          const poses = d['glyph-ids'].map((id) => byId.get(id));
          const first = poses[0], last = poses[poses.length - 1];
          const top = lab.project(first.x, first.y + 0.9, 0);
          const bottom = lab.project(last.x + lab.advance(last.char), last.y - 0.3, 0);
          return { as: d.as, text: poses.map((p) => p.char).join(''), poses,
                   box: { x0: top.x, y0: top.y, x1: bottom.x, y1: bottom.y } };
        }),
      };
    });
    const the = (state, as) => {
      const found = state.dress.filter((d) => d.as === as);
      if (found.length !== 1) throw new Error(`${found.length} ${as} in ${JSON.stringify(state.dress.map((d) => [d.as, d.text]))}`);
      return found[0];
    };
    // (a) and (d): the gilt phrase and the whispered aside, by data and by
    // the still's own pixels.
    const checkDressed = async (page, lights, shot) => {
      await retype(page, 'elegant', dressed);
      await page.waitForTimeout(1000);
      const state = await worn(page);
      if (state.lights !== lights) throw new Error(`the house lights are at ${state.lights}, not ${lights}`);
      const gilt = the(state, 'gilt'), whisper = the(state, 'whisper');
      if (state.dress.length !== 2) throw new Error(`Elegant dresses ${JSON.stringify(state.dress.map((d) => [d.as, d.text]))}`);
      if (gilt.text !== 'the one thing') throw new Error(`the gilt is on "${gilt.text}"`);
      if (whisper.text !== 'trust me') throw new Error(`the whisper is on "${whisper.text}"`);
      const ungilded = gilt.poses.find((p) => p.ink !== 'gold' || p.tint !== 1);
      if (ungilded) throw new Error(`a letter of the gilt phrase has ink ${ungilded.ink}, tint ${ungilded.tint}`);
      const loud = whisper.poses.find((p) => !(p.scale < 1) || p.ink !== 'muted');
      if (loud) throw new Error(`a letter of the whisper has scale ${loud.scale}, ink ${loud.ink}`);
      const bare = state.poses.find((p) => !gilt.poses.concat(whisper.poses).some((q) => q.id === p.id)
        && ('ink' in p || p.scale !== 1));
      if (bare) throw new Error(`"${bare.char}", outside any dress, has ink ${bare.ink}, scale ${bare.scale}`);
      const line = 'dress: gilt "the one thing" · whisper "trust me"';
      if (!state.overlay.includes(line)) throw new Error(`the overlay does not read '${line}': ${state.overlay}`);
      const img = decodePng(await page.screenshot({ path: `${out}/${shot}.png` }));
      const most = commonInk(img, inkMap(img, gilt.box, commonColour(img), state.gold));
      const worst = Math.max(...most.map((v, i) => Math.abs(v - state.gold[i])));
      if (!(worst <= 12)) throw new Error(`the gold phrase is mostly rgb(${most}), not lab.ink's gold rgb(${state.gold})`);
      return { state, gilt, summary: `gilt "${gilt.text}" drawn rgb(${most}) (gold is rgb(${state.gold})), whisper "${whisper.text}" at scale ${whisper.poses[0].scale}` };
    };

    const paper = await openDressed(1);
    const onPaper = await checkDressed(paper, 0, 'dress-paper');
    if (onPaper.state.level !== 1) throw new Error(`the paper page is at reveal level ${onPaper.state.level}`);

    // (b) the gold catches the pointer. The mouse goes there slowly: a
    // quick sweep is a wand. The glow is a function of where the pointer
    // is, so each letter is checked against the curve at its own distance.
    let pointer = { x: 20, y: 600 };
    const glide = async (to) => {
      const from = pointer;
      const n = Math.ceil(Math.hypot(to.x - from.x, to.y - from.y) / 30);
      for (let k = 1; k <= n; k++) {
        await paper.mouse.move(from.x + (to.x - from.x) * k / n, from.y + (to.y - from.y) * k / n);
        await paper.waitForTimeout(40);
      }
      pointer = to;
    };
    const giltIds = onPaper.gilt.poses.map((p) => p.id);
    const middle = onPaper.gilt.poses[Math.floor(giltIds.length / 2)];
    const glows = () => paper.evaluate((ids) => {
      const f = window.__lab.lastFrame();
      return { level: f.reveal.level, glows: ids.map((id) => f.poses.find((p) => p.id === id).glow) };
    }, giltIds);
    await paper.mouse.move(pointer.x, pointer.y);
    const unlit = await glows();
    if (unlit.glows.some((g) => Math.abs(g - 0.12) > 0.02)) throw new Error(`with the pointer far away the gold glows ${unlit.glows}`);
    await glide(await paper.evaluate((p) => {
      const at = window.__lab.project(p.x, p.y, 0);
      return { x: at.x, y: at.y };
    }, middle));
    await paper.waitForFunction((id) => window.__lab.lastFrame().poses.find((p) => p.id === id).glow > 0.4, middle.id, { timeout: 10000 })
      .catch(() => { throw new Error('the letter under the pointer never glowed above 0.4'); });
    const lit = await glows();
    if (lit.level !== 1) throw new Error(`pointing at the gold took the reveal to level ${lit.level}: it was read as a wand`);
    onPaper.gilt.poses.forEach((p, i) => {
      const d2 = (p.x - middle.x) ** 2 + (p.y - middle.y) ** 2;
      const want = 0.12 + 0.5 * Math.exp(-d2 / 2.88);
      if (!(Math.abs(lit.glows[i] - want) <= 0.03))
        throw new Error(`"${p.char}", ${Math.sqrt(d2).toFixed(2)} em from the pointer, glows ${lit.glows[i]}, not ${want.toFixed(3)}`);
    });
    const brightest = Math.max(...lit.glows);
    await paper.screenshot({ path: `${out}/dress-glint.png` });
    await glide({ x: 20, y: 600 });
    await paper.waitForFunction((ids) => {
      const f = window.__lab.lastFrame();
      return ids.every((id) => Math.abs(f.poses.find((p) => p.id === id).glow - 0.12) <= 0.02);
    }, giltIds, { timeout: 10000 })
      .catch(() => { throw new Error('with the pointer moved away the gold did not go back to about 0.12'); });

    // (c) a versal takes its room
    await retype(paper, 'caffeinated', fourteen);
    const opened = await paper.evaluate(() => {
      const lab = window.__lab;
      const [first, second] = lab.lastFrame().poses;
      return { first, second, advance: lab.advance(first.char), dress: lab.dress() };
    });
    if (!opened.dress.some((d) => d.as === 'versal' && d['glyph-ids'].length === 1 && d['glyph-ids'][0] === opened.first.id))
      throw new Error(`Caffeinated gives 14 words ${JSON.stringify(opened.dress)}: no versal on the first letter`);
    if (!(Math.abs(opened.first.scale - 1.8) <= 0.02)) throw new Error(`the versal's scale is ${opened.first.scale}`);
    const rightEdge = opened.first.x - 0.4 * opened.advance + 1.8 * opened.advance;
    if (!(opened.second.x >= rightEdge - 1e-6))
      throw new Error(`the second letter starts at ${opened.second.x} em, inside the versal, whose right edge is at ${rightEdge}`);

    // (e) Plain leaves no dress at all
    await retype(paper, 'plain', dressed);
    const plain = await worn(paper);
    if (plain.dress.length) throw new Error(`Plain dresses ${JSON.stringify(plain.dress.map((d) => [d.as, d.text]))}`);
    const worn1 = plain.poses.find((p) => 'ink' in p || 'weight' in p || p.scale !== 1 || p.glow !== 0);
    if (worn1) throw new Error(`in Plain "${worn1.char}" has ink ${worn1.ink}, weight ${worn1.weight}, scale ${worn1.scale}, glow ${worn1.glow}`);
    if (/^dress: /m.test(plain.overlay)) throw new Error('the overlay lists dress in Plain');
    await paper.close();

    // (d) the same on the dark stage
    const dark = await openDressed(3);
    const onStage = await checkDressed(dark, 1, 'dress-dark');
    await dark.close();
    done(`on paper ${onPaper.summary}; under the pointer the gold glows up to ${brightest.toFixed(2)}, each letter on the curve for its distance, `
      + `and 0.12 again with it away; Caffeinated's versal at scale ${opened.first.scale.toFixed(2)}, the next letter ${(opened.second.x - rightEdge).toFixed(3)} em `
      + `past its right edge; Plain wears nothing; on the stage ${onStage.summary}`);
  }

  // The lab on three.js's real WebGPU path. The browser above never gets
  // there: this Chromium's GPUTexture.createView rejects the 'swizzle' field
  // three.js 0.187 passes, so the lab falls back to WebGL2, which has no
  // limit on vertex buffers and so drew ten of them happily while WebGPU
  // (which guarantees eight) drew nothing. Here a shim drops the field, and
  // every render pipeline the page asks the device for is recorded.
  {
    begin('webgpu', null);
    gpuBrowser = await chromium.launch({
      args: ['--enable-unsafe-webgpu', '--enable-unsafe-swiftshader', '--use-angle=swiftshader'],
    });
    const context = await gpuBrowser.newContext({ viewport: { width: 1000, height: 640 } });
    await context.addInitScript(() => {
      window.__pipelines = [];
      if (!('gpu' in navigator) || typeof GPUDevice === 'undefined' || typeof GPUTexture === 'undefined') return;
      const createView = GPUTexture.prototype.createView;
      GPUTexture.prototype.createView = function (descriptor) {
        if (!descriptor || !('swizzle' in descriptor)) return createView.call(this, descriptor);
        const { swizzle, ...rest } = descriptor;
        return createView.call(this, rest);
      };
      for (const name of ['createRenderPipeline', 'createRenderPipelineAsync']) {
        const create = GPUDevice.prototype[name];
        GPUDevice.prototype[name] = function (descriptor) {
          // counted now: three.js reuses and resets the descriptor
          const buffers = [...(descriptor?.vertex?.buffers ?? [])].filter(Boolean);
          window.__pipelines.push({
            label: descriptor?.label ?? '',
            buffers: buffers.length,
            attributes: buffers.reduce((n, b) => n + [...(b.attributes ?? [])].length, 0),
          });
          return create.call(this, descriptor);
        };
      }
    });
    // Not tracked: a software device that is lost logs errors that are this
    // container's nature. The step asserts on what it collects itself.
    const listen = (page) => {
      const messages = [];
      page.on('console', (m) => messages.push(m.text()));
      page.on('pageerror', (e) => messages.push(String(e)));
      return messages;
    };
    const word = 'hello';
    const at = (params) => {
      const u = new URL(url);
      for (const [k, v] of Object.entries(params)) u.searchParams.set(k, v);
      return u.href;
    };
    const typeWord = async (page) => {
      await page.selectOption('#style', 'plain');
      await page.locator('#type').focus();
      await page.keyboard.type(word, { delay: 30 });
      // on the data, not a clock: the frame spells the word and its poses
      // have not changed for 700 ms of the lab's own time
      await page.waitForFunction((word) => {
        const f = window.__lab.lastFrame();
        const here = JSON.stringify(f.poses);
        const w = (window.__still ??= {});
        if (here !== w.here) { w.here = here; w.since = f['t-ms']; }
        return window.__lab.liveText() === word && f.poses.every((p) => !p['ghost?']) && f['t-ms'] - w.since >= 700;
      }, word, { timeout: 45000 })
        .catch(() => { throw new Error(`the letters of "${word}" did not come to rest in 45 s`); });
    };
    // How many pixels of the word's box are mostly ink, in a screenshot.
    const inkedPixels = async (page) => {
      const { box, ink } = await page.evaluate(() => {
        const lab = window.__lab;
        const f = lab.lastFrame();
        const poses = f.poses.filter((p) => !p['ghost?']);
        const first = poses[0], last = poses[poses.length - 1];
        const top = lab.project(first.x, first.y + 0.9, 0);
        const bottom = lab.project(last.x + lab.advance(last.char), last.y - 0.3, 0);
        return { box: { x0: top.x, y0: top.y, x1: bottom.x, y1: bottom.y }, ink: lab.inkColour('text', f.reveal.lights) };
      });
      const img = decodePng(await page.screenshot());
      return inkMap(img, box, commonColour(img), ink).cov.reduce((n, c) => n + (c > 0.5 ? 1 : 0), 0);
    };
    // Fewer than this and the word is not there: five letters ink thousands.
    const drawnPixels = 200;
    const state = (page) => page.evaluate(() => ({
      gpu: 'gpu' in navigator,
      backend: window.__lab.backend,
      status: document.getElementById('status').textContent,
      pipelines: window.__pipelines,
      errors: window.__lab.gpuErrors(),
      allowed: window.__lab.vertexBuffers(),
    }));

    // (a) the pipelines three.js really asks WebGPU for
    const page = await context.newPage();
    const messages = listen(page);
    current = page;
    await open(page, at({ reveal: '3', debug: '' }));
    await typeWord(page);
    const first = await state(page);
    let proved;
    if (first.pipelines.length) {
      const most = (k) => Math.max(...first.pipelines.map((p) => p[k]));
      const over = first.pipelines.filter((p) => p.buffers > 8 || p.attributes > 16);
      if (over.length) throw new Error(`pipelines beyond WebGPU's guaranteed 8 buffers and 16 attributes: ${JSON.stringify(over)}`);
      if (most('buffers') > first.allowed)
        throw new Error(`the glyph pipeline binds ${most('buffers')} vertex buffers; lab.instance says ${first.allowed}`);
      const failed = messages.filter((m) => /pipeline creation failed|exceeds the maximum/i.test(m));
      if (failed.length) throw new Error(`WebGPU refused a pipeline: ${failed.join(' | ')}`);
      proved = `three.js's WebGPU path asked the device for ${first.pipelines.length} render pipelines: `
        + `the glyph pipeline binds ${most('buffers')} vertex buffers (lab.instance ${first.allowed}, WebGPU's limit 8), `
        + `at most ${most('attributes')} attributes (limit 16), no pipeline refused`;
    } else {
      proved = `${first.gpu ? 'no WebGPU pipeline was asked for' : 'navigator.gpu is absent'}, so NO pipeline was checked`;
      if (first.backend === 'WebGPU') throw new Error('the lab reports WebGPU but asked the device for no render pipeline');
    }
    if (first.backend === 'WebGL2' && !((await inkedPixels(page)) >= drawnPixels))
      throw new Error(`on WebGL2 "${word}" is not drawn; status "${first.status}"`);

    // (b) never blank and silent: the word is drawn, or the status line
    // names what went wrong. A software device may be lost at any moment
    // after the start, so wait for one of the two, bounded.
    let outcome = null, last = first;
    for (const until = Date.now() + 30000; !outcome && Date.now() < until;) {
      last = await state(page);
      const pixels = await inkedPixels(page);
      if (pixels >= drawnPixels)
        outcome = last.backend === 'WebGL2'
          ? `the lab fell back to WebGL2 and drew "${word}" (${pixels} inked px)`
          : `"${word}" drawn on WebGPU (${pixels} inked px)`;
      else if (/with errors|Couldn't start/.test(last.status))
        outcome = `nothing drawn, and the status line says so: "${last.status}" (${last.errors[0] ?? 'no message'})`;
      else await page.waitForTimeout(500);
    }
    await page.screenshot({ path: `${out}/webgpu.png` });
    if (!outcome)
      throw new Error(`blank and silent: "${word}" is not drawn and the status line reads "${last.status}" on ${last.backend}; `
        + `console: ${messages.slice(-5).join(' | ')}`);
    await page.close();

    // (c) ?webgl never touches WebGPU
    const forced = await context.newPage();
    listen(forced);
    current = forced;
    await open(forced, at({ webgl: '' }));
    await typeWord(forced);
    const gl = await state(forced);
    if (gl.backend !== 'WebGL2' || !gl.status.includes('WebGL2')) throw new Error(`?webgl ended on ${gl.backend}; status "${gl.status}"`);
    if (gl.pipelines.length || gl.errors.length)
      throw new Error(`?webgl asked WebGPU for ${gl.pipelines.length} pipelines and kept ${gl.errors.length} GPU errors`);
    const glPixels = await inkedPixels(forced);
    if (!(glPixels >= drawnPixels)) throw new Error(`with ?webgl "${word}" is not drawn (${glPixels} inked px)`);
    await forced.close();
    await gpuBrowser.close();
    done(`${proved}; not blank and silent: ${outcome}; ?webgl starts on WebGL2 without asking WebGPU for anything and draws "${word}" (${glPixels} inked px)`);
  }

  for (const [p, errors] of consoleErrors) {
    current = p;
    if (errors.length) throw new Error(`console errors: ${errors.join(' | ')}`);
  }
  console.log(`screenshots: ${['typing-mid', 'embers', 'settled', 'wrapped', 'reveal-0', 'reveal-1', 'reveal-2', 'reveal-3',
    'crawl', 'steady', 'fuse', 'debug', 'stage-grid', 'swell', 'card', 'push', 'specimen-dark', 'specimen-paper', 'reading', 'dress-paper', 'dress-glint', 'dress-dark', 'webgpu',
    ...['elegant', 'caffeinated', 'drunken', 'coarse', 'plain', 'auto-fast', 'auto-slow'].map((n) => `style-${n}`)].map((n) => `${out}/${n}.png`).join(', ')}`);
} catch (e) {
  code = 1;
  console.error(`FAIL ${step}: ${e.message}`);
  const errors = (current && consoleErrors.get(current)) ?? [];
  console.error(`console errors (${errors.length}):`);
  errors.forEach((m) => console.error('  ' + m));
  try { await current?.screenshot({ path: `${out}/failure.png` });
        console.error(`screenshot: ${out}/failure.png`); } catch {}
} finally {
  await gpuBrowser?.close();
  await browser.close();
}
process.exit(code);
