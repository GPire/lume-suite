import React from "react";
import {
  AbsoluteFill,
  Audio,
  OffthreadVideo,
  Easing,
  Img,
  Sequence,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { loadFont } from "@remotion/google-fonts/Inter";

const { fontFamily } = loadFont("normal", { weights: ["500", "700", "800"], subsets: ["latin"] });

const AMBER = "#ff9f0a";
const GREEN = "#30d158";
const RED = "#ff453a";
const INK = "#f5f5f7";
const DIM = "#9a9aa6";
const smooth = Easing.bezier(0.16, 1, 0.3, 1);

// Real numbers, measured on a Fire TV Stick 4K Max with the same key presses (dumpsys gfxinfo), 2026-10-31.
const BEFORE = { janky: 87, median: 28, p90: 42 }; // the stock Fire TV home, factory state
const AFTER = { janky: 13, median: 9, p90: 17 }; // Lume, same stick, same keys

// The 16 packages TV Doctor switched off in the recorded run (Amazon's own extras).
const OFF_LIST = [
  "bueller.music", "bueller.photos", "dummy.music", "gamehub",
  "minitv.android.app", "ods.kindleconnect", "shoptv.client", "shoptv.firetv.client",
  "smarthomemapviewapp", "sneakpeek", "storm.lightning.tutorial", "tahoe",
  "tmm.tutorial", "tv.releasenotes", "tv.website_launcher", "printservice.recommendation",
];

// ---------------------------------------------------------------- helpers

const useFade = (duration: number, inFrames = 7, outFrames = 9) => {
  const frame = useCurrentFrame();
  return interpolate(frame, [0, inFrames, duration - outFrames, duration], [0, 1, 1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
};

const Words: React.FC<{
  text: string;
  start?: number;
  size?: number;
  color?: string;
  accent?: string;
  accentWords?: string[];
  weight?: number;
}> = ({ text, start = 4, size = 100, color = INK, accent = AMBER, accentWords = [], weight = 800 }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return (
    <div
      style={{
        fontFamily,
        fontWeight: weight,
        fontSize: size,
        lineHeight: 1.04,
        letterSpacing: -size * 0.025,
        textAlign: "center",
        color,
        textWrap: "balance",
      }}
    >
      {text.split(" ").map((w, i) => {
        const p = spring({ frame: frame - start - i * 3, fps, config: { damping: 200 } });
        const clean = w.replace(/[.,]/g, "");
        return (
          <span
            key={`${w}-${i}`}
            style={{
              display: "inline-block",
              marginRight: size * 0.26,
              opacity: p,
              translate: `0px ${(1 - p) * 46}px`,
              filter: `blur(${(1 - p) * 12}px)`,
              color: accentWords.includes(clean) ? accent : color,
            }}
          >
            {w}
          </span>
        );
      })}
    </div>
  );
};

const Sub: React.FC<{ text: string; start?: number; size?: number; color?: string }> = ({
  text,
  start = 30,
  size = 46,
  color = DIM,
}) => {
  const frame = useCurrentFrame();
  const o = interpolate(frame, [start, start + 14], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <div style={{ fontFamily, fontWeight: 500, fontSize: size, color, textAlign: "center", opacity: o, letterSpacing: -0.5 }}>
      {text}
    </div>
  );
};

const Backdrop: React.FC = () => {
  const frame = useCurrentFrame();
  const drift = Math.sin(frame / 90);
  return (
    <AbsoluteFill style={{ backgroundColor: "#06060a" }}>
      <AbsoluteFill
        style={{
          background: `radial-gradient(900px 760px at ${22 + drift * 5}% 8%, rgba(255,159,10,0.20), transparent 62%),
                       radial-gradient(1000px 900px at ${90 - drift * 6}% 92%, rgba(94,92,230,0.22), transparent 60%)`,
        }}
      />
    </AbsoluteFill>
  );
};

const Vignette: React.FC = () => (
  <AbsoluteFill
    style={{
      background: "radial-gradient(ellipse at center, transparent 55%, rgba(0,0,0,0.55) 100%)",
      pointerEvents: "none",
    }}
  />
);

const TvFrame: React.FC<{ children: React.ReactNode; glow: string; width?: number }> = ({ children, glow, width = 1000 }) => (
  <div
    style={{
      position: "relative",
      width,
      height: (width * 9) / 16,
      borderRadius: 28,
      overflow: "hidden",
      border: "2px solid rgba(255,255,255,0.16)",
      boxShadow: `0 40px 100px rgba(0,0,0,0.65), 0 0 140px ${glow}`,
    }}
  >
    {children}
    <div style={{ position: "absolute", inset: 0, boxShadow: "inset 0 0 90px rgba(0,0,0,0.38)" }} />
  </div>
);

/** A slow push-in: the camera creeping toward the screen for the whole scene. */
const PushIn: React.FC<{ children: React.ReactNode; duration: number; to?: number }> = ({ children, duration, to = 1.07 }) => {
  const frame = useCurrentFrame();
  const s = interpolate(frame, [0, duration], [1, to], { easing: Easing.bezier(0.3, 0, 0.7, 1) });
  return <div style={{ scale: String(s), transformOrigin: "50% 50%" }}>{children}</div>;
};

// frame-time bars: one bar is one frame; lognormal around the measured median and 90th percentile
const mulberry32 = (a: number) => () => {
  a |= 0;
  a = (a + 0x6d2b79f5) | 0;
  let t = Math.imul(a ^ (a >>> 15), 1 | a);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const frameTimes = (median: number, p90: number, seed: number, n = 46) => {
  const sigma = Math.log(p90 / median) / 1.2816;
  const r = mulberry32(seed);
  return Array.from({ length: n }, () => {
    const u1 = Math.max(r(), 1e-6);
    const u2 = r();
    const z = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    return median * Math.exp(sigma * z);
  });
};

const Bars: React.FC<{ median: number; p90: number; seed: number; start?: number; height?: number }> = ({
  median,
  p90,
  seed,
  start = 30,
  height = 230,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const ft = frameTimes(median, p90, seed);
  const max = 70;
  const w = 1000 / ft.length;
  return (
    <div style={{ position: "relative", width: 1000, height }}>
      <div
        style={{
          position: "absolute",
          left: 0,
          right: 0,
          bottom: (16.7 / max) * height,
          height: 2,
          background: "rgba(255,255,255,0.35)",
        }}
      />
      <div
        style={{
          position: "absolute",
          right: 0,
          bottom: (16.7 / max) * height + 8,
          fontFamily,
          fontSize: 26,
          fontWeight: 500,
          color: "rgba(255,255,255,0.6)",
        }}
      >
        16.7 ms: smooth at 60 fps
      </div>
      {ft.map((v, i) => {
        const p = spring({ frame: frame - start - i * 0.7, fps, config: { damping: 18, stiffness: 140 } });
        const h = (Math.min(v, max) / max) * height * p;
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: i * w + 1,
              bottom: 0,
              width: w - 3,
              height: h,
              borderRadius: 3,
              background: v > 16.7 ? RED : GREEN,
              opacity: 0.92,
            }}
          />
        );
      })}
    </div>
  );
};

const Count: React.FC<{ to: number; start: number; size?: number; color: string; suffix?: string }> = ({
  to,
  start,
  size = 190,
  color,
  suffix = "%",
}) => {
  const frame = useCurrentFrame();
  const v = interpolate(frame, [start, start + 36], [0, to], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: smooth });
  return (
    <div style={{ fontFamily, fontWeight: 800, fontSize: size, color, letterSpacing: -size * 0.04, lineHeight: 1 }}>
      {Math.round(v)}
      <span style={{ fontSize: size * 0.5, letterSpacing: 0 }}>{suffix}</span>
    </div>
  );
};

const Chip: React.FC<{ text: string; color?: string; size?: number }> = ({ text, color = INK, size = 34 }) => (
  <div
    style={{
      fontFamily,
      fontWeight: 700,
      fontSize: size,
      color,
      padding: "10px 24px",
      borderRadius: 40,
      background: "rgba(255,255,255,0.09)",
      border: "1px solid rgba(255,255,255,0.14)",
      whiteSpace: "nowrap",
    }}
  >
    {text}
  </div>
);

const Foot: React.FC<{ text: string }> = ({ text }) => (
  <div style={{ fontFamily, fontWeight: 500, fontSize: 26, color: "rgba(255,255,255,0.45)", textAlign: "center" }}>{text}</div>
);

// ---------------------------------------------------------------- scenes

const D1 = 150;
const SceneHook: React.FC = () => {
  const frame = useCurrentFrame();
  const o = useFade(D1, 4, 8);
  const glitch = frame < 14 ? Math.sin(frame * 7.3) * (14 - frame) * 0.9 : 0;
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <div style={{ position: "absolute", top: 150, left: 40, right: 40, translate: `${glitch}px 0px` }}>
        <Words text="This Fire TV Stick was dying." start={2} size={104} accent={RED} accentWords={["dying"]} />
      </div>
      <div style={{ position: "absolute", top: 600, left: 40 }}>
        <PushIn duration={D1} to={1.04}>
          <TvFrame glow="rgba(255,69,58,0.35)">
            <OffthreadVideo src={staticFile("clipA.mp4")} muted startFrom={6} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          </TvFrame>
        </PushIn>
      </div>
      <div style={{ position: "absolute", top: 1230, left: 40, right: 40, display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
        <Count to={BEFORE.janky} start={34} color={RED} />
        <Sub text="of frames dropped scrolling the stock home" start={40} size={44} color={INK} />
      </div>
      <div style={{ position: "absolute", top: 1560, left: 40 }}>
        <Bars median={BEFORE.median} p90={BEFORE.p90} seed={11} start={48} height={170} />
      </div>
    </AbsoluteFill>
  );
};

const D2 = 180;
const CardCrop: React.FC<{ from: number; rate: number; height: number }> = ({ from, rate, height }) => {
  const s = 1000 / 1040;
  return (
    <div
      style={{
        position: "relative",
        width: 1000,
        height,
        borderRadius: 28,
        overflow: "hidden",
        border: "2px solid rgba(255,255,255,0.16)",
        boxShadow: "0 40px 100px rgba(0,0,0,0.6), 0 0 120px rgba(10,132,255,0.28)",
        background: "#0b0d12",
      }}
    >
      <OffthreadVideo
        src={staticFile("tvd_screen.mp4")}
        muted
        startFrom={from * 30}
        playbackRate={rate}
        style={{ position: "absolute", width: 1920, height: 1200, left: -440 * s, top: -22 * s, transformOrigin: "0 0", scale: String(s) }}
      />
    </div>
  );
};

const SceneFind: React.FC = () => {
  const o = useFade(D2);
  const frame = useCurrentFrame();
  const chip = interpolate(frame, [92, 108], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <div style={{ position: "absolute", top: 150, left: 40, right: 40 }}>
        <Words text="One app finds your TV." size={104} accentWords={["finds"]} />
      </div>
      <div style={{ position: "absolute", top: 520, left: 40 }}>
        {/* the search runs fast, then the screen that matters is held: "I recognised your TV, 16 useless apps" */}
        <Sequence layout="none" from={0} durationInFrames={76}>
          <CardCrop from={4} rate={6.1} height={1000} />
        </Sequence>
        <Sequence layout="none" from={76}>
          <CardCrop from={19.5} rate={1} height={1000} />
        </Sequence>
      </div>
      <div style={{ position: "absolute", top: 1560, left: 0, right: 0, display: "flex", justifyContent: "center", gap: 14, opacity: chip }}>
        <Chip text="Amazon Fire TV Stick 4K Max" />
        <Chip text="Android 11" />
        <Chip text="1.7 GB RAM" />
      </div>
    </AbsoluteFill>
  );
};

const D3 = 210;
const SceneClean: React.FC = () => {
  const o = useFade(D3);
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const done = spring({ frame: frame - 160, fps, config: { damping: 14, stiffness: 120 } });
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <div style={{ position: "absolute", top: 140, left: 40, right: 40 }}>
        <Words text="It switches off what slows it down." size={96} accentWords={["switches"]} />
        <div style={{ height: 22 }} />
        <Sub text="Nothing is deleted. Undo any time." start={44} size={44} />
      </div>
      <div style={{ position: "absolute", top: 560, left: 40 }}>
        <CardCrop from={23} rate={4.1} height={600} />
      </div>
      <div
        style={{
          position: "absolute",
          top: 1190,
          left: 40,
          width: 1000,
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: "8px 28px",
          opacity: 1 - done * 0.0,
        }}
      >
        {OFF_LIST.map((name, i) => {
          const p = spring({ frame: frame - 28 - i * 6, fps, config: { damping: 200 } });
          const off = interpolate(frame, [46 + i * 6, 58 + i * 6], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
          return (
            <div
              key={name}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                fontFamily,
                fontWeight: 500,
                fontSize: 27,
                color: INK,
                opacity: p,
                translate: `0px ${(1 - p) * 16}px`,
                padding: "6px 14px",
                borderRadius: 14,
                background: "rgba(255,255,255,0.06)",
              }}
            >
              <span style={{ color: off > 0.5 ? DIM : INK }}>{name}</span>
              <span
                style={{
                  width: 64,
                  height: 34,
                  borderRadius: 17,
                  background: off > 0.5 ? "rgba(255,255,255,0.18)" : GREEN,
                  position: "relative",
                }}
              >
                <span
                  style={{
                    position: "absolute",
                    top: 4,
                    left: 4 + (1 - off) * 30,
                    width: 26,
                    height: 26,
                    borderRadius: 13,
                    background: "#fff",
                  }}
                />
              </span>
            </div>
          );
        })}
      </div>
      <div
        style={{
          position: "absolute",
          top: 1640,
          left: 0,
          right: 0,
          textAlign: "center",
          fontFamily,
          fontWeight: 800,
          fontSize: 62,
          color: GREEN,
          opacity: done,
          scale: String(0.86 + done * 0.14),
        }}
      >
        16 apps switched off. 0 deleted.
      </div>
    </AbsoluteFill>
  );
};

const D4 = 240;
const SceneAfter: React.FC = () => {
  const o = useFade(D4);
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <div style={{ position: "absolute", top: 150, left: 40, right: 40 }}>
        <Words text="Two minutes later." size={112} accent={GREEN} accentWords={["later"]} />
      </div>
      <div style={{ position: "absolute", top: 480, left: 40 }}>
        <PushIn duration={D4} to={1.06}>
          <TvFrame glow="rgba(48,209,88,0.35)">
            <OffthreadVideo src={staticFile("clipB.mp4")} muted startFrom={30} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          </TvFrame>
        </PushIn>
      </div>
      <div style={{ position: "absolute", top: 1140, left: 40, right: 40, display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
        <Count to={AFTER.janky} start={26} color={GREEN} />
        <Sub text="of frames dropped. Same stick. Same buttons." start={34} size={44} color={INK} />
      </div>
      <div style={{ position: "absolute", top: 1470, left: 40 }}>
        <Bars median={AFTER.median} p90={AFTER.p90} seed={23} start={44} height={170} />
      </div>
      <div style={{ position: "absolute", top: 1670, left: 0, right: 0, display: "flex", justifyContent: "center", gap: 14 }}>
        <Chip text={`Median frame ${BEFORE.median} ms → ${AFTER.median} ms`} color={GREEN} size={32} />
      </div>
    </AbsoluteFill>
  );
};

const D5 = 180;
const Finger: React.FC<{ path: [number, number][]; start: number }> = ({ path, start }) => {
  const frame = useCurrentFrame();
  const t = interpolate(frame, [start, start + 70], [0, path.length - 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const i = Math.min(Math.floor(t), path.length - 2);
  const f = t - i;
  const x = path[i][0] + (path[i + 1][0] - path[i][0]) * f;
  const y = path[i][1] + (path[i + 1][1] - path[i][1]) * f;
  const vis = interpolate(frame, [start - 6, start, start + 66, start + 76], [0, 1, 1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <div
      style={{
        position: "absolute",
        left: x - 36,
        top: y - 36,
        width: 72,
        height: 72,
        borderRadius: 36,
        background: "radial-gradient(circle, rgba(255,179,64,0.9), rgba(255,159,10,0.15) 70%)",
        boxShadow: "0 0 40px rgba(255,159,10,0.6)",
        opacity: vis,
      }}
    />
  );
};

const ScenePhone: React.FC = () => {
  const o = useFade(D5);
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const rise = spring({ frame: frame - 18, fps, config: { damping: 200 } });
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <div style={{ position: "absolute", top: 140, left: 40, right: 40 }}>
        <Words text="Your phone is the remote." size={100} accentWords={["phone"]} />
      </div>
      <div style={{ position: "absolute", top: 440, left: 90 }}>
        <PushIn duration={D5} to={1.05}>
          <TvFrame glow="rgba(255,159,10,0.3)" width={900}>
            <OffthreadVideo src={staticFile("clipC.mp4")} muted startFrom={104} playbackRate={2} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          </TvFrame>
        </PushIn>
      </div>
      <div
        style={{
          position: "absolute",
          top: 880,
          left: 350,
          width: 380,
          height: 760,
          borderRadius: 54,
          background: "#101015",
          border: "3px solid rgba(255,255,255,0.22)",
          boxShadow: "0 40px 100px rgba(0,0,0,0.7), 0 0 90px rgba(255,159,10,0.22)",
          overflow: "hidden",
          opacity: rise,
          translate: `0px ${(1 - rise) * 160}px`,
        }}
      >
        <Img src={staticFile("phone-remote.jpg")} style={{ width: 380, position: "absolute", top: 38, left: 0 }} />
        <div style={{ position: "absolute", top: 10, left: 140, width: 100, height: 20, borderRadius: 10, background: "#000" }} />
        <Finger start={52} path={[[190, 300], [250, 340], [190, 400], [130, 360], [190, 300]]} />
        <Finger start={120} path={[[130, 330], [250, 330]]} />
      </div>
      <div style={{ position: "absolute", top: 1680, left: 0, right: 0, display: "flex", justifyContent: "center" }}>
        <Chip text="Swipe. Tap. Hold for Menu." size={36} />
      </div>
    </AbsoluteFill>
  );
};

const D6 = 150;
const Star: React.FC<{ size: number; color: string }> = ({ size, color }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={1.8} strokeLinejoin="round" strokeLinecap="round">
    <path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1.1 5.9L12 16.9l-5.3 2.8 1.1-5.9-4.3-4.1 5.9-.8z" />
  </svg>
);
const SceneEnd: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const o = interpolate(frame, [0, 8], [0, 1], { extrapolateRight: "clamp" });
  const orb = spring({ frame: frame - 2, fps, config: { damping: 12, stiffness: 90 } });
  const pulse = 1 + Math.sin(frame / 10) * 0.025;
  const url = spring({ frame: frame - 44, fps, config: { damping: 200 } });
  return (
    <AbsoluteFill style={{ opacity: o, alignItems: "center", justifyContent: "center" }}>
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 36, translate: "0px -40px" }}>
        <div
          style={{
            width: 300,
            height: 300,
            borderRadius: 150,
            background: "radial-gradient(circle at 38% 32%, #ffd27a, #ff9f0a 45%, #c2500a)",
            boxShadow: "0 0 160px rgba(255,159,10,0.6), inset 0 0 30px rgba(255,255,255,0.25)",
            scale: String(orb * pulse),
          }}
        />
        <div style={{ fontFamily, fontWeight: 800, fontSize: 124, color: INK, letterSpacing: -4 }}>Lume Suite</div>
        <div style={{ fontFamily, fontWeight: 500, fontSize: 54, color: INK, textAlign: "center" }}>
          Free. Open source. Undo any time.
        </div>
        <div
          style={{
            fontFamily,
            fontWeight: 800,
            fontSize: 56,
            color: "#1a1000",
            padding: "20px 44px",
            borderRadius: 60,
            background: "linear-gradient(180deg, #ffb340, #ff9f0a)",
            boxShadow: "0 12px 50px rgba(255,159,10,0.45)",
            opacity: url,
            scale: String(0.9 + url * 0.1),
          }}
        >
          gpire.github.io/lume-suite
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 14, fontFamily, fontWeight: 700, fontSize: 44, color: DIM, opacity: url }}>
          <Star size={50} color={AMBER} /> Star it on GitHub
        </div>
      </div>
      <div style={{ position: "absolute", bottom: 230, left: 0, right: 0 }}>
        <Foot text="Real screens and measurements from a Fire TV Stick 4K Max. Not affiliated with Amazon." />
      </div>
    </AbsoluteFill>
  );
};

// ---------------------------------------------------------------- the video

export const Promo: React.FC = () => (
  <AbsoluteFill style={{ fontFamily }}>
    <Backdrop />
    <Sequence name="Hook" from={0} durationInFrames={D1}>
      <SceneHook />
    </Sequence>
    <Sequence name="Find" from={150} durationInFrames={D2}>
      <SceneFind />
    </Sequence>
    <Sequence name="Clean" from={330} durationInFrames={D3}>
      <SceneClean />
    </Sequence>
    <Sequence name="After" from={540} durationInFrames={D4}>
      <SceneAfter />
    </Sequence>
    <Sequence name="Phone" from={780} durationInFrames={D5}>
      <ScenePhone />
    </Sequence>
    <Sequence name="End" from={960} durationInFrames={D6}>
      <SceneEnd />
    </Sequence>
    <Vignette />
    <Audio src={staticFile("score.wav")} />
  </AbsoluteFill>
);
