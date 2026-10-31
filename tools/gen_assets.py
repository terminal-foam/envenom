#!/usr/bin/env python3
"""Generate Envenom 1.0 icons, item models, crafting recipes, and recipe advancements."""

import json
from pathlib import Path

from PIL import Image

ROOT = Path("/workspace")
ASSETS = ROOT / "src/main/resources/assets"
DATA = ROOT / "src/main/resources/data/envenom"

PACKS = [
    ("swiftness_pack", "minecraft:sugar"),
    ("leaping_pack", "minecraft:rabbit_foot"),
    ("strength_pack", "minecraft:blaze_powder"),
    ("healing_pack", "minecraft:glistering_melon_slice"),
    ("poison_pack", "minecraft:spider_eye"),
    ("regeneration_pack", "minecraft:ghast_tear"),
    ("fire_resistance_pack", "minecraft:magma_cream"),
    ("water_breathing_pack", "minecraft:pufferfish"),
    ("night_vision_pack", "minecraft:golden_carrot"),
    ("turtle_master_pack", "minecraft:turtle_helmet"),
    ("slow_falling_pack", "minecraft:phantom_membrane"),
    ("weakness_pack", "minecraft:fermented_spider_eye"),
    ("wind_charged_pack", "minecraft:breeze_rod"),
    ("weaving_pack", "minecraft:cobweb"),
    ("oozing_pack", "minecraft:slime_block"),
    ("infested_pack", "minecraft:stone"),
]


def paint(rows, palette):
    height = len(rows)
    width = len(rows[0])
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    pixels = image.load()
    for y, row in enumerate(rows):
        if len(row) != width:
            raise SystemExit(f"row {y} has {len(row)} pixels, expected {width}")
        for x, key in enumerate(row):
            color = palette[key]
            if color is not None:
                pixels[x, y] = color
    return image


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def icons():
    # 眩晕：旋转的眼睛和两颗星，保留原版 HUD 的深色描边。
    vertigo = paint(
        [
            "..................",
            "......######......",
            "....##yyyyyy##....",
            "...#yywwwwwwyy#...",
            "..#yyw######wyy#..",
            ".#yyw#pppppp#wyy#.",
            ".#yw#pp####pp#wy#.",
            ".#yw#p#llll#p#wy#.",
            ".#yw#p#l##l#p#wy#.",
            ".#yw#p#l##l#p#wy#.",
            ".#yw#p#llll#p#wy#.",
            ".#yw#pp####pp#wy#.",
            ".#yyw#pppppp#wyy#.",
            "..#yyw######wyy#..",
            "...#yywwwwwwyy#...",
            "....##yyyyyy##....",
            "......######......",
            "..................",
        ],
        {
            ".": None,
            "#": (44, 32, 88, 255),
            "y": (236, 196, 72, 255),
            "w": (255, 244, 196, 255),
            "p": (122, 92, 210, 255),
            "l": (214, 196, 255, 255),
        },
    )

    # 反胃：眯眼、撇嘴的绿脸，和眩晕的螺旋分开。
    nausea = paint(
        [
            "..................",
            "......oooooo......",
            "....oossssssso....",
            "...osssssssssso...",
            "..osssssssssssso..",
            "..ossseessseessso.",
            "..osseeeeeeessso..",
            "..ossseessseessso.",
            "..osssssssssssso..",
            "..osssssssssssso..",
            "..ossmmmmmmsssso..",
            "..osssmmmmssssso..",
            "...osssmmmmssso...",
            "....oossssssso....",
            ".....oooooooo.....",
            "......o....o......",
            "..................",
            "..................",
        ],
        {
            ".": None,
            "o": (28, 58, 18, 255),
            "s": (92, 158, 58, 255),
            "e": (18, 36, 12, 255),
            "m": (36, 72, 22, 255),
            "b": (176, 214, 96, 255),
        },
    )
    # 中间那一横被画成一整条黑带，看起来像连在一起。改成两只分开的眼睛。
    face = nausea.load()
    skin = (92, 158, 58, 255)
    eye = (18, 36, 12, 255)
    for y in (5, 6, 7):
        for x in range(4, 15):
            if face[x, y][:3] == (18, 36, 12):
                face[x, y] = skin
    for x, y in ((5, 6), (6, 6), (5, 7), (6, 7), (11, 6), (12, 6), (11, 7), (12, 7)):
        face[x, y] = eye

    # 易伤：裂开的红心。
    vulnerability = paint(
        [
            "..................",
            "..................",
            "..oo......oo......",
            ".orrro..orrrro....",
            "orhhrrroorhhrrro..",
            "orhhhrrrrhhhhrro..",
            "orhhhhrrhhhhhrro..",
            ".orhhhrrhhhhhro...",
            "..orhhrrhhhhro....",
            "...orhrrhhhrro....",
            "....orrrhhro......",
            ".....orrhro.......",
            "......orro........",
            ".......oo.........",
            "..................",
            "..................",
            "..................",
            "..................",
        ],
        {
            ".": None,
            "o": (92, 18, 22, 255),
            "r": (214, 64, 58, 255),
            "h": (255, 150, 138, 255),
        },
    )
    # Crack through the heart.
    crack = vulnerability.load()
    for x, y, color in (
        (8, 4, (255, 236, 220, 255)),
        (8, 5, (72, 16, 18, 255)),
        (9, 6, (255, 236, 220, 255)),
        (8, 7, (72, 16, 18, 255)),
        (8, 8, (255, 236, 220, 255)),
        (7, 9, (72, 16, 18, 255)),
        (8, 10, (255, 236, 220, 255)),
        (8, 11, (72, 16, 18, 255)),
    ):
        if crack[x, y][3]:
            crack[x, y] = color

    effect_dir = ASSETS / "envenom/textures/mob_effect"
    vanilla_dir = ASSETS / "minecraft/textures/mob_effect"
    save(vertigo, vanilla_dir / "nausea.png")
    save(nausea, effect_dir / "nausea.png")
    save(vulnerability, effect_dir / "vulnerability.png")

    # 16x16 paper pack. Layer 1 is the wax seal, tinted per potion.
    pack = paint(
        [
            "................",
            "................",
            "..ooooooooooo...",
            "..opppppppppo...",
            "..odddddddddo...",
            "..opppppppppo...",
            "..opppppppppo...",
            "..osssssssssso..",
            "..opppppppppo...",
            "..oppphhhpppo...",
            "..opphhhhhppo...",
            "..opphhhhhppo...",
            "..oppphhhpppo...",
            "..opppppppppo...",
            "..ooooooooooo...",
            "................",
        ],
        {
            ".": None,
            "o": (98, 68, 38, 255),
            "p": (236, 220, 180, 255),
            "d": (196, 168, 116, 255),
            "s": (122, 78, 42, 255),
            "h": (255, 248, 236, 255),
        },
    )
    overlay = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = pack.load()
    over = overlay.load()
    for y in range(16):
        for x in range(16):
            if base[x, y] == (255, 248, 236, 255):
                over[x, y] = (255, 255, 255, 255)
                base[x, y] = (0, 0, 0, 0)
    item_dir = ASSETS / "envenom/textures/item"
    save(pack, item_dir / "potion_pack.png")
    save(overlay, item_dir / "potion_pack_overlay.png")

    # Scaled previews for a quick visual check.
    preview = Image.new("RGBA", (18 * 8 * 3 + 16, 18 * 8), (32, 32, 36, 255))
    for index, image in enumerate((vertigo, nausea, vulnerability)):
        preview.paste(image.resize((18 * 8, 18 * 8), Image.Resampling.NEAREST), (index * 18 * 8, 0))
    save(preview, ROOT / "tools/icon_preview.png")
    pack_preview = Image.new("RGBA", (16 * 8, 16 * 8), (32, 32, 36, 255))
    composed = pack.copy()
    composed.alpha_composite(overlay)
    pack_preview.paste(composed.resize((16 * 8, 16 * 8), Image.Resampling.NEAREST), (0, 0))
    save(pack_preview, ROOT / "tools/pack_preview.png")


def models_and_recipes():
    model = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": "envenom:item/potion_pack",
            "layer1": "envenom:item/potion_pack_overlay",
        },
    }
    model_dir = ASSETS / "envenom/models/item"
    recipe_dir = DATA / "recipe"
    adv_dir = DATA / "advancement/recipes/misc"
    model_dir.mkdir(parents=True, exist_ok=True)
    recipe_dir.mkdir(parents=True, exist_ok=True)
    adv_dir.mkdir(parents=True, exist_ok=True)
    for name, ingredient in PACKS:
        (model_dir / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
        recipe = {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": "envenom_potion_pack",
            "ingredients": [
                {"item": "minecraft:paper"},
                {"item": ingredient},
            ],
            "result": {"count": 1, "id": f"envenom:{name}"},
        }
        (recipe_dir / f"{name}.json").write_text(json.dumps(recipe, indent=2) + "\n", encoding="utf-8")
        advancement = {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_ingredient": {
                    "conditions": {"items": [{"items": ingredient}]},
                    "trigger": "minecraft:inventory_changed",
                },
                "has_the_recipe": {
                    "conditions": {"recipe": f"envenom:{name}"},
                    "trigger": "minecraft:recipe_unlocked",
                },
            },
            "requirements": [["has_the_recipe", "has_ingredient"]],
            "rewards": {"recipes": [f"envenom:{name}"]},
        }
        (adv_dir / f"{name}.json").write_text(json.dumps(advancement, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    icons()
    models_and_recipes()
    print("assets written")
