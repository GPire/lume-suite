"""Discovery: parsing of mDNS and UPnP answers, and the family guess. Packets are built by hand (logic, not a real network)."""
import os, struct, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import discovery as D

def name(n): return D._qname(n)
def rr(n, t, rdata, ttl=120): return name(n) + struct.pack(">HHIH", t, 1, ttl, len(rdata)) + rdata
txt = lambda *kv: b"".join(bytes([len(x)]) + x.encode() for x in kv)

# a Chromecast-style answer: PTR -> instance, TXT with fn/md, A record
inst = "Salotto-abc._googlecast._tcp.local"
pkt = struct.pack(">HHHHHH", 0, 0x8400, 0, 3, 0, 0)
pkt += rr("_googlecast._tcp.local", 12, name(inst))
pkt += rr(inst, 16, txt("fn=Salotto", "md=Chromecast Ultra", "id=abc"))
pkt += rr("host.local", 1, bytes([192, 168, 1, 50]))
recs = D.parse_response(pkt)
assert ("host.local", "A", "192.168.1.50") in recs, recs
s = D.summarize_mdns([("192.168.1.50", recs)])
assert s["192.168.1.50"]["name"] == "Salotto" and s["192.168.1.50"]["model"] == "Chromecast Ultra", s
assert "_googlecast._tcp.local" in s["192.168.1.50"]["services"], s

# a truncated or hostile packet must not raise
for bad in (b"", b"\x00" * 5, pkt[:20], b"\xff" * 64):
    D.parse_response(bad)

# a pointer loop in a name must end
loop = struct.pack(">HHHHHH", 0, 0x8400, 0, 1, 0, 0) + b"\xc0\x0c" + struct.pack(">HHIH", 12, 1, 1, 2) + b"\xc0\x0c"
D.parse_response(loop)

# UPnP description
x = "<root><device><friendlyName>[TV] Samsung 7 Series</friendlyName><manufacturer>Samsung Electronics</manufacturer><modelName>UE55NU7400</modelName></device></root>"
d = D.parse_description(x)
assert d == {"name": "[TV] Samsung 7 Series", "maker": "Samsung Electronics", "model": "UE55NU7400"}, d
assert D.parse_description("<root></root>") is None

# family guesses
for words, fam in [(("Samsung Electronics", "UE55NU7400", ""), "tizen"), (("LG Electronics", "OLED55C1", ""), "webos"),
                   (("Roku", "3800X", ""), "roku"), (("Sony", "BRAVIA 4K", ""), "android"), (("", "", "Salotto Fire TV"), "fire"),
                   (("Hisense", "55A6", ""), "vidaa"), (("", "", "Sonos Beam"), None)]:
    assert D.family_of(*words) == fam, (words, D.family_of(*words), fam)
print("discovery OK")
