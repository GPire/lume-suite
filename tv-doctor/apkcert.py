"""Who signed this APK? Reads the signing certificate(s) straight from the file (APK Signature Scheme v2/v3), no Java needed.
Used to check that a downloaded app is signed by the publisher we expect before it goes near a TV."""
import hashlib, struct

V2, V3 = 0x7109871A, 0xF05368C0
MAGIC = b"APK Sig Block 42"


def _u32(b, o): return struct.unpack_from("<I", b, o)[0]


def certs(path):
    """SHA-256 (hex) of the first certificate of every signer, from the v2/v3 signing block. [] if there is none."""
    d = open(path, "rb").read()
    i = d.rfind(b"PK\x05\x06")
    if i < 0: return []
    cd = _u32(d, i + 16)
    if d[cd - 16:cd] != MAGIC: return []
    size2 = struct.unpack_from("<Q", d, cd - 24)[0]
    start = cd - size2 - 8
    pos, end, out = start + 8, cd - 24, []
    while pos < end:
        ln = struct.unpack_from("<Q", d, pos)[0]
        pid = _u32(d, pos + 8)
        val = d[pos + 12:pos + 8 + ln]
        pos += 8 + ln
        if pid not in (V2, V3): continue
        try:
            o = 4                                        # signers: u32 length, then each signer as u32 length + body
            total = _u32(val, 0)
            while o < 4 + total:
                sl = _u32(val, o); signer = val[o + 4:o + 4 + sl]; o += 4 + sl
                sd_len = _u32(signer, 0); sd = signer[4:4 + sd_len]   # signed data: digests, certificates, ...
                dg = _u32(sd, 0)
                cs = _u32(sd, 4 + dg)
                certs_blob = sd[8 + dg:8 + dg + cs]
                cl = _u32(certs_blob, 0)
                out.append(hashlib.sha256(certs_blob[4:4 + cl]).hexdigest())
        except (struct.error, IndexError):
            continue
    return list(dict.fromkeys(out))


def signed_by(path, expected_sha256):
    return expected_sha256.lower().replace(":", "") in certs(path)
