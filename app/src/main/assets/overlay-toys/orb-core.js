/* Particle-orb avatar (light 2D version of the home-screen orb).
   Used by my1.html ... my4.html. Edit the config inside each avatar file to change colours/motion. */
(function () {
  const TAU = Math.PI * 2;
  const mix = (a, b, t) => a + (b - a) * t;
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const smooth = (e0, e1, x) => { const t = clamp((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); };
  const mix3 = (a, b, t) => [mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)];
  const rgb = (c, k) => 'rgb(' + Math.round(clamp(c[0] * k, 0, 1) * 255) + ',' + Math.round(clamp(c[1] * k, 0, 1) * 255) + ',' + Math.round(clamp(c[2] * k, 0, 1) * 255) + ')';

  window.OrbAvatar = {
    start: function (user) {
      const cfg = Object.assign({
        count: 700, radius: 0.40, spin: 0.45, tilt: 0.35, noise: 0.05, bright: 1.0,
        cyan: 0.35, purple: 0.45, pulse: 1.0, halo: 0.30, flicker: 0, fps: 30,
        pal: { deep: [0.03, 0.07, 0.30], blue: [0.22, 0.52, 1.0], cyan: [0.20, 0.85, 1.0], purp: [0.55, 0.35, 1.0] },
        rings: []
      }, user || {});
      const cv = document.getElementById('c');
      const ctx = cv.getContext('2d');
      let W = 0, H = 0, dpr = 1;
      function resize() {
        dpr = Math.min(window.devicePixelRatio || 1, 3);
        W = window.innerWidth || 88; H = window.innerHeight || 88;
        cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
      }
      window.addEventListener('resize', resize); resize();

      const N = cfg.count, pts = [];
      for (let i = 0; i < N; i++) {
        const y = 1 - 2 * (i + 0.5) / N, r = Math.sqrt(1 - y * y), th = i * 2.39996;
        pts.push({ dx: Math.cos(th) * r, dy: y, dz: Math.sin(th) * r, shell: 0.93 + 0.07 * Math.random(), rnd: Math.random() });
      }
      const P = cfg.pal;
      let burst = 0, nextBurst = 2.5, last = 0, amp = 0;
      window.NovaOrb = { burst: function (v) { burst = Math.max(burst, v == null ? 1 : v); } };

      function drawRing(rg, t, R, cx, cy, front) {
        const col = rg.color || P.cyan;
        const rot = rg.rot || 0, cr = Math.cos(rot), sr = Math.sin(rot);
        const rx = rg.rx * R, ry = rg.ry * R;
        const pos = a => {
          const x = Math.cos(a) * rx, y = Math.sin(a) * ry;
          return [cx + x * cr - y * sr, cy + x * sr + y * cr];
        };
        // faint ring line (only the matching half so front/back layering works)
        ctx.lineWidth = 0.8; ctx.strokeStyle = rgb(col, 1); ctx.globalAlpha = front ? 0.28 : 0.12;
        ctx.beginPath();
        const a0 = front ? 0 : Math.PI, a1 = front ? Math.PI : TAU;
        for (let a = a0; a <= a1 + 0.01; a += 0.12) { const p = pos(a); a === a0 ? ctx.moveTo(p[0], p[1]) : ctx.lineTo(p[0], p[1]); }
        ctx.stroke();
        // orbiting dots
        const n = rg.n || 8;
        for (let k = 0; k < n; k++) {
          const a = (t * (rg.speed || 0.8) + k * TAU / n) % TAU, aa = (a + TAU) % TAU;
          const isFront = Math.sin(aa) > 0;
          if (isFront !== front) continue;
          const p = pos(aa);
          ctx.globalAlpha = front ? 0.95 : 0.35;
          ctx.fillStyle = rgb(col, 1.1);
          ctx.beginPath(); ctx.arc(p[0], p[1], (rg.size || 1.5) * (front ? 1 : 0.8), 0, TAU); ctx.fill();
        }
      }

      function frame(ts) {
        requestAnimationFrame(frame);
        if (document.hidden) return;
        if (ts - last < 1000 / cfg.fps) return;
        last = ts;
        const t = ts / 1000;
        // gentle "alive" pulse + an occasional stronger one (like the orb when Jarvis speaks)
        if (t > nextBurst) { burst = 1; nextBurst = t + 4 + Math.random() * 4; }
        burst *= 0.93;
        amp = (0.16 + 0.10 * Math.sin(t * 1.7)) * cfg.pulse + burst * 0.55 * cfg.pulse;
        if (cfg.flicker) amp += cfg.flicker * (Math.random() * 0.12);

        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, W, H);
        const cx = W / 2, cy = H / 2, R = cfg.radius * Math.min(W, H) * (1 + 0.04 * amp);

        // halo
        const hg = ctx.createRadialGradient(cx, cy, R * 0.2, cx, cy, R * 1.55);
        const hc = P.blue;
        hg.addColorStop(0, 'rgba(' + Math.round(hc[0] * 255) + ',' + Math.round(hc[1] * 255) + ',' + Math.round(hc[2] * 255) + ',' + (cfg.halo * (0.7 + amp)) + ')');
        hg.addColorStop(1, 'rgba(' + Math.round(hc[0] * 255) + ',' + Math.round(hc[1] * 255) + ',' + Math.round(hc[2] * 255) + ',0)');
        ctx.globalCompositeOperation = 'lighter';
        ctx.globalAlpha = 1; ctx.fillStyle = hg; ctx.fillRect(0, 0, W, H);

        cfg.rings.forEach(rg => drawRing(rg, t, R, cx, cy, false));

        const ang = t * cfg.spin, tilt = cfg.tilt + 0.12 * Math.sin(t * 0.5);
        const ca = Math.cos(ang), sa = Math.sin(ang), ct = Math.cos(tilt), st = Math.sin(tilt);
        const CAM = 3.2;
        for (let i = 0; i < N; i++) {
          const p = pts[i];
          const n = Math.sin(p.dx * 3 + t * 0.8 + p.rnd * 6) * Math.sin(p.dy * 3 - t * 0.6) * Math.sin(p.dz * 3 + t * 0.5);
          const r = p.shell * (1 + n * cfg.noise + amp * 0.1 * p.rnd + 0.015 * Math.sin(t * 2 + p.rnd * 20));
          let x = p.dx * r, y = p.dy * r, z = p.dz * r;
          const x1 = x * ca + z * sa, z1 = -x * sa + z * ca;      // spin around Y
          const y2 = y * ct - z1 * st, z2 = y * st + z1 * ct;      // tilt around X
          const k = CAM / (CAM - z2);
          const depth = smooth(-1, 1, z2);
          let c = mix3(P.blue, P.cyan, smooth(-0.5, 0.9, p.dy + n) * cfg.cyan);
          c = mix3(c, P.purp, smooth(0.1, 0.9, p.dx * 0.6 - p.dy * 0.3 + p.rnd * 0.4) * cfg.purple);
          c = mix3(P.deep, c, 0.5 + 0.5 * p.rnd);
          const size = Math.max(0.7, (0.009 + p.rnd * 0.016) * R * (1 + amp * 0.5) * k * mix(0.85, 1.2, depth));
          ctx.globalAlpha = mix(0.22, 1, depth);
          ctx.fillStyle = rgb(c, cfg.bright * mix(0.6, 1, depth) * 1.25);
          ctx.beginPath(); ctx.arc(cx + x1 * R * k, cy + y2 * R * k, size, 0, TAU); ctx.fill();
        }

        cfg.rings.forEach(rg => drawRing(rg, t, R, cx, cy, true));
        ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
      }
      requestAnimationFrame(frame);
    }
  };
})();
