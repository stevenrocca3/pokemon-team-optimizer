"""
Downloads PokéAPI's 96px game sprites (https://github.com/PokeAPI/sprites) for the site to serve itself.

    python3 fetch.py 1025 ../../pokemon-optimizer-frontend/public/sprites

Saves 1.png to N.png, skipping ones already there. Each file must be a real 96 x 96 PNG or the run stops, so a
GitHub error page or a renamed sprite can't slip in. Run it again with a higher N when new Pokémon come out.
"""
import os
import struct
import sys
import time
import urllib.request

URL = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/{}.png"
PNG = b"\x89PNG\r\n\x1a\n"


def checked(data, dex):
    if not data.startswith(PNG) or data[12:16] != b"IHDR":
        raise ValueError(f"{dex}.png isn't a PNG")
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (96, 96):
        raise ValueError(f"{dex}.png is {width} x {height}, not 96 x 96")
    return data


def fetch(dex):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(URL.format(dex), timeout=30) as response:
                return checked(response.read(), dex)
        except OSError:
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)


def main(last, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    total = 0
    for dex in range(1, int(last) + 1):
        path = os.path.join(out_dir, f"{dex}.png")
        if not os.path.exists(path):
            with open(path, "wb") as f:
                f.write(fetch(dex))
        total += os.path.getsize(path)
    print(f"{last} sprites, {total / 1024:.0f} KB in {out_dir}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(*sys.argv[1:])
