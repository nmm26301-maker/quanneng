import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const script=fs.readFileSync(new URL('../public/media-discovery.js',import.meta.url),'utf8');
function fixture(){
 const messages=[],events={};let response;
 class LocalURL extends URL {static createObjectURL(){return 'blob:https://frame.example/'+Math.random();}static revokeObjectURL(){}}
 class XHR{addEventListener(event,fn){this.listener=fn;}send(){this.status=200;this.responseURL='https://media.example/unrelated';this.listener();}getResponseHeader(){return 'video/mp4';}}
 const context={URL:LocalURL,Set,Map,WeakMap,Date,XMLHttpRequest:XHR,location:{href:'https://frame.example/player',hostname:'frame.example'},document:{title:'我的剧 第12集',querySelector:()=>null,querySelectorAll:()=>[],addEventListener:(name,fn)=>events[name]=fn},performance:{getEntriesByType:()=>[{name:'https://media.example/other.mp4'}]}};
 context.window=context;context.fetch=()=>Promise.resolve(response);context.QuannengMedia={postMessage:raw=>messages.push(JSON.parse(raw))};vm.runInNewContext(script,context);
 const video=(src)=>({tagName:'VIDEO',currentSrc:src,paused:false,querySelector:()=>null,getAttribute:()=>null});
 return {context,messages,events,video,fetch:async(url,type='video/mp4')=>{response={ok:true,url,headers:{get:()=>type},blob:async()=>({})};return context.fetch(url);}};
}
test('preloaded sources, page scans and autoplay never export a video',async()=>{
 const f=fixture();await f.fetch('https://media.example/preload.mp4');new f.context.XMLHttpRequest().send();
 f.events.play({target:f.video('https://media.example/autoplay.mp4')});f.context.QuannengMedia.onmessage({data:'scan'});assert.equal(f.messages.filter(x=>x.kind!=='ready').length,0);
});
test('only the clicked video is exported with title; selecting another replaces the session',async()=>{
 const f=fixture();f.events.pointerdown({isTrusted:true});const one=f.video('https://media.example/one.mp4');f.events.play({target:one});
 await f.fetch('https://media.example/unrelated.mp4');
 assert.deepEqual(f.messages.filter(x=>x.kind==='source').map(x=>x.url),['https://media.example/one.mp4']);
 assert.equal(f.messages.find(x=>x.kind==='source').title,'我的剧 第12集');
 f.events.pointerdown({isTrusted:true});f.events.play({target:f.video('https://media.example/two.mp4')});
 assert.equal(f.messages.filter(x=>x.kind==='activate').length,2);assert.equal(f.messages.at(-2).session,'2');
});
test('a selected blob resolves to its own fetched URL, including preloaded blobs',async()=>{
 const f=fixture();const selected=await f.fetch('https://media.example/stream?id=chosen');const blob=await selected.blob();const src=f.context.URL.createObjectURL(blob);
 await f.fetch('https://media.example/unrelated.mp4');f.events.pointerdown({isTrusted:true});f.events.play({target:f.video(src)});
 assert.deepEqual(f.messages.filter(x=>x.kind==='source').map(x=>x.url),['https://media.example/stream?id=chosen']);
});
test('MSE request observation is limited to the user playback window and excludes fragments',async()=>{
 const f=fixture();f.events.pointerdown({isTrusted:true});await f.fetch('https://media.example/manifest','application/vnd.apple.mpegurl');f.events.play({target:f.video('blob:https://frame.example/mse')});
 await f.fetch('https://media.example/chunk-1.mp4');await f.fetch('https://media.example/1.m4s');
 assert.deepEqual(f.messages.filter(x=>x.kind==='source').map(x=>x.url),['https://media.example/manifest']);
 assert.ok(f.messages.some(x=>x.kind==='status'&&x.blob));assert.ok(f.messages.every(x=>x.frame==='https://frame.example/player'));
});

test('native user gesture arms embedded playback and encrypted events are explicit',()=>{
 const f=fixture();f.context.QuannengMedia.onmessage({data:'gesture'});const video=f.video('https://media.example/12.mp4');f.events.play({target:video});f.events.encrypted({target:video});
 assert.equal(f.messages.filter(x=>x.kind==='source').length,1);assert.ok(f.messages.some(x=>x.kind==='status'&&x.encrypted));
});

test('reusing one video element for the next episode replaces source session and title',()=>{
 const f=fixture(),video=f.video('https://media.example/ep12.mp4');f.events.pointerdown({isTrusted:true});f.events.play({target:video});
 video.currentSrc='https://media.example/ep13.mp4';f.context.document.title='我的剧 第13集';f.events.pointerdown({isTrusted:true});f.events.play({target:video});
 const sources=f.messages.filter(x=>x.kind==='source');assert.equal(sources.at(-1).session,'2');assert.equal(sources.at(-1).title,'我的剧 第13集');assert.equal(sources.at(-1).url,'https://media.example/ep13.mp4');assert.equal(f.messages.filter(x=>x.kind==='activate').length,2);
});
