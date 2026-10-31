"""A tiny ADB-over-Wi-Fi client in pure Python (standard library only): no adb program to install.

Speaks just what TV Doctor needs: connect, authenticate with an RSA key, run a shell command.
The key is made once and kept in ~/.tvdoctor/adbkey.json. The TV shows the usual "Allow debugging?" box the first time.
"""
import base64, json, os, secrets, socket, struct

A_CNXN, A_AUTH, A_OPEN, A_OKAY, A_CLSE, A_WRTE = 0x4e584e43, 0x48545541, 0x4e45504f, 0x59414b4f, 0x45534c43, 0x45545257
SHA1_PREFIX = bytes.fromhex("3021300906052b0e03021a05000414")
MAXDATA = {}   # per connection: the largest data packet the TV said it takes
KEYFILE = os.path.join(os.path.expanduser("~"), ".tvdoctor", "adbkey.json")


def _is_prime(n, rounds=24):
    if n < 4: return n in (2, 3)
    if n % 2 == 0: return False
    for p in (3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37):
        if n % p == 0: return n == p
    d, s = n - 1, 0
    while d % 2 == 0: d //= 2; s += 1
    for _ in range(rounds):
        x = pow(secrets.randbelow(n - 3) + 2, d, n)
        if x in (1, n - 1): continue
        for _ in range(s - 1):
            x = pow(x, 2, n)
            if x == n - 1: break
        else:
            return False
    return True


def _prime(bits):
    while True:
        c = secrets.randbits(bits) | (1 << (bits - 1)) | (1 << (bits - 2)) | 1
        if _is_prime(c): return c


def load_key():
    if os.path.exists(KEYFILE):
        k = json.load(open(KEYFILE, encoding="utf-8")); return k["n"], k["d"]
    e = 65537
    while True:
        p, q = _prime(1024), _prime(1024)
        phi = (p - 1) * (q - 1)
        if p != q and phi % e: break
    n, d = p * q, pow(e, -1, phi)
    os.makedirs(os.path.dirname(KEYFILE), exist_ok=True)
    with open(KEYFILE, "w", encoding="utf-8") as f: json.dump({"n": n, "d": d}, f)
    os.chmod(KEYFILE, 0o600)
    return n, d


def android_pubkey(n, e=65537, who=b"tvdoctor@computer"):
    """The key in Android's own layout: length, -1/n[0], modulus, R^2 mod n, exponent (little endian)."""
    n0inv = (-pow(n, -1, 1 << 32)) % (1 << 32)
    rr = pow(1 << 2048, 2, n)
    raw = struct.pack("<II", 64, n0inv) + n.to_bytes(256, "little") + rr.to_bytes(256, "little") + struct.pack("<I", e)
    return base64.b64encode(raw) + b" " + who + b"\0"


def sign_token(token, n, d):
    t = SHA1_PREFIX + token
    em = b"\x00\x01" + b"\xff" * (256 - len(t) - 3) + b"\x00" + t
    return pow(int.from_bytes(em, "big"), d, n).to_bytes(256, "big")


def _send(s, cmd, a0, a1, data=b""):
    s.sendall(struct.pack("<6I", cmd, a0, a1, len(data), sum(data) & 0xffffffff, cmd ^ 0xffffffff) + data)


def _recvn(s, n):
    buf = b""
    while len(buf) < n:
        c = s.recv(n - len(buf))
        if not c: raise ConnectionError("la TV ha chiuso la connessione")
        buf += c
    return buf


def _recv(s):
    cmd, a0, a1, ln, _, magic = struct.unpack("<6I", _recvn(s, 24))
    if magic != cmd ^ 0xffffffff: raise ConnectionError("risposta non valida dalla TV")
    return cmd, a0, a1, _recvn(s, ln) if ln else b""


def connect(host, port=5555, timeout=45):
    """Open and authenticate. Waits up to `timeout` s the first time, for the Allow box on the TV."""
    s = socket.create_connection((host, port), 5)
    s.settimeout(timeout)
    n, d = load_key()
    _send(s, A_CNXN, 0x01000000, 4096, b"host::\0")
    tried_sig = False
    while True:
        cmd, a0, a1, data = _recv(s)
        if cmd == A_CNXN:
            MAXDATA[id(s)] = max(4096, min(a1 or 4096, 65536))   # the biggest packet the TV accepts, within what is safe to send
            return s
        if cmd != A_AUTH or a0 != 1: raise ConnectionError("risposta inattesa dalla TV")
        if not tried_sig:
            _send(s, A_AUTH, 2, 0, sign_token(data, n, d)); tried_sig = True
        else:
            _send(s, A_AUTH, 3, 0, android_pubkey(n))


def shell(host, command, port=5555):
    s = connect(host, port)
    try:
        _send(s, A_OPEN, 1, 0, b"shell:" + command.encode() + b"\0")
        out, remote = b"", 0
        while True:
            cmd, a0, a1, data = _recv(s)
            if cmd == A_OKAY: remote = a0
            elif cmd == A_WRTE:
                out += data; _send(s, A_OKAY, 1, a0)
            elif cmd == A_CLSE: break
        return out.decode("utf-8", "replace").replace("\r\n", "\n").strip()
    finally:
        s.close()


def install(host, apk_path, port=5555, progress=None):
    """Install (or update) an app on the TV by streaming the file: the same thing `adb install -r` does, with no adb program.
    Returns the TV's answer ("Success" when it worked). Needs Android 7 or newer on the TV (the streaming installer)."""
    size = os.path.getsize(apk_path)
    s = connect(host, port)
    try:
        _send(s, A_OPEN, 1, 0, f"exec:cmd package install -r -S {size}".encode() + b"\0")
        remote, out, sent, ready = 0, b"", 0, False
        chunk_size = MAXDATA.pop(id(s), 4096)
        f = open(apk_path, "rb")
        try:
            while True:
                cmd, a0, a1, data = _recv(s)
                if cmd == A_OKAY:
                    remote = a0
                    ready = True
                    chunk = f.read(chunk_size)
                    if chunk:
                        _send(s, A_WRTE, 1, remote, chunk)
                        sent += len(chunk)
                        if progress: progress(sent, size)
                elif cmd == A_WRTE:
                    out += data
                    _send(s, A_OKAY, 1, a0)
                elif cmd == A_CLSE:
                    break
        finally:
            f.close()
        if not ready: raise ConnectionError("la TV non ha aperto il servizio di installazione")
        return out.decode("utf-8", "replace").strip()
    finally:
        s.close()
