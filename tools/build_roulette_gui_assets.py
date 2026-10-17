"""Deterministic 32px Roulette UI artwork; no changes to furniture assets."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
from pixel_art import draw_glyph, glyph_width

GOLD = '#d9b45d'
IVORY = '#fff4d5'
ROOT = Path(__file__).resolve().parent.parent


def label(draw, text, y, color=IVORY, scale=2):
    x = (32 - glyph_width(text, scale)) // 2
    draw_glyph(draw, (x + 1, y + 1), text, '#151a22', scale)
    draw_glyph(draw, (x, y), text, color, scale)


def tile(color, active=False):
    image = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((3, 4, 29, 30), radius=4, fill='#101722')
    d.rounded_rectangle((2, 1, 29, 28), radius=4, fill=GOLD if active else '#737c8c')
    d.rounded_rectangle((4, 3, 27, 26), radius=2, fill=color)
    d.line((6, 4, 25, 4), fill='#ffffff60')
    if active:
        d.polygon(((1, 12), (5, 15), (1, 18)), fill=IVORY)
        d.polygon(((30, 12), (26, 15), (30, 18)), fill=IVORY)
    return image


def chip(text, color):
    image = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(image)
    d.ellipse((2, 4, 30, 31), fill='#111722')
    d.ellipse((1, 1, 30, 29), fill=GOLD)
    d.ellipse((3, 3, 28, 27), fill=color)
    for box in ((14, 2, 17, 6), (14, 24, 17, 28), (2, 13, 6, 16), (25, 13, 29, 16)):
        d.rectangle(box, fill=IVORY)
    d.ellipse((7, 7, 24, 23), fill=color, outline=GOLD)
    label(d, text, 8, scale=3)
    return image


def selected(image):
    image = image.copy()
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((0, 0, 31, 31), radius=3, outline='#fff4d5', width=2)
    d.rectangle((22, 22, 31, 31), fill='#11362b', outline='#f9da7d')
    d.line(((24, 26), (26, 28), (30, 24)), fill='#8cffb0', width=2)
    return image


def background(kind='felt'):
    image = Image.new('RGBA', (32, 32), '#12382f' if kind == 'felt' else '#172326')
    d = ImageDraw.Draw(image)
    for y in range(2, 32, 4):
        for x in range(2 + y % 8, 32, 8):
            d.point((x, y), fill='#194338' if kind == 'felt' else '#202d2e')
    if kind == 'rail':
        d.line((0, 3, 31, 3), fill='#9d8045')
        d.line((0, 27, 31, 27), fill='#665332')
    elif kind == 'border':
        d.line((4, 0, 4, 31), fill='#665332')
        d.line((27, 0, 27, 31), fill='#665332')
    return image


def generate(pack):
    icons = {}
    for number in range(13):
        color = '#237b52' if number == 0 else '#b83845' if number in {1, 3, 5, 7, 9, 12} else '#293342'
        for active in (False, True):
            image = tile(color, active)
            label(ImageDraw.Draw(image), str(number), 7, scale=3)
            icons[f'number_{number}' + ('_active' if active else '')] = image
        icons[f'number_{number}_selected'] = selected(icons[f'number_{number}'])
    for amount, color in ((1, '#9b6823'), (5, '#9b6823'), (25, '#9b6823')):
        icons[f'stake_{amount}'] = chip(str(amount), color)
    for name, color in (('red', '#b83845'), ('black', '#293342')):
        image = chip('', color)
        d = ImageDraw.Draw(image)
        d.polygon(((16, 8), (22, 15), (16, 22), (10, 15)), fill=IVORY)
        icons[name] = image
    for name, text, color in (('even', '2', '#286f80'), ('odd', '1', '#6c4891')):
        image = tile(color)
        d = ImageDraw.Draw(image)
        label(d, text, 8)
        for x in ((12, 19) if name == 'even' else (16,)):
            d.rectangle((x - 1, 22, x + 1, 24), fill=GOLD)
        icons[name] = image
    for name, top, bottom, color in (('low', '1', '6', '#286f80'), ('high', '7', '12', '#6c4891')):
        image = tile(color)
        d = ImageDraw.Draw(image)
        label(d, top, 5, scale=1)
        label(d, bottom, 20, scale=1)
        points = ((12, 12), (20, 12), (16, 17)) if name == 'low' else ((12, 17), (20, 17), (16, 12))
        d.polygon(points, fill=GOLD)
        icons[name] = image
    image = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(image)
    d.ellipse((1, 3, 30, 31), fill='#171c25')
    d.ellipse((1, 0, 30, 29), fill=GOLD)
    for index in range(12):
        d.pieslice((4, 3, 27, 26), index * 30, (index + 1) * 30,
                   fill='#25704e' if index == 0 else '#b83845' if index % 2 else '#25303c')
    d.ellipse((10, 9, 21, 20), fill=GOLD, outline=IVORY)
    d.line((16, 7, 16, 22), fill=IVORY, width=2)
    d.line((9, 14, 23, 14), fill=IVORY, width=2)
    d.ellipse((23, 5, 27, 9), fill=IVORY)
    icons['spin'] = image

    for name in ['stake_1', 'stake_5', 'stake_25', 'red', 'black', 'even', 'odd', 'low', 'high']:
        icons[name + '_selected'] = selected(icons[name])
    for name, color in [('win', '#268651'), ('loss', '#a93343'), ('close', '#a93343')]:
        image = tile(color)
        d = ImageDraw.Draw(image)
        if name == 'win':
            d.line((8, 15, 24, 15), fill=IVORY, width=4)
            d.line((16, 7, 16, 23), fill=IVORY, width=4)
        elif name == 'loss':
            d.line((8, 15, 24, 15), fill=IVORY, width=4)
        else:
            d.line((9, 8, 23, 22), fill=IVORY, width=4)
            d.line((9, 22, 23, 8), fill=IVORY, width=4)
        icons[name] = image
    image = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(image)
    d.rectangle((6, 4, 25, 27), fill='#ead7a5', outline=GOLD, width=2)
    d.rectangle((4, 2, 23, 6), fill=IVORY, outline=GOLD)
    d.rectangle((8, 26, 27, 30), fill=IVORY, outline=GOLD)
    for y in (10, 15, 20):
        d.line((10, y, 21, y), fill='#74603d', width=2)
    icons['stats'] = image
    icons['balance'] = chip('', '#a07429')
    ImageDraw.Draw(icons['balance']).polygon(((16, 8), (21, 15), (16, 22), (11, 15)), fill=IVORY)

    image = tile('#9b6823')
    d = ImageDraw.Draw(image)
    d.rectangle((7, 7, 19, 23), fill='#bba979', outline=GOLD)
    d.rectangle((12, 5, 24, 22), fill=IVORY, outline=GOLD)
    d.polygon(((18, 8), (22, 13), (18, 18), (14, 13)), fill='#b83845')
    icons['deal'] = image
    for action, color in [('hit', '#237b52'), ('stand', '#9b6823'), ('double', '#286f80')]:
        for disabled in (False, True):
            image = tile('#293342' if disabled else color)
            d = ImageDraw.Draw(image)
            ink = '#7f8993' if disabled else IVORY
            if action == 'hit':
                d.line((8, 14, 24, 14), fill=ink, width=4)
                d.line((16, 6, 16, 22), fill=ink, width=4)
            elif action == 'stand':
                d.rectangle((9, 7, 23, 21), fill=ink)
            else:
                label(d, '2', 7, ink, scale=3)
                d.line((6, 21, 10, 25), fill=ink, width=2)
                d.line((6, 25, 10, 21), fill=ink, width=2)
            icons[action + ('_disabled' if disabled else '')] = image

    # The custom items carry their own felt backing; no global vanilla GUI texture override.
    for name, image in list(icons.items()):
        backed = background()
        backed.alpha_composite(image)
        icons[name] = backed
    for kind in ('felt', 'rail', 'border'):
        icons['background_' + kind] = background(kind)

    for identifier, image in icons.items():
        base = pack / 'assets' / 'casinocore'
        path = f'item/roulette/gui/{identifier}'
        files = {
            base / 'models' / f'{path}.json': {
                'parent': 'minecraft:item/generated', 'textures': {'layer0': f'casinocore:{path}'},
                'display': {'gui': {'scale': [1.125, 1.125, 1]}}},
            base / 'items' / 'roulette' / 'gui' / f'{identifier}.json': {
                'model': {'type': 'minecraft:model', 'model': f'casinocore:{path}'}}
        }
        for target, data in files.items():
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
        texture = base / 'textures' / f'{path}.png'
        texture.parent.mkdir(parents=True, exist_ok=True)
        image.save(texture)

    # Preview against a neutral inventory-like background, with nearest-neighbour pixels.
    slots = {7: 'stats', 28: 'red_selected', 29: 'black', 30: 'even', 31: 'odd',
             32: 'low', 33: 'high', 40: 'spin', 46: 'stake_1', 47: 'stake_5_selected',
             48: 'stake_25', 50: 'balance', 53: 'close'}
    slots.update({slot: f'number_{n}' for n, slot in enumerate([10,11,12,13,14,15,16,19,20,21,22,23,24])})
    preview = Image.new('RGBA', (9 * 32, 6 * 32), '#12382f')
    for slot in range(54):
        name = slots.get(slot, 'background_rail' if slot < 9 or slot >= 45 else
                         'background_border' if slot % 9 in (0,8) else 'background_felt')
        preview.alpha_composite(icons[name], ((slot % 9) * 32, (slot // 9) * 32))
    target = ROOT / 'assets' / 'roulette' / 'preview' / 'roulette_gui_icons.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    preview.resize((864, 576), Image.Resampling.NEAREST).save(target)
    return len(icons)


if __name__ == '__main__':
    print(f'Generated {generate(ROOT / "assets/roulette/resource_pack")} Roulette GUI icons')
