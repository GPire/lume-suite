# -*- coding: utf-8 -*-
"""Synthesises the score and sound effects for the promo from scratch (numpy only): no samples, no third-party music.
Scene starts (seconds): 0 hook, 5 find, 11 clean, 18 after, 26 phone, 32 end, 37 out."""
import numpy as np, wave, sys
SR = 44100; DUR = 37.5; N = int(SR * DUR)
rng = np.random.default_rng(7)
t = np.arange(N) / SR
L = np.zeros(N); R = np.zeros(N)

def env(n, a, d, s=0.0, r=0.0):
    e = np.ones(n); na = max(1, int(a * SR)); e[:na] = np.linspace(0, 1, na)
    nd = int(d * SR); nd = min(nd, n - na)
    if nd > 0: e[na:na + nd] = np.linspace(1, s, nd) if s else np.exp(-np.linspace(0, 6, nd))
    if s and na + nd < n: e[na + nd:] = s
    nr = int(r * SR)
    if nr > 0 and nr < n: e[-nr:] *= np.linspace(1, 0, nr)
    return e

def lp(x, fc):
    a = np.exp(-2 * np.pi * fc / SR); y = np.zeros_like(x); s = 0.0
    for i in range(len(x)):
        s = (1 - a) * x[i] + a * s; y[i] = s
    return y

def add(x, at, pan=0.0, gain=1.0):
    i = int(at * SR); n = min(len(x), N - i)
    if n <= 0: return
    l = gain * np.cos((pan + 1) * np.pi / 4); r = gain * np.sin((pan + 1) * np.pi / 4)
    L[i:i + n] += x[:n] * l; R[i:i + n] += x[:n] * r

def tone(f, dur, kind="sine", det=0.0):
    n = int(dur * SR); tt = np.arange(n) / SR; ph = 2 * np.pi * f * tt
    if kind == "saw":
        y = np.zeros(n)
        for h in range(1, 9): y += np.sin(h * ph * (1 + det)) / h
        return y * 0.5
    if kind == "tri": return np.arcsin(np.sin(ph)) * 2 / np.pi
    return np.sin(ph)

def note_hz(m): return 440.0 * 2 ** ((m - 69) / 12)

def pad(midis, at, dur, gain=0.09, bright=1800):
    for m in midis:
        for det in (-0.003, 0.0, 0.003):
            y = tone(note_hz(m), dur + 1.5, "saw", det)
            y = lp(y, bright) * env(len(y), 1.2, 0.0, 1.0, 1.3)
            add(y, at, rng.uniform(-0.6, 0.6), gain / 3)

def kick(at, g=0.9):
    n = int(0.35 * SR); tt = np.arange(n) / SR; f = 45 + 110 * np.exp(-tt * 28)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 9); add(y, at, 0, g)

def hat(at, g=0.18, open_=False):
    n = int((0.25 if open_ else 0.05) * SR); y = rng.standard_normal(n)
    y = y - lp(y, 6000); y *= np.exp(-np.linspace(0, 9 if not open_ else 5, n)); add(y, at, 0.25, g)

def pluck(m, at, g=0.22, pan=0.0):
    y = tone(note_hz(m), 0.9, "tri") + 0.4 * tone(note_hz(m) * 2, 0.9)
    y *= np.exp(-np.linspace(0, 7, len(y))); add(y, at, pan, g)

def whoosh(at, dur=0.9, g=0.3, up=True):
    n = int(dur * SR); y = rng.standard_normal(n)
    cut = np.exp(np.linspace(np.log(300), np.log(7000), n)) if up else np.exp(np.linspace(np.log(7000), np.log(300), n))
    out = np.zeros(n); s = 0.0
    for i in range(n):
        a = np.exp(-2 * np.pi * cut[i] / SR); s = (1 - a) * y[i] + a * s; out[i] = s
    out *= np.sin(np.linspace(0, np.pi, n)) ** 2; add(out * 3, at - (dur * 0.7 if up else 0), 0, g)

def bell(m, at, g=0.25):
    f = note_hz(m); y = np.zeros(int(2.8 * SR)); tt = np.arange(len(y)) / SR
    for k, a in ((1, 1), (2.76, 0.5), (5.4, 0.3), (8.93, 0.15)): y += a * np.sin(2 * np.pi * f * k * tt) * np.exp(-tt * (2.2 + k * 0.8))
    add(y, at, 0, g * 0.5)

def glitch(at, g=0.25):
    for k in range(7):
        n = int(rng.uniform(0.012, 0.04) * SR); y = rng.standard_normal(n) * np.exp(-np.linspace(0, 5, n)); add(y, at + k * rng.uniform(0.04, 0.12), rng.uniform(-1, 1), g)

# ---- 0-5 s: the hook. A tired low drone, a flat stutter.
for k, m in enumerate((33, 40)):
    y = lp(tone(note_hz(m), 6, "saw"), 260) * env(int(6 * SR), 0.8, 0, 1.0, 1.0); add(y, 0, -0.2 + 0.4 * k, 0.28)
trem = 0.6 + 0.4 * np.sin(2 * np.pi * 5.5 * t)
glitch(0.15, 0.22); glitch(1.9, 0.18); glitch(3.4, 0.2)
for b in (2.4, 3.2, 4.0, 4.5): kick(b, 0.55)
# ---- 5-18 s: find and clean (A minor, steady pulse that builds)
beat = 60 / 96
pad([57, 60, 64], 5.0, 6.0, 0.10); pad([53, 57, 60], 11.0, 7.2, 0.10)
for k in range(int((18.0 - 5.0) / beat)):
    a = 5.0 + k * beat
    if a >= 18: break
    kick(a, 0.55 if a < 11 else 0.8)
    hat(a + beat / 2, 0.10 if a < 11 else 0.15)
    if a > 11: hat(a, 0.07)
for k, m in enumerate((57, 64, 60, 67, 64, 69, 67, 72)):
    pluck(m, 11.0 + k * beat / 2, 0.10, -0.3 + 0.08 * k)
# ---- 18-26 s: after (relief, C major, brighter)
pad([60, 64, 67, 71], 18.0, 4.6, 0.12, 2800); pad([55, 59, 62, 67], 22.6, 4.0, 0.12, 2800)
for k in range(int(8.0 / beat)):
    a = 18.0 + k * beat; kick(a, 0.7); hat(a + beat / 2, 0.12)
    if k % 2 == 1: hat(a, 0.06, True)
arp = (72, 76, 79, 84, 79, 76, 72, 67)
for k in range(int(8.0 / (beat / 2))): pluck(arp[k % len(arp)] - (5 if k > 15 else 0), 18.0 + k * beat / 2, 0.13, -0.5 + (k % 8) * 0.14)
# ---- 26-32 s: phone (groove, lighter)
pad([57, 60, 64, 69], 26.0, 6.2, 0.10, 3200)
for k in range(int(6.0 / beat)):
    a = 26.0 + k * beat; kick(a, 0.6); hat(a + beat / 2, 0.13)
    pluck([69, 72, 76, 72][k % 4], a + beat * 0.75, 0.10, 0.4)
# ---- 32-37.5 s: end card (open chord, one long tail)
pad([60, 67, 72, 76], 32.0, 5.6, 0.14, 3600)
# ---- effects tied to the cuts
for at in (5.0, 11.0, 18.0, 26.0, 32.0): whoosh(at, 0.7, 0.25)
bell(84, 17.1, 0.30); bell(88, 17.3, 0.25)           # "16 apps switched off"
kick(18.0, 1.0); bell(91, 18.0, 0.22)                 # "Two minutes later."
bell(96, 28.0, 0.20)                                  # "Phone connected"
kick(32.0, 1.0); bell(79, 32.1, 0.25); bell(91, 32.4, 0.18)
# ---- reverb (synthetic room), master, fade
ir = rng.standard_normal(int(1.4 * SR)) * np.exp(-np.linspace(0, 6.5, int(1.4 * SR)))
def reverb(x, mix=0.22):
    n = 1 << int(np.ceil(np.log2(len(x) + len(ir)))); y = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)[:len(x)]
    return x * (1 - mix) + y / np.max(np.abs(y)) * np.max(np.abs(x)) * mix * 1.6
L = reverb(L); R = reverb(R)
fade = np.ones(N); fi = int(0.4 * SR); fo = int(2.0 * SR); fade[:fi] = np.linspace(0, 1, fi); fade[-fo:] = np.linspace(1, 0, fo)
L *= fade; R *= fade
pk = max(np.max(np.abs(L)), np.max(np.abs(R))); g = 0.89 / pk; L *= g; R *= g
data = (np.stack([L, R], 1) * 32767).astype(np.int16)
with wave.open(sys.argv[1], "wb") as w: w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data.tobytes())
print("wrote", sys.argv[1], round(DUR, 1), "s")
