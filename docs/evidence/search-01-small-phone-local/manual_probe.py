"""ADB-driven emulator gestures, with observed UI hierarchy and screenshots."""
import json, re, sys, time
from pathlib import Path
import xml.etree.ElementTree as ET
import qa_driver as q

OUT=q.OUT
actions=[]
def command(*args):
    value=q.text(*args)
    actions.append({'args':list(args),'stdout':value})
    return value
def tree(label):
    command('shell','uiautomator','dump','/sdcard/search01-qa-window.xml')
    raw=q.adb('exec-out','cat','/sdcard/search01-qa-window.xml').stdout
    (OUT/(label+'.xml')).write_bytes(raw)
    return ET.fromstring(raw)
def tap(root, suffix=None, text=None):
    for node in root.iter('node'):
        if (suffix is None or node.attrib.get('resource-id','').endswith(':'+suffix) or node.attrib.get('resource-id','').endswith('/'+suffix)) and (text is None or node.attrib.get('text')==text):
            x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
            command('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); return
    raise AssertionError((suffix,text))
def shot(label):
    time.sleep(.4); q.screenshot(label)
def assert_query(root, raw):
    # Accessibility XML exposes the hint as text when EditText is empty.
    allowed = {raw} if raw else {'', 'Tìm từ tiếng Anh hoặc nghĩa tiếng Việt'}
    assert any(n.attrib.get('resource-id','').endswith('/search_src_text') and n.attrib.get('text') in allowed for n in root.iter('node')),raw

try:
    command('shell','am','force-stop',q.PKG) # QA only; original package untouched.
    command('shell','am','start','-n',q.PKG+'/com.vocamind.app.MainActivity')
    time.sleep(.5)
    tap(tree('manual-01-home'), 'open_vocabulary'); time.sleep(.3)
    tap(tree('manual-02-noinput'),'search_src_text')
    command('shell','input','text','bank'); time.sleep(.9)
    root=tree('manual-03-typed-bank'); assert_query(root,'bank'); shot('manual-03-keyboard-bank')
    # A real tap on the displayed result, rather than programmatic holder.performClick.
    tap(root,'word_title','bank'); time.sleep(.5)
    root=tree('manual-04-detail'); assert any('5aecfbf5b9a23aee' in n.attrib.get('text','') for n in root.iter('node'))
    shot('manual-04-detail')
    tap(root,'back'); time.sleep(.4)
    root=tree('manual-05-back'); assert_query(root,'bank'); shot('manual-05-back')
    command('shell','settings','put','system','accelerometer_rotation','0')
    command('shell','settings','put','system','user_rotation','1'); time.sleep(1)
    root=tree('manual-06-landscape'); assert_query(root,'bank'); shot('manual-06-landscape')
    command('shell','settings','put','system','user_rotation','0'); time.sleep(1)
    root=tree('manual-07-portrait'); assert_query(root,'bank'); shot('manual-07-portrait')
    tap(root,'reset'); time.sleep(.8)
    root=tree('manual-08-clear'); assert_query(root,''); assert any('Nhập từ tiếng Anh' in n.attrib.get('text','') for n in root.iter('node'))
    shot('manual-08-clear')
    actions.append({'result':'PASS','scope':'Real ADB taps/text, keyboard, result detail stableId, Back, physical rotation, clear'})
finally:
    q.write('manual-actions.json',actions)
print(json.dumps(actions[-1],ensure_ascii=False))
