"""Small bitmap glyph primitives shared by the casino asset generators."""
from PIL import ImageDraw


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
