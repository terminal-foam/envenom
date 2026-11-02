"""Prepare approved artwork for both game versions. Requires Pillow."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def sprite(name, size, content_size):
    source = Image.open(ROOT / 'artwork' / name).convert('RGBA')
    alpha = source.getchannel('A').point(lambda a: 255 if a >= 128 else 0)
    source.putalpha(alpha)
    source = source.crop(alpha.getbbox())
    source.thumbnail(content_size, Image.Resampling.NEAREST)
    result = Image.new('RGBA', size)
    result.paste(source, ((size[0] - source.width) // 2, (size[1] - source.height) // 2))
    # Fully transparent pixels have no hidden color, avoiding atlas edge bleed.
    result.putdata([(r, g, b, a) if a else (0, 0, 0, 0) for r, g, b, a in (result.getpixel((x, y)) for y in range(result.height) for x in range(result.width))])
    return result


def main():
    heart = sprite('vulnerability.png', (18, 18), (16, 16))
    composite = sprite('potion-pack.png', (16, 16), (13, 14))
    paper = composite.copy()
    seal = Image.new('RGBA', composite.size)
    pixels = 0
    for y in range(4, 11):
        for x in range(4, 12):
            r, g, b, a = composite.getpixel((x, y))
            if a and min(r, g, b) >= 70 and max(r, g, b) - min(r, g, b) <= 20:
                # The selected art uses neutral gray only for the tintable wax seal.
                shade = round((r + g + b) / 3)
                seal.putpixel((x, y), (shade, shade, shade, a))
                paper.putpixel((x, y), (210, 166, 100, 255))
                pixels += 1
    assert pixels >= 4, 'Seal extraction failed; review the artwork before replacing textures'
    logo = Image.open(ROOT / 'artwork/envenom-icon.png').convert('RGBA').resize((256, 256), Image.Resampling.NEAREST)
    for base in (ROOT, ROOT / 'mc26.1.2'):
        resources = base / 'src/main/resources'
        logo.save(resources / 'envenom.png')
        heart.save(resources / 'assets/envenom/textures/mob_effect/vulnerability.png')
        paper.save(resources / 'assets/envenom/textures/item/potion_pack.png')
        seal.save(resources / 'assets/envenom/textures/item/potion_pack_overlay.png')
    # Preview is an ignored build output, not a game resource.
    preview = Image.new('RGBA', (432, 144), (40, 35, 46, 255))
    preview.alpha_composite(heart.resize((144, 144), Image.Resampling.NEAREST), (0, 0))
    for i, color in enumerate(((235, 55, 90), (70, 155, 245)), start=1):
        tinted = seal.copy()
        tinted.putdata([(r*color[0]//255, g*color[1]//255, b*color[2]//255, a) for r,g,b,a in (seal.getpixel((x, y)) for y in range(seal.height) for x in range(seal.width))])
        pack = Image.alpha_composite(paper, tinted)
        preview.alpha_composite(pack.resize((144, 144), Image.Resampling.NEAREST), (144*i, 0))
    (ROOT / 'build').mkdir(exist_ok=True)
    preview.save(ROOT / 'build/texture-preview.png')
    print(f'Prepared both versions: 18x18 effect, 16x16 item layers ({pixels} seal pixels), 256x256 logo')


if __name__ == '__main__':
    main()
