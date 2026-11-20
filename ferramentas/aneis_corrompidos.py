"""Gera as texturas dos anéis corrompidos (rachaduras brancas + manchas cinzas) e convertidos (preto, rachaduras
brancas, símbolo claro) a partir das texturas de cada Tropa. Cada textura é uma paleta 4x4 de ladrilhos 16x16 usados
pelo modelo de microcubos: (0,0) aro, (1,0) gravura, (2,0) chanfro, (3,0) friso, (0,1) face, (1,1) símbolo,
(3,1) aro interno."""
from PIL import Image
import random, colorsys, json, os, copy
BASE='src/main/resources/assets/lanterna'
TEX=BASE+'/textures/item/ring/'; MOD=BASE+'/models/item/ring/'
NOMES={'green':'anel_vontade_verde','yellow':'anel_medo_amarelo','red':'anel_raiva_vermelho','orange':'anel_avareza_laranja',
       'blue':'anel_esperanca_azul','indigo':'anel_compaixao_indigo','violet':'anel_amor_violeta'}
METAL=[(0,0),(1,0),(2,0),(3,0),(3,1),(0,1)]

def lum(c): return (0.299*c[0]+0.587*c[1]+0.114*c[2])/255

def rachadura(rng, px, x0, y0, branco=(250,250,250), forca=1.0, ramos=1):
    """Uma rachadura de 1 px que atravessa o ladrilho em zigue-zague, com um galho."""
    lado=rng.randint(0,3)
    if lado==0: x,y,dx,dy=rng.randint(3,12),1,0,1
    elif lado==1: x,y,dx,dy=1,rng.randint(3,12),1,0
    elif lado==2: x,y,dx,dy=rng.randint(3,12),14,0,-1
    else: x,y,dx,dy=14,rng.randint(3,12),-1,0
    pontos=[]
    for i in range(rng.randint(9,14)):
        pontos.append((x,y))
        if dx==0: x+=rng.choice([-1,0,0,1]); y+=dy
        else: y+=rng.choice([-1,0,0,1]); x+=dx
        x=max(1,min(14,x)); y=max(1,min(14,y))
    for k in range(ramos):
        if len(pontos)<5: break
        bx,by=pontos[rng.randint(3,len(pontos)-2)]
        ddx,ddy=rng.choice([(1,1),(-1,1),(1,-1),(-1,-1)])
        for j in range(rng.randint(2,4)):
            bx+=ddx if rng.random()<0.7 else 0; by+=ddy if rng.random()<0.7 else 0
            if 1<=bx<=14 and 1<=by<=14: pontos.append((bx,by))
    for (x,y) in pontos:
        c=px[x0+x,y0+y]
        if c[3]==0: continue
        a=forca*(0.85 if rng.random()<0.8 else 0.6)
        px[x0+x,y0+y]=tuple(int(c[i]*(1-a)+branco[i]*a) for i in range(3))+(c[3],)
        # sombra fina do lado (dá profundidade à trinca)
        sx,sy=x+(1 if dx==0 else 0),y+(1 if dy==0 else 0)
        if 0<=sx<=15 and 0<=sy<=15:
            s=px[x0+sx,y0+sy]
            if s[3] and lum(s[:3])<0.95: px[x0+sx,y0+sy]=tuple(int(s[i]*0.7) for i in range(3))+(s[3],)

def mancha(rng, px, x0, y0, forca):
    """Manchas cinzas suaves (a cor some aos poucos onde a morte tocou)."""
    cx,cy,r=rng.uniform(3,12),rng.uniform(3,12),rng.uniform(3,6)
    for y in range(16):
        for x in range(16):
            c=px[x0+x,y0+y]
            if c[3]==0: continue
            d=((x-cx)**2+(y-cy)**2)**0.5
            t=max(0,1-d/r)*forca
            if rng.random()<0.25: t*=0.6
            g=lum(c[:3])*255*0.85
            px[x0+x,y0+y]=tuple(int(c[i]*(1-t)+g*t) for i in range(3))+(c[3],)

def corrompido(src):
    im=src.copy(); px=im.load(); rng=random.Random(7)
    for (tx,ty) in METAL:
        x0,y0=tx*16,ty*16
        if rng.random()<0.7: mancha(rng,px,x0,y0,0.7)
        rachadura(rng,px,x0,y0,forca=0.95,ramos=1)
    # o símbolo: uma trinca fina só
    rachadura(rng,px,16,16,forca=0.7,ramos=0)
    return im

def mancha_preta(rng, px, x0, y0, forca):
    """Mancha preta (a corrosão do estágio 2), com borda irregular."""
    cx,cy,r=rng.uniform(2,13),rng.uniform(2,13),rng.uniform(2.5,5.5)
    for y in range(16):
        for x in range(16):
            c=px[x0+x,y0+y]
            if c[3]==0: continue
            d=((x-cx)**2+(y-cy)**2)**0.5 + rng.uniform(-0.8,0.8)
            t=max(0,min(1,(r-d)/1.6))*forca
            px[x0+x,y0+y]=tuple(int(c[i]*(1-t)+10*t) for i in range(3))+(c[3],)

def corroido(src):
    """Estágio 2 (0.51): o anel bem mais escuro, manchas pretas e cinzas, ainda com manchas da cor da Tropa, e
    rachaduras brancas por toda parte."""
    im=src.copy(); px=im.load(); rng=random.Random(29)
    W,H=im.size
    for y in range(H):
        for x in range(W):
            c=px[x,y]
            if c[3]==0: continue
            g=lum(c[:3])*255
            # escurece e tira metade da cor (a da Tropa ainda aparece por baixo)
            px[x,y]=tuple(int((c[i]*0.5+g*0.5)*0.55) for i in range(3))+(c[3],)
    for (tx,ty) in METAL:
        x0,y0=tx*16,ty*16
        # manchas da Tropa que resistem (a cor original volta forte em pontos)
        for k in range(rng.randint(1,2)):
            cx,cy,r=rng.uniform(3,12),rng.uniform(3,12),rng.uniform(2,3.5)
            for y in range(16):
                for x in range(16):
                    o=src.getpixel((x0+x,y0+y)); c=px[x0+x,y0+y]
                    if c[3]==0: continue
                    t=max(0,1-((x-cx)**2+(y-cy)**2)**0.5/r)
                    px[x0+x,y0+y]=tuple(int(c[i]*(1-t)+o[i]*t) for i in range(3))+(c[3],)
        for k in range(2): mancha_preta(rng,px,x0,y0,0.95)
        mancha(rng,px,x0,y0,0.6)
        for k in range(2): rachadura(rng,px,x0,y0,forca=1.0,ramos=2)
    # o símbolo: escurece menos (ainda se reconhece a Tropa), com duas trincas
    for y in range(16):
        for x in range(16):
            o=src.getpixel((16+x,16+y))
            if o[3]: px[16+x,16+y]=tuple(int(o[i]*0.7) for i in range(3))+(o[3],)
    mancha_preta(rng,px,16,16,0.8)
    for k in range(2): rachadura(rng,px,16,16,forca=0.9,ramos=1)
    return im

def convertido(src):
    im=src.copy(); px=im.load(); rng=random.Random(13)
    W,H=im.size
    for y in range(H):
        for x in range(W):
            c=px[x,y]
            if c[3]==0: continue
            l=lum(c[:3])
            tx,ty=x//16,y//16
            if (tx,ty)==(1,1):
                # símbolo da Tropa antiga, em osso pálido sobre o preto
                v=int(150+105*l); px[x,y]=(v,v,int(v*0.96),c[3])
            elif (tx,ty)==(0,2):
                v=int(200+55*l); px[x,y]=(v,v,v,c[3])
            else:
                v=int(14+70*l**1.4); px[x,y]=(v,v,int(v*1.08),c[3])
    for (tx,ty) in METAL:
        for k in range(2 if (tx,ty) in [(0,0),(2,0)] else 1):
            rachadura(rng,px,tx*16,ty*16,forca=1.0,ramos=2)
    return im

modelo_base=json.load(open('src/main/resources/assets/lanterna/models/item/power_ring.json'))
for corps,nome in NOMES.items():
    src=Image.open(TEX+nome+'.png').convert('RGBA')
    for estado,fn in (('corrompido',corrompido),('corroido',corroido),('negro',convertido)):
        os.makedirs(TEX+estado,exist_ok=True); os.makedirs(MOD+estado,exist_ok=True)
        fn(src).save(TEX+estado+'/'+nome+'.png')
        m=json.load(open(MOD+nome+'.json'))
        m['textures']={'0':'lanterna:item/ring/'+estado+'/'+nome,'particle':'lanterna:item/ring/'+estado+'/'+nome}
        json.dump(m,open(MOD+estado+'/'+nome+'.json','w'),indent=1)
# overrides: base (1..9), corrompido por Tropa, negro por Tropa (o último que bate vence)
INDICE={'green':1,'yellow':2,'red':3,'blue':4,'violet':5,'indigo':6,'orange':7}
ovs=[o for o in modelo_base['overrides'] if 'lanterna:corrupcao' not in o['predicate']]
for estado,valor in (('corrompido',1),('corroido',2),('negro',3)):
    for corps,idx in sorted(INDICE.items(),key=lambda kv:kv[1]):
        ovs.append({'predicate':{'lanterna:corps':idx,'lanterna:corrupcao':valor},'model':'lanterna:item/ring/'+estado+'/'+NOMES[corps]})
modelo_base['overrides']=ovs
json.dump(modelo_base,open('src/main/resources/assets/lanterna/models/item/power_ring.json','w'),indent=1)
# folha de comparação
folha=Image.new('RGBA',(64*4*4,64*7*4),(40,40,40,255))
for i,(corps,nome) in enumerate(NOMES.items()):
    for j,p in enumerate([TEX+nome+'.png',TEX+'corrompido/'+nome+'.png',TEX+'corroido/'+nome+'.png',TEX+'negro/'+nome+'.png']):
        folha.paste(Image.open(p).resize((256,256),Image.NEAREST),(j*256,i*256))
folha.save('aneis_folha.png')
print('ok')
