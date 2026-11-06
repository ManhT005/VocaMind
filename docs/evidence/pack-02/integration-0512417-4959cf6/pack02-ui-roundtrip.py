from pathlib import Path
exec(Path('build/pack02-process-restoration.py').read_text(encoding='utf-8-sig').split("cmd(['shell','am','start'")[0])
def await_node(rid,part=None,visible=True):
 for _ in range(10):
  xml,tree=hierarchy();node=find(tree,rid)
  if node is not None and (part is None or part in node.get('text','')) and node.get('enabled')=='true':return node,xml
  time.sleep(.3)
 raise AssertionError('Missing '+rid+' '+str(part))
def click(rid,part=None):
 node,_=await_node(rid,part);tap(node)
def capture(name,xml):
 (out/(name+'.xml')).write_text(xml,encoding='utf-8')
 r=subprocess.run([adb,'-s',serial,'exec-out','screencap','-p'],capture_output=True,check=True);(out/(name+'.png')).write_bytes(r.stdout)
cmd(['shell','am','force-stop',pkg])
cmd(['shell','am','start','-n',pkg+'/.MainActivity'])
for _ in range(8):
 xml,tree=hierarchy()
 if find(tree,'open_packs') is not None:break
 cmd(['shell','input','keyevent','KEYCODE_BACK'])
 time.sleep(.5)
 cmd(['shell','am','start','-n',pkg+'/.MainActivity'])
click('open_packs');click('pack_root');click('activation','Kích hoạt');await_node('activation','Tạm dừng')
cmd(['shell','input','keyevent','KEYCODE_BACK'])
node,xml=await_node('pack_title','Đang học');capture('M02-active-list',xml)
cmd(['shell','input','keyevent','KEYCODE_BACK']);click('open_today')
node,xml=await_node('message','Đã có gói đang học')
assert find(ET.fromstring(xml),'explore') is None
capture('M02-active-today',xml)
cmd(['shell','input','keyevent','KEYCODE_BACK']);click('open_packs');click('pack_root');click('activation','Tạm dừng');await_node('activation','Kích hoạt')
cmd(['shell','input','keyevent','KEYCODE_BACK']);node,xml=await_node('pack_title');assert 'Đang học' not in node.get('text')
cmd(['shell','input','keyevent','KEYCODE_BACK']);click('open_today');node,xml=await_node('explore','Khám phá');capture('M03-paused-today',xml)
(out/'M02-M03-active-paused-navigation.json').write_text(json.dumps(dict(status='PASS',checks=['detail activate','active badge on list','active Today no CTA','detail pause','inactive list','Today CTA returns']),indent=2))
print('M02-M03 active/paused navigation PASS')
