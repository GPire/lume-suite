import { Composition } from "remotion";
import { Promo } from "./Promo";
import { PromoWide } from "./PromoWide";

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition id="Promo" component={Promo} durationInFrames={1110} fps={30} width={1080} height={1920} />
      <Composition id="PromoWide" component={PromoWide} durationInFrames={1110} fps={30} width={1920} height={1080} />
    </>
  );
};
