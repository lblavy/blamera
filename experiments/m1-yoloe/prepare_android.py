"""Separate compact measurement baseline and two public fixtures, not app-model export."""
from pathlib import Path
import hashlib,json
import cv2,numpy as np,onnx
from run_evaluation import ROOT,OUT,BASE,NAMES80,create,prepare,decode,sha
from checks import compact_decode
from metrics import match_instances
from ultralytics import YOLO, YOLOE

folder=ROOT/'models/m1-yoloe/deployment/baseline';folder.mkdir(parents=True,exist_ok=True)
pt=ROOT/'models/m1-01/yolo11n-seg/yolo11n-seg.pt'
assert sha(pt)=='55ed65c56c91713d23e8402371c6c49a6fd84f257f7dce452e8d70e41dcbe152'
assert sha(BASE)=='38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b'
def compact_export(checkpoint, destination, family, date, expected):
    # Restore only the volatile exporter timestamp, never graph weights/operators.
    if not destination.exists():
        copy=destination.with_suffix('.pt');copy.write_bytes(checkpoint.read_bytes())
        exported=Path(family(str(copy)).export(format='onnx',imgsz=640,batch=1,device='cpu',half=False,dynamic=False,
            simplify=False,opset=17,nms=True,end2end=False,conf=.35,iou=.5,max_det=20,agnostic_nms=False,verbose=False))
        graph=onnx.load(exported)
        dates=[field for field in graph.metadata_props if field.key=='date'];assert len(dates)==1
        dates[0].value=date
        onnx.checker.check_model(graph);onnx.save(graph,destination)
    assert sha(destination)==expected, 'Export differs beyond date: stop, do not change the pinned hash'
    return destination

export=compact_export(pt,folder/'yolo11n-seg.onnx',YOLO,'2026-10-27T15:11:44.124529+01:00',
    '5e11ab61781495f96661a0409a3a868fd0bfe213f04f3e83e1b61fe4d0bfcc92')
pf=ROOT/'models/m1-yoloe/yoloe-26n-seg-pf.pt'
assert sha(pf)=='ae805a9ac11c85214588b32da8248d68a516e1d071465e1e86a03cbfadb66ba7'
compact_export(pf,ROOT/'models/m1-yoloe/deployment/yoloe-26n-seg-pf.onnx',YOLOE,
    '2026-10-27T14:41:12.521444+01:00','289e096cef23c0c3bec3a46ead571059702414109aa5319d679891e879d3ee91')
raw=create(BASE);compact=create(export);parity=[]
for name,file in [('nasa','build/reports/validation/m1-01/astronaut.png'),('negative','build/reports/validation/m1-yoloe/corpus/negative.png')]:
    image=cv2.imread(str(ROOT/file));tensor,p=prepare(image)
    original=raw.run(None,{'images':tensor});changed=compact.run(None,{'images':tensor})
    a,_=decode(*original,p,NAMES80);b=compact_decode(*changed,p)
    comparison=match_instances(b,a)
    assert comparison['fp']==0 and comparison['fn']==0 and min(comparison['matched_mask_iou'],default=1)>=.99
    assert changed[0].shape==(1,20,38)
    if name=='negative':assert np.count_nonzero(changed[0])==0
    parity.append({'fixture':name,'comparison':comparison})
fixtures=ROOT/'models/m1-yoloe/benchmark-fixtures';fixtures.mkdir(exist_ok=True)
nasa=fixtures/'astronaut.png';nasa.write_bytes((ROOT/'build/reports/validation/m1-01/astronaut.png').read_bytes())
assert sha(nasa)=='88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5'
pattern=np.full((480,640,3),114,np.uint8)
for y in range(0,480,40):
    for x in range(0,640,40):cv2.rectangle(pattern,(x,y),(x+24,y+24),(30+(x%200),50+(y%180),180),-1)
cv2.circle(pattern,(320,240),90,(30,210,240),-1)
synthetic=fixtures/'synthetic.png';assert cv2.imwrite(str(synthetic),pattern)
models=[('baseline',export,80),('yoloe',ROOT/'models/m1-yoloe/deployment/yoloe-26n-seg-pf.onnx',4585)]
lines=['# Debug-personal benchmark; no production model replacement.']
for name,path,classes in models:
    lines.extend([f'{name}.file={path.relative_to(ROOT).as_posix()}',f'{name}.asset={name}.onnx',f'{name}.sha256={sha(path)}',f'{name}.size={path.stat().st_size}',f'{name}.classes={classes}'])
for name,path in [('nasa',nasa),('synthetic',synthetic)]:
    lines.extend([f'{name}.file={path.relative_to(ROOT).as_posix()}',f'{name}.asset={path.name}',f'{name}.sha256={sha(path)}',f'{name}.size={path.stat().st_size}'])
license=ROOT/'models/m1-yoloe/LICENSE-assets-AGPL.txt'
lines.extend([f'license.file={license.relative_to(ROOT).as_posix()}','license.asset=LICENSE-yoloe-AGPL.txt',f'license.sha256={sha(license)}',f'license.size={license.stat().st_size}'])
(ROOT/'config/benchmark.properties').write_bytes(('\n'.join(lines)+'\n').encode())
record={'baseline_compact':{'sha256':sha(export),'bytes':export.stat().st_size},'parity':parity,
    'fixtures':{'nasa':{'sha256':sha(nasa),'bytes':nasa.stat().st_size},'synthetic':{'sha256':sha(synthetic),'bytes':synthetic.stat().st_size}},
    'original_onnx_unchanged':sha(BASE),'original_pt_unchanged':sha(pt)}
(OUT/'android-preparation.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
print(json.dumps(record,indent=2))
