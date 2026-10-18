"""Extract existing tray stacks for animation; preserve the editable complete tables."""
import copy
import json

def generate(pack, game):
    root=pack/'assets/casinocore'
    source=root/f'models/entity/{game}_table{ "_base" if game=="roulette" else ""}.json'
    model=json.loads(source.read_text())
    def write(relative, value):
        path=root/relative;path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,indent=2)+'\n')
    for color in ('ivory','red','green'):
        elements=copy.deepcopy([e for e in model['elements'] if e['name'].startswith('finish_chips_'+color)])
        assert len(elements)==3
        pivot=[(min(e['from'][i] for e in elements)+max(e['to'][i] for e in elements))/2
               if i!=1 else min(e['from'][i] for e in elements) for i in range(3)]
        for e in elements:
            for key in ('from','to'):e[key]=[round(v-pivot[i]+8,6) for i,v in enumerate(e[key])]
        name=f'effects/{game}_stack_{color}'
        write(f'models/{name}.json',{'textures':model['textures'],'elements':elements})
        write(f'items/{name}.json',{'model':{'type':'minecraft:model','model':'casinocore:'+name}})
    model['elements']=[e for e in model['elements'] if not e['name'].startswith('finish_chips_')]
    name=f'effects/{game}_table_show'
    write(f'models/{name}.json',model)
    write(f'items/{name}.json',{'model':{'type':'minecraft:model','model':'casinocore:'+name}})
