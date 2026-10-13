/* Light copy of the home-screen particle orb (orb.html): same palettes, states and motion.
   Fast version: the dust is rasterised into a small pixel buffer (no per-dot canvas calls),
   so it stays smooth on slow phones and does not drain the battery.
   Used by my1.html ... my4.html and by the gallery preview cards. */
(function () {
  const TAU = Math.PI * 2;
  const FOVTAN = Math.tan(22.5 * Math.PI / 180), VIS = 3.1, CAMZ = VIS / (2 * FOVTAN);
  const clamp = (v, a, b) => v < a ? a : (v > b ? b : v);
  const mix = (a, b, t) => a + (b - a) * t;
  const smooth = (e0, e1, x) => { const t = clamp((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); };
  const rgba = (r, g, b, a) => 'rgba(' + ((clamp(r, 0, 1) * 255) | 0) + ',' + ((clamp(g, 0, 1) * 255) | 0) + ',' + ((clamp(b, 0, 1) * 255) | 0) + ',' + a + ')';

  // identical to STATES in orb.html
  const STATES = {
    idle:      { scale: 1.00, speed: .35, cyan: .35, purple: .45, bright: 1.00, glow: .50, ring: .15 },
    listening: { scale: 1.05, speed: .85, cyan: 1.00, purple: .25, bright: 1.12, glow: .95, ring: .35 },
    thinking:  { scale: 1.00, speed: 1.55, cyan: .30, purple: 1.00, bright: 1.05, glow: .75, ring: 1.00 },
    speaking:  { scale: 1.04, speed: 1.15, cyan: .90, purple: .90, bright: 1.20, glow: 1.00, ring: .45 }
  };
  // palettes copied from THEMES in orb.html  (deep, blue, cyan, purple)
  const PRESETS = {
    nova:   { add: true,  state: 'idle',      pal: { deep: [.10, .20, .80], blue: [.25, .50, 1], cyan: [.30, .92, 1], purp: [.70, .45, 1] } },   // Dark theme orb
    ruby:   { add: true,  state: 'idle',      pal: { deep: [.55, .05, .10], blue: [.95, .15, .20], cyan: [1, .45, .30], purp: [1, .25, .55] } },   // Red theme orb
    sunset: { add: false, state: 'idle',      pal: { deep: [.55, .12, .45], blue: [.95, .30, .45], cyan: [1, .55, .20], purp: [.60, .30, .90] } },  // Sunset theme orb
    aqua:   { add: true,  state: 'listening', pal: { deep: [.10, .20, .80], blue: [.25, .50, 1], cyan: [.30, .92, 1], purp: [.70, .45, 1] } }    // Dark orb, "listening" look
  };

  function start(user) {
    user = user || {};
    const base = PRESETS[user.preset || 'nova'] || PRESETS.nova;
    const cfg = Object.assign({ fps: 24, count: 0, bright: 1, amp: 0, once: false }, base, user);
    const cv = user.canvas || document.getElementById('c');
    const ctx = cv.getContext('2d');
    const dust = document.createElement('canvas'), dctx = dust.getContext('2d');
    const P = cfg.pal, D0 = P.deep, B0 = P.blue, C0 = P.cyan, U0 = P.purp;

    let W = 88, H = 88, dpr = 1, dprE = 1, S = 88, unit = 28, PXK = 1, BW = 1, BH = 1;
    let N = 0, dxA, dyA, dzA, shA, rnA, stA, acc, img;

    const orbit = [];
    for (let i = 0; i < 24; i++) orbit.push({ a: Math.random() * TAU, r: 1.5 + Math.random() * .55, sp: (.08 + Math.random() * .18) * (Math.random() < .5 ? -1 : 1), ph: Math.random() * TAU, tilt: (Math.random() - .5) * 1.2, y: (Math.random() - .5) * 1.1, fs: .2 + Math.random() * .4, size: .02 + Math.random() * .045 });
    const OPAL = [[.1, .7, 1], [.2, .4, 1], [.55, .3, .95]];

    function resize() {
      dpr = Math.min(window.devicePixelRatio || 1, 3); dprE = Math.min(dpr, 2);
      W = cv.clientWidth || window.innerWidth || 88; H = cv.clientHeight || window.innerHeight || 88;
      cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
      S = Math.min(W, H); unit = S / VIS; PXK = S * dprE / (2 * FOVTAN);
      BW = Math.max(8, Math.round(W * dprE)); BH = Math.max(8, Math.round(H * dprE));
      dust.width = BW; dust.height = BH;
      acc = new Float32Array(BW * BH * 4); img = dctx.createImageData(BW, BH);
      // keep the same dust density as the big home orb: fewer points on a small orb
      const Ddev = 0.645 * S * dprE;
      N = cfg.count || clamp(Math.round(6000 * Math.pow(Ddev / 300, 2)), 300, 2200);
      dxA = new Float32Array(N); dyA = new Float32Array(N); dzA = new Float32Array(N);
      shA = new Float32Array(N); rnA = new Float32Array(N); stA = new Float32Array(N);
      for (let i = 0; i < N; i++) {
        const y = 1 - 2 * (i + .5) / N, r = Math.sqrt(1 - y * y), th = i * 2.39996;
        dxA[i] = Math.cos(th) * r; dyA[i] = y; dzA[i] = Math.sin(th) * r;
        shA[i] = .93 + .07 * Math.random(); rnA[i] = Math.random();
        stA[i] = smooth(.1, .9, dxA[i] * .6 - dyA[i] * .3 + rnA[i] * .4);
      }
    }
    resize();
    if (!cfg.once) window.addEventListener('resize', resize);

    let tgt = STATES[cfg.state] || STATES.idle;
    const cur = Object.assign({}, tgt);
    let time = 0, flow = 0, rotY = 0, r1a = 0, r2a = 0, amp = cfg.amp, last = 0, lastDraw = 0;
    const api = { setState: n => { if (STATES[n]) tgt = STATES[n]; }, setAudio: a => { amp = clamp(+a || 0, 0, 1); } };
    if (!user.canvas) window.NovaOrb = api;

    function ring(rho, width, segs, seed, alpha, rot, s) {
      const steps = 44, lw = Math.max(.5, width * unit * s * .8), rr = rho * unit * s, cx = W / 2, cy = H / 2;
      ctx.lineWidth = lw; ctx.lineCap = 'round';
      for (let i = 0; i < steps; i++) {
        const al = (i + .5) / steps * TAU;
        const m = smooth(-.25, .35, Math.sin(al * segs + seed)) * smooth(-.9, -.2, Math.sin(al + seed * .7));
        if (m < .05) continue;
        const t1 = .5 + .5 * Math.sin(al + seed), t2 = .5 + .5 * Math.sin(al * 2 - seed);
        let r = mix(C0[0], B0[0], t1), g = mix(C0[1], B0[1], t1), b = mix(C0[2], B0[2], t1);
        r = mix(r, U0[0], t2); g = mix(g, U0[1], t2); b = mix(b, U0[2], t2);
        r = mix(r, 1, .25); g = mix(g, 1, .25); b = mix(b, 1, .25);
        ctx.strokeStyle = rgba(r, g, b, m * alpha * .7);
        ctx.beginPath(); ctx.arc(cx, cy, rr, al + rot - .09, al + rot + .09 + TAU / steps); ctx.stroke();
      }
    }

    function drawFrame(dt) {
      const k = 1 - Math.exp(-dt * 3.5);
      for (const key in cur) cur[key] += (tgt[key] - cur[key]) * k;
      time += dt; flow += dt * cur.speed; r1a += dt * (.12 + cur.ring * .6); r2a -= dt * (.08 + cur.ring * .35);
      rotY += dt * .18 * (.5 + cur.speed);
      const idlePulse = Math.pow(Math.max(0, Math.sin(time * .5)), 12) * .15;
      const s = cur.scale * (1 + .018 * Math.sin(time * 1.2) + amp * .07);
      const bright = (cur.bright + idlePulse) * cfg.bright;
      const strength = cur.glow + amp * .6 + idlePulse * 2;
      const cyanK = cur.cyan, purpK = cur.purple;

      // ---- dust -> pixel buffer (premultiplied, additive) ----
      acc.fill(0);
      const ca = Math.cos(rotY), sa = Math.sin(rotY), halfW = BW / 2, halfH = BH / 2, uB = unit * dprE;
      let minX = BW, maxX = 0, minY = BH, maxY = 0;
      for (let i = 0; i < N; i++) {
        const dx = dxA[i], dy = dyA[i], dz = dzA[i], rn = rnA[i];
        const n = Math.sin(dx * 3 + flow * .8 + rn * 6) * Math.sin(dy * 3 - flow * .6) * Math.sin(dz * 3 + flow * .5);
        const rr = shA[i] * (1 + n * .05 + amp * .1 * rn + .015 * Math.sin(flow * 2 + rn * 20)) * s;
        const x = dx * rr, y = dy * rr, z = dz * rr;
        const x1 = x * ca + z * sa, z1 = -x * sa + z * ca;
        const dist = CAMZ - z1, kp = CAMZ / dist, depth = smooth(-1, 1, z1 / s);
        const t1 = smooth(-.5, .9, dy + n) * cyanK, t2 = stA[i] * purpK, mm = .5 + .5 * rn, bk = bright * mix(.6, 1, depth);
        let r = B0[0] + (C0[0] - B0[0]) * t1, g = B0[1] + (C0[1] - B0[1]) * t1, b = B0[2] + (C0[2] - B0[2]) * t1;
        r += (U0[0] - r) * t2; g += (U0[1] - g) * t2; b += (U0[2] - b) * t2;
        r = (D0[0] + (r - D0[0]) * mm) * bk; g = (D0[1] + (g - D0[1]) * mm) * bk; b = (D0[2] + (b - D0[2]) * mm) * bk;
        const sizeDev = Math.max((.009 + rn * .017) * (1 + amp * .5) * PXK / dist * mix(.85, 1.2, depth), 1.7);
        const a0 = mix(.22, 1, depth) * 2.4 * clamp(sizeDev / 2.2, .8, 1.5);
        // bilinear splat into the 4 nearest pixels (smooth sub-pixel motion)
        const px = halfW + x1 * uB * kp - .5, py = halfH - y * uB * kp - .5;
        const ix = Math.floor(px), iy = Math.floor(py);
        if (ix < 0 || iy < 0 || ix + 1 >= BW || iy + 1 >= BH) continue;
        const fx = px - ix, fy = py - iy;
        const w00 = (1 - fx) * (1 - fy) * a0, w10 = fx * (1 - fy) * a0, w01 = (1 - fx) * fy * a0, w11 = fx * fy * a0;
        let o = (iy * BW + ix) * 4;
        acc[o] += r * w00; acc[o + 1] += g * w00; acc[o + 2] += b * w00; acc[o + 3] += w00;
        o += 4;
        acc[o] += r * w10; acc[o + 1] += g * w10; acc[o + 2] += b * w10; acc[o + 3] += w10;
        o += BW * 4 - 4;
        acc[o] += r * w01; acc[o + 1] += g * w01; acc[o + 2] += b * w01; acc[o + 3] += w01;
        o += 4;
        acc[o] += r * w11; acc[o + 1] += g * w11; acc[o + 2] += b * w11; acc[o + 3] += w11;
        if (ix < minX) minX = ix; if (ix + 1 > maxX) maxX = ix + 1;
        if (iy < minY) minY = iy; if (iy + 1 > maxY) maxY = iy + 1;
      }
      const out = img.data; out.fill(0);
      for (let yy = minY; yy <= maxY; yy++) {
        let o = (yy * BW + minX) * 4;
        for (let xx = minX; xx <= maxX; xx++, o += 4) {
          const a = acc[o + 3]; if (a <= 0.004) continue;
          const A = a > 1 ? 1 : a, q = 255 / a;   // straight colour = premultiplied / alpha
          out[o] = acc[o] * q; out[o + 1] = acc[o + 1] * q; out[o + 2] = acc[o + 2] * q; out[o + 3] = A * 255;
        }
      }
      dctx.putImageData(img, 0, 0);

      // ---- everything else, drawn with a handful of canvas calls ----
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, W, H);
      ctx.globalCompositeOperation = cfg.add ? 'lighter' : 'source-over';
      const cx = W / 2, cy = H / 2;

      const A = clamp(.75 * strength * .5, 0, .55) * .55;
      for (let h = 0; h < 2; h++) {
        const c = h === 0 ? [mix(B0[0], C0[0], cyanK * .5), mix(B0[1], C0[1], cyanK * .5), mix(B0[2], C0[2], cyanK * .5)] : U0;
        const sg = h === 0 ? 1 : -1, ox = Math.cos(time * .4) * unit * .10 * sg, oy = Math.sin(time * .4) * unit * .10 * sg;
        const r = mix(c[0], 1, .35), g = mix(c[1], 1, .35), b = mix(c[2], 1, .35);
        const gr = ctx.createRadialGradient(cx + ox, cy + oy, unit * s, cx + ox, cy + oy, unit * 1.55);
        gr.addColorStop(0, rgba(r, g, b, A * .5)); gr.addColorStop(.44, rgba(r, g, b, A * .22)); gr.addColorStop(.75, rgba(r, g, b, A * .05)); gr.addColorStop(1, rgba(r, g, b, 0));
        ctx.globalAlpha = 1; ctx.fillStyle = gr; ctx.fillRect(0, 0, W, H);
      }

      if (cfg.add) {   // dark glass core (dense dust makes the real orb dark)
        ctx.globalCompositeOperation = 'source-over';
        const cg = ctx.createRadialGradient(cx, cy, 0, cx, cy, unit * s);
        cg.addColorStop(0, rgba(D0[0] * .10, D0[1] * .10, D0[2] * .16, .78)); cg.addColorStop(.85, rgba(D0[0] * .10, D0[1] * .10, D0[2] * .16, .72)); cg.addColorStop(1, rgba(D0[0] * .10, D0[1] * .10, D0[2] * .16, .30));
        ctx.globalAlpha = 1; ctx.fillStyle = cg;
        ctx.beginPath(); ctx.arc(cx, cy, unit * s, 0, TAU); ctx.fill();
        ctx.globalCompositeOperation = 'lighter';
      }

      ring(1.2635, .017, 2, 0, .4 + cur.ring * .3 + amp * .35, r1a, s);
      ring(1.384, .0085, 3, 2.1, .22 + cur.ring * .2 + amp * .2, r2a, s);

      ctx.globalAlpha = 1;
      ctx.drawImage(dust, 0, 0, W, H);

      for (let i = 0; i < orbit.length; i++) {
        const d = orbit[i];
        d.a += d.sp * dt * (.6 + cur.speed * .8);
        const rr2 = d.r + Math.sin(time * d.fs + d.ph) * .12 + amp * .1;
        const x = Math.cos(d.a) * rr2, z = Math.sin(d.a) * rr2 * .6, y = d.y * .8 + Math.sin(d.a * 1.3 + d.ph) * .18 + x * d.tilt * .25;
        const kp = CAMZ / (CAMZ - z), rad = Math.max(d.size * PXK / (CAMZ - z) * .18, .6) / dprE;
        const pc = OPAL[i % 3];
        ctx.globalAlpha = Math.pow(.5 + .5 * Math.sin(time * d.fs * 1.6 + d.ph), 1.5) * (.55 + cur.glow * .3);
        ctx.fillStyle = rgba(pc[0], pc[1], pc[2], 1);
        ctx.fillRect(cx + x * unit * kp - rad, cy - y * unit * kp - rad, rad * 2, rad * 2);
      }
      ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
    }

    function frame(ts) {
      requestAnimationFrame(frame);
      if (document.hidden) return;
      if (ts - lastDraw < 1000 / cfg.fps - 4) return;
      const dt = Math.min((ts - (last || ts)) / 1000, .1); last = ts; lastDraw = ts;
      drawFrame(dt);
    }
    if (cfg.once) { drawFrame(0.016); } else { requestAnimationFrame(frame); }
    return api;
  }
  window.OrbAvatar = { start: start, PRESETS: PRESETS, STATES: STATES };
})();
