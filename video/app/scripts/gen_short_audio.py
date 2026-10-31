# -*- coding: utf-8 -*-
"""Scores for the three 15-second videos: a low, uneasy drone and a stutter for the problem, a hit and a bell on the turn, then a
bright pulse for the reward. Mood differs per video: lume (A minor to C), gallery (D to G, slow plucked), airplay (E minor to G, groove).
Cuts (s): 0 hook, 2.3 pain, 5.0 turn, 6.3 reward, 13.0 end card, 15 out. numpy only."""
import numpy as np, wave, sys
SR = 44100; DUR = 15.2; N = int(SR * DUR)
kind, out = sys.argv[1], sys.argv[2]
rng = np.random.default_rng({"lume": 3, "gallery": 5, "airplay": 9}[kind])
L = np.zeros(N); R = np.zeros(N)
ROOT = {"lume": 57, "gallery": 62, "airplay": 52}[kind]       # tonic of the "problem" chord (minor)
BPM = {"lume": 100, "gallery": 76, "airplay": 108}[kind]; beat = 60 / BPM

def add(x, at, pan=0.0, gain=1.0):
    i = int(at * SR); n = min(len(x), N - i)
    if n <= 0: return
    L[i:i + n] += x[:n] * gain * np.cos((pan + 1) * np.pi / 4); R[i:i + n] += x[:n] * gain * np.sin((pan + 1) * np.pi / 4)
def hz(m): return 440.0 * 2 ** ((m - 69) / 12)
def lp(x, fc):
    a = np.exp(-2 * np.pi * fc / SR); y = np.zeros_like(x); s = 0.0
    for i in range(len(x)): s = (1 - a) * x[i] + a * s; y[i] = s
    return y
def tone(f, dur, saw=False, det=0.0):
    n = int(dur * SR); ph = 2 * np.pi * f * np.arange(n) / SR
    if not saw: return np.sin(ph)
    y = np.zeros(n)
    for h in range(1, 9): y += np.sin(h * ph * (1 + det)) / h
    return y * 0.5
def swell(y, a, r):
    e = np.ones(len(y)); na = int(a * SR); nr = int(r * SR); e[:na] = np.linspace(0, 1, na); e[-nr:] *= np.linspace(1, 0, nr); return y * e
def pad(ms, at, dur, g, br):
    for m in ms:
        for d in (-0.003, 0, 0.003): add(swell(lp(tone(hz(m), dur + 1, True, d), br), 0.8, 1.0), at, rng.uniform(-0.6, 0.6), g / 3)
def kick(at, g=0.8):
    n = int(0.33 * SR); tt = np.arange(n) / SR; f = 45 + 110 * np.exp(-tt * 28); add(np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 9), at, 0, g)
def hat(at, g=0.12):
    n = int(0.05 * SR); y = rng.standard_normal(n); y = (y - lp(y, 6000)) * np.exp(-np.linspace(0, 9, n)); add(y, at, 0.25, g)
def pluck(m, at, g=0.2, pan=0.0):
    y = (tone(hz(m), 0.9) * 0.8 + 0.4 * tone(hz(m) * 2, 0.9)) * np.exp(-np.linspace(0, 7, int(0.9 * SR))); add(y, at, pan, g)
def bell(m, at, g=0.25):
    tt = np.arange(int(2.6 * SR)) / SR; y = sum(a * np.sin(2 * np.pi * hz(m) * k * tt) * np.exp(-tt * (2.2 + k * 0.8)) for k, a in ((1, 1), (2.76, 0.5), (5.4, 0.3))); add(y, at, 0, g * 0.5)
def whoosh(at, dur=0.8, g=0.28):
    n = int(dur * SR); y = rng.standard_normal(n); cut = np.exp(np.linspace(np.log(300), np.log(7000), n)); o = np.zeros(n); s = 0.0
    for i in range(n): a = np.exp(-2 * np.pi * cut[i] / SR); s = (1 - a) * y[i] + a * s; o[i] = s
    add(o * 3 * np.sin(np.linspace(0, np.pi, n)) ** 2, at - dur * 0.7, 0, g)
def glitch(at, g=0.22):
    for k in range(6):
        n = int(rng.uniform(0.012, 0.04) * SR); add(rng.standard_normal(n) * np.exp(-np.linspace(0, 5, n)), at + k * rng.uniform(0.04, 0.1), rng.uniform(-1, 1), g)

# 0-5 s: the problem. Low drone, a stutter, a heartbeat that speeds up.
for k, m in enumerate((ROOT - 24, ROOT - 17)): add(swell(lp(tone(hz(m), 6.5, True), 240), 0.6, 1.2), 0, -0.2 + 0.4 * k, 0.3)
glitch(0.2); glitch(2.0, 0.18); glitch(3.8, 0.2)
for b in (2.4, 3.2, 3.9, 4.4, 4.8): kick(b, 0.5)
# 5-6.3 s: the turn. A riser, then silence for a breath, then the hit.
whoosh(6.3, 1.2, 0.34); kick(6.3, 1.0); bell(ROOT + 31, 6.3, 0.3)
# 6.3-13 s: the reward. Major chord, pulse, a little melody.
major = [ROOT + 3, ROOT + 7, ROOT + 10, ROOT + 14]  # relative major colours
pad(major, 6.3, 6.8, 0.15, 3200)
for k in range(int((13.0 - 6.3) / beat)):
    a = 6.3 + k * beat; kick(a, 0.65); hat(a + beat / 2, 0.13)
for k in range(int((13.0 - 6.3) / (beat / 2))):
    pluck(major[k % 4] + 12 + (12 if k % 8 > 4 else 0), 6.3 + k * beat / 2, 0.11, -0.5 + (k % 8) * 0.14)
# 13-15 s: the promise
kick(13.0, 0.9); bell(ROOT + 34, 13.1, 0.28); bell(ROOT + 41, 13.35, 0.2); pad([ROOT + 7, ROOT + 14, ROOT + 19], 13.0, 2.2, 0.14, 3600)
for at in (2.3, 5.0, 13.0): whoosh(at, 0.5, 0.2)
ir = rng.standard_normal(int(1.2 * SR)) * np.exp(-np.linspace(0, 6.5, int(1.2 * SR)))
def reverb(x, mix=0.2):
    n = 1 << int(np.ceil(np.log2(len(x) + len(ir)))); y = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)[:len(x)]
    return x * (1 - mix) + y / np.max(np.abs(y)) * np.max(np.abs(x)) * mix * 1.6
L = reverb(L); R = reverb(R)
fade = np.ones(N); fi = int(0.2 * SR); fo = int(1.2 * SR); fade[:fi] = np.linspace(0, 1, fi); fade[-fo:] = np.linspace(1, 0, fo); L *= fade; R *= fade
g = 0.89 / max(np.max(np.abs(L)), np.max(np.abs(R))); L *= g; R *= g
with wave.open(out, "wb") as w: w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((np.stack([L, R], 1) * 32767).astype(np.int16).tobytes())
print("wrote", out)
