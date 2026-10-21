"""Read-only paging parity checks against the task-local full host; no secrets."""
import urllib.request,urllib.error,urllib.parse,json,argparse,datetime
p=argparse.ArgumentParser();p.add_argument('--report',required=True);a=p.parse_args()
op=urllib.request.build_opener(urllib.request.ProxyHandler({}));base='http://127.0.0.1:48082/app-api/ilvy';checks=[]
def call(path,params=None):
 url=base+path+('?' + urllib.parse.urlencode(params,doseq=True) if params else '')
 req=urllib.request.Request(url,headers={'tenant-id':'1'})
 try:r=op.open(req,timeout=10)
 except urllib.error.HTTPError as e:r=e
 return json.load(r)
def expect(name,ok):
 assert ok,name
 checks.append(name);print('PASS',name)
queries=[{}, {'q':'广州'},{'province':'广东','city':'广州','district':'天河区'},{'types':'公园,营地'},{'themes':'亲子,露营'},{'levels':'待核实'},{'free':'true'},{'budget':0},{'age':3},{'maxDrive':60},{'maxStay':3},{'booking':'true'},{'stroller':'true'},{'q':'%'},{'q':'_'},{'q':"' OR 1=1"},{'themes':'露营','free':'true','stroller':'true'}]
for index,q in enumerate(queries):
 expected=call('/places',q);first=call('/places/page',{**q,'pageSize':2});expect(f'query {index} first page succeeds',first['code']==0)
 data=first['data'];actual=data['list'];version=data['catalogVersion'];page=1
 while page*2<data['total']:
  page+=1;more=call('/places/page',{**q,'pageSize':2,'pageNo':page,'catalogVersion':version});expect(f'query {index} page {page} uses same version',more['code']==0 and more['data']['catalogVersion']==version);actual+=more['data']['list']
 expect(f'query {index} all-page IDs equal legacy filter results',[r['id'] for r in actual]==[r['id'] for r in expected['data']] and data['total']==len(expected['data']))
for q in [{'pageSize':0},{'pageSize':51},{'pageNo':0},{'pageNo':2}]:expect('invalid paging rejected '+str(q),call('/places/page',q)['code']==400)
first=call('/places/page',{'pageSize':2})['data'];empty=call('/places/page',{'pageNo':999,'pageSize':2,'catalogVersion':first['catalogVersion']})['data'];expect('out-of-range page is empty and preserves total',not empty['list'] and empty['total']==first['total'])
open(a.report,'w').write(json.dumps({'time':datetime.datetime.now(datetime.timezone.utc).isoformat(),'host':'YudaoServerApplication','database':'isolated MySQL8 ilvy_dev','passed':checks},ensure_ascii=False,indent=2));print('PAGE_MYSQL_PASS',len(checks))
