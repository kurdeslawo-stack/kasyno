"""Animate only the front LAINA badge; retain the static atlas for Blockbench."""
import base64
import json
import re
import math
from PIL import Image, ImageDraw

GLYPHS = {
    'L': ('100', '100', '100', '100', '111'),
    'A': ('010', '101', '111', '101', '101'),
    'I': ('111', '010', '010', '010', '111'),
    'N': ('1001', '1101', '1011', '1001', '1001'),
}


def badge(image, brightness):
    result = image.copy()
    draw = ImageDraw.Draw(result)
    draw.rectangle((2, 362, 125, 381), fill='#182821')
    ink = tuple(round(channel * brightness) for channel in (255, 63, 72)) + (255,)
    x = 64 - 62 // 2
    for letter in 'LAINA':
        glyph = GLYPHS[letter]
        for y, row in enumerate(glyph):
            for column, pixel in enumerate(row):
                if pixel == '1':
                    draw.rectangle((x + column * 3, 364 + y * 3,
                                    x + column * 3 + 2, 366 + y * 3), fill=ink)
        x += (len(glyph[0]) + 1) * 3
    return result


def effects(static, index, roulette=False):
    phase=index/16
    frame=badge(static,.85+.15*(1-math.cos(phase*2*math.pi))/2)
    draw=ImageDraw.Draw(frame)
    perimeter=([(x,8) for x in range(8,312)]+[(311,y) for y in range(9,168)]
               +[(x,167) for x in range(310,7,-1)]+[(8,y) for y in range(166,8,-1)])
    for offset in (0,len(perimeter)//2):
        head=int(phase*len(perimeter))+offset
        for distance in range(80):
            x,y=perimeter[(head-distance)%len(perimeter)]
            strength=(1-distance/80)**2
            color=tuple(round(a+(b-a)*strength) for a,b in zip((49,96,68),(194,164,93)))
            draw.rectangle((x,y,x+1,y+1),fill=color)
    for chip in range(3):
        pulse=(1+math.cos((phase-chip/3)*2*math.pi))/2
        color=tuple(round(a+(b-a)*pulse**3) for a,b in zip((163,129,65),(255,235,156)))
        draw.ellipse((chip*32+4,436,chip*32+19,451),outline=color,width=2)
        x=chip*96
        draw.rectangle((x+3,323,x+76,340),outline=color,width=1)
        if not roulette:
            x=(103,144,185)[chip]
            draw.rounded_rectangle((x,102,x+31,138),radius=4,outline=color,width=2)
    if roulette:
        angle=phase*360
        for trail in range(7):
            strength=1-trail/7
            color=tuple(round(a+(b-a)*strength) for a,b in zip((179,138,67),(255,244,193)))
            draw.arc((355,3,477,125),angle-trail*9,angle-trail*9+8,fill=color,width=2)
    return frame


def generate(pack):
    texture = pack / 'assets/casinocore/textures/entity/roulette_table.png'
    original = Image.open(texture).convert('RGBA')
    assert original.size == (512, 512)
    static = badge(original, 1.0)
    static.save(texture)
    frames = [effects(static, index, roulette=True) for index in range(16)]
    strip = Image.new('RGBA', (512, 512 * len(frames)))
    for index, frame in enumerate(frames):
        strip.paste(frame, (0, 512 * index))
    animated = texture.with_name('roulette_table_neon.png')
    strip.save(animated)
    metadata = {'animation': {'width': 512, 'height': 512,
                              'frametime': 4, 'interpolate': True}}
    animated.with_suffix('.png.mcmeta').write_text(
        json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    atlas = pack / 'assets/minecraft/atlases/blocks.json'
    atlas.write_text(json.dumps({'sources': [{'type': 'minecraft:single',
        'resource': 'casinocore:entity/roulette_table_neon',
        'sprite': 'casinocore:entity/roulette_table'}]}, indent=2) + '\n', encoding='utf-8')
    # Embedded editor texture stays square; geometry, UVs and pivots are untouched.
    encoded = 'data:image/png;base64,' + base64.b64encode(texture.read_bytes()).decode('ascii')
    for path in (pack.parent / 'blockbench').glob('*.bbmodel'):
        contents = path.read_text(encoding='utf-8')
        contents = re.sub(r'data:image/png;base64,[A-Za-z0-9+/=]+', encoded, contents)
        path.write_text(contents, encoding='utf-8', newline='\n')
    preview = pack.parent / 'preview'
    preview.mkdir(exist_ok=True)
    crops = [frame.crop((0, 360, 128, 384)).resize((768, 144), Image.Resampling.NEAREST)
             for frame in frames]
    crops[0].save(preview / 'laina_neon.gif', save_all=True, append_images=crops[1:],
                  duration=200, loop=0)
    from furniture_preview import render
    model = json.loads((pack / 'assets/casinocore/models/entity/roulette_table.json').read_text(encoding='utf-8'))
    render(model['elements'], static, preview / 'roulette_table_hero.png', head='ROULETTE / 01', sub='LAINA   |   WALNUT - EMERALD - BRASS')
    render(model['elements'], static, preview / 'roulette_table_top.png', az=0, elev=80, head='ROULETTE / TOP')
    top = [f.crop((0,0,480,176)).resize((960,352),Image.Resampling.NEAREST) for f in frames]
    top[0].save(preview / 'roulette_effects.gif',save_all=True,append_images=top[1:],duration=200,loop=0)
    print('Roulette: 16 frames, perimeter trails, stakes, chips, wheel accent and LAINA pulse')
