import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const script=fs.readFileSync(new URL('../public/media-discovery.js',import.meta.url),'utf8');
function fixture(){
 const messages=[],events={};const pending=Promise.resolve({ok:true,url:'https://media.example/stream?id=1',headers:{get:()=> 'video/mp4'}});
 class XHR{addEventListener(event,fn){this.listener=fn;}send(){this.status=200;this.responseURL='https://media.example/stream?id=xhr';this.listener();}getResponseHeader(){return 'application/vnd.apple.mpegurl';}}
 const context={URL,Set,XMLHttpRequest:XHR,location:{href:'https://frame.example/player'},document:{querySelectorAll:()=>[],addEventListener:(name,fn)=>events[name]=fn},performance:{getEntriesByType:()=>[{name:'https://media.example/chunk.m4s'},{name:'https://media.example/movie.m3u8'}]}};
 context.window=context;context.fetch=()=>pending;context.QuannengMedia={postMessage:raw=>messages.push(JSON.parse(raw))};vm.runInNewContext(script,context);return {context,messages,pending};
}
test('observes extensionless fetch/XHR media without consuming or replacing the response',async()=>{
 const f=fixture();assert.equal(f.context.fetch('anything'),f.pending);new f.context.XMLHttpRequest().send();await Promise.resolve();
 assert.ok(f.messages.some(x=>x.url==='https://media.example/stream?id=1'));
 assert.ok(f.messages.some(x=>x.url==='https://media.example/stream?id=xhr'));
 assert.ok(f.messages.every(x=>x.frame==='https://frame.example/player'));
 assert.ok(!f.messages.some(x=>x.url?.includes('chunk.m4s')));
 const count=f.messages.length;f.context.QuannengMedia.onmessage({data:'scan'});assert.equal(f.messages.length,count);
});
test('blob is status rather than an unusable native URL, unsafe source is ignored',()=>{
 const f=fixture();f.context.document.querySelectorAll=()=>[{currentSrc:'blob:https://frame.example/123',paused:false,readyState:4,querySelectorAll:()=>[{src:'javascript:alert(1)',type:'video/mp4'}]}];f.context.QuannengMedia.onmessage({data:'scan'});
 assert.ok(f.messages.some(x=>x.kind==='status'&&x.blob&&x.playing));assert.ok(!f.messages.some(x=>x.url?.startsWith('blob:')||x.url?.startsWith('javascript:')));
});
