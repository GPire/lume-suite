// Drives the real TV Doctor page against the real TV and records it (CDP screencast) for the video.
import puppeteer from "puppeteer-core";
import fs from "fs";
const [url, out] = process.argv.slice(2);
fs.rmSync(out, { recursive: true, force: true }); fs.mkdirSync(out, { recursive: true });
const b = await puppeteer.launch({ executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", headless: "new",
  args: ["--window-size=1280,800", "--hide-scrollbars"], defaultViewport: { width: 1280, height: 800, deviceScaleFactor: 1.5 } });
const p = await b.newPage();
await p.evaluateOnNewDocument(() => { Object.defineProperty(navigator, "language", { get: () => "en-US" }); Object.defineProperty(navigator, "languages", { get: () => ["en-US", "en"] }); });
const c = await p.createCDPSession(); const frames = []; let n = 0;
c.on("Page.screencastFrame", async (f) => { const name = `f${String(n++).padStart(5, "0")}.jpg`; fs.writeFileSync(`${out}/${name}`, Buffer.from(f.data, "base64")); frames.push({ name, t: f.metadata.timestamp }); try { await c.send("Page.screencastFrameAck", { sessionId: f.sessionId }); } catch (e) {} });
await p.goto(url, { waitUntil: "networkidle2" });
await c.send("Page.startScreencast", { format: "jpeg", quality: 88, maxWidth: 1920, maxHeight: 1200, everyNthFrame: 1 });
const waitH1 = (re, ms) => p.waitForFunction((s) => new RegExp(s, "i").test(document.body.innerText), { timeout: ms }, re);
await waitH1("I recognised your TV", 120000);
await new Promise(r => setTimeout(r, 3000));
await p.click("#a");
console.log("clicked Clean now at frame", n);
await p.waitForSelector("#h", { timeout: 600000 }); await new Promise(r => setTimeout(r, 800));
await new Promise(r => setTimeout(r, 5500));
await c.send("Page.stopScreencast");
fs.writeFileSync(`${out}/frames.json`, JSON.stringify(frames));
console.log("frames", frames.length, "seconds", (frames.at(-1).t - frames[0].t).toFixed(1));
const txt = await p.evaluate(() => document.body.innerText); fs.writeFileSync(`${out}/result.txt`, txt);
await b.close();
