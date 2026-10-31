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
  BEFORE,
  Backdrop,
  Bars,
  CardCrop,
  Chip,
  Count,
  DIM,
  Finger,
  GREEN,
  INK,
  OFF_LIST,
  PushIn,
  RED,
  SceneEnd,
  Sub,
  TvFrame,
  Vignette,
  Words,
  useFade,
} from "./Promo";

// The 16:9 cut for YouTube: words on the left, the screen on the right. Same footage, same numbers, same score.

const COL_L = { position: "absolute" as const, left: 90, width: 760 };
const COL_R = 960; // the screens start here (880 wide)

const Left: React.FC<{ top: number; children: React.ReactNode }> = ({ top, children }) => (
  <div style={{ ...COL_L, top, display: "flex", flexDirection: "column", alignItems: "flex-start", gap: 26 }}>{children}</div>
);

// Words centres its lines; here the headline is left aligned.
const Head: React.FC<{ text: string; accent?: string; accentWords?: string[]; size?: number }> = ({ text, accent, accentWords, size = 92 }) => (
  <div style={{ textAlign: "left", width: "100%" }}>
    <div style={{ display: "flex", justifyContent: "flex-start" }}>
      <div style={{ textAlign: "left" }}>
        <Words text={text} size={size} accent={accent} accentWords={accentWords} />
      </div>
    </div>
  </div>
);

const W1 = 150;
const SceneHookW: React.FC = () => {
  const o = useFade(W1, 4, 8);
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <Left top={150}>
        <Head text="This Fire TV Stick was dying." accent={RED} accentWords={["dying"]} />
        <div style={{ display: "flex", alignItems: "baseline", gap: 22, marginTop: 20 }}>
          <Count to={BEFORE.janky} start={34} color={RED} size={170} />
          <Sub text="of frames dropped" start={40} size={42} color={INK} />
        </div>
        <Bars median={BEFORE.median} p90={BEFORE.p90} seed={11} start={48} height={150} width={760} />
      </Left>
      <div style={{ position: "absolute", top: 292, left: COL_R }}>
        <PushIn duration={W1} to={1.04}>
          <TvFrame glow="rgba(255,69,58,0.35)" width={880}>
            <OffthreadVideo src={staticFile("clipA.mp4")} muted startFrom={6} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          </TvFrame>
        </PushIn>
      </div>
    </AbsoluteFill>
  );
};

const W2 = 180;
const Card: React.FC<{ from: number; rate: number; height: number; k?: number; layout?: boolean }> = ({ from, rate, height, k = 0.88 }) => (
  <div style={{ width: 1000 * k, height: height * k, position: "relative" }}>
    <div style={{ position: "absolute", left: 0, top: 0, width: 1000, height, scale: String(k), transformOrigin: "0 0" }}>
      <CardCrop from={from} rate={rate} height={height} />
    </div>
  </div>
);

const SceneFindW: React.FC = () => {
  const o = useFade(W2);
  const frame = useCurrentFrame();
  const chip = interpolate(frame, [92, 108], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <Left top={250}>
        <Head text="One app finds your TV." accentWords={["finds"]} />
        <div style={{ opacity: chip, display: "flex", flexDirection: "column", gap: 12, alignItems: "flex-start" }}>
          <Chip text="Amazon Fire TV Stick 4K Max" size={30} />
          <Chip text="Android 11 · 1.7 GB RAM" size={30} />
        </div>
      </Left>
      <div style={{ position: "absolute", top: 80, left: COL_R }}>
        <Sequence layout="none" from={0} durationInFrames={76}>
          <Card from={4} rate={6.1} height={1100} />
        </Sequence>
        <Sequence layout="none" from={76}>
          <Card from={19.5} rate={1} height={1100} />
        </Sequence>
      </div>
    </AbsoluteFill>
  );
};

const W3 = 210;
const SceneCleanW: React.FC = () => {
  const o = useFade(W3);
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const done = spring({ frame: frame - 160, fps, config: { damping: 14, stiffness: 120 } });
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <Left top={170}>
        <Head text="It switches off what slows it down." accentWords={["switches"]} size={84} />
        <Sub text="Nothing is deleted. Undo any time." start={44} size={40} />
        <div
          style={{
            marginTop: 30,
            fontFamily: "Inter, sans-serif",
            fontWeight: 800,
            fontSize: 54,
            color: GREEN,
            opacity: done,
            scale: String(0.88 + done * 0.12),
            transformOrigin: "0 50%",
          }}
        >
          16 apps switched off. 0 deleted.
        </div>
      </Left>
      <div style={{ position: "absolute", top: 60, left: COL_R }}>
        <Card from={23} rate={4.1} height={470} />
      </div>
      <div
        style={{
          position: "absolute",
          top: 520,
          left: COL_R,
          width: 880,
          display: "grid",
          gridTemplateColumns: "1fr 1fr",
          gap: "6px 22px",
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
                fontFamily: "Inter, sans-serif",
                fontWeight: 500,
                fontSize: 24,
                color: off > 0.5 ? DIM : INK,
                opacity: p,
                padding: "5px 12px",
                borderRadius: 12,
                background: "rgba(255,255,255,0.06)",
              }}
            >
              <span>{name}</span>
              <span style={{ width: 56, height: 30, borderRadius: 15, background: off > 0.5 ? "rgba(255,255,255,0.18)" : GREEN, position: "relative" }}>
                <span style={{ position: "absolute", top: 3, left: 3 + (1 - off) * 26, width: 24, height: 24, borderRadius: 12, background: "#fff" }} />
              </span>
            </div>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};

const W4 = 240;
const SceneAfterW: React.FC = () => {
  const o = useFade(W4);
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <Left top={160}>
        <Head text="Two minutes later." accent={GREEN} accentWords={["later"]} size={104} />
        <div style={{ display: "flex", alignItems: "baseline", gap: 22, marginTop: 14 }}>
          <Count to={AFTER.janky} start={26} color={GREEN} size={170} />
          <Sub text="of frames dropped" start={34} size={42} color={INK} />
        </div>
        <Sub text="Same stick. Same buttons." start={40} size={38} />
        <Bars median={AFTER.median} p90={AFTER.p90} seed={23} start={44} height={150} width={760} />
        <Chip text={`Median frame ${BEFORE.median} ms → ${AFTER.median} ms`} color={GREEN} size={30} />
      </Left>
      <div style={{ position: "absolute", top: 292, left: COL_R }}>
        <PushIn duration={W4} to={1.06}>
          <TvFrame glow="rgba(48,209,88,0.35)" width={880}>
            <OffthreadVideo src={staticFile("clipB.mp4")} muted startFrom={30} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          </TvFrame>
        </PushIn>
      </div>
    </AbsoluteFill>
  );
};

const W5 = 180;
const ScenePhoneW: React.FC = () => {
  const o = useFade(W5);
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const rise = spring({ frame: frame - 18, fps, config: { damping: 200 } });
  return (
    <AbsoluteFill style={{ opacity: o }}>
      <Left top={110}>
        <Head text="Your phone is the remote." accentWords={["phone"]} size={84} />
        <Chip text="Swipe. Tap. Hold for Menu." size={30} />
      </Left>
      <div
        style={{
          position: "absolute",
          top: 470,
          left: 250,
          width: 300,
          height: 590,
          borderRadius: 44,
          background: "#101015",
          border: "3px solid rgba(255,255,255,0.22)",
          boxShadow: "0 40px 100px rgba(0,0,0,0.7), 0 0 90px rgba(255,159,10,0.22)",
          overflow: "hidden",
          opacity: rise,
          translate: `0px ${(1 - rise) * 120}px`,
        }}
      >
        <Img src={staticFile("phone-remote.jpg")} style={{ width: 300, position: "absolute", top: 30, left: 0 }} />
        <div style={{ position: "absolute", top: 8, left: 110, width: 80, height: 16, borderRadius: 8, background: "#000" }} />
        <div style={{ position: "absolute", inset: 0, scale: String(300 / 380), transformOrigin: "0 0", width: 380, height: 760 }}>
          <Finger start={52} path={[[190, 300], [250, 340], [190, 400], [130, 360], [190, 300]]} />
          <Finger start={120} path={[[130, 330], [250, 330]]} />
        </div>
      </div>
      <div style={{ position: "absolute", top: 292, left: COL_R }}>
        <PushIn duration={W5} to={1.05}>
          <TvFrame glow="rgba(255,159,10,0.3)" width={880}>
            <OffthreadVideo src={staticFile("clipC.mp4")} muted startFrom={104} playbackRate={2} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          </TvFrame>
        </PushIn>
      </div>
    </AbsoluteFill>
  );
};

export const PromoWide: React.FC = () => (
  <AbsoluteFill>
    <Backdrop />
    <Sequence name="Hook" from={0} durationInFrames={W1}>
      <SceneHookW />
    </Sequence>
    <Sequence name="Find" from={150} durationInFrames={W2}>
      <SceneFindW />
    </Sequence>
    <Sequence name="Clean" from={330} durationInFrames={W3}>
      <SceneCleanW />
    </Sequence>
    <Sequence name="After" from={540} durationInFrames={W4}>
      <SceneAfterW />
    </Sequence>
    <Sequence name="Phone" from={780} durationInFrames={W5}>
      <ScenePhoneW />
    </Sequence>
    <Sequence name="End" from={960} durationInFrames={150}>
      <SceneEnd />
    </Sequence>
    <Vignette />
    <Audio src={staticFile("score.wav")} />
  </AbsoluteFill>
);
