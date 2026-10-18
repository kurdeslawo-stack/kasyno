"""Build the matching Blackjack furniture without modifying Roulette assets."""
import base64
import copy
import json
import math
import uuid
from pathlib import Path
from PIL import Image, ImageDraw
from build_roulette_neon import badge, effects
from furniture_preview import render
from furniture_finish import finish

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'assets/blackjack/table'
PACK = SOURCE / 'resource_pack'
LETTERS = {
    'A': ('010','101','111','101','101'), 'B': ('110','101','110','101','110'),
    'C': ('111','100','100','100','111'), 'D': ('110','101','101','101','110'),
    'E': ('111','100','110','100','111'), 'I': ('111','010','010','010','111'),
    'J': ('001','001','001','101','111'), 'K': ('101','101','110','101','101'),
    'L': ('100','100','100','100','111'), 'N': ('1001','1101','1011','1001','1001'),
    'P': ('110','101','110','100','100'), 'R': ('110','101','110','101','101'),
    'Y': ('101','101','010','010','010'), 'S': ('111','100','111','001','111'),
    'T': ('111','010','010','010','010'), '3': ('111','001','111','001','111'),
    '2': ('111','001','111','100','111'), ':': ('0','1','0','1','0'),
    ' ': ('00',)*5,
}


def text(draw, center, y, value, color='#e2c77a', scale=1):
    width = sum((len(LETTERS[c][0])+1)*scale for c in value)-scale
    x = center-width//2
    for c in value:
        for row, pixels in enumerate(LETTERS[c]):
            for col, pixel in enumerate(pixels):
                if pixel == '1':
                    draw.rectangle((x+col*scale,y+row*scale,x+(col+1)*scale-1,
                                    y+(row+1)*scale-1),fill=color)
        x += (len(LETTERS[c][0])+1)*scale


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')


def generate():
    base = PACK / 'assets/casinocore'
    from build_roulette_gui_assets import chip
    for amount in (1,5,25):
        identifier=f'effects/chip_{amount}'
        path=base/f'textures/item/{identifier}.png'
        path.parent.mkdir(parents=True,exist_ok=True)
        chip(str(amount),'#9b6823').save(path)
        # Three stepped prisms form a thick clipped token, with continuous face UVs.
        token=[]
        for n,(x0,x1,y0,y1) in enumerate(((3,13,1,3),(1,15,3,13),(3,13,13,15))):
            faces={face:{'texture':'#0','uv':[0,0,16,2]} for face in ('up','down','east','west')}
            for face in ('north','south'):
                faces[face]={'texture':'#0','uv':[x0,16-y1,x1,16-y0]}
            token.append({'from':[x0,y0,7.3],'to':[x1,y1,8.7],'faces':faces})
        write(base/f'models/item/{identifier}.json',{'textures':{'0':f'casinocore:item/{identifier}'},
              'elements':token,'gui_light':'front'})
        write(base/f'items/{identifier}.json',{'model':{'type':'minecraft:model',
              'model':f'casinocore:item/{identifier}'}})

    texture = base / 'textures/entity/blackjack_table.png'
    texture.parent.mkdir(parents=True, exist_ok=True)
    atlas = Image.open(ROOT/'assets/roulette/resource_pack/assets/casinocore/textures/entity/roulette_table.png').convert('RGBA')
    d = ImageDraw.Draw(atlas)
    d.rectangle((0,0,319,175),fill='#14543e')
    for y in range(3,176,8):
        for x in range((y//8)%2*4,320,8): d.point((x,y),fill='#185b44')
    d.rounded_rectangle((4,4,315,171),radius=12,outline='#b38a43',width=2)
    d.arc((17,18,302,152),0,180,fill='#a6a271',width=2)
    text(d,160,63,'BLACKJACK',scale=2)
    d.line((108,79,212,79),fill='#897c48')
    d.arc((21,22,298,148),0,180,fill='#406d51',width=1)
    # Branding is visual only: all actual rules and payouts remain in the plugin.
    text(d,160,15,'DEALER',scale=1)
    text(d,160,148,'PLAYER',scale=1)
    for x in (132,164):
        # The felt is 8 pixels per model unit; keep an even 2px margin around each card.
        d.rounded_rectangle((x-2,22,x+26,62),radius=3,outline='#8ba989')
    for x in (103,144,185):
        d.rounded_rectangle((x,102,x+31,138),radius=4,outline='#d9c383',width=2)
        d.polygon(((x+15,113),(x+21,120),(x+15,127),(x+9,120)),outline='#d9c383')
    for x in (47,259):
        d.ellipse((x-11,84,x+11,106),outline='#b38a43',width=2)
    # Reuse the atlas's material swatches, stake plaques, and matching front badge.
    atlas=badge(atlas,1.0)
    atlas.save(texture)
    animated=texture.with_name('blackjack_table_neon.png')

    roulette=json.loads((ROOT/'assets/roulette/resource_pack/assets/casinocore/models/entity/roulette_table_base.json').read_text())
    model=copy.deepcopy(roulette)
    model['credit']='CasinoCore / Blackjack table'
    model['textures']={'0':'casinocore:entity/blackjack_table','particle':'casinocore:entity/blackjack_table'}
    model['elements']=[e for e in model['elements'] if not e['name'].startswith(('wheel_bowl_','recessed_bowl_','dealer_','finish_'))]
    def box(name,start,end,rect):
        uv=[v/32 for v in rect]
        model['elements'].append({'name':name,'from':start,'to':end,
            'faces':{f:{'uv':uv,'texture':'#0'} for f in ('up','down','north','south','east','west')}})
    box('dealer_chip_tray',[-8,15.72,-1],[-1,16.2,2],(352,224,408,248))
    for index,(material,rect) in enumerate((('ivory',(352,296,368,312)),('red',(400,296,415,311)),('green',(424,296,439,311)))):
        box('dealer_chips_'+material,[-7+index*2,16.2,-0.5],[-5.6+index*2,16.65+index*.15,1],rect)
    box('card_shoe',[20,15.72,-1],[24,17.4,3],(352,224,384,256))
    box('card_shoe_gold_trim',[20,17.4,-1],[24,17.55,3],(272,224,304,256))
    box('deck_in_shoe',[20.6,17.55,-.5],[23.4,17.8,2.5],(352,296,368,312))
    # Raised cards give the silhouette depth; their faces come from a dedicated atlas tile.
    d=ImageDraw.Draw(atlas)
    d.rectangle((448,320,479,367),fill='#eee8d3',outline='#b38a43',width=2)
    text(d,463,325,'A',color='#b8323e',scale=2)
    d.ellipse((455,343,463,351),fill='#b8323e')
    d.ellipse((463,343,471,351),fill='#b8323e')
    d.polygon(((455,348),(471,348),(463,361)),fill='#b8323e')
    d.rectangle((480,320,511,367),fill='#eee8d3',outline='#b38a43',width=2)
    text(d,495,325,'K',color='#24332d',scale=2)
    d.polygon(((495,343),(503,353),(487,353)),fill='#24332d')
    d.ellipse((487,349,495,356),fill='#24332d')
    d.ellipse((495,349,503,356),fill='#24332d')
    d.polygon(((495,352),(499,361),(491,361)),fill='#24332d')
    finish(model['elements'],atlas)
    atlas.save(texture)
    frames=[effects(atlas,index) for index in range(16)]
    strip=Image.new('RGBA',(512,512*16))
    for index,frame in enumerate(frames):
        strip.paste(frame,(0,index*512))
    strip.save(animated)
    write(animated.with_suffix('.png.mcmeta'),{'animation':{'width':512,'height':512,
          'frametime':4,'interpolate':True}})
    box('dealer_card_left',[4.5,15.72,0],[7.5,15.8,4.5],(448,320,480,368))
    box('dealer_card_right',[8.5,15.72,0],[11.5,15.8,4.5],(480,320,512,368))
    write(base/'models/entity/blackjack_table.json',model)
    write(base/'items/blackjack_table.json',{'model':{'type':'minecraft:model','model':'casinocore:entity/blackjack_table'}})
    write(PACK/'assets/minecraft/atlases/blocks.json',{'sources':[{'type':'minecraft:single',
        'resource':'casinocore:entity/blackjack_table_neon','sprite':'casinocore:entity/blackjack_table'}]})
    identifier=lambda name:str(uuid.uuid5(uuid.NAMESPACE_URL,'casinocore/blackjack/table/'+name))
    cubes=[]
    for element in model['elements']:
        faces={f:{'uv':[v*32 for v in spec['uv']],'texture':0,'rotation':0} for f,spec in element['faces'].items()}
        for f in ('up','down','north','south','east','west'): faces.setdefault(f,{'uv':[0,0,0,0],'texture':None})
        cubes.append({'name':element['name'],'type':'cube','uuid':identifier(element['name']),
            'from':element['from'],'to':element['to'],'origin':[8,8,8],'rotation':[0,0,0],
            'autouv':0,'box_uv':False,'rescale':False,'locked':False,'visibility':True,'export':True,'shade':True,'faces':faces})
    write(SOURCE/'blockbench/blackjack_table.bbmodel',{
        'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},
        'name':'blackjack_table','model_identifier':'casinocore:blackjack_table',
        'resolution':{'width':512,'height':512},'elements':cubes,
        'outliner':[{'name':'BASE','uuid':identifier('BASE'),'origin':[8,8,8],'children':[c['uuid'] for c in cubes]}],
        'textures':[{'name':'blackjack_table.png','id':'0','uuid':identifier('texture'),
            'relative_path':'../resource_pack/assets/casinocore/textures/entity/blackjack_table.png',
            'width':512,'height':512,'uv_width':512,'uv_height':512,'mode':'bitmap','saved':True,
            'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode()}],
        'display':model['display']})
    write(SOURCE/'integration.json',{'asset':'blackjack_table','item_model':'casinocore:blackjack_table',
        'target':'Minecraft Java 26.3 item_display','units_per_block':16,'model_anchor':[8,8,8],
        'floor_offset_blocks':-.5,'front_direction':'+Z','size_blocks':[2.75,1.1125,1.639375],
        'element_count':len(cubes),'dealer_position_model':[8,16,-1],'player_position_model':[8,16,15],
        'visual_only':True,'animation':'3.2-second loop: red LAINA pulse, gold felt perimeter trail, sequential chip sheen'})
    preview=SOURCE/'preview';preview.mkdir(exist_ok=True)
    animation_preview=[frame.crop((0,0,320,176)).resize((640,352),Image.Resampling.NEAREST) for frame in frames]
    animation_preview[0].save(preview/'blackjack_table_animation.gif',save_all=True,
        append_images=animation_preview[1:],duration=200,loop=0)
    render(model['elements'],atlas,preview/'blackjack_table_hero.png',head='BLACKJACK / 01',sub='LAINA   |   WALNUT - EMERALD - BRASS')
    render(model['elements'],atlas,preview/'blackjack_table_top.png',az=0,elev=80,head='BLACKJACK / TOP',sub='DEALER / PLAYER / 1 - 5 - 25')
    print(f'Blackjack table: {len(cubes)} elements, static Blockbench source + animated runtime atlas')
    return PACK


if __name__=='__main__': generate()
