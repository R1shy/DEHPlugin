package net.rishy.dehplugin.bot;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal Minecraft RCON client, one connection per command. */
public final class RconClient {

    public record RconResult(boolean ok, String output) {}

    private RconClient() {
    }

    public static RconResult command(String host, int port, String password, String command) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            if (!authenticate(in, out, password)) {
                return new RconResult(false, "authentication failed");
            }
            int id = (int) (System.currentTimeMillis() & 0x7fffffff);
            writePacket(out, id, 2, command);
            String output = "";
            while (true) {
                Packet packet = readPacket(in);
                if (packet == null) {
                    break;
                }
                if (packet.id == id) {
                    output = packet.payload;
                    break;
                }
            }
            return new RconResult(true, output);
        } catch (IOException e) {
            return new RconResult(false, e.getMessage());
        }
    }

    private static boolean authenticate(InputStream in, OutputStream out, String password)
            throws IOException {
        int id = 0x0000DEAD;
        writePacket(out, id, 3, password);
        while (true) {
            Packet packet = readPacket(in);
            if (packet == null) {
                return false;
            }
            if (packet.id == id) {
                return true;
            }
        }
    }

    private static void writePacket(OutputStream out, int id, int type, String payload)
            throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        int length = 4 + 4 + body.length + 2;
        ByteBuffer buffer = ByteBuffer.allocate(length + 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(length);
        buffer.putInt(id);
        buffer.putInt(type);
        buffer.put(body);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        out.write(buffer.array());
        out.flush();
    }

    private static Packet readPacket(InputStream in) throws IOException {
        DataInputStream data = in instanceof DataInputStream ? (DataInputStream) in : new DataInputStream(in);
        int length = data.readInt();
        if (length < 10 || length > 4096) {
            return null;
        }
        int id = data.readInt();
        int type = data.readInt();
        int payloadLen = length - 10;
        byte[] payload = new byte[payloadLen];
        data.readFully(payload);
        return new Packet(id, type, new String(payload, 0, payload.length, StandardCharsets.UTF_8).strip());
    }

    private record Packet(int id, int type, String payload) {}
}
