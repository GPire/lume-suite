import puppeteer from "puppeteer-core";
const url=process.argv[2];
const b=await puppeteer.launch({executablePath:"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",headless:"new",args:["--window-size=1280,860"],defaultViewport:{width:1280,height:860,deviceScaleFactor:1.5}});
const p=await b.newPage();await p.evaluateOnNewDocument(()=>{Object.defineProperty(navigator,"language",{get:()=>"en-US"});Object.defineProperty(navigator,"languages",{get:()=>["en-US","en"]})});await p.goto(url,{waitUntil:"networkidle2"});
for(let i=0;i<7;i++){await new Promise(r=>setTimeout(r,3500));
 const s=await p.evaluate(()=>({h1:[...document.querySelectorAll("h1,h2")].map(e=>e.innerText).join(" | "),btn:[...document.querySelectorAll("button")].map(e=>(e.id||"-")+":"+e.innerText.slice(0,40)).join(" ; ")}));
 console.log(i,JSON.stringify(s));}
await b.close();
