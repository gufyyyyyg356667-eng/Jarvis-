/* Light 2D copy of the home-screen particle orb (orb.html): same palettes, same states,
   same motion (slow spin, noise flow, breathing, idle pulse, thin arcs, orbit dots).
   Used by my1.html ... my4.html and by the gallery preview cards. */
(function () {
  const TAU = Math.PI * 2;
  const FOVTAN = Math.tan(22.5 * Math.PI / 180), VIS = 3.1, CAMZ = VIS / (2 * FOVTAN);
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const mix = (a, b, t) => a + (b - a) * t;
  const smooth = (e0, e1, x) => { const t = clamp((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); };
  const mix3 = (a, b, t) => [mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)];
  const css = (c, k, a) => 'rgba(' + Math.round(clamp(c[0] * k, 0, 1) * 255) + ',' + Math.round(clamp(c[1] * k, 0, 1) * 255) + ',' + Math.round(clamp(c[2] * k, 0, 1) * 255) + ',' + a + ')';

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
    const cfg = Object.assign({ fps: 30, count: 0, bright: 1, amp: 0 }, base, user);
    const cv = user.canvas || document.getElementById('c');
    const ctx = cv.getContext('2d');
    let W = 88, H = 88, dpr = 1, S = 88, unit = 28, pts = [], PXK = 1;

    const orbit = [];
    for (let i = 0; i < 36; i++) orbit.push({ a: Math.random() * TAU, r: 1.5 + Math.random() * .55, sp: (.08 + Math.random() * .18) * (Math.random() < .5 ? -1 : 1), ph: Math.random() * TAU, tilt: (Math.random() - .5) * 1.2, y: (Math.random() - .5) * 1.1, fs: .2 + Math.random() * .4, size: .02 + Math.random() * .045 });
    const OPAL = [[.1, .7, 1], [.2, .4, 1], [.55, .3, .95]];

    function resize() {
      dpr = Math.min(window.devicePixelRatio || 1, 3);
      W = cv.clientWidth || window.innerWidth || 88; H = cv.clientHeight || window.innerHeight || 88;
      cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
      S = Math.min(W, H); unit = S / VIS; PXK = S * dpr / (2 * FOVTAN);
      // keep the same dust density as the big home orb: fewer points on a small orb
      const Ddev = 0.645 * S * dpr;
      const N = cfg.count || clamp(Math.round(6000 * Math.pow(Ddev / 300, 2)), 350, 2400);
      pts = [];
      for (let i = 0; i < N; i++) {
        const y = 1 - 2 * (i + .5) / N, r = Math.sqrt(1 - y * y), th = i * 2.39996;
        pts.push({ dx: Math.cos(th) * r, dy: y, dz: Math.sin(th) * r, shell: .93 + .07 * Math.random(), rnd: Math.random() });
      }
    }
    window.addEventListener('resize', resize); resize();

    let tgt = STATES[cfg.state] || STATES.idle;
    const cur = Object.assign({}, tgt);
    let time = 0, flow = 0, rotY = 0, r1a = 0, r2a = 0, amp = cfg.amp, last = 0, lastDraw = 0;
    const api = { setState: n => { if (STATES[n]) tgt = STATES[n]; }, setAudio: a => { amp = clamp(+a || 0, 0, 1); } };
    if (!user.canvas) window.NovaOrb = api;
    const P = cfg.pal;

    function ring(rho, width, segs, seed, alpha, rot, s) {
      const steps = 120, lw = Math.max(.6, width * unit * s);
      ctx.lineWidth = Math.max(.5, lw * .8); ctx.lineCap = 'round';
      for (let i = 0; i < steps; i++) {
        const al = i / steps * TAU;
        const m = smooth(-.25, .35, Math.sin(al * segs + seed)) * smooth(-.9, -.2, Math.sin(al + seed * .7));
        if (m < .02) continue;
        const col = mix3(mix3(P.cyan, P.blue, .5 + .5 * Math.sin(al + seed)), P.purp, .5 + .5 * Math.sin(al * 2 - seed));
        const a0 = al + rot, a1 = (i + 1.4) / steps * TAU + rot, rr = rho * unit * s;
        ctx.strokeStyle = css(mix3(col, [1, 1, 1], .25), 1, m * alpha * .7);
        ctx.beginPath(); ctx.arc(S / 2 + (W - S) / 2, S / 2 + (H - S) / 2, rr, a0, a1); ctx.stroke();
      }
    }

    function frame(ts) {
      requestAnimationFrame(frame);
      if (document.hidden) return;
      if (ts - lastDraw < 1000 / cfg.fps) return;
      const dt = Math.min((ts - (last || ts)) / 1000, .05); last = ts; lastDraw = ts;

      const k = 1 - Math.exp(-dt * 3.5);
      for (const key in cur) cur[key] += (tgt[key] - cur[key]) * k;
      time += dt; flow += dt * cur.speed; r1a += dt * (.12 + cur.ring * .6); r2a -= dt * (.08 + cur.ring * .35);
      rotY += dt * .18 * (.5 + cur.speed);
      const idlePulse = Math.pow(Math.max(0, Math.sin(time * .5)), 12) * .15;
      const s = cur.scale * (1 + .018 * Math.sin(time * 1.2) + amp * .07);
      const bright = (cur.bright + idlePulse) * cfg.bright;
      const strength = cur.glow + amp * .6 + idlePulse * 2;

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, W, H);
      ctx.globalCompositeOperation = cfg.add ? 'lighter' : 'source-over';
      const cx = W / 2, cy = H / 2;

      // outer halo (only outside the sphere, like the home orb)
      const hv = [[P.blue, 1], [P.purp, -1]];
      for (let h = 0; h < 2; h++) {
        const col0 = h === 0 ? mix3(P.blue, P.cyan, cur.cyan * .5) : P.purp, col = mix3(col0, [1, 1, 1], .35), sgn = hv[h][1];
        const ox = Math.cos(time * .4) * unit * .10 * sgn, oy = Math.sin(time * .4) * unit * .10 * sgn;
        const g = ctx.createRadialGradient(cx + ox, cy + oy, unit * 1.0 * s, cx + ox, cy + oy, unit * 1.55);
        const A = clamp(.75 * strength * .5, 0, .55) * .55;
        g.addColorStop(0, css(col, 1, A * .19 / .19 * .5));
        g.addColorStop(.40, css(col, 1, A * .44 * .5));
        g.addColorStop(.75, css(col, 1, A * .10 * .5));
        g.addColorStop(1, css(col, 1, 0));
        ctx.globalAlpha = 1; ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
      }

      // dark glass core: dense dust makes the real orb opaque-dark, so paint that first
      if (cfg.add) {
        ctx.globalCompositeOperation = 'source-over';
        const cg = ctx.createRadialGradient(cx, cy, 0, cx, cy, unit * s);
        const dk = [P.deep[0] * .10, P.deep[1] * .10, P.deep[2] * .16];
        cg.addColorStop(0, css(dk, 1, .78)); cg.addColorStop(.85, css(dk, 1, .72)); cg.addColorStop(1, css(dk, 1, .30));
        ctx.globalAlpha = 1; ctx.fillStyle = cg;
        ctx.beginPath(); ctx.arc(cx, cy, unit * s, 0, TAU); ctx.fill();
        ctx.globalCompositeOperation = 'lighter';
      }

      // thin rotating arcs
      ring(1.2635, .017, 2, 0, .4 + cur.ring * .3 + amp * .35, r1a, s);
      ring(1.384, .0085, 3, 2.1, .22 + cur.ring * .2 + amp * .2, r2a, s);

      // particle sphere
      const ca = Math.cos(rotY), sa = Math.sin(rotY);
      for (let i = 0; i < pts.length; i++) {
        const p = pts[i];
        const n = Math.sin(p.dx * 3 + flow * .8 + p.rnd * 6) * Math.sin(p.dy * 3 - flow * .6) * Math.sin(p.dz * 3 + flow * .5);
        const r = p.shell * (1 + n * .05 + amp * .1 * p.rnd + .015 * Math.sin(flow * 2 + p.rnd * 20)) * s;
        const x = p.dx * r, y = p.dy * r, z = p.dz * r;
        const x1 = x * ca + z * sa, z1 = -x * sa + z * ca;
        const dist = CAMZ - z1, kp = CAMZ / dist, depth = smooth(-1, 1, z1 / s);
        let c = mix3(P.blue, P.cyan, smooth(-.5, .9, p.dy + n) * cur.cyan);
        c = mix3(c, P.purp, smooth(.1, .9, p.dx * .6 - p.dy * .3 + p.rnd * .4) * cur.purple);
        c = mix3(P.deep, c, .5 + .5 * p.rnd);
        const sizeDev = Math.max((.009 + p.rnd * .017) * (1 + amp * .5) * PXK / dist * mix(.85, 1.2, depth), 1.7);
        const rad = Math.max(sizeDev * .32, .8) / dpr;
        ctx.globalAlpha = mix(.22, 1, depth) * .9;
        ctx.fillStyle = css(c, bright * mix(.6, 1, depth), 1);
        ctx.beginPath(); ctx.arc(cx + x1 * unit * kp, cy - y * unit * kp, rad, 0, TAU); ctx.fill();
      }

      // small orbiting dots
      for (let i = 0; i < orbit.length; i++) {
        const d = orbit[i];
        d.a += d.sp * dt * (.6 + cur.speed * .8);
        const rr = d.r + Math.sin(time * d.fs + d.ph) * .12 + amp * .1;
        const x = Math.cos(d.a) * rr, z = Math.sin(d.a) * rr * .6, y = d.y * .8 + Math.sin(d.a * 1.3 + d.ph) * .18 + x * d.tilt * .25;
        const kp = CAMZ / (CAMZ - z), rad = Math.max(d.size * PXK / (CAMZ - z) * .32, .8) / dpr;
        ctx.globalAlpha = Math.pow(.5 + .5 * Math.sin(time * d.fs * 1.6 + d.ph), 1.5) * (.55 + cur.glow * .3);
        ctx.fillStyle = css(OPAL[i % 3], 1, 1);
        ctx.beginPath(); ctx.arc(cx + x * unit * kp, cy - y * unit * kp, rad, 0, TAU); ctx.fill();
      }
      ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
    }
    requestAnimationFrame(frame);
    return api;
  }
  window.OrbAvatar = { start: start, PRESETS: PRESETS, STATES: STATES };
})();
