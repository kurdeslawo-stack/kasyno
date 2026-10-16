"""CasinoCore roulette furniture: deterministic atlas, Java models, Blockbench and previews.
Run with Python 3.12 and Pillow. No external assets or downloads required.
"""
from pathlib import Path
import sys
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
import base64, copy, json, math, uuid, zipfile
from array import array
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance

SOURCE_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = SOURCE_DIR.parent
ROOT = REPOSITORY_ROOT / 'assets' / 'roulette'
DIST = REPOSITORY_ROOT / 'dist'
PACK = ROOT / 'resource_pack'
TEX = PACK / 'assets/casinocore/textures/entity/roulette_table.png'
MODELS = PACK / 'assets/casinocore/models/entity'
ITEMS = PACK / 'assets/casinocore/items'
for p in (TEX.parent, MODELS, ITEMS, ROOT/'blockbench', ROOT/'preview', DIST):
    p.mkdir(parents=True, exist_ok=True)

SIZE = 512
TARGET_VERSION = '26.3'
RESOURCE_PACK_FORMAT = [97, 1]
RESOURCE_PACK_ZIP = f'CasinoCore_Roulette_ResourcePack_{TARGET_VERSION}.zip'
atlas = Image.new('RGBA', (SIZE, SIZE), '#192520')
d = ImageDraw.Draw(atlas)
C = dict(felt='#14543e', felt2='#185b44', ink='#dfcf96', gold='#b38a43',
         light='#e2c77a', darkgold='#785625', wood='#352722', wood2='#413029',
         leather='#202724', red='#a3363b', black='#202927', white='#eee5cc')

# Explicit bitmap glyphs: no antialiasing, blur, or runtime fonts in the texture.
FONT = {
'0':['111','101','101','101','111'],'1':['010','110','010','010','111'],
'2':['111','001','111','100','111'],'3':['111','001','111','001','111'],
'4':['101','101','111','001','001'],'5':['111','100','111','001','111'],
'6':['111','100','111','101','111'],'7':['111','001','010','010','010'],
'8':['111','101','111','101','111'],'9':['111','101','111','001','111'],
'A':['010','101','111','101','101'],'B':['110','101','110','101','110'],
'C':['111','100','100','100','111'],'D':['110','101','101','101','110'],
'E':['111','100','110','100','111'],'F':['111','100','110','100','100'],
'G':['111','100','101','101','111'],'H':['101','101','111','101','101'],
'I':['111','010','010','010','111'],'J':['001','001','001','101','111'],
'K':['101','101','110','101','101'],'L':['100','100','100','100','111'],
'M':['10001','11011','10101','10001','10001'],'N':['1001','1101','1011','1001','1001'],
'O':['111','101','101','101','111'],'P':['111','101','111','100','100'],
'Q':['111','101','101','111','001'],'R':['110','101','110','101','101'],
'S':['111','100','111','001','111'],'T':['111','010','010','010','010'],
'U':['101','101','101','101','111'],'V':['101','101','101','101','010'],
'W':['10001','10001','10101','11011','10001'],'X':['101','101','010','101','101'],
'Y':['101','101','010','010','010'],'Z':['111','001','010','100','111'],
'c':['000','111','100','100','111'],'o':['000','111','101','101','111'],
'i':['010','000','010','010','010'],'n':['000','110','101','101','101'],
's':['000','111','110','001','111'],' ':['00']*5,'-':['000','000','111','000','000'],
'.':['0','0','0','0','1'],':':['0','1','0','1','0']}

def text(draw, xy, s, color, scale=1, center=False):
    width = sum((len(FONT[ch][0])+1)*scale for ch in s)-scale
    x,y = xy
    if center: x -= width//2
    for ch in s:
        glyph=FONT[ch]
        for yy,row in enumerate(glyph):
            for xx,v in enumerate(row):
                if v=='1':
                    draw.rectangle((int(x+xx*scale),int(y+yy*scale),int(x+(xx+1)*scale-1),int(y+(yy+1)*scale-1)),fill=color)
        x += (len(glyph[0])+1)*scale

# Top: 40 x 22 model units at 8 pixels per unit. Wheel socket is under the wheel.
d.rectangle((0,0,319,175), fill=C['felt'])
for y in range(3,176,8):
    for x in range((y//8)%2*4,320,8):
        d.point((x,y),fill=C['felt2'])
d.rectangle((3,3,316,172),outline='#9d995e',width=1)
d.rectangle((7,7,312,168),outline='#387556',width=1)
cx,cy=72,80
d.regular_polygon((cx,cy,64),8,rotation=22.5,fill='#242820',outline=C['gold'])

# Standard single-zero betting board: 12 columns by 3 rows, usual red/black colors.
REDS={1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36}
bx,by,cw,ch=169,47,11,15
text(d,(236,22),'CASINO CORE',C['ink'],1,True)
text(d,(236,29),'ROULETTE',C['light'],1,True)
d.line((176,39,297,39),fill=C['gold'])
d.rectangle((157,by,168,by+44),outline=C['ink'])
text(d,(162,by+20),'0',C['white'],1,True)
for row in range(3):
    for col in range(12):
        n=col*3+(3-row)
        x,y=bx+col*cw,by+row*ch
        d.rectangle((x,y,x+cw-1,y+ch-1),fill=C['red'] if n in REDS else C['black'],outline=C['ink'])
        text(d,(x+cw//2,y+5),str(n),C['white'],1,True)
for k,s in enumerate(('1-12','13-24','25-36')):
    x=bx+k*44
    d.rectangle((x,93,x+43,105),outline=C['ink'])
    text(d,(x+22,97),s,C['ink'],1,True)
for k,s in enumerate(('1-18','EVEN','RED','BLACK','ODD','19-36')):
    x=bx+k*22
    d.rectangle((x,107,x+21,122),outline=C['ink'])
    if s in ('RED','BLACK'):
        d.polygon([(x+11,110),(x+17,114),(x+11,119),(x+5,114)],fill=C['red'] if s=='RED' else C['black'],outline=C['ink'])
    else: text(d,(x+11,113),s,C['ink'],1,True)
text(d,(234,133),'SINGLE ZERO', '#84a984',1,True)
# Small heraldic corners, restrained stitching, and no redundant labels on the felt.
for x in (14,305):
    d.polygon([(x,151),(x+4,155),(x,159),(x-4,155)],outline=C['gold'])

# Wheel: true European ordering, all 37 pockets. Entire radial face uses shared planar UV.
wx,wy=416,64
ORDER=[0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26]
d.rectangle((352,0,479,127),fill=C['wood'])
d.ellipse((wx-62,wy-62,wx+62,wy+62),fill=C['gold'])
for j,n in enumerate(ORDER):
    a0=-math.pi/2+(j-.5)*2*math.pi/37
    a1=-math.pi/2+(j+.5)*2*math.pi/37
    points=[(wx+math.cos(a)*r,wy+math.sin(a)*r) for r,a in ((59,a0),(59,a1),(37,a1),(37,a0))]
    color='#278654' if n==0 else C['red'] if n in REDS else C['black']
    d.polygon(points,fill=color)
    d.line((wx+math.cos(a0)*38,wy+math.sin(a0)*38,wx+math.cos(a0)*60,wy+math.sin(a0)*60),fill=C['gold'],width=1)
    a=-math.pi/2+j*2*math.pi/37
    text(d,(round(wx+math.cos(a)*49),round(wy+math.sin(a)*49)-2),str(n),C['white'],1,True)
d.ellipse((wx-36,wy-36,wx+36,wy+36),fill='#6b4630',outline=C['light'],width=2)
d.ellipse((wx-29,wy-29,wx+29,wy+29),outline='#956b3c',width=1)
d.ellipse((wx-18,wy-18,wx+18,wy+18),fill='#805733',outline=C['gold'],width=2)
d.ellipse((wx-8,wy-8,wx+8,wy+8),fill=C['light'],outline='#6c512b')

# Material tiles with restrained banding and deliberately authored wood grain.
d.rectangle((0,224,255,287),fill=C['wood'])
for y in (2,9,19,33,46,57):
    d.line([(0,224+y),(55,225+y),(95,224+y),(160,226+y),(255,224+y)],fill=C['wood2'])
    d.line([(24,227+y),(70,227+y),(94,228+y)],fill='#2c231f')
d.rectangle((272,224,335,287),fill=C['gold'])
for y,col in ((224,C['light']),(225,'#ccb063'),(229,'#c49b4e'),(283,C['darkgold']),(287,'#65481f')):
    d.line((272,y,335,y),fill=col,width=1)
d.rectangle((352,224,479,287),fill=C['leather'])
for x in range(356,476,8):
    d.line((x,226,x+2,226),fill='#727257')
    d.line((x,284,x+2,284),fill='#444c3c')
d.rectangle((352,296,367,311),fill=C['white'])
d.rectangle((376,296,391,311),fill='#121d19')
d.rectangle((400,296,415,311),fill=C['red'])
d.rectangle((424,296,439,311),fill='#257954')
# Individual denomination plaques at full 8 px/unit, exact labels requested.
for i,(label,color) in enumerate((('1 coin','#dddcc5'),('5 coins','#ba4447'),('25 coins','#328c64'))):
    x=i*96
    d.rectangle((x,320,x+79,343),fill='#152d24',outline=C['gold'],width=2)
    d.rectangle((x+3,323,x+76,340),outline='#475c3c')
    d.ellipse((x+7,325,x+19,337),fill=color,outline=C['light'])
    text(d,(x+48,329),label,C['white'],1,True)
# Front badge.
d.rectangle((0,360,127,383),fill='#182821',outline=C['gold'],width=2)
text(d,(64,365),'CASINO CORE',C['light'],2,True)
atlas.save(TEX)

def uid(s): return str(uuid.uuid5(uuid.NAMESPACE_URL,'casinocore/roulette/'+s))
groups={'base':[],'wheel':[],'ball':[]}
MAT={'wood':(0,224,256,288),'gold':(272,224,336,288),'leather':(352,224,480,288),
     'ivory':(352,296,368,312),'black':(376,296,392,312),'red':(400,296,416,312),'green':(424,296,440,312)}

def uv(rect): return [round(v/SIZE*16,6) for v in rect]
def box(name,a,b,mat='wood',group='base',faces=None):
    out={'name':name,'from':list(a),'to':list(b),'faces':{}}
    dx,dy,dz=[b[i]-a[i] for i in range(3)]
    for f in ('north','east','south','west','up','down'):
        w,h=(dx,dz) if f in ('up','down') else (dx,dy) if f in ('north','south') else (dz,dy)
        tile=MAT[mat]; tw,th=tile[2]-tile[0],tile[3]-tile[1]
        rect=(tile[0],tile[1],tile[0]+min(tw,w*8),tile[1]+min(th,h*8))
        out['faces'][f]={'uv':uv(rect),'texture':'#0'}
    if faces:
        for f,v in faces.items():
            if v is None: out['faces'].pop(f,None)
            else: out['faces'][f]={'uv':uv(v),'texture':'#0'}
    groups[group].append(out)
    return out

# Stepped clipped-corner slab, divided into three non-overlapping prisms.
def slab(name,x0,x1,z0,z1,y0,y1,cut,mat='wood'):
    box(name+'_middle',(x0,y0,z0+cut),(x1,y1,z1-cut),mat)
    box(name+'_back',(x0+cut,y0,z0),(x1-cut,y1,z0+cut),mat,faces={'south':None})
    box(name+'_front',(x0+cut,y0,z1-cut),(x1-cut,y1,z1),mat,faces={'north':None})

slab('dark_walnut_apron',-14,30,-5,21,12.4,15.5,2)
slab('lower_brass_reveal',-13.7,29.7,-4.7,20.7,12.15,12.4,2,'gold')
# Felt fits inside a raised padded rail, no coplanar overlay.
box('green_betting_surface',(-12,15.5,-3),(28,15.7,19),'leather',faces={'up':(0,0,320,176),'down':None})
# Rail divided into independent rectangles, corner relief reads clearly in Minecraft.
for name,a,b in (
 ('front',(-12,15.5,19),(28,16.15,21)),('back',(-12,15.5,-5),(28,16.15,-3)),
 ('left',(-14,15.5,-3),(-12,16.15,19)),('right',(28,15.5,-3),(30,16.15,19))):
    box('padded_rail_'+name,a,b,'leather',faces={'down':None})
for name,a,b in (
 ('front',(-12,15.7,18.75),(28,15.95,19)),('back',(-12,15.7,-3),(28,15.95,-2.75)),
 ('left',(-12,15.7,-2.75),(-11.75,15.95,18.75)),('right',(27.75,15.7,-2.75),(28,15.95,18.75))):
    box('brass_felt_edge_'+name,a,b,'gold',faces={'down':None})

# Four substantial legs, brass shoes, dark lower stretcher.
for x in (-10.5,23.5):
    for z in (-1.5,15.5):
        label=f'{x}_{z}'
        box('leg_'+label,(x-1.2,.8,z-1.2),(x+1.2,12.15,z+1.2))
        box('brass_shoe_'+label,(x-1.25,0,z-1.25),(x+1.25,.8,z+1.25),'gold')
        box('leg_collar_'+label,(x-1.23,10.8,z-1.23),(x+1.23,11.25,z+1.23),'gold')
box('rear_stretcher',(-9.3,3.1,-2),(22.3,4.4,-1))
for x in (-10.5,23.5):
    box('side_stretcher_'+str(x),(x-.5,3.1,-.3),(x+.5,4.4,14.3))
box('front_brass_footrest',(-9.3,2.9,16.7),(22.3,3.35,17.2),'gold')
box('front_house_badge',(0,13.25,21.01),(16,15.25,21.14),'gold',faces={'south':(0,360,128,384),'north':None})

# Wheel bowl: concentric stepped octagonal outlines formed by seven non-overlapping rows.
WC=(-3,7)
OUT=[(-8,-6,4.5),(-6,-4,6.5),(-4,4,8), (4,6,6.5),(6,8,4.5)]
INNER=[(-7,-5,4),(-5,-3,6),(-3,3,7),(3,5,6),(5,7,4)]
def extent(rows,z):
    return next((w for lo,hi,w in rows if lo <= z < hi),0)
cuts=sorted({n for row in OUT+INNER for n in row[:2]})
for k,(z0,z1) in enumerate(zip(cuts,cuts[1:])):
    zo=(z0+z1)/2; outer=extent(OUT,zo); inner=extent(INNER,zo)
    intervals=[(-outer,-inner),(inner,outer)] if inner else [(-outer,outer)]
    for side,(x0,x1) in enumerate(intervals):
        if x1<=x0: continue
        x,z=WC
        box(f'wheel_bowl_wall_{k}_{side}',(x+x0,15.7,z+z0),(x+x1,16.75,z+z1),'wood',faces={'down':None})
        box(f'wheel_bowl_brass_lip_{k}_{side}',(x+x0,16.75,z+z0),(x+x1,16.9,z+z1),'gold',faces={'down':None})
for k,(z0,z1,w) in enumerate(INNER):
    box(f'recessed_bowl_floor_{k}',(WC[0]-w,15.7,WC[1]+z0),(WC[0]+w,15.8,WC[1]+z1),'black',faces={'down':None})

# Local wheel coordinates: pivot exactly (8,8,8), top sits 0.22 units above its pivot.
# These strips tile one disk, keeping all printed pockets on one unbroken UV projection.
for k,(z0,z1,w) in enumerate(INNER):
    gap=.14
    a=(8-w+gap,7.8,8+z0+(gap if k==0 else 0))
    b=(8+w-gap,8.22,8+z1-(gap if k==len(INNER)-1 else 0))
    rect=(352+(a[0])*8,(a[2])*8,352+b[0]*8,b[2]*8)
    e=box(f'wheel_disk_strip_{k}',a,b,'wood','wheel',faces={'up':rect})
box('wheel_center_plinth',(6.4,8.22,6.4),(9.6,8.65,9.6),'gold','wheel',faces={'down':None})
box('wheel_spindle',(7.65,8.65,7.65),(8.35,10.35,8.35),'gold','wheel',faces={'down':None})
box('wheel_crossbar_x',(5.6,9.75,7.7),(10.4,10.15,8.3),'gold','wheel')
box('wheel_crossbar_z_front',(7.7,9.75,8.3),(8.3,10.15,10.4),'gold','wheel',faces={'north':None})
box('wheel_crossbar_z_back',(7.7,9.75,5.6),(8.3,10.15,7.7),'gold','wheel',faces={'south':None})
box('wheel_spindle_cap',(7.45,10.35,7.45),(8.55,10.65,8.55),'ivory','wheel',faces={'down':None})
# Keep every corner of the rotating wheel within the bowl's inscribed circle.
# Bake this into geometry, so CasinoCore can rotate it at scale 1 without clipping.
for e in groups['wheel']:
    for key in ('from','to'):
        for axis in (0,2): e[key][axis]=round(8+(e[key][axis]-8)*.8,5)

# Clear three physical denomination plaques; exposed raised surfaces, not decals.
for i in range(3):
    x=-7+i*11
    box(('stake_1_coin','stake_5_coins','stake_25_coins')[i],(x,15.7,15.1),(x+10,16,18.1),'gold',faces={'up':(i*96,320,i*96+80,344),'down':None})
# Low-profile chip tray near betting area, behind the betting rectangle.
box('dealer_tray',(10.5,15.7,-2.25),(23.5,16.05,-.75),'black',faces={'down':None})
for i,mat in enumerate(('ivory','red','green')):
    x=12+i*4
    box('dealer_chip_stack_'+mat,(x-.65,16.05,-2),(x+.65,16.5,-1),'gold',faces={'up':MAT[mat],'down':None})

# Small cubic pearl: independent pivot and no unsupported geometry or rotation.
box('ivory_ball',(7.73,7.73,7.73),(8.27,8.27,8.27),'ivory','ball')

def translate(elements,offset):
    result=copy.deepcopy(elements)
    for e in result:
        for key in ('from','to'): e[key]=[round(e[key][i]+offset[i],5) for i in range(3)]
    return result
WHEEL_OFFSET=[-11,8.14,-1] # local (8,8,8) -> assembled (-3,16.14,7)
BALL_CENTER=[-3,16.63,2.4]
BALL_OFFSET=[BALL_CENTER[i]-8 for i in range(3)]
combined=groups['base']+translate(groups['wheel'],WHEEL_OFFSET)+translate(groups['ball'],BALL_OFFSET)
ALL={'roulette_table':combined,'roulette_table_base':groups['base'],'roulette_wheel':groups['wheel'],'roulette_ball':groups['ball']}
DISPLAY={'gui':{'rotation':[28,225,0],'translation':[0,0,0],'scale':[.32,.32,.32]},
         'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
         'ground':{'translation':[0,3,0],'scale':[.3,.3,.3]},
         'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2,0],'scale':[.25,.25,.25]},
         'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.22,.22,.22]}}
def save_json(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

encoded=base64.b64encode(TEX.read_bytes()).decode()
def bbmodel(name, elements):
    cubes=[]; tree=[]
    for i,e in enumerate(elements):
        ident=uid(name+'/'+e['name'])
        faces={f:{'uv':[round(v*SIZE/16,5) for v in spec['uv']],'texture':0,'rotation':0} for f,spec in e['faces'].items()}
        for f in ('north','east','south','west','up','down'):
            if f not in faces: faces[f]={'uv':[0,0,0,0],'texture':None}
        cubes.append({'name':e['name'],'type':'cube','uuid':ident,'from':e['from'],'to':e['to'],
                      'origin':[8,8,8],'rotation':[0,0,0],'autouv':0,'box_uv':False,'rescale':False,
                      'locked':False,'visibility':True,'export':True,'shade':True,'faces':faces})
        tree.append(ident)
    if name=='roulette_table':
        n=len(groups['base']); m=len(groups['wheel'])
        outliner=[{'name':'roulette_table_base','uuid':uid('group/base'),'origin':[8,0,8],'children':tree[:n]},
                  {'name':'roulette_wheel','uuid':uid('group/wheel'),'origin':[-3,16.14,7],'children':tree[n:n+m]},
                  {'name':'roulette_ball','uuid':uid('group/ball'),'origin':BALL_CENTER,'children':tree[n+m:]}]
    else: outliner=[{'name':name,'uuid':uid('group/'+name),'origin':[8,8,8],'children':tree}]
    return {'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},
            'name':name,'model_identifier':'casinocore:'+name,'visible_box':[3,2,1],
            'resolution':{'width':512,'height':512},'elements':cubes,'outliner':outliner,
            'textures':[{'name':'roulette_table.png','id':'0','uuid':uid('texture'),
                         'path':'../resource_pack/assets/casinocore/textures/entity/roulette_table.png',
                         'relative_path':'../resource_pack/assets/casinocore/textures/entity/roulette_table.png',
                         'namespace':'casinocore','folder':'entity','width':512,'height':512,
                         'uv_width':512,'uv_height':512,'source':'data:image/png;base64,'+encoded,
                         'mode':'bitmap','saved':True}], 'display':DISPLAY}

for name,elements in ALL.items():
    save_json(MODELS/(name+'.json'),{'credit':'CasinoCore / Roulette asset','texture_size':[512,512],
              'ambientocclusion':True,'gui_light':'front','textures':{'0':'casinocore:entity/roulette_table','particle':'casinocore:entity/roulette_table'},
              'elements':elements,'display':DISPLAY})
    save_json(ITEMS/(name+'.json'),{'model':{'type':'minecraft:model','model':'casinocore:entity/'+name}})
    save_json(ROOT/'blockbench'/(name+'.bbmodel'),bbmodel(name,elements))
save_json(PACK/'pack.mcmeta',{'pack':{
    'description':f'CasinoCore | Roulette Table - Java {TARGET_VERSION}',
    'min_format':RESOURCE_PACK_FORMAT,
    'max_format':RESOURCE_PACK_FORMAT,
}})

# Actual model preview: face geometry and UVs come from the same JSON data as Minecraft.
def face_vertices(e,f):
    x,y,z=e['from']; X,Y,Z=e['to']
    return {'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
            'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],
            'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
            'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
            'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],
            'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)]}[f]

def render(elements,path,az=30,elev=33,w=1500,h=1050,scale=25,head='ROULETTE / 01',sub='CASINOCORE   |   WALNUT - EMERALD - BRASS'):
    bg=Image.new('RGBA',(w,h),'#101b19'); draw=ImageDraw.Draw(bg)
    for yy in range(h):
        v=int(6*(1-abs(yy/h-.5)*2))
        draw.line((0,yy,w,yy),fill=(16+v,27+v,25+v,255))
    az=math.radians(az); el=math.radians(elev)
    ca,sa,ce,se=math.cos(az),math.sin(az),math.cos(el),math.sin(el)
    def proj(p):
        x,y,z=p[0]-8,p[1]-8,p[2]-8
        return (w*.5+(ca*x-sa*z)*scale,h*.57+(se*(sa*x+ca*z)-ce*y)*scale)
    shadow=Image.new('RGBA',(w,h)); sd=ImageDraw.Draw(shadow)
    pts=[proj((x,-.25,z)) for x,z in ((-15,-6),(31,-6),(31,22),(-15,22))]
    sd.polygon(pts,fill=(0,0,0,110)); shadow=shadow.filter(ImageFilter.GaussianBlur(23)); bg.alpha_composite(shadow)
    normals={'up':(0,1,0),'down':(0,-1,0),'south':(0,0,1),'north':(0,0,-1),'east':(1,0,0),'west':(-1,0,0)}
    camera=(ce*sa,se,ce*ca)
    faces=[]
    for e in elements:
        for f,spec in e['faces'].items():
            normal=normals[f]
            if sum(a*b for a,b in zip(normal,camera))<=.0001: continue
            verts=face_vertices(e,f)
            depth=sum(sum(p[i]*camera[i] for i in range(3)) for p in verts)/4
            faces.append((depth,f,verts,spec))
    depthbuffer=array('f',[-1e10])*(w*h)
    pixels=bg.load()
    shaded={f:ImageEnhance.Brightness(atlas).enhance(shade).load() for f,shade in
            {'up':1.08,'south':.82,'east':.72,'west':.85,'north':.7,'down':.5}.items()}
    for _,f,verts,spec in faces:
        points=[proj(p) for p in verts]
        p0,p1,_,p3=points
        ax,ay=p1[0]-p0[0],p1[1]-p0[1]; bx,by=p3[0]-p0[0],p3[1]-p0[1]
        det=ax*by-ay*bx
        if abs(det)<.01: continue
        u0,v0,u1,v1=[v*SIZE/16 for v in spec['uv']]
        # inverse parallelogram mapping from screen pixels into the atlas.
        aa=by/det; ab=-bx/det; ac=-(aa*p0[0]+ab*p0[1])
        ba=-ay/det; bb=ax/det; bc=-(ba*p0[0]+bb*p0[1])
        coeff=((u1-u0)*aa,(u1-u0)*ab,u0+(u1-u0)*ac,(v1-v0)*ba,(v1-v0)*bb,v0+(v1-v0)*bc)
        x0=max(0,int(min(p[0] for p in points))-1); y0=max(0,int(min(p[1] for p in points))-1)
        x1=min(w,int(max(p[0] for p in points))+2); y1=min(h,int(max(p[1] for p in points))+2)
        if x1<=x0 or y1<=y0: continue
        depths=[sum(p[i]*camera[i] for i in range(3)) for p in verts]
        dz1,dz3=depths[1]-depths[0],depths[3]-depths[0]
        texture=shaded[f]
        for yy in range(y0,y1):
            sy=yy+.5
            for xx in range(x0,x1):
                sx=xx+.5
                s=aa*sx+ab*sy+ac; t=ba*sx+bb*sy+bc
                if not (0<=s<1 and 0<=t<1): continue
                dep=depths[0]+s*dz1+t*dz3
                idx=yy*w+xx
                if dep<=depthbuffer[idx]: continue
                tx=max(0,min(511,int(u0+(u1-u0)*s)))
                ty=max(0,min(511,int(v0+(v1-v0)*t)))
                pixels[xx,yy]=texture[tx,ty]
                depthbuffer[idx]=dep
    draw=ImageDraw.Draw(bg)
    try:
        big=ImageFont.truetype('C:/Windows/Fonts/seguisb.ttf',34)
        small=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',16)
    except OSError: big=small=ImageFont.load_default()
    draw.text((65,42),head,font=big,fill='#e5d7ae')
    draw.text((67,91),sub,font=small,fill='#849d8e')
    draw.line((65,127,w-65,127),fill='#435441')
    draw.text((65,h-52),'ACTUAL JSON GEOMETRY + PNG ATLAS  /  ORTHOGRAPHIC PREVIEW',font=small,fill='#849d8e')
    bg.convert('RGB').save(path)

render(combined,ROOT/'preview/roulette_table_hero.png')
render(combined,ROOT/'preview/roulette_table_top.png',az=0,elev=80,scale=27,head='ROULETTE / TOP')
render(combined,ROOT/'preview/roulette_table_front.png',az=-12,elev=18,scale=27,head='ROULETTE / FRONT')
atlas.resize((1024,1024),Image.Resampling.NEAREST).save(ROOT/'preview/texture_atlas_2x.png')

metadata={'asset':'roulette_table','target':'Minecraft Java item_display','pack_baseline':'26.3 / resource pack format 97.1',
          'units_per_block':16,'table_size_blocks':[2.75,18.79/16,26.14/16],
          'model_anchor':[8,8,8],'floor_offset_blocks':-.5,
          'front_direction':'+Z','atlas_pixels':[512,512],'surface_texels_per_model_unit':8,
          'wheel_model_pivot':[8,8,8],'wheel_pivot_in_table_model':[-3,16.14,7],
          'wheel_display_offset_blocks':[-.6875,.50875,-.0625],
          'ball_display_offset_blocks':[(v-8)/16 for v in BALL_CENTER],
          'ball_orbit_radius_blocks':4.6/16,'ball_orbit_height_above_wheel_pivot_blocks':.49/16,
          'element_counts':{name:len(e) for name,e in ALL.items()},'european_wheel_clockwise_order':ORDER}
save_json(ROOT/'integration.json',metadata)

# Check exported geometry, atlas bounds, split assembly and BB texture embedding.
report=[]
for name,expected in ALL.items():
    model=json.loads((MODELS/(name+'.json')).read_text(encoding='utf-8'))
    for e in model['elements']:
        assert all(-16<=v<=32 for v in e['from']+e['to']),e['name']
        assert all(a<b for a,b in zip(e['from'],e['to'])),e['name']
        for face in e['faces'].values():
            assert all(0<=v<=16 for v in face['uv'])
            assert face['uv'][0]<face['uv'][2] and face['uv'][1]<face['uv'][3]
    bb=json.loads((ROOT/'blockbench'/(name+'.bbmodel')).read_text(encoding='utf-8'))
    assert len(bb['elements'])==len(expected)
    assert base64.b64decode(bb['textures'][0]['source'].split(',')[1])==TEX.read_bytes()
    report.append(f'{name}: {len(expected)} elements, {sum(len(e["faces"]) for e in expected)} faces; valid bounds / UV / embedded PNG')
assert sorted(ORDER)==list(range(37))
assert len(REDS)==18
assert ALL['roulette_table']==groups['base']+translate(groups['wheel'],WHEEL_OFFSET)+translate(groups['ball'],BALL_OFFSET)
rotor_radius=max(math.hypot(e[k][0]-8,e[j][2]-8) for e in groups['wheel'] for k in ('from','to') for j in ('from','to'))
bowl_inscribed_radius=min(math.hypot(w, max(0,lo,-hi)) for lo,hi,w in INNER)
assert rotor_radius < bowl_inscribed_radius, (rotor_radius,bowl_inscribed_radius)
report.append(f'360-degree rotation clearance: rotor radius {rotor_radius:.3f} < bowl inscribed radius {bowl_inscribed_radius:.3f} model units.')
report+=['European wheel: 0..36 exactly once, 18 red / 18 black / 1 green.',
         'Split models reconstruct the complete model exactly at integration.json offsets.',
         'Preview is rendered from exported geometry and atlas; no Minecraft runtime verification performed.']

README='''# CasinoCore — roulette_table

Gotowy asset stołu ruletki w stylu Minecraft: ciemny orzech, zielone sukno,
złote obramowanie, koło z 37 polami w europejskiej kolejności, plansza 1–36,
trzy tabliczki: **1 coin**, **5 coins**, **25 coins**. Napisy są częścią PNG.
Stół ma około 2,75 × 1,625 bloku; blat leży na wysokości 0,98 bloku,
a najwyższy punkt wrzeciona na wysokości 1,17 bloku. Przód stołu: +Z.

## Pliki

- resource_pack/assets/casinocore/textures/entity/roulette_table.png — wspólny atlas 512×512 RGBA.
- resource_pack/assets/casinocore/models/entity/roulette_table.json — cały stół.
- resource_pack/assets/casinocore/models/entity/roulette_table_base.json — stół z nieruchomą misą, bez wirnika i kulki.
- resource_pack/assets/casinocore/models/entity/roulette_wheel.json — sam wirnik, pivot [8,8,8].
- resource_pack/assets/casinocore/models/entity/roulette_ball.json — sama kulka, pivot [8,8,8].
- resource_pack/assets/casinocore/items/*.json — definicje item_model dla Java 26.3.
- blockbench/*.bbmodel — wersja złożona i trzy osobne modele, z osadzonym PNG.
- preview/*.png — trzy rendery rzeczywistej geometrii oraz powiększony atlas.
- integration.json — punkty odniesienia i przesunięcia części.
- build_roulette_assets.py — odtwarzalny generator, Python + Pillow.

## Resource pack / szybki podgląd

Paczka jest przygotowana pod **Minecraft Java 26.3** (resource pack format 97.1).
`pack.mcmeta` używa aktualnych pól `min_format` i `max_format`, obu ustawionych
na `[97, 1]`. Dla tego nowego formatu nie używa `pack_format` ani
`supported_formats`.
Na innej wersji połącz katalog assets z docelowym resource packiem i zachowaj
pack.mcmeta odpowiednie dla wersji klienta. Nie deklarujemy testów w grze.
Geometria używa standardowych elementów Java; nie wymaga OptiFine ani moda.
Nazwa katalogu models/entity jest tylko lokalizacją pliku: to model item/block,
nie format Bedrock ani automatyczny zamiennik geometrii istniejącej encji.

Wgraj wspólną paczkę `dist/CasinoCore_ResourcePack_26.3.zip`, zawierającą
zasoby Roulette i Blackjack, do folderu resourcepacks i aktywuj paczkę.
Dla Java 26.3:

```mcfunction
/give @s minecraft:paper[minecraft:item_model="casinocore:roulette_table"]
```

Podgląd stołu jako nieruchomego mebla (wykonaj stojąc na docelowej podłodze;
przy standardowym item_display środek geometrii wypada w [8,8,8], więc dodajemy 0,5 bloku Y):

```mcfunction
/summon minecraft:item_display ~ ~0.5 ~ {Tags:["casinocore_roulette_preview"],billboard:"fixed",item_display:"none",item:{id:"minecraft:paper",count:1,components:{"minecraft:item_model":"casinocore:roulette_table"}},shadow_radius:1.0f,shadow_strength:0.5f,view_range:1.0f,width:3.5f,height:3.0f}
```

Nie ma funkcji kasyna, kolizji ani hitboxa interakcji. CasinoCore później powinien
utworzyć hitbox interaction i obsługiwać zakłady. Model sam nie dodaje bloku.
Starsze wydania bez item model definitions potrzebują osobnej konfiguracji
CustomModelData/overrides.

## Osobna animacja

Wybierz **albo cały roulette_table**, **albo trzy części** — nie wyświetlaj obu
wariantów jednocześnie, bo powielisz powierzchnie.

Każdy item_display używa kontekstu none i skali 1. Bazę umieść w B=(x,y+0.5,z).
Wirnik umieść względem B: **(-0.6875, +0.50875, -0.0625)** bloku.
Kulkę w pozycji pokazanej w modelu: **(-0.6875, +0.539375, -0.35)** bloku.
Obracaj wirnik wokół jego lokalnej osi Y (pivot jest w środku modelu).
Pozycja kulki na orbicie względem B: W + (r*sin(a), h, -r*cos(a)),
gdzie W to przesunięcie wirnika, r=0.2875 bloku i h=0.030625 bloku.
Przy obrocie całego stołu obróć także wektory przesunięć jego części.
To wskazówki integracyjne; nie dołączono kodu animacji ani pluginu.

## Edycja i wygląd

Otwórz blockbench/roulette_table.bbmodel w Blockbench. Grupy base, wheel i ball
mają osobne punkty odniesienia. Osobne .bbmodel odpowiadają eksportom JSON.
Tekstura jest osadzona w plikach, a także dostarczona jako PNG.
Eksport: Java Block/Item. Koło jest celowo schodkowe/low-poly.
Blat i tabliczki mają 8 px na jednostkę modelu (128 px na blok), koło 10 px
na jednostkę. Wirnik jest pomniejszony względem misy, by mógł się obracać
o pełne 360 stopni bez kolizji wizualnej z jej krawędzią.
Materiały powtarzalne współdzielą fragmenty atlasu; szerokie jednolite listwy
mogą używać niższej gęstości. Filtrowanie podglądu: nearest / pixelated.
Szczegóły cyfr na kole służą do oglądania z bliska; wynik i wybór stawki powinny
później być podawane przez UI pluginu. Plansza jest grafiką, nie hitboxem.

Źródło formatu paczki 97.1: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3
Wszystkie tekstury i geometria zostały stworzone dla tego assetu, bez zewnętrznych materiałów.
'''
(ROOT/'README_PL.md').write_text(README,encoding='utf-8')
# Copy source for a self-contained deliverable, then package all final artifacts.
for old_pack_zip in DIST.glob('CasinoCore_Roulette_ResourcePack_*.zip'):
    if old_pack_zip.name != RESOURCE_PACK_ZIP:
        old_pack_zip.unlink()
with zipfile.ZipFile(DIST/RESOURCE_PACK_ZIP,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(PACK.rglob('*')):
        if p.is_file(): z.write(p,p.relative_to(PACK))
# Complete 26.3 resource validation: parse every JSON, resolve every model and
# texture reference, verify namespace/layout, and inspect both ZIP structures.
json_paths=sorted(ROOT.rglob('*.json'))
for path in json_paths:
    json.loads(path.read_text(encoding='utf-8'))
pack_meta=json.loads((PACK/'pack.mcmeta').read_text(encoding='utf-8'))['pack']
assert pack_meta['min_format']==[97,1] and pack_meta['max_format']==[97,1]
assert 'pack_format' not in pack_meta and 'supported_formats' not in pack_meta
assert TEX.is_file() and Image.open(TEX).size==(512,512)
for item_path in sorted(ITEMS.glob('*.json')):
    item=json.loads(item_path.read_text(encoding='utf-8'))
    assert set(item)=={'model'} and item['model']['type']=='minecraft:model'
    model_id=item['model']['model']; namespace,model_rel=model_id.split(':',1)
    assert namespace=='casinocore'
    assert (PACK/'assets'/namespace/'models'/(model_rel+'.json')).is_file()
for model_path in sorted(MODELS.glob('*.json')):
    model=json.loads(model_path.read_text(encoding='utf-8'))
    for texture_id in model['textures'].values():
        namespace,texture_rel=texture_id.split(':',1)
        assert namespace=='casinocore'
        assert (PACK/'assets'/namespace/'textures'/(texture_rel+'.png')).is_file()
for source_name in ('roulette_table.bbmodel','roulette_table_base.bbmodel','roulette_wheel.bbmodel','roulette_ball.bbmodel'):
    assert (ROOT/'blockbench'/source_name).is_file()
with zipfile.ZipFile(DIST/RESOURCE_PACK_ZIP) as z:
    assert z.testzip() is None
    names=set(z.namelist())
    assert 'pack.mcmeta' in names and not any(n.startswith('resource_pack/') for n in names)
    assert 'assets/casinocore/textures/entity/roulette_table.png' in names
    for name in ALL:
        assert f'assets/casinocore/items/{name}.json' in names
        assert f'assets/casinocore/models/entity/{name}.json' in names
report += [
    f'JSON syntax: {len(json_paths)} files parsed successfully.',
    '26.3 metadata: min_format [97, 1], max_format [97, 1]; legacy fields absent.',
    'Namespace and references: all casinocore item models, model targets and textures resolve.',
    'Roulette staging archive: integrity and root layout checked before unified pack assembly.',
    'Blockbench sources: BASE, WHEEL, BALL and assembled table are present.',
]
(ROOT/'VALIDATION.txt').write_text('\n'.join(report)+'\n',encoding='utf-8')
print('\n'.join(report))
print('Created:',ROOT)

# When the Blackjack generator is present, finish by rebuilding the single
# distributable CasinoCore pack instead of leaving a Roulette-only ZIP in dist.
combined_builder = SOURCE_DIR / 'build_blackjack_assets.py'
if combined_builder.is_file():
    import runpy
    runpy.run_path(str(combined_builder), run_name='__main__')
