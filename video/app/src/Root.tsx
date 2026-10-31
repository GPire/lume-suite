import { Composition } from "remotion";
import { Promo } from "./Promo";
import { PromoWide } from "./PromoWide";
import { ShortAirPlay, ShortGallery, ShortLume } from "./Shorts";

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition id="Promo" component={Promo} durationInFrames={1110} fps={30} width={1080} height={1920} />
      <Composition id="PromoWide" component={PromoWide} durationInFrames={1110} fps={30} width={1920} height={1080} />
      <Composition id="ShortLume" component={ShortLume} durationInFrames={450} fps={30} width={1080} height={1920} />
      <Composition id="ShortGallery" component={ShortGallery} durationInFrames={450} fps={30} width={1080} height={1920} />
      <Composition id="ShortAirPlay" component={ShortAirPlay} durationInFrames={450} fps={30} width={1080} height={1920} />
    </>
  );
};
