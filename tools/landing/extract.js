const fs=require("fs");const t=fs.readFileSync("template.html","utf8").split("\n");
const pick=n=>t.find(l=>l.startsWith("const "+n+"="));
const code=pick("IT")+"\n"+pick("EN")+"\n"+pick("LANGS")+"\nmodule.exports={IT,EN,LANGS};";
fs.writeFileSync("/tmp/_dicts.js",code);const d=require("/tmp/_dicts.js");
fs.writeFileSync("i18n.json",JSON.stringify({it:d.IT,en:d.EN,...d.LANGS},null,1));
console.log(Object.keys(d.EN).length,Object.keys(d.LANGS));
