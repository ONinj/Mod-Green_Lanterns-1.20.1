import json,sys
def add(pt,en):
    for f,d in [('src/main/resources/assets/lanterna/lang/pt_br.json',pt),('src/main/resources/assets/lanterna/lang/en_us.json',en)]:
        src=open(f,encoding='utf-8').read(); cur=json.loads(src)
        s=''.join(',\n  '+json.dumps(k,ensure_ascii=False)+': '+json.dumps(v,ensure_ascii=False) for k,v in d.items() if k not in cur)
        for k,v in d.items():
            if k in cur and cur[k]!=v:
                src=src.replace(json.dumps(k,ensure_ascii=False)+': '+json.dumps(cur[k],ensure_ascii=False),json.dumps(k,ensure_ascii=False)+': '+json.dumps(v,ensure_ascii=False))
        i=src.rstrip().rfind('}'); src=src[:i].rstrip()+s+'\n}\n'; json.loads(src); open(f,'w',encoding='utf-8').write(src)
