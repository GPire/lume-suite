# -*- coding: utf-8 -*-
"""Builds the landing page as real, separate pages per language (docs/index.html in English, docs/<code>/index.html) from
tools/landing/template.html and tools/landing/i18n.json, plus sitemap.xml and robots.txt. Search engines index what is in the
HTML, not what a script writes later, so each language has to be a page of its own. Run: python3 tools/landing/build_landing.py"""
import json, os, re, html
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
DOCS = os.path.join(ROOT, "docs")
BASE = "https://gpire.github.io/lume-suite/"
tpl = open(os.path.join(os.path.dirname(__file__), "template.html"), encoding="utf-8").read()
d = json.load(open(os.path.join(os.path.dirname(__file__), "i18n.json"), encoding="utf-8"))
NAMES = {"en": "English", "it": "Italiano", "es": "Español", "de": "Deutsch", "fr": "Français", "pt": "Português"}
LOCALE = {"en": "en_US", "it": "it_IT", "es": "es_ES", "de": "de_DE", "fr": "fr_FR", "pt": "pt_PT"}
OTHERS = ["it", "es", "de", "fr", "pt"]

def url(code): return BASE if code == "en" else BASE + code + "/"

def build(code):
    s = tpl
    dic = d["en"] if code == "it" else dict(d["en"], **d[code])
    if code == "it": dic = dict(d["en"], **{})  # only for the JS labels below
    meta = d[code] if code in d else d["en"]
    title = meta.get("mTitle") or d["en"]["mTitle"]; desc = meta.get("mDesc") or d["en"]["mDesc"]
    prefix = "" if code == "en" else "../"
    # 1. static text for every data-t element (Italian is the template's own text)
    if code != "it":
        def sub(m):
            key = m.group(3)
            return m.group(1) + dic.get(key, m.group(4)) + m.group(5) if key in dic else m.group(0)
        s = re.sub(r'(<(\w+)[^>]*?data-t="([A-Za-z0-9_]+)"[^>]*>)(.*?)(</\2>)', lambda m: (m.group(1) + dic[m.group(3)] + m.group(5)) if m.group(3) in dic else m.group(0), s, flags=re.S)
    # 2. head: language, title, description, canonical, hreflang, social cards, structured data
    s = s.replace('<html lang="it">', '<html lang="%s">' % code, 1)
    s = re.sub(r"<title>.*?</title>", "<title>%s</title>" % html.escape(title), s, count=1, flags=re.S)
    s = re.sub(r'<meta property="og:[^>]*>', "", s)
    s = re.sub(r'<meta name="description"[^>]*>', "", s)
    alts = "".join('<link rel="alternate" hreflang="%s" href="%s">' % (c, url(c)) for c in ["en"] + OTHERS) + '<link rel="alternate" hreflang="x-default" href="%s">' % BASE
    ld = {"@context": "https://schema.org", "@type": "SoftwareApplication", "name": "Lume Suite (TV Doctor, Lume, Lume Cast)",
          "applicationCategory": "UtilitiesApplication", "operatingSystem": "Android TV, Google TV, Fire OS",
          "description": desc, "inLanguage": code, "url": url(code), "image": BASE + "img/banner.png",
          "offers": {"@type": "Offer", "price": "0", "priceCurrency": "EUR"},
          "license": "https://github.com/GPire/lume-suite/blob/main/LICENSE.md", "codeRepository": "https://github.com/GPire/lume-suite"}
    head = ('<meta name="description" content="%s"><link rel="canonical" href="%s">%s'
            '<meta property="og:type" content="website"><meta property="og:site_name" content="Lume Suite"><meta property="og:title" content="%s">'
            '<meta property="og:description" content="%s"><meta property="og:url" content="%s"><meta property="og:image" content="%simg/banner.png">'
            '<meta property="og:locale" content="%s"><meta name="twitter:card" content="summary_large_image"><meta name="twitter:title" content="%s">'
            '<meta name="twitter:description" content="%s"><meta name="twitter:image" content="%simg/banner.png">'
            '<script type="application/ld+json">%s</script>') % (
        html.escape(desc, True), url(code), alts, html.escape(title, True), html.escape(desc, True), url(code), BASE, LOCALE[code],
        html.escape(title, True), html.escape(desc, True), BASE, json.dumps(ld, ensure_ascii=False))
    s = s.replace("<title>", head + "<title>", 1)
    # 3. relative links and the page's own script
    s = s.replace('location.replace("tv/")', 'location.replace("%stv/")' % prefix)
    a = s.index("const IT=")
    b = s.index("const ua=navigator.userAgent")
    labels = {k: (d["it"] if code == "it" else dic).get(k) for k in ("dlWin", "dlMac", "dlLin", "copy", "copied", "shareL")}
    labels = {k: v for k, v in labels.items() if v}
    block = 'const lang=%s;\n' % json.dumps(labels, ensure_ascii=False)
    if code == "en":   # the English root sends visitors whose browser speaks another of our languages to their own page
        block += ('const _c=(navigator.language||"en").toLowerCase().slice(0,2);'
                  'if(%s.indexOf(_c)>=0&&!/[?&]l=/.test(location.search))location.replace(_c+"/"+location.search);\n') % json.dumps(OTHERS)
    s = s[:a] + block + s[b:]
    # 4. language links
    links = " · ".join('<a href="%s%s">%s</a>' % (prefix if c != "en" else prefix, ("" if c == "en" else c + "/") + ("?l=en" if c == "en" else ""), NAMES[c]) if c != code else "<b>%s</b>" % NAMES[c] for c in ["en"] + OTHERS)
    links = links.replace('href="../?l=en"', 'href="../?l=en"').replace('href="?l=en"', 'href="./?l=en"')
    s = s.replace("</main>", '<p class="dim" style="margin-top:22px;font-size:15px" lang="en">%s</p>\n</main>' % links, 1)
    out = os.path.join(DOCS, "index.html") if code == "en" else os.path.join(DOCS, code, "index.html")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, "w", encoding="utf-8").write(s)
    return out

for c in ["en"] + OTHERS: print(build(c))
urls = [BASE, BASE + "tv/"] + [url(c) for c in OTHERS]
open(os.path.join(DOCS, "sitemap.xml"), "w", encoding="utf-8").write('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' + "".join("<url><loc>%s</loc></url>\n" % u for u in urls) + "</urlset>\n")
open(os.path.join(DOCS, "robots.txt"), "w", encoding="utf-8").write("User-agent: *\nAllow: /\nSitemap: %ssitemap.xml\n" % BASE)
