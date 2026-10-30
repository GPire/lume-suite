"""Finds TVs and streamers on the local network from what they announce themselves, with no extra programs.

Two announcements cover nearly every TV sold: mDNS (Bonjour: Chromecast/Google TV, AirPlay, Fire TV, Samsung, LG...)
and SSDP (UPnP: maker and model in a small XML file). Both are read-only, local-network only, and ask nothing of the TV
beyond answering a "who is there?" question. The result is a hint (name, maker, model, family) per address; TV Doctor
still checks the TV itself before it does anything.
"""
import re
import socket
import struct
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

MDNS_GROUP, MDNS_PORT = "224.0.0.251", 5353
SSDP_GROUP, SSDP_PORT = "239.255.255.250", 1900

# Service types that TVs and streamers advertise, and what each one says about the device.
SERVICES = ["_googlecast._tcp.local", "_airplay._tcp.local", "_raop._tcp.local", "_androidtvremote2._tcp.local",
            "_spotify-connect._tcp.local", "_amzn-wplay._tcp.local", "_samsungmsf._tcp.local", "_lg-smart-device._tcp.local",
            "_sonos._tcp.local", "_roku-rcp._tcp.local", "_hap._tcp.local", "_homekit._tcp.local"]

# Maker words -> the family TV Doctor works with. Order matters: first match wins.
FAMILY_BY_WORD = [("samsung", "tizen"), ("lg electronics", "webos"), ("lge", "webos"), ("webos", "webos"), ("roku", "roku"),
                  ("amazon", "fire"), ("fire tv", "fire"), ("aftm", "fire"), ("vidaa", "vidaa"), ("hisense", "vidaa"),
                  ("sony", "android"), ("bravia", "android"), ("tcl", "android"), ("philips", "android"), ("xiaomi", "android"),
                  ("nvidia", "android"), ("shield", "android"), ("chromecast", "android"), ("google", "android"),
                  ("panasonic", "android"), ("sharp", "android"), ("toshiba", "android"), ("skyworth", "android"),
                  ("android tv", "android"), ("google tv", "android")]


def family_of(*words):
    text = " ".join(w for w in words if w).lower()
    for key, fam in FAMILY_BY_WORD:
        if key in text:
            return fam
    return None


# ---------------------------------------------------------------- mDNS

def _qname(name):
    return b"".join(bytes([len(p)]) + p.encode() for p in name.split(".") if p) + b"\0"


def build_query(names):
    """One DNS query asking for the PTR records of each service type (QU bit set: answers may come straight back to us)."""
    header = struct.pack(">HHHHHH", 0, 0, len(names), 0, 0, 0)
    return header + b"".join(_qname(n) + struct.pack(">HH", 12, 0x8001) for n in names)


def _read_name(data, pos):
    labels, jumped, end = [], False, pos
    for _ in range(64):
        if pos >= len(data):
            break
        length = data[pos]
        if length == 0:
            pos += 1
            break
        if length & 0xC0 == 0xC0:
            ptr = ((length & 0x3F) << 8) | data[pos + 1]
            if not jumped:
                end = pos + 2
            pos, jumped = ptr, True
            continue
        labels.append(data[pos + 1:pos + 1 + length].decode("utf-8", "replace"))
        pos += 1 + length
    return ".".join(labels), (end if jumped else pos)


def parse_response(data):
    """The records of one mDNS packet as (name, type, value): PTR/SRV/TXT/A, enough to name a device."""
    out = []
    try:
        _, flags, qd, an, ns, ar = struct.unpack(">HHHHHH", data[:12])
        pos = 12
        for _ in range(qd):
            _, pos = _read_name(data, pos)
            pos += 4
        for _ in range(an + ns + ar):
            name, pos = _read_name(data, pos)
            rtype, _, _, rdlen = struct.unpack(">HHIH", data[pos:pos + 10])
            pos += 10
            rd = data[pos:pos + rdlen]
            if rtype == 12:
                out.append((name, "PTR", _read_name(data, pos)[0]))
            elif rtype == 33 and len(rd) >= 7:
                out.append((name, "SRV", _read_name(data, pos + 6)[0]))
            elif rtype == 16:
                txt, i = {}, 0
                while i < len(rd):
                    n = rd[i]
                    item = rd[i + 1:i + 1 + n].decode("utf-8", "replace")
                    if "=" in item:
                        k, v = item.split("=", 1)
                        txt[k.lower()] = v
                    i += 1 + n
                out.append((name, "TXT", txt))
            elif rtype == 1 and len(rd) == 4:
                out.append((name, "A", socket.inet_ntoa(rd)))
            pos += rdlen
    except (struct.error, IndexError):
        pass
    return out


def mdns_scan(seconds=3.0):
    """address -> {name, model, maker, services}, from the services devices announce."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.settimeout(0.3)
    try:
        sock.bind(("", 0))
        sock.sendto(build_query(SERVICES), (MDNS_GROUP, MDNS_PORT))
    except OSError:
        return {}
    records, deadline, resent = [], time.time() + seconds, False
    while time.time() < deadline:
        try:
            data, (ip, _) = sock.recvfrom(9000)
        except socket.timeout:
            if not resent:
                sock.sendto(build_query(SERVICES), (MDNS_GROUP, MDNS_PORT)); resent = True
            continue
        except OSError:
            break
        records.append((ip, parse_response(data)))
    sock.close()
    return summarize_mdns(records)


def summarize_mdns(records):
    found = {}
    for ip, recs in records:
        d = found.setdefault(ip, {"name": "", "model": "", "maker": "", "services": set(), "_instance": ""})
        for name, kind, value in recs:
            svc = re.search(r"(_[a-z0-9-]+\._(?:tcp|udp)\.local)$", name)
            if svc:
                d["services"].add(svc.group(1))
            if kind == "TXT":
                d["name"] = d["name"] or value.get("fn") or value.get("name") or ""
                d["model"] = d["model"] or value.get("md") or value.get("model") or value.get("am") or ""
                d["maker"] = d["maker"] or value.get("manufacturer") or ""
            elif kind == "PTR" and "._" in value:
                d["_instance"] = d["_instance"] or value.split("._", 1)[0]
    for d in found.values():
        d["name"] = d["name"] or d.pop("_instance")   # the name the device wrote itself beats the one in the service label
        d.pop("_instance", None)
    return {ip: v for ip, v in found.items() if v["services"] or v["name"]}


# ---------------------------------------------------------------- SSDP / UPnP

def ssdp_scan(seconds=3.0):
    """address -> {name, maker, model}: M-SEARCH, then each answering device's description XML."""
    msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n").encode()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(0.4)
    locations = {}
    try:
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
        for _ in range(2):
            sock.sendto(msg, (SSDP_GROUP, SSDP_PORT))
        deadline = time.time() + seconds
        while time.time() < deadline:
            try:
                data, (ip, _) = sock.recvfrom(4096)
            except socket.timeout:
                continue
            m = re.search(rb"(?im)^location:\s*(\S+)", data)
            if m:
                locations.setdefault(ip, m.group(1).decode())
    except OSError:
        pass
    finally:
        sock.close()

    def describe(item):
        ip, url = item
        try:
            xml = urllib.request.urlopen(url, timeout=1.5).read(60000).decode("utf-8", "replace")
        except Exception:
            return ip, None
        return ip, parse_description(xml)

    with ThreadPoolExecutor(16) as ex:
        return {ip: d for ip, d in ex.map(describe, locations.items()) if d}


def parse_description(xml):
    g = lambda tag: (re.search(r"<%s>([^<]*)</%s>" % (tag, tag), xml) or [0, ""])[1].strip()
    d = {"name": g("friendlyName"), "maker": g("manufacturer"), "model": g("modelName") or g("modelNumber")}
    return d if any(d.values()) else None


# ---------------------------------------------------------------- both together

def discover(seconds=3.0):
    """address -> {name, maker, model, family, how}. Merges both announcements; family is None when it is not a TV we know."""
    with ThreadPoolExecutor(2) as ex:
        m, s = ex.submit(mdns_scan, seconds), ex.submit(ssdp_scan, seconds)
        mdns, ssdp = m.result(), s.result()
    out = {}
    for ip in set(mdns) | set(ssdp):
        a, b = mdns.get(ip, {}), ssdp.get(ip, {})
        name = a.get("name") or b.get("name") or ""
        maker = b.get("maker") or a.get("maker") or ""
        model = b.get("model") or a.get("model") or ""
        services = " ".join(sorted(a.get("services", [])))
        family = family_of(maker, model, name) or ("fire" if "_amzn-wplay" in services else None) \
            or ("android" if "_androidtvremote2" in services or "_googlecast" in services and not maker else None)
        out[ip] = {"name": name, "maker": maker, "model": model, "family": family,
                   "how": "+".join(x for x, y in (("mDNS", a), ("SSDP", b)) if y), "services": services}
    return out


if __name__ == "__main__":
    import json
    print(json.dumps(discover(), indent=1, ensure_ascii=False))
