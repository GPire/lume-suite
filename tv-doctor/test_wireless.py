"""Android 11+ wireless debugging: a TV that announces only its adb-tls port is still found, and pairing refuses bad input."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tvdoctor as T, tvdoctor_app as A, discovery as D, shutil
# no port 5555, announced port 37123, pairing port 41555
A.discovery.discover = lambda s=2.5: {"192.168.77.9": {"name": "Salotto", "model": "Chromecast", "maker": "", "family": "android",
                                                       "adb_connect_port": 37123, "adb_pair_port": 41555}}
A.classify = lambda ip: None
A.port_open = lambda ip, port: False
A.arp_names = lambda: {}
A.local_prefix = lambda: "192.168.77"
seen = []
A.T.probe = lambda serial, wait=4, wireless=False: (seen.append((serial, wireless)) or "pair")
A.ThreadPoolExecutor = A.ThreadPoolExecutor
res = A.find_tvs()
mine = [r for r in res if r["serial"] == "192.168.77.9:37123"]
assert mine and mine[0]["state"] == "pair" and mine[0]["pairPort"] == 41555 and mine[0]["wireless"], res
assert ("192.168.77.9:37123", True) in seen
# pairing refuses nonsense before it runs anything
real_which = shutil.which
shutil.which = lambda n: "/bin/true"
for port, code in (("abc", "123456"), ("41555", "12345"), ("41555", "12345a")):
    assert A.pair_tv("192.168.77.9", port, code) == {"ok": False, "error": "code"}, (port, code)
shutil.which = lambda n: None
assert A.pair_tv("192.168.77.9", "41555", "123456")["error"] == "tools"
shutil.which = real_which
print("TUTTO OK wireless")
