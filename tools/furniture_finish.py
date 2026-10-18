"""Shared walnut, leather and brass detailing for CasinoCore tables."""
from PIL import ImageDraw


def box(name, start, end, rect):
    return {'name':name,'from':list(start),'to':list(end),'faces':{
        face:{'uv':[v/32 for v in rect],'texture':'#0'}
        for face in ('up','down','north','south','east','west')}}


def finish(elements, atlas):
    elements[:] = [e for e in elements if not e['name'].startswith('finish_')]
    d=ImageDraw.Draw(atlas)
    # Long-grain walnut, satin brass and discreet leather stitching.
    d.rectangle((0,224,255,287),fill='#30231f')
    for y in range(228,288,7):
        d.line(((0,y),(64,y+1),(126,y),(196,y+2),(255,y+1)),fill='#423029')
        d.line(((12,y+2),(69,y+3),(113,y+2)),fill='#251d1a')
    d.rectangle((352,224,479,287),fill='#172421')
    d.line((352,225,479,225),fill='#34453a')
    for x in range(356,476,8):
        for y in (228,282): d.line((x,y,x+3,y),fill='#827451')
    # Chip edge stripes are printed, so each clipped stack needs only three cuboids.
    for i,color in enumerate(('#e9dfbd','#aa3542','#267657')):
        x=i*32
        d.rectangle((x,400,x+23,423),fill=color)
        for y in (403,409,415,421): d.line((x,y,x+23,y),fill='#d5bb78')
        d.rectangle((x,432,x+23,455),fill=color)
        d.ellipse((x+4,436,x+19,451),outline='#d5bb78',width=2)
    gold=(272,224,304,248)
    # Small ferrules and thin inlays give the body depth without bulky ornaments.
    for x in (-10.5,23.5):
        for z in (-1.5,15.5):
            elements.append(box(f'finish_leg_flute_{x}_{z}',(x-.18,1.2,z+1.205),(x+.18,10.5,z+1.27),gold))
    for side,x0,x1 in (('left',-12,-.4),('right',16.4,28)):
        elements.append(box('finish_apron_'+side,(x0,13.1,21.015),(x1,13.3,21.08),gold))
    for x in (-14,29.92):
        elements.append(box('finish_side_inlay_'+str(x),(x,13.1,-2.5),(x+.08,13.3,18.5),gold))
    for x in (-12,26.8):
        for z in (-4.8,19.7):
            elements.append(box(f'finish_rail_cap_{x}_{z}',(x,16.16,z),(x+1.2,16.23,z+.8),gold))
    # Replace colored square blocks with stepped, striped chip stacks.
    for e in list(elements):
        if not e['name'].startswith(('dealer_chip_stack_','dealer_chips_')): continue
        elements.remove(e)
        material=next(c for c in ('ivory','red','green') if c in e['name'])
        i=('ivory','red','green').index(material)
        x0,y0,z0=e['from'];x1,y1,z1=e['to']
        cx=(x0+x1)/2;cz=(z0+z1)/2;r=.78;cut=.22
        for j,(za,zb,width) in enumerate(((-r,-r+cut,r-cut),(-r+cut,r-cut,r),(r-cut,r,r-cut))):
            chip=box(f'finish_chips_{material}_{j}',(cx-width,y0,cz+za),
                     (cx+width,y0+.65+i*.17,cz+zb),(i*32,400,i*32+24,424))
            chip['faces']['up']['uv']=[(i*32+(r-width)*24/(2*r))/32,
                (432+(za+r)*24/(2*r))/32,(i*32+(r+width)*24/(2*r))/32,
                (432+(zb+r)*24/(2*r))/32]
            elements.append(chip)

    # Sculpted cushions sit above the square rail: walnut plinth, leather crown,
    # then a narrow brass piping line on the outer face.
    leather=(352,232,416,276)
    wood=(0,224,128,272)
    for label,start,end in (
        ('front',(-11.6,16.15,19.25),(27.6,16.48,20.75)),
        ('back',(-11.6,16.15,-4.75),(27.6,16.48,-3.25)),
        ('left',(-13.75,16.15,-2.7),(-12.25,16.48,18.7)),
        ('right',(28.25,16.15,-2.7),(29.75,16.48,18.7))):
        elements.append(box('finish_cushion_'+label,start,end,leather))
    # Recessed walnut apron panels and three-dimensional brass frames.
    for n,(left,right) in enumerate(((-11.5,-.6),(16.6,27.5))):
        elements.append(box(f'finish_panel_{n}',(left,12.65,21.08),(right,15.05,21.16),wood))
        for suffix,a,b in (
            ('top',(left,14.92,21.16),(right,15.05,21.23)),
            ('bottom',(left,12.65,21.16),(right,12.78,21.23)),
            ('left',(left,12.78,21.16),(left+.13,14.92,21.23)),
            ('right',(right-.13,12.78,21.16),(right,14.92,21.23))):
            elements.append(box(f'finish_panel_frame_{n}_{suffix}',a,b,gold))
    # Dealer's tray gets a low brass lip; chips remain fully visible.
    trays=[e for e in elements if e['name'] in ('dealer_tray','dealer_chip_tray')]
    for tray in trays:
        x0,_,z0=tray['from'];x1,y,z1=tray['to']
        for n,(a,b) in enumerate((
            ((x0,y,z0),(x1,y+.12,z0+.12)),
            ((x0,y,z1-.12),(x1,y+.12,z1)),
            ((x0,y,z0+.12),(x0+.12,y+.12,z1-.12)),
            ((x1-.12,y,z0+.12),(x1,y+.12,z1-.12)))):
            elements.append(box(f'finish_tray_lip_{n}',a,b,gold))
