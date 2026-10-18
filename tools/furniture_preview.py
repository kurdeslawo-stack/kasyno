"""Textured orthographic previews from exported furniture geometry."""
import math
from array import array
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance, ImageFont

def face_vertices(e,f):
    x,y,z=e['from']; X,Y,Z=e['to']
    return {'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
            'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],
            'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
            'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
            'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],
            'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)]}[f]

def render(elements,atlas,path,az=30,elev=33,w=1500,h=1050,scale=25,head='ROULETTE / 01',sub='CASINOCORE   |   WALNUT - EMERALD - BRASS'):
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
        u0,v0,u1,v1=[v*512/16 for v in spec['uv']]
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
