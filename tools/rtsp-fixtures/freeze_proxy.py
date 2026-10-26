"""Local RTSP fixture: stop forwarding server bytes after 7s, keeping TCP open."""
import socket
import threading
import time


def handle(client):
    upstream = socket.create_connection(("127.0.0.1", 18554))
    started = time.monotonic()

    def forward(source, target, freeze):
        try:
            while chunk := source.recv(65536):
                if not freeze or time.monotonic() - started < 7:
                    target.sendall(chunk)
        except OSError:
            pass
        finally:
            client.close()
            upstream.close()

    threading.Thread(target=forward, args=(client, upstream, False), daemon=True).start()
    forward(upstream, client, True)


with socket.create_server(("0.0.0.0", 38554)) as server:
    while True:
        client, _ = server.accept()
        threading.Thread(target=handle, args=(client,), daemon=True).start()
