"""Exercise the real host using local fixture credentials. Never prints tokens or passwords."""
import argparse,json,urllib.request,urllib.error,urllib.parse
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--credentials',required=True);parser.add_argument('--base',default='http://127.0.0.1:48082');parser.add_argument('--report',default='/workspace/tools/ilvy-host-acceptance.json');args=parser.parse_args()
parsed=urllib.parse.urlparse(args.base)
assert parsed.scheme=='http' and parsed.hostname in ['127.0.0.1','localhost'] and parsed.port==48082,'Only the isolated loopback host is permitted'
users=json.loads(Path(args.credentials).read_text());opener=urllib.request.build_opener(urllib.request.ProxyHandler({}));results=[]
def call(path,method='GET',data=None,token=None,tenant='1'):
 headers={'Content-Type':'application/json','tenant-id':tenant}
 if token:headers['Authorization']='Bearer '+token
 request=urllib.request.Request(args.base+'/app-api/'+path,method=method,headers=headers,data=json.dumps(data,ensure_ascii=False).encode()if data is not None else None)
 try:response=opener.open(request,timeout=10)
 except urllib.error.HTTPError as e:response=e
 return response.status,json.load(response)
def expect(name,condition):
 assert condition,name
 results.append(name);print('PASS',name)
def report():Path(args.report).write_text(json.dumps({'startupClass':'cn.iocoder.yudao.server.YudaoServerApplication','hostPort':48082,'mysqlPort':43306,'redisPort':46379,'identity':'existing member password authentication and OAuth2 service, no mock login','passed':results},ensure_ascii=False,indent=2))
try:
 tokens=[]
 for i,user in enumerate(users):
  status,d=call('member/auth/login','POST',{'mobile':user['mobile'],'password':user['password']})
  expect('member '+str(i+1)+' real password login',d.get('code')==0 and bool(d.get('data',{}).get('accessToken')))
  tokens.append(d['data']['accessToken'])
 a,b=tokens
 _,d=call('ilvy/state');expect('anonymous state access denied',d.get('code')==401)
 _,d=call('ilvy/places');expect('public catalog is read from MySQL',d.get('code')==0 and len(d.get('data',[]))==4)
 _,d=call('ilvy/state',token=a);state=d['data'];expect('member has readable private state',isinstance(state['version'],int));expected=state['version']
 state.update(favorites=[{'placeId':'forest-park','group':'宿主会员收藏'}],plan=[{'placeId':'forest-park','note':'真实 MySQL 保存'}],planName='宿主集成验收',planDate='2026-10-25')
 _,d=call('ilvy/state','PUT',state,token=a);expect('member state saves through actual host',d.get('code')==0 and d['data']['version']==expected+1)
 _,d=call('ilvy/state',token=a);expect('state readback preserves group and note',d.get('code')==0 and d['data']['favorites'][0]['group']=='宿主会员收藏' and d['data']['plan'][0]['note']=='真实 MySQL 保存')
 _,d=call('ilvy/state',token=b);expect('second member cannot read first member state',d.get('code')==0 and all(f['group']!='宿主会员收藏'for f in d['data']['favorites']))
 _,d=call('ilvy/state','PUT',state,token=a);expect('optimistic conflict uses correct API error',d.get('code')==409)
 _,d=call('ilvy/state',token=a,tenant='999');expect('forged tenant header is denied',d.get('code')!=0)
 _,d=call('ilvy/feedback','POST',{'placeId':'forest-park','category':'设施','description':'完整宿主接口验收'},token=a);expect('feedback stored by real host',d.get('code')==0)
 _,d=call('ilvy/feedback',token=b);expect('feedback is private to member',d.get('code')==0 and all(f['description']!='完整宿主接口验收'for f in d['data']))
 _,d=call('ilvy/local-session','POST');expect('development anonymous-session endpoint unavailable in full host',d.get('code')!=0)
 _,d=call('member/auth/logout','POST',token=a);expect('existing member logout succeeds',d.get('code')==0)
 _,d=call('ilvy/state',token=a);expect('revoked OAuth2 token cannot read private state',d.get('code')==401)
 report();print('HOST_INTEGRATION_PASS',len(results))
except Exception as e:
 report();print('HOST_INTEGRATION_FAILED',str(e));raise SystemExit(1)
