import {formatTime,parseTime,clampSeek,validAB,normalizeUrl,srtToVtt} from './core.js';
const $ = id => document.getElementById(id), video = $('video');
const KEY = 'quanneng-playlist-v1';
let idSequence = 0;
const makeId = () => crypto.randomUUID?.() || 'video-' + Date.now().toString(36) + '-' + (++idSequence);
let items = [], currentId = null, hls = null, loop = false, ab = [0,0], subtitleUrl = null, saveAt = 0, toastTimer;
function toast(message) { $('toast').textContent = message; $('toast').classList.add('visible'); clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),4500); }
function save() {
  try { localStorage.setItem(KEY,JSON.stringify(items.filter(x=>x.kind==='url').map(({id,name,url,position})=>({id,name,url,position})))); }
  catch { /* Playback remains available without browser storage. */ }
}
try {
  const stored = JSON.parse(localStorage.getItem(KEY)||'[]');
  if(Array.isArray(stored)) items=stored.slice(0,100).flatMap(x=>{
    try { return [{id:makeId(),name:String(x.name||'网络视频').slice(0,200),url:normalizeUrl(x.url),position:Math.max(0,Number(x.position)||0),kind:'url'}]; } catch { return []; }
  });
} catch { items=[]; }
function renderList() {
  const parent=$('playlist'); parent.replaceChildren(); $('listCount').textContent=items.length;
  if(!items.length) { const empty=document.createElement('div');empty.className='list-empty';empty.innerHTML='<span>☷</span><p>还没有视频</p><small>添加后，在这里接着看</small>';parent.append(empty);return; }
  items.forEach((item,index)=>{
    const row=document.createElement('div'); row.className='playlist-item'+(item.id===currentId?' active':'');
    const button=document.createElement('button');button.className='item-play';button.title=item.name;
    const num=document.createElement('span');num.className='item-number';num.textContent=item.id===currentId?'▶':String(index+1).padStart(2,'0');
    const info=document.createElement('span');info.className='item-info';
    const name=document.createElement('span');name.className='item-name';name.textContent=item.name;
    const meta=document.createElement('span');meta.className='item-meta';meta.textContent=(item.kind==='file'?'本地文件':'网络视频')+(item.position>0?' · 上次 '+formatTime(item.position):'');
    info.append(name,meta);button.append(num,info);button.onclick=()=>loadItem(item.id);
    const remove=document.createElement('button');remove.className='remove-item';remove.textContent='×';remove.setAttribute('aria-label','移除 '+item.name);remove.onclick=()=>removeItem(item.id);
    row.append(button,remove);parent.append(row);
  });
}
function savePosition() { const item=items.find(x=>x.id===currentId);if(item&&Number.isFinite(video.currentTime))item.position=video.ended?0:video.currentTime;save(); }
function resetAB() {loop=false;ab=[0,0];$('pointA').value='00:00:00';$('pointB').value='00:00:00';updateLoop();}
function updateLoop() {
  $('loopAB').setAttribute('aria-pressed',String(loop));$('loopAB').innerHTML='<i></i>'+ (loop?'循环中 · 点击关闭':'开启循环');
  $('loopStatus').textContent=loop?'正在重复 '+formatTime(ab[0],true)+' → '+formatTime(ab[1],true):'先设起点 A，再设终点 B';
}
function clearSubtitle() {video.querySelectorAll('track').forEach(x=>x.remove());if(subtitleUrl)URL.revokeObjectURL(subtitleUrl);subtitleUrl=null;}
function detach() {video.pause();if(hls){hls.destroy();hls=null;}video.removeAttribute('src');video.load();clearSubtitle();resetAB();}
function stop() {savePosition();detach();currentId=null;video.hidden=true;$('empty').hidden=false;$('nowTitle').textContent='准备好，开始播放';$('sourceTag').textContent='本地 / 网络';$('seek').disabled=true;$('current').textContent=$('duration').textContent='00:00:00';$('seek').value=0;renderList();}
async function safePlay() {try {await video.play();}catch(e){if(e.name==='NotAllowedError')toast('视频已就绪，请点播放');else if(e.name!=='AbortError')toast('无法播放，请检查视频格式或链接访问权限');}}
function loadItem(id) {
  const item=items.find(x=>x.id===id);if(!item)return;
  savePosition();detach();currentId=id;video.hidden=false;$('empty').hidden=true;$('nowTitle').textContent=item.name;$('sourceTag').textContent=item.kind==='file'?'本地文件':'网络视频';$('seek').disabled=true;
  $('current').textContent=$('duration').textContent='00:00:00';$('seek').value=0;
  video.playbackRate=Number($('speed').value);video.loop=$('wholeLoop').checked;
  const isHls=/\.m3u8(?:$|[?#])/i.test(item.url);
  if(isHls&&!video.canPlayType('application/vnd.apple.mpegurl')){
    if(!window.Hls?.isSupported()){toast('当前浏览器不支持此 HLS 视频流');renderList();return;}
    hls=new window.Hls();hls.loadSource(item.url);hls.attachMedia(video);
    hls.on(window.Hls.Events.MANIFEST_PARSED,()=>{if(currentId===id)safePlay();});
    hls.on(window.Hls.Events.ERROR,(_event,data)=>{if(data.fatal){toast('视频流加载失败：请检查跨域权限、地址有效期和网络');hls?.destroy();hls=null;}});
  }else {video.src=item.url;video.load();safePlay();}
  renderList();
}
video.addEventListener('loadedmetadata',()=>{
  $('duration').textContent=Number.isFinite(video.duration)?formatTime(video.duration):'直播';$('seek').disabled=!Number.isFinite(video.duration);
  const item=items.find(x=>x.id===currentId);if(item?.position>0&&item.position<video.duration-2){video.currentTime=item.position;toast('已恢复到 '+formatTime(item.position));}
  video.playbackRate=Number($('speed').value);
});
video.addEventListener('timeupdate',()=>{
  $('current').textContent=formatTime(video.currentTime);
  if(Number.isFinite(video.duration)&&video.duration>0)$('seek').value=video.currentTime/video.duration*1000;
  if(Date.now()-saveAt>3000){savePosition();saveAt=Date.now();}
  enforceLoop();
});
function enforceLoop(){if(loop&&video.readyState>=1&&(video.currentTime>=ab[1]||video.currentTime<ab[0]-.2))video.currentTime=ab[0];}
setInterval(()=>{if(loop&&!video.paused)enforceLoop();},40);
video.addEventListener('play',()=>{$('play').textContent='Ⅱ';$('play').setAttribute('aria-label','暂停');});
video.addEventListener('pause',()=>{$('play').textContent='▶';$('play').setAttribute('aria-label','播放');savePosition();});
video.addEventListener('error',()=>{if(currentId&&video.error?.code!==1)toast('播放失败：可能是网页链接、格式不兼容、链接失效或来源限制');});
video.addEventListener('ended',()=>{
  if(loop){video.currentTime=ab[0];safePlay();return;}
  const item=items.find(x=>x.id===currentId);if(item)item.position=0;save();
  const index=items.findIndex(x=>x.id===currentId);if($('autoNext').checked&&items[index+1])loadItem(items[index+1].id);
});
function needMedia(){if(video.readyState<1){toast('请先打开一个可播放的视频');return false;}return true;}
function togglePlay(){if(!currentId){$('files').click();return;}if(video.paused)safePlay();else video.pause();}
$('play').onclick=togglePlay;
function seekTo(seconds){if(!needMedia())return;loop=false;updateLoop();video.currentTime=clampSeek(seconds,video.duration);}
document.querySelectorAll('[data-skip]').forEach(button=>button.onclick=()=>seekTo(video.currentTime+Number(button.dataset.skip)));
$('seek').oninput=()=>seekTo(Number($('seek').value)/1000*video.duration);
$('mute').onclick=()=>{video.muted=!video.muted;$('mute').textContent=video.muted?'×':'♪';$('mute').setAttribute('aria-label',video.muted?'取消静音':'静音');};
$('volume').oninput=()=>{video.volume=Number($('volume').value);video.muted=video.volume===0;$('mute').textContent=video.muted?'×':'♪';};
$('speed').onchange=()=>{video.playbackRate=Number($('speed').value);};
$('wholeLoop').onchange=()=>{video.loop=$('wholeLoop').checked;};
function mark(index){if(!needMedia())return;ab[index]=Math.round(video.currentTime*100)/100;$(index===0?'pointA':'pointB').value=formatTime(ab[index],true);loop=false;updateLoop();toast('已设置'+(index===0?'起点 A':'终点 B'));}
$('markA').onclick=()=>mark(0);$('markB').onclick=()=>mark(1);$('resetAB').onclick=resetAB;
function toggleAB(){if(loop){loop=false;updateLoop();return;}if(!needMedia())return;const a=parseTime($('pointA').value),b=parseTime($('pointB').value);if(!validAB(a,b,video.duration)){toast('请设置有效区间：终点 B 大于起点 A，且不超过视频时长');return;}ab=[a,b];loop=true;video.currentTime=a;updateLoop();safePlay();}
$('loopAB').onclick=toggleAB;
['pointA','pointB'].forEach(id=>$(id).oninput=()=>{loop=false;updateLoop();});
function tab(local){$('tabLocal').classList.toggle('active',local);$('tabUrl').classList.toggle('active',!local);$('localPane').hidden=!local;$('urlForm').hidden=local;}
$('tabLocal').onclick=()=>tab(true);$('tabUrl').onclick=()=>tab(false);
$('openLocal').onclick=$('localPick').onclick=$('openTop').onclick=()=>$('files').click();
$('openUrl').onclick=()=>{tab(false);$('urlInput').scrollIntoView({behavior:'smooth',block:'center'});$('urlInput').focus({preventScroll:true});};
$('files').onchange=()=>{
  const added=[];
  for(const file of $('files').files){
    if(/\.m3u8$/i.test(file.name)){toast('本地 M3U8 依赖分片文件，请改用在线 M3U8 地址');continue;}
    if(!file.type.startsWith('video/')&&!file.type.startsWith('audio/')&&!/\.(mp4|webm|mkv|mov|m4v|avi|mp3|ogg|flac)$/i.test(file.name)){toast('请选择视频或音频文件');continue;}
    added.push({id:makeId(),name:file.name,url:URL.createObjectURL(file),position:0,kind:'file'});
  }
  items.push(...added);renderList();if(added.length)loadItem(added[0].id);$('files').value='';
};
$('urlForm').onsubmit=e=>{
  e.preventDefault();let url;try{url=normalizeUrl($('urlInput').value);}catch(err){toast(err.message||'请输入完整的视频直链');return;}
  let item=items.find(x=>x.kind==='url'&&x.url===url);
  if(!item){let name;try{name=decodeURIComponent(new URL(url).pathname.split('/').filter(Boolean).pop()||new URL(url).hostname);}catch{name='网络视频';}item={id:makeId(),name,url,position:0,kind:'url'};items.push(item);}
  save();loadItem(item.id);$('urlInput').value='';$('playerShell').scrollIntoView({behavior:'smooth',block:'start'});
};
function removeItem(id){const item=items.find(x=>x.id===id);if(id===currentId)stop();if(item?.kind==='file')URL.revokeObjectURL(item.url);items=items.filter(x=>x.id!==id);save();renderList();}
$('clearList').onclick=()=>{stop();items.forEach(x=>{if(x.kind==='file')URL.revokeObjectURL(x.url);});items=[];save();renderList();toast('播放列表与进度已清空');};
$('full').onclick=async()=>{if(!needMedia())return;try{if(document.fullscreenElement)await document.exitFullscreen();else if($('playerShell').requestFullscreen)await $('playerShell').requestFullscreen();else if(video.webkitEnterFullscreen)video.webkitEnterFullscreen();else toast('当前浏览器不支持全屏');}catch{toast('当前浏览器无法进入全屏');}};
$('pip').onclick=async()=>{if(!needMedia())return;try{if(document.pictureInPictureElement)await document.exitPictureInPicture();else if(document.pictureInPictureEnabled)await video.requestPictureInPicture();else toast('当前浏览器不支持画中画');}catch{toast('此视频或浏览器暂不支持画中画');}};
$('subtitle').onclick=()=>{if(needMedia())$('subs').click();};
$('subs').onchange=async()=>{
  const file=$('subs').files[0];if(!file)return;const id=currentId;
  try{if(file.size>5*1024*1024)throw new Error('字幕文件请小于 5MB');const text=srtToVtt(await file.text());if(currentId!==id)return;clearSubtitle();subtitleUrl=URL.createObjectURL(new Blob([text],{type:'text/vtt'}));const track=document.createElement('track');track.kind='subtitles';track.label=file.name;track.srclang='zh';track.src=subtitleUrl;track.default=true;video.append(track);track.track.mode='showing';toast('字幕已导入');}catch(e){toast(e.message||'字幕导入失败');}$('subs').value='';
};
$('screenshot').onclick=()=>{
  if(!needMedia()||!video.videoWidth){toast('当前没有可截图的视频画面');return;}
  try{const canvas=document.createElement('canvas');canvas.width=video.videoWidth;canvas.height=video.videoHeight;canvas.getContext('2d').drawImage(video,0,0);canvas.toBlob(blob=>{if(!blob){toast('截图失败');return;}const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='全能-'+formatTime(video.currentTime).replaceAll(':','-')+'.png';a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);},'image/png');}catch{toast('来源网站限制截图，请使用本地视频或手机系统截图');}
};
$('help').onclick=()=>$('helpDialog').showModal();$('closeHelp').onclick=()=>$('helpDialog').close();
document.addEventListener('keydown',e=>{
  if(e.target.closest('input,textarea,select,button')||$('helpDialog').open||e.ctrlKey||e.metaKey||e.altKey)return;
  const actions={' ':togglePlay,ArrowLeft:()=>seekTo(video.currentTime-10),ArrowRight:()=>seekTo(video.currentTime+10),a:()=>mark(0),b:()=>mark(1),l:toggleAB,f:()=>$('full').click()};const action=actions[e.key];if(action){e.preventDefault();action();}
});
window.addEventListener('pagehide',savePosition);renderList();
