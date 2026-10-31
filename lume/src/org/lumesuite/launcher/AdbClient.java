package org.lumesuite.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;

/**
 * A small ADB-over-network client: Lume talks to this TV's own debugging service (127.0.0.1:5555), so cleaning the TV
 * does not need a computer. It speaks only what is needed: connect, sign in with a key made once on this TV, run a shell command.
 * The TV shows its usual "Allow debugging?" box the first time. Nothing leaves the TV.
 */
final class AdbClient implements Closeable {
    private static final int A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f, A_OKAY = 0x59414b4f,
            A_CLSE = 0x45534c43, A_WRTE = 0x45545257;
    private static final byte[] SHA1_PREFIX = hex("3021300906052b0e03021a05000414");

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private int maxData = 4096;

    /** Why a connection failed, in the order a person fixes it. */
    static final class NotReady extends IOException {
        final boolean waitingForOk;

        NotReady(String m, boolean waitingForOk) {
            super(m);
            this.waitingForOk = waitingForOk;
        }
    }

    private AdbClient(Socket s) throws IOException {
        socket = s;
        in = s.getInputStream();
        out = s.getOutputStream();
    }

    static AdbClient connect(Context c, String host, int port, int waitMs) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), 2500);
        } catch (IOException e) {
            throw new NotReady("debugging is off", false);
        }
        s.setSoTimeout(waitMs);
        AdbClient a = new AdbClient(s);
        BigInteger[] key = key(c);
        a.send(A_CNXN, 0x01000000, 4096, "host::\0".getBytes());
        boolean signed = false;
        try {
            while (true) {
                ByteBuffer m = a.recv();
                int cmd = m.getInt(0), a0 = m.getInt(4), a1 = m.getInt(8);
                if (cmd == A_CNXN) {
                    a.maxData = Math.max(4096, Math.min(a1 == 0 ? 4096 : a1, 65536));
                    s.setSoTimeout(90000);   // switching an app off can take a while on a slow TV
                    return a;
                }
                if (cmd != A_AUTH || a0 != 1) throw new IOException("unexpected answer");
                byte[] token = a.payload;
                if (!signed) {
                    a.send(A_AUTH, 2, 0, sign(token, key));
                    signed = true;
                } else {
                    a.send(A_AUTH, 3, 0, pubkey(key[0]));   // the "Allow debugging?" box appears now
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            s.close();
            throw new NotReady("waiting for OK on the TV", true);
        }
    }

    private int nextId = 1;

    String shell(String command) throws IOException {
        final int id = nextId++;   // a new channel number per command: the TV may still be closing the last one
        send(A_OPEN, id, 0, ("shell:" + command + "\0").getBytes());
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        int remote = 0;
        boolean ready = false;
        while (true) {
            ByteBuffer m = recv();
            int cmd = m.getInt(0), a0 = m.getInt(4), a1 = m.getInt(8);
            if (a1 != id && cmd != A_CNXN) continue;   // another channel's leftovers
            if (cmd == A_OKAY) {
                remote = a0;
                ready = true;
            } else if (cmd == A_WRTE) {
                res.write(payload);
                send(A_OKAY, id, a0, new byte[0]);
            } else if (cmd == A_CLSE) {
                if (remote == 0) remote = a0;
                send(A_CLSE, id, remote, new byte[0]);
                break;
            }
        }
        if (!ready) throw new IOException("the TV did not open the shell");
        return new String(res.toByteArray(), "UTF-8").trim();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------- wire

    private byte[] payload = new byte[0];

    private void send(int cmd, int a0, int a1, byte[] data) throws IOException {
        long sum = 0;
        for (byte b : data) sum += b & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(a0).putInt(a1).putInt(data.length).putInt((int) (sum & 0xffffffffL)).putInt(~cmd);
        out.write(h.array());
        out.write(data);
        out.flush();
    }

    private ByteBuffer recv() throws IOException {
        byte[] h = readN(24);
        ByteBuffer m = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        if (m.getInt(20) != ~m.getInt(0)) throw new IOException("invalid answer");
        int len = m.getInt(12);
        payload = len > 0 ? readN(len) : new byte[0];
        return m;
    }

    private byte[] readN(int n) throws IOException {
        byte[] b = new byte[n];
        int got = 0;
        while (got < n) {
            int r = in.read(b, got, n - got);
            if (r < 0) throw new IOException("the TV closed the connection");
            got += r;
        }
        return b;
    }

    // ---------------------------------------------------------------- key (made once on this TV)

    private static BigInteger[] key(Context c) {
        SharedPreferences p = c.getSharedPreferences("adbkey", Context.MODE_PRIVATE);
        String n = p.getString("n", null), d = p.getString("d", null);
        if (n != null && d != null) return new BigInteger[]{new BigInteger(n, 16), new BigInteger(d, 16)};
        SecureRandom rnd = new SecureRandom();
        BigInteger e = BigInteger.valueOf(65537);
        while (true) {
            BigInteger pr = BigInteger.probablePrime(1024, rnd), q = BigInteger.probablePrime(1024, rnd);
            BigInteger phi = pr.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE)), mod = pr.multiply(q);
            if (pr.equals(q) || mod.bitLength() != 2048 || !phi.gcd(e).equals(BigInteger.ONE)) continue;
            BigInteger dd = e.modInverse(phi);
            p.edit().putString("n", mod.toString(16)).putString("d", dd.toString(16)).apply();
            return new BigInteger[]{mod, dd};
        }
    }

    private static byte[] sign(byte[] token, BigInteger[] k) {
        byte[] t = new byte[SHA1_PREFIX.length + token.length];
        System.arraycopy(SHA1_PREFIX, 0, t, 0, SHA1_PREFIX.length);
        System.arraycopy(token, 0, t, SHA1_PREFIX.length, token.length);
        byte[] em = new byte[256];
        em[1] = 1;
        for (int i = 2; i < 256 - t.length - 1; i++) em[i] = (byte) 0xff;
        System.arraycopy(t, 0, em, 256 - t.length, t.length);
        return fixed(new BigInteger(1, em).modPow(k[1], k[0]), 256, false);
    }

    private static byte[] pubkey(BigInteger n) {
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        long n0inv = n.modInverse(two32).negate().mod(two32).longValue();
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64).putInt((int) n0inv).put(fixed(n, 256, true)).put(fixed(rr, 256, true)).putInt(65537);
        byte[] raw = Base64.encode(b.array(), Base64.NO_WRAP);
        byte[] who = " lume@tv\0".getBytes();
        byte[] all = new byte[raw.length + who.length];
        System.arraycopy(raw, 0, all, 0, raw.length);
        System.arraycopy(who, 0, all, raw.length, who.length);
        return all;
    }

    private static byte[] fixed(BigInteger v, int len, boolean littleEndian) {
        byte[] be = v.toByteArray();
        byte[] r = new byte[len];
        int copy = Math.min(be.length, len);
        System.arraycopy(be, be.length - copy, r, len - copy, copy);
        if (littleEndian) for (int i = 0; i < len / 2; i++) {
            byte t = r[i];
            r[i] = r[len - 1 - i];
            r[len - 1 - i] = t;
        }
        return r;
    }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}
