"""Forward RTSP to port 18554 and map negotiated UDP ports into one dedicated emulator.

Usage: python emulator_udp_proxy.py emulator-5570
The host's emulator alias (10.0.2.2) otherwise negotiates UDP but drops unsolicited RTP.
"""
import re
import socket
import subprocess
import sys
import threading

serial = sys.argv[1]


def redirect(action, port):
    mapping = f"udp:{port}:{port}" if action == "add" else f"udp:{port}"
    result = subprocess.run(
        ["adb", "-s", serial, "emu", "redir", action, mapping],
        capture_output=True, text=True,
    )
    if action == "add" and (result.returncode or "KO:" in result.stdout):
        raise RuntimeError(f"Unable to forward UDP port {port}: {result.stdout} {result.stderr}")


def read_exact(stream, count):
    data = bytearray()
    while len(data) < count:
        chunk = stream.read(count - len(data))
        if not chunk:
            raise EOFError()
        data.extend(chunk)
    return bytes(data)


def handle(client):
    upstream = socket.create_connection(("127.0.0.1", 18554))
    ports = set()

    def requests():
        try:
            with client.makefile("rb") as stream:
                while first := stream.read(1):
                    if first == b"$":
                        header = read_exact(stream, 3)
                        upstream.sendall(first + header + read_exact(stream, int.from_bytes(header[1:], "big")))
                        continue
                    header = first + stream.readline()
                    while not header.endswith(b"\r\n\r\n"):
                        line = stream.readline()
                        if not line or len(header) > 32768:
                            raise EOFError()
                        header += line
                    match = re.search(rb"client_port=(\d+)-(\d+)", header)
                    if match:
                        for value in match.groups():
                            port = int(value)
                            if port not in ports:
                                redirect("add", port)
                                ports.add(port)
                    body = re.search(rb"content-length:\s*(\d+)", header, re.I)
                    upstream.sendall(header + (read_exact(stream, int(body[1])) if body else b""))
        except (OSError, EOFError, RuntimeError) as error:
            if isinstance(error, RuntimeError):
                print(error, flush=True)
        finally:
            upstream.close()
            client.close()

    threading.Thread(target=requests, daemon=True).start()
    try:
        while chunk := upstream.recv(65536):
            client.sendall(chunk)
    except OSError:
        pass
    finally:
        client.close()
        upstream.close()
        for port in ports:
            redirect("del", port)


with socket.create_server(("0.0.0.0", 48554)) as server:
    while True:
        client, _ = server.accept()
        threading.Thread(target=handle, args=(client,), daemon=True).start()
