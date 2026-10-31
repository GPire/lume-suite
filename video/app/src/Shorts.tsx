import React from "react";
import {
  AbsoluteFill,
  Audio,
  Img,
  OffthreadVideo,
  Sequence,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import {
  AFTER,
  AMBER,
  BEFORE,
  Chip,
  Count,
  DIM,
  GREEN,
  INK,
  PushIn,
  RED,
  SceneEnd,
  TvFrame,
  Vignette,
  Words,
  useFade,
} from "./Promo";

// Three 15-second videos for Reels, Shorts and TikTok. One arc each: a hook that stops the thumb (0-2.5 s), the pain made
// specific (2.3-5 s), the turn (5-6.3 s), the reward (6.3-13 s: real footage, a number, a feeling), the promise and the address
// (13-15 s). Red light before the turn, amber after it. A hit on every cut. English on screen; no hashtags, no emoji.

const font = "Inter, sans-serif";
const CLAMP = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const T_TURN = 150;
const T_PAY = 190;
const T_CTA = 390;

const ShortBg: React.FC = () => {
  const frame = useCurrentFrame();
  const k = interpolate(frame, [T_TURN, T_PAY], [0, 1], CLAMP);
  const flash = interpolate(frame, [T_TURN + 36, T_TURN + 40, T_TURN + 52], [0, 0.32, 0], CLAMP);
  return (
    <AbsoluteFill style={{ backgroundColor: "#06060a" }}>
      <AbsoluteFill
        style={{
          background: `radial-gradient(950px 820px at 22% 8%, rgba(255,69,58,${0.24 * (1 - k)}), transparent 62%),
                       radial-gradient(950px 820px at 22% 8%, rgba(255,159,10,${0.26 * k}), transparent 62%),
                       radial-gradient(1000px 900px at 88% 96%, rgba(94,92,230,0.2), transparent 60%)`,
        }}
      />
      <AbsoluteFill style={{ backgroundColor: "#fff", opacity: flash }} />
    </AbsoluteFill>
  );
};

const Phase: React.FC<{ from: number; to: number; children: React.ReactNode }> = ({ from, to, children }) => (
  <Sequence layout="none" from={from} durationInFrames={to - from}>
    <PhaseBody dur={to - from}>{children}</PhaseBody>
  </Sequence>
);
const PhaseBody: React.FC<{ dur: number; children: React.ReactNode }> = ({ dur, children }) => {
  const o = useFade(dur, 6, 8);
  return <AbsoluteFill style={{ opacity: o }}>{children}</AbsoluteFill>;
};

const Center: React.FC<{ children: React.ReactNode; top?: number }> = ({ children, top = 560 }) => (
  <div style={{ position: "absolute", top, left: 56, right: 56 }}>{children}</div>
);

const Line: React.FC<{ text: string; size?: number; color?: string; top: number; start?: number }> = ({
  text,
  size = 58,
  color = DIM,
  top,
  start = 6,
}) => {
  const frame = useCurrentFrame();
  const o = interpolate(frame, [start, start + 14], [0, 1], CLAMP);
  return (
    <div style={{ position: "absolute", top, left: 56, right: 56, textAlign: "center", fontFamily: font, fontWeight: 700, fontSize: size, color, opacity: o, letterSpacing: -0.5, lineHeight: 1.15 }}>
      {text}
    </div>
  );
};

/** A word that lands with weight: big, one at a time (the reward needs room). */
const Pop: React.FC<{ text: string; at: number; dur: number; color?: string; size?: number; top?: number }> = ({
  text,
  at,
  dur,
  color = INK,
  size = 170,
  top = 1180,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = spring({ frame: frame - at, fps, config: { damping: 11, stiffness: 140 } });
  const out = interpolate(frame, [at + dur - 6, at + dur], [1, 0], CLAMP);
  if (frame < at || frame > at + dur) return null;
  return (
    <div
      style={{
        position: "absolute",
        top,
        left: 0,
        right: 0,
        textAlign: "center",
        fontFamily: font,
        fontWeight: 800,
        fontSize: size,
        color,
        letterSpacing: -size * 0.03,
        scale: String(0.6 + p * 0.4),
        opacity: p * out,
      }}
    >
      {text}
    </div>
  );
};

const Note: React.FC<{ text: string }> = ({ text }) => (
  <div style={{ position: "absolute", bottom: 210, left: 60, right: 60, textAlign: "center", fontFamily: font, fontWeight: 500, fontSize: 26, color: "rgba(255,255,255,0.5)" }}>{text}</div>
);

const Tv: React.FC<{ top: number; glow: string; children: React.ReactNode; dur: number; to?: number }> = ({ top, glow, children, dur, to = 1.05 }) => (
  <div style={{ position: "absolute", top, left: 40 }}>
    <PushIn duration={dur} to={to}>
      <TvFrame glow={glow}>{children}</TvFrame>
    </PushIn>
  </div>
);

const Fill: React.FC<{ src: string; from?: number; rate?: number }> = ({ src, from = 0, rate = 1 }) => (
  <OffthreadVideo src={staticFile(src)} muted startFrom={from} playbackRate={rate} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
);

const EndCard: React.FC = () => (
  <Sequence layout="none" from={T_CTA} durationInFrames={60}>
    <SceneEnd />
  </Sequence>
);

// ================================================================ 1. Lume: the home screen

export const ShortLume: React.FC = () => (
  <AbsoluteFill>
    <ShortBg />
    <Phase from={0} to={80}>
      <Center top={620}>
        <Words text="Your TV home screen is an ad." size={128} start={2} accent={RED} accentWords={["ad"]} />
      </Center>
    </Phase>
    <Phase from={70} to={160}>
      <Line text="Sponsored rows. Lag. Apps you never opened." size={60} color={INK} top={170} />
      <Tv top={560} glow="rgba(255,69,58,0.35)" dur={90} to={1.04}>
        <Fill src="clipA.mp4" from={6} />
      </Tv>
      <div style={{ position: "absolute", top: 1230, left: 0, right: 0, display: "flex", justifyContent: "center", alignItems: "baseline", gap: 22 }}>
        <Count to={BEFORE.janky} start={14} color={RED} size={200} />
        <div style={{ fontFamily: font, fontWeight: 700, fontSize: 48, color: INK }}>of frames dropped</div>
      </div>
    </Phase>
    <Phase from={150} to={198}>
      <Center top={760}>
        <Words text="Same stick. Now watch." size={120} start={2} accentWords={["watch"]} />
      </Center>
    </Phase>
    <Phase from={190} to={400}>
      <Tv top={300} glow="rgba(255,159,10,0.38)" dur={210} to={1.07}>
        <Sequence layout="none" from={0} durationInFrames={110}>
          <Fill src="clipB.mp4" from={30} />
        </Sequence>
        <Sequence layout="none" from={110}>
          <Fill src="cc.mp4" from={45} />
        </Sequence>
      </Tv>
      <Pop text="Calm." at={24} dur={52} color={INK} />
      <Pop text="Smooth." at={78} dur={52} color={GREEN} />
      <Pop text="Yours." at={132} dur={70} color={AMBER} />
      <div style={{ position: "absolute", top: 1480, left: 0, right: 0, display: "flex", justifyContent: "center" }}>
        <Chip text={`${AFTER.janky}% of frames dropped, from ${BEFORE.janky}%`} color={GREEN} size={36} />
      </div>
      <Note text="Same Fire TV Stick, same buttons. Measured with gfxinfo." />
    </Phase>
    <EndCard />
    <Vignette />
    <Audio src={staticFile("short_lume.wav")} />
  </AbsoluteFill>
);

// ================================================================ 2. Gallery: the screensaver

export const ShortGallery: React.FC = () => {
  const frame = useCurrentFrame();
  const hours = Math.round(interpolate(frame, [76, 120], [0, 20], CLAMP));
  return (
    <AbsoluteFill>
      <ShortBg />
      <Phase from={0} to={80}>
        <Center top={560}>
          <Words text="Your TV is a black rectangle 20 hours a day." size={112} start={2} accent={RED} accentWords={["black"]} />
        </Center>
      </Phase>
      <Phase from={70} to={160}>
        <Line text="Dead glass on your wall." size={68} color={INK} top={200} />
        <div style={{ position: "absolute", top: 520, left: 40 }}>
          <TvFrame glow="rgba(255,69,58,0.25)">
            <AbsoluteFill style={{ background: "linear-gradient(135deg, #0a0a0e 0%, #14141a 60%, #0a0a0e 100%)" }} />
            <AbsoluteFill style={{ background: "linear-gradient(115deg, transparent 38%, rgba(255,255,255,0.05) 46%, transparent 54%)" }} />
          </TvFrame>
        </div>
        <div style={{ position: "absolute", top: 1230, left: 0, right: 0, display: "flex", justifyContent: "center", alignItems: "baseline", gap: 18 }}>
          <div style={{ fontFamily: font, fontWeight: 800, fontSize: 200, color: RED, letterSpacing: -8 }}>{hours}</div>
          <div style={{ fontFamily: font, fontWeight: 700, fontSize: 52, color: INK }}>hours a day, off</div>
        </div>
      </Phase>
      <Phase from={150} to={198}>
        <Center top={760}>
          <Words text="Make it a gallery." size={128} start={2} accentWords={["gallery"]} />
        </Center>
      </Phase>
      <Phase from={190} to={400}>
        <Tv top={330} glow="rgba(255,159,10,0.4)" dur={210} to={1.08}>
          <Sequence layout="none" from={0} durationInFrames={92}>
            <Img src={staticFile("gal1.jpg")} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          </Sequence>
          <Sequence layout="none" from={92}>
            <Fill src="gal2.mp4" from={30} />
          </Sequence>
        </Tv>
        <Pop text="36 masterpieces." at={20} dur={86} size={104} color={INK} top={1090} />
        <Pop text="Monet. Homer. Hokusai." at={100} dur={106} size={80} color={AMBER} top={1110} />
        <div style={{ position: "absolute", top: 1450, left: 0, right: 0, display: "flex", justifyContent: "center", gap: 14 }}>
          <Chip text="Bright by day" size={32} />
          <Chip text="Warm at dusk" size={32} />
          <Chip text="Dark at night" size={32} />
        </div>
        <Note text="Lume Gallery. Public-domain paintings, shown with their frames." />
      </Phase>
      <EndCard />
      <Vignette />
      <Audio src={staticFile("short_gallery.wav")} />
    </AbsoluteFill>
  );
};

// ================================================================ 3. AirPlay: Lume Cast

const PhoneShell: React.FC<{ children: React.ReactNode; top: number; left: number; w?: number; h?: number }> = ({ children, top, left, w = 420, h = 840 }) => (
  <div
    style={{
      position: "absolute",
      top,
      left,
      width: w,
      height: h,
      borderRadius: 56,
      background: "#101015",
      border: "3px solid rgba(255,255,255,0.22)",
      boxShadow: "0 40px 100px rgba(0,0,0,0.7), 0 0 90px rgba(255,159,10,0.18)",
      overflow: "hidden",
    }}
  >
    <div style={{ position: "absolute", top: 12, left: w / 2 - 55, width: 110, height: 24, borderRadius: 12, background: "#000" }} />
    {children}
  </div>
);

const AirPlayIcon: React.FC<{ size: number; color: string }> = ({ size, color }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round">
    <path d="M6 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-1" />
    <path d="M12 14l5 6H7z" />
  </svg>
);

export const ShortAirPlay: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const pick = spring({ frame: frame - 212, fps, config: { damping: 200 } });
  return (
    <AbsoluteFill>
      <ShortBg />
      <Phase from={0} to={80}>
        <Center top={600}>
          <Words text="You play a song. The TV shows nothing." size={116} start={2} accent={RED} accentWords={["nothing"]} />
        </Center>
      </Phase>
      <Phase from={70} to={160}>
        <Line text="No words. No cover. Just sound from a speaker." size={58} color={INK} top={180} />
        <PhoneShell top={440} left={330} w={420} h={860}>
          <div style={{ position: "absolute", top: 90, left: 36, right: 36 }}>
            <div style={{ width: "100%", aspectRatio: "1", borderRadius: 28, background: "linear-gradient(135deg, #e2536b, #2e3a8c)" }} />
            <div style={{ fontFamily: font, fontWeight: 800, fontSize: 34, color: INK, marginTop: 26 }}>Sing With Me</div>
            <div style={{ fontFamily: font, fontWeight: 500, fontSize: 26, color: DIM }}>Sample Song</div>
            <div style={{ height: 6, borderRadius: 3, background: "rgba(255,255,255,0.18)", marginTop: 26 }}>
              <div style={{ width: "34%", height: 6, borderRadius: 3, background: INK }} />
            </div>
          </div>
        </PhoneShell>
        <div style={{ position: "absolute", top: 1400, left: 0, right: 0, display: "flex", justifyContent: "center" }}>
          <Chip text="TV: black screen" color={RED} size={38} />
        </div>
      </Phase>
      <Phase from={150} to={198}>
        <Center top={700}>
          <Words text="Tap AirPlay. Pick the TV." size={120} start={2} accentWords={["AirPlay"]} />
        </Center>
      </Phase>
      <Phase from={190} to={400}>
        <Sequence layout="none" from={0} durationInFrames={62}>
          <PhoneShell top={300} left={330} w={420} h={860}>
            <div style={{ position: "absolute", top: 110, left: 30, right: 30 }}>
              <div style={{ display: "flex", justifyContent: "center", marginBottom: 14 }}>
                <AirPlayIcon size={74} color={AMBER} />
              </div>
              <div style={{ fontFamily: font, fontWeight: 800, fontSize: 34, color: INK, textAlign: "center", marginBottom: 26 }}>AirPlay</div>
              {["Kitchen speaker", "Lume Cast"].map((n, i) => {
                const on = i === 1 ? pick : 0;
                return (
                  <div
                    key={n}
                    style={{
                      fontFamily: font,
                      fontWeight: 700,
                      fontSize: 30,
                      color: INK,
                      padding: "20px 24px",
                      borderRadius: 22,
                      marginBottom: 14,
                      background: `rgba(255,159,10,${0.1 + 0.55 * on})`,
                      border: "1px solid rgba(255,255,255,0.15)",
                      display: "flex",
                      justifyContent: "space-between",
                    }}
                  >
                    <span>{n}</span>
                    <span style={{ opacity: on }}>On</span>
                  </div>
                );
              })}
              <div style={{ fontFamily: font, fontSize: 22, color: "rgba(255,255,255,0.4)", textAlign: "center", marginTop: 26 }}>Illustration</div>
            </div>
          </PhoneShell>
        </Sequence>
        <Sequence layout="none" from={62}>
          <Tv top={300} glow="rgba(255,159,10,0.4)" dur={140} to={1.07}>
            <Fill src="cast_en.mp4" from={36} />
          </Tv>
          <Pop text="Words, in time." at={14} dur={58} size={104} color={INK} top={1020} />
          <Pop text="Cover. Colour. Calm." at={76} dur={60} size={84} color={AMBER} top={1040} />
        </Sequence>
        <div style={{ position: "absolute", top: 1500, left: 0, right: 0, display: "flex", justifyContent: "center" }}>
          <Chip text="Free AirPlay receiver for your Fire TV" size={32} />
        </div>
        <Note text="Lume Cast. The song on screen is a sample made for this demo." />
      </Phase>
      <EndCard />
      <Vignette />
      <Audio src={staticFile("short_airplay.wav")} />
    </AbsoluteFill>
  );
};
