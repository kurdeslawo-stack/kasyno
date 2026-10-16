"""Generate CasinoCore Blackjack cards and assemble the unified 26.3 resource pack.

Requires Python 3.12+ and Pillow. The output is deterministic and uses no fonts,
downloads, or third-party artwork.
"""

from __future__ import annotations

import json
import shutil
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw


TOOLS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = TOOLS_DIR.parent
BLACKJACK_ROOT = REPOSITORY_ROOT / "assets" / "blackjack"
GENERATED_ROOT = BLACKJACK_ROOT / "generated"
ROULETTE_PACK = REPOSITORY_ROOT / "assets" / "roulette" / "resource_pack"
UNIFIED_PACK = REPOSITORY_ROOT / "assets" / "resource_pack"
DIST = REPOSITORY_ROOT / "dist"
ZIP_PATH = DIST / "CasinoCore_ResourcePack_26.3.zip"

TARGET_VERSION = "26.3"
RESOURCE_PACK_FORMAT = [97, 1]
NAMESPACE = "casinocore"
TEXTURE_SIZE = 32

RANKS = (
    ("ace", "A"), ("2", "2"), ("3", "3"), ("4", "4"), ("5", "5"),
    ("6", "6"), ("7", "7"), ("8", "8"), ("9", "9"), ("10", "10"),
    ("jack", "J"), ("queen", "Q"), ("king", "K"),
)
SUITS = (
    ("spades", "spade", "#202725"),
    ("hearts", "heart", "#b8323e"),
    ("diamonds", "diamond", "#b8323e"),
    ("clubs", "club", "#202725"),
)

COLORS = {
    "transparent": (0, 0, 0, 0),
    "shadow": "#101816",
    "outline": "#342d25",
    "gold": "#ba9148",
    "gold_light": "#e1c878",
    "ivory": "#eee8d3",
    "ivory_shadow": "#d8cfb7",
    "emerald": "#153f35",
    "emerald_light": "#1f5a49",
}

GLYPHS = {
    "0": ("111", "101", "101", "101", "111"),
    "1": ("010", "110", "010", "010", "111"),
    "2": ("111", "001", "111", "100", "111"),
    "3": ("111", "001", "111", "001", "111"),
    "4": ("101", "101", "111", "001", "001"),
    "5": ("111", "100", "111", "001", "111"),
    "6": ("111", "100", "111", "101", "111"),
    "7": ("111", "001", "010", "010", "010"),
    "8": ("111", "101", "111", "101", "111"),
    "9": ("111", "101", "111", "001", "111"),
    "A": ("010", "101", "111", "101", "101"),
    "C": ("111", "100", "100", "100", "111"),
    "J": ("001", "001", "001", "101", "111"),
    "K": ("101", "101", "110", "101", "101"),
    "Q": ("111", "101", "101", "111", "001"),
}


def safe_recreate(directory: Path) -> None:
    resolved = directory.resolve()
    repository = REPOSITORY_ROOT.resolve()
    if repository not in resolved.parents:
        raise RuntimeError(f"Refusing to recreate path outside repository: {resolved}")
    if directory.exists():
        shutil.rmtree(directory)
    directory.mkdir(parents=True)


def save_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def draw_glyph(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str, color: str, scale: int = 1) -> None:
    x, y = xy
    for character in text:
        glyph = GLYPHS[character]
        for row_index, row in enumerate(glyph):
            for column_index, pixel in enumerate(row):
                if pixel == "1":
                    draw.rectangle(
                        (
                            x + column_index * scale,
                            y + row_index * scale,
                            x + (column_index + 1) * scale - 1,
                            y + (row_index + 1) * scale - 1,
                        ),
                        fill=color,
                    )
        x += (len(glyph[0]) + 1) * scale


def glyph_width(text: str, scale: int = 1) -> int:
    return sum((len(GLYPHS[character][0]) + 1) * scale for character in text) - scale


def draw_suit(draw: ImageDraw.ImageDraw, suit: str, center: tuple[int, int], color: str, size: int) -> None:
    x, y = center
    radius = max(1, size // 4)
    if suit == "heart":
        draw.ellipse((x - radius * 2, y - radius * 2, x, y), fill=color)
        draw.ellipse((x, y - radius * 2, x + radius * 2, y), fill=color)
        draw.polygon(((x - radius * 2, y - radius), (x + radius * 2, y - radius), (x, y + radius * 2)), fill=color)
    elif suit == "diamond":
        draw.polygon(((x, y - size // 2), (x + size // 2, y),
                      (x, y + size // 2), (x - size // 2, y)), fill=color)
    elif suit == "club":
        draw.ellipse((x - radius, y - radius * 3, x + radius, y - radius), fill=color)
        draw.ellipse((x - radius * 3, y - radius, x - radius, y + radius), fill=color)
        draw.ellipse((x + radius, y - radius, x + radius * 3, y + radius), fill=color)
        draw.rectangle((x - radius, y - radius, x + radius, y + radius * 2), fill=color)
        draw.polygon(((x - radius * 2, y + radius * 2), (x + radius * 2, y + radius * 2),
                      (x, y)), fill=color)
    elif suit == "spade":
        draw.ellipse((x - radius * 2, y - radius, x, y + radius), fill=color)
        draw.ellipse((x, y - radius, x + radius * 2, y + radius), fill=color)
        draw.polygon(((x, y - radius * 3), (x - radius * 2, y), (x + radius * 2, y)), fill=color)
        draw.rectangle((x - radius, y, x + radius, y + radius * 2), fill=color)
        draw.polygon(((x - radius * 2, y + radius * 2), (x + radius * 2, y + radius * 2),
                      (x, y)), fill=color)
    else:
        raise ValueError(f"Unknown suit: {suit}")


def card_front(rank: str, suit: str, color: str) -> Image.Image:
    image = Image.new("RGBA", (TEXTURE_SIZE, TEXTURE_SIZE), COLORS["transparent"])
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((4, 2, 30, 31), radius=3, fill=COLORS["shadow"])
    draw.rounded_rectangle((2, 0, 28, 29), radius=3, fill=COLORS["outline"])
    draw.rounded_rectangle((3, 1, 27, 28), radius=2, fill=COLORS["gold"])
    draw.rounded_rectangle((4, 2, 26, 27), radius=2, fill=COLORS["ivory"])
    draw.line((5, 24, 25, 24), fill=COLORS["ivory_shadow"])

    draw_glyph(draw, (6, 4), rank, color)
    draw_suit(draw, suit, (7, 12), color, 4)

    if rank in {"J", "Q", "K"}:
        draw.rectangle((10, 8, 22, 22), fill=COLORS["outline"])
        draw.rectangle((11, 9, 21, 21), outline=COLORS["gold_light"])
        width = glyph_width(rank, 2)
        draw_glyph(draw, (16 - width // 2, 11), rank, COLORS["gold_light"], 2)
        draw_suit(draw, suit, (16, 20), color, 4)
    elif rank == "A":
        draw_suit(draw, suit, (16, 17), color, 12)
    else:
        draw_suit(draw, suit, (16, 17), color, 8)

    corner = Image.new("RGBA", (9, 14), COLORS["transparent"])
    corner_draw = ImageDraw.Draw(corner)
    corner_x = max(0, 7 - glyph_width(rank))
    draw_glyph(corner_draw, (corner_x, 1), rank, color)
    draw_suit(corner_draw, suit, (6, 10), color, 4)
    image.alpha_composite(corner.rotate(180), (17, 13))
    return image


def card_back() -> Image.Image:
    image = Image.new("RGBA", (TEXTURE_SIZE, TEXTURE_SIZE), COLORS["transparent"])
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((4, 2, 30, 31), radius=3, fill=COLORS["shadow"])
    draw.rounded_rectangle((2, 0, 28, 29), radius=3, fill=COLORS["outline"])
    draw.rounded_rectangle((3, 1, 27, 28), radius=2, fill=COLORS["gold"])
    draw.rounded_rectangle((5, 3, 25, 26), radius=1, fill=COLORS["emerald"])
    pattern = Image.new("RGBA", (17, 20), COLORS["emerald"])
    pattern_draw = ImageDraw.Draw(pattern)
    for offset in range(-16, 33, 6):
        pattern_draw.line((0, offset, 16, 16 + offset), fill=COLORS["emerald_light"])
        pattern_draw.line((0, 16 + offset, 16, offset), fill=COLORS["emerald_light"])
    image.alpha_composite(pattern, (7, 5))
    draw.rectangle((7, 5, 23, 24), outline=COLORS["gold_light"])
    draw.rectangle((10, 10, 20, 19), fill=COLORS["emerald"])
    draw.rectangle((10, 10, 20, 19), outline=COLORS["gold"])
    draw_glyph(draw, (12, 12), "CC", COLORS["gold_light"])
    return image


def generate_card_assets() -> list[str]:
    texture_dir = GENERATED_ROOT / "assets" / NAMESPACE / "textures" / "item" / "blackjack" / "cards"
    model_dir = GENERATED_ROOT / "assets" / NAMESPACE / "models" / "item" / "blackjack" / "cards"
    item_dir = GENERATED_ROOT / "assets" / NAMESPACE / "items" / "blackjack" / "cards"
    for directory in (texture_dir, model_dir, item_dir):
        directory.mkdir(parents=True, exist_ok=True)

    identifiers: list[str] = []
    for suit_name, suit_shape, color in SUITS:
        for rank_name, rank_label in RANKS:
            identifier = f"{rank_name}_{suit_name}"
            identifiers.append(identifier)
            card_front(rank_label, suit_shape, color).save(texture_dir / f"{identifier}.png")
            save_json(model_dir / f"{identifier}.json", {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"{NAMESPACE}:item/blackjack/cards/{identifier}"},
            })
            save_json(item_dir / f"{identifier}.json", {
                "model": {
                    "type": "minecraft:model",
                    "model": f"{NAMESPACE}:item/blackjack/cards/{identifier}",
                }
            })

    identifiers.append("card_back")
    card_back().save(texture_dir / "card_back.png")
    save_json(model_dir / "card_back.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{NAMESPACE}:item/blackjack/cards/card_back"},
    })
    save_json(item_dir / "card_back.json", {
        "model": {
            "type": "minecraft:model",
            "model": f"{NAMESPACE}:item/blackjack/cards/card_back",
        }
    })
    return identifiers


def generate_preview(identifiers: list[str]) -> None:
    texture_dir = GENERATED_ROOT / "assets" / NAMESPACE / "textures" / "item" / "blackjack" / "cards"
    scale = 3
    gap = 4
    width = 13 * (TEXTURE_SIZE * scale + gap) + gap
    height = 5 * (TEXTURE_SIZE * scale + gap) + gap
    preview = Image.new("RGBA", (width, height), "#101917")
    for index, identifier in enumerate(identifiers[:-1]):
        row, column = divmod(index, 13)
        card = Image.open(texture_dir / f"{identifier}.png").resize(
            (TEXTURE_SIZE * scale, TEXTURE_SIZE * scale), Image.Resampling.NEAREST)
        preview.alpha_composite(card, (gap + column * (TEXTURE_SIZE * scale + gap),
                                       gap + row * (TEXTURE_SIZE * scale + gap)))
    back = Image.open(texture_dir / "card_back.png").resize(
        (TEXTURE_SIZE * scale, TEXTURE_SIZE * scale), Image.Resampling.NEAREST)
    preview.alpha_composite(back, (gap, gap + 4 * (TEXTURE_SIZE * scale + gap)))
    preview_dir = BLACKJACK_ROOT / "preview"
    preview_dir.mkdir(parents=True, exist_ok=True)
    preview.convert("RGB").save(preview_dir / "blackjack_card_sheet.png")


def assemble_pack() -> None:
    if not (ROULETTE_PACK / "pack.mcmeta").is_file():
        raise FileNotFoundError("Roulette resource pack source is missing")
    safe_recreate(UNIFIED_PACK)
    shutil.copytree(ROULETTE_PACK, UNIFIED_PACK, dirs_exist_ok=True)
    shutil.copytree(GENERATED_ROOT / "assets", UNIFIED_PACK / "assets", dirs_exist_ok=True)
    save_json(UNIFIED_PACK / "pack.mcmeta", {
        "pack": {
            "description": f"CasinoCore | Roulette + Blackjack - Java {TARGET_VERSION}",
            "min_format": RESOURCE_PACK_FORMAT,
            "max_format": RESOURCE_PACK_FORMAT,
        }
    })


def validate(identifiers: list[str]) -> list[str]:
    if len(identifiers) != 53 or len(set(identifiers)) != 53:
        raise AssertionError("Expected 53 unique Blackjack identifiers")

    json_paths = sorted(UNIFIED_PACK.rglob("*.json"))
    for path in json_paths:
        json.loads(path.read_text(encoding="utf-8"))

    pack = json.loads((UNIFIED_PACK / "pack.mcmeta").read_text(encoding="utf-8"))["pack"]
    if pack.get("min_format") != RESOURCE_PACK_FORMAT or pack.get("max_format") != RESOURCE_PACK_FORMAT:
        raise AssertionError("Unexpected resource pack format")
    if "pack_format" in pack or "supported_formats" in pack:
        raise AssertionError("Legacy resource pack format fields are not allowed for 26.3")

    namespace_root = UNIFIED_PACK / "assets" / NAMESPACE
    for item_path in sorted((namespace_root / "items").rglob("*.json")):
        item = json.loads(item_path.read_text(encoding="utf-8"))
        model_definition = item.get("model", {})
        if model_definition.get("type") != "minecraft:model":
            raise AssertionError(f"Unsupported item model definition: {item_path}")
        model_id = model_definition.get("model", "")
        namespace, separator, relative_model = model_id.partition(":")
        if separator != ":" or namespace != NAMESPACE:
            raise AssertionError(f"Invalid model namespace: {item_path}")
        if not (namespace_root / "models" / f"{relative_model}.json").is_file():
            raise AssertionError(f"Missing model target for {item_path}: {model_id}")

    for model_path in sorted((namespace_root / "models").rglob("*.json")):
        model = json.loads(model_path.read_text(encoding="utf-8"))
        parent = model.get("parent")
        if parent and not parent.startswith("minecraft:"):
            namespace, separator, relative_parent = parent.partition(":")
            if separator != ":" or namespace != NAMESPACE:
                raise AssertionError(f"Invalid parent namespace: {model_path}")
            if not (namespace_root / "models" / f"{relative_parent}.json").is_file():
                raise AssertionError(f"Missing model parent for {model_path}: {parent}")
        for texture_id in model.get("textures", {}).values():
            if texture_id.startswith("#"):
                continue
            namespace, separator, relative_texture = texture_id.partition(":")
            if separator != ":" or namespace != NAMESPACE:
                raise AssertionError(f"Invalid texture namespace: {model_path}")
            if not (namespace_root / "textures" / f"{relative_texture}.png").is_file():
                raise AssertionError(f"Missing texture for {model_path}: {texture_id}")

    texture_dir = UNIFIED_PACK / "assets" / NAMESPACE / "textures" / "item" / "blackjack" / "cards"
    model_dir = UNIFIED_PACK / "assets" / NAMESPACE / "models" / "item" / "blackjack" / "cards"
    item_dir = UNIFIED_PACK / "assets" / NAMESPACE / "items" / "blackjack" / "cards"
    for identifier in identifiers:
        texture = texture_dir / f"{identifier}.png"
        model_path = model_dir / f"{identifier}.json"
        item_path = item_dir / f"{identifier}.json"
        if not texture.is_file() or Image.open(texture).size != (TEXTURE_SIZE, TEXTURE_SIZE):
            raise AssertionError(f"Missing or invalid texture: {identifier}")
        model = json.loads(model_path.read_text(encoding="utf-8"))
        expected_texture = f"{NAMESPACE}:item/blackjack/cards/{identifier}"
        if model.get("textures", {}).get("layer0") != expected_texture:
            raise AssertionError(f"Invalid texture reference: {identifier}")
        item = json.loads(item_path.read_text(encoding="utf-8"))
        expected_model = f"{NAMESPACE}:item/blackjack/cards/{identifier}"
        if item.get("model") != {"type": "minecraft:model", "model": expected_model}:
            raise AssertionError(f"Invalid item definition: {identifier}")

    required_roulette = (
        "assets/casinocore/items/roulette_table.json",
        "assets/casinocore/items/roulette_table_base.json",
        "assets/casinocore/items/roulette_wheel.json",
        "assets/casinocore/items/roulette_ball.json",
        "assets/casinocore/textures/entity/roulette_table.png",
    )
    for relative_path in required_roulette:
        if not (UNIFIED_PACK / relative_path).is_file():
            raise AssertionError(f"Missing Roulette asset: {relative_path}")

    DIST.mkdir(parents=True, exist_ok=True)
    for old_zip in DIST.glob("CasinoCore_ResourcePack_*.zip"):
        if old_zip != ZIP_PATH:
            old_zip.unlink()
    for old_zip in DIST.glob("CasinoCore_Roulette_ResourcePack_*.zip"):
        old_zip.unlink()
    if ZIP_PATH.exists():
        ZIP_PATH.unlink()
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(UNIFIED_PACK.rglob("*")):
            if not path.is_file():
                continue
            archive_entry = zipfile.ZipInfo(
                path.relative_to(UNIFIED_PACK).as_posix(),
                date_time=(2026, 1, 1, 0, 0, 0),
            )
            archive_entry.compress_type = zipfile.ZIP_DEFLATED
            archive_entry.external_attr = 0o100644 << 16
            archive.writestr(archive_entry, path.read_bytes(), compresslevel=9)
    with zipfile.ZipFile(ZIP_PATH) as archive:
        if archive.testzip() is not None:
            raise AssertionError("ZIP integrity check failed")
        names = set(archive.namelist())
        if "pack.mcmeta" not in names or any(name.startswith("resource_pack/") for name in names):
            raise AssertionError("pack.mcmeta is not at the ZIP root")
        for relative_path in required_roulette:
            if relative_path not in names:
                raise AssertionError(f"Roulette asset missing from ZIP: {relative_path}")
        for identifier in identifiers:
            for category, extension in (("textures", "png"), ("models", "json"), ("items", "json")):
                middle = "item/" if category in {"textures", "models"} else ""
                expected = f"assets/{NAMESPACE}/{category}/{middle}blackjack/cards/{identifier}.{extension}"
                if expected not in names:
                    raise AssertionError(f"Blackjack asset missing from ZIP: {expected}")

    return [
        "CasinoCore unified resource pack validation: PASS",
        f"Target: Minecraft Java {TARGET_VERSION}; format: {RESOURCE_PACK_FORMAT}",
        "Blackjack cards: 52 unique fronts + 1 card back",
        "Blackjack identifiers: 53 unique item model definitions",
        "Blackjack references: every item definition, model and texture resolves",
        f"JSON syntax: {len(json_paths)} unified pack JSON files parsed",
        "Unified references: every casinocore item, model and texture reference resolves",
        "Roulette: table, BASE, WHEEL, BALL and atlas present in unified pack",
        "ZIP: integrity valid and pack.mcmeta located at archive root",
        f"Output: {ZIP_PATH.name}",
    ]


def write_documentation(report: list[str]) -> None:
    readme = """# CasinoCore Blackjack cards

Deterministycznie generowana talia 52 kart oraz rewers dla Minecraft Java 26.3.
Karty mają jasny front, złotą ramkę, czytelną rangę i duży symbol koloru.
Rewers używa ciemnej zieleni, złotej ramki i monogramu CasinoCore.

Uruchom z katalogu repozytorium:

```powershell
py -3 tools/build_blackjack_assets.py
```

Generator tworzy źródłowe PNG i JSON-y w `assets/blackjack/generated`, podgląd
w `assets/blackjack/preview`, wspólną paczkę w `assets/resource_pack` oraz ZIP
`dist/CasinoCore_ResourcePack_26.3.zip`. Paczka zawiera jednocześnie Roulette
i Blackjack. Nie wymaga OptiFine.

Identyfikatory mają postać `casinocore:blackjack/cards/<ranga>_<kolor>`, np.
`casinocore:blackjack/cards/ace_spades` i `casinocore:blackjack/cards/10_hearts`.
Rewers: `casinocore:blackjack/cards/card_back`.

```mcfunction
/give @s minecraft:paper[minecraft:item_model="casinocore:blackjack/cards/ace_spades"]
/give @s minecraft:paper[minecraft:item_model="casinocore:blackjack/cards/card_back"]
```
"""
    (BLACKJACK_ROOT / "README_PL.md").write_text(readme, encoding="utf-8")
    (BLACKJACK_ROOT / "VALIDATION.txt").write_text("\n".join(report) + "\n", encoding="utf-8")


def main() -> None:
    safe_recreate(GENERATED_ROOT)
    identifiers = generate_card_assets()
    generate_preview(identifiers)
    assemble_pack()
    report = validate(identifiers)
    write_documentation(report)
    print("\n".join(report))


if __name__ == "__main__":
    main()
