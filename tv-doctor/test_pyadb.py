"""Tests pyadb against a mock TV that really verifies the RSA signature and parses the Android key layout."""
import socket, struct, threading, base64, secrets, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pyadb as P
pyadb_keyfile = P.KEYFILE
P.KEYFILE = os.path.join(os.environ.get('TMPDIR','/tmp'), 'tvdoctor_test_key.json')
if os.path.exists(P.KEYFILE): os.remove(P.KEYFILE)

state = {"known": None, "asked": 0, "verified": False, "cmd": None}

def mock_tv(srv):
    c, _ = srv.accept()
    cmd, a0, a1, data = P._recv(c); assert cmd == P.A_CNXN
    token = secrets.token_bytes(20)
    P._send(c, P.A_AUTH, 1, 0, token)
    while True:
        cmd, a0, a1, data = P._recv(c); assert cmd == P.A_AUTH
        if a0 == 2 and state["known"]:
            n, e = state["known"]
            em = pow(int.from_bytes(data, "big"), e, n).to_bytes(256, "big")
            exp = b"\x00\x01" + b"\xff" * (256 - 3 - len(P.SHA1_PREFIX) - 20) + b"\x00" + P.SHA1_PREFIX + token
            assert em == exp, "signature does not verify"; state["verified"] = True
            P._send(c, P.A_CNXN, 0x01000000, 4096, b"device::\0"); break
        if a0 == 2:
            P._send(c, P.A_AUTH, 1, 0, token)        # unknown key: ask again
        if a0 == 3:
            raw = base64.b64decode(data.split(b" ")[0]); ln, n0inv = struct.unpack("<II", raw[:8])
            n = int.from_bytes(raw[8:264], "little"); e = struct.unpack("<I", raw[520:524])[0]
            assert ln == 64 and e == 65537 and (n * n0inv) % (1 << 32) == (1 << 32) - 1, "bad Android key layout"
            state["known"] = (n, e); state["asked"] += 1
            P._send(c, P.A_CNXN, 0x01000000, 4096, b"device::\0"); break   # user accepted: connected
    cmd, a0, a1, data = P._recv(c); assert cmd == P.A_OPEN
    state["cmd"] = data.rstrip(b"\0").decode()
    P._send(c, P.A_OKAY, 7, a0)
    P._send(c, P.A_WRTE, 7, a0, b"package:com.x\r\npackage:com.y\r\n"); assert P._recv(c)[0] == P.A_OKAY
    P._send(c, P.A_CLSE, 7, a0)
    c.close()

srv = socket.socket(); srv.bind(("127.0.0.1", 0)); srv.listen(2)
srv.settimeout(20)
port = srv.getsockname()[1]
for run in (1, 2):
    t = threading.Thread(target=mock_tv, args=(srv,)); t.start()
    out = P.shell("127.0.0.1", "pm list packages", port)
    t.join(20)
    assert out == "package:com.x\npackage:com.y", out
assert state["asked"] == 1 and state["verified"] and state["cmd"] == "shell:pm list packages"
print("pyadb OK: chiave Android valida, firma RSA verificata, comando eseguito, TV nuova autorizzata una volta")
os.remove(P.KEYFILE)
