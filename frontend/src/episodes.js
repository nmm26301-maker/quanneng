import {normalizeUrl} from './core.js';
export function parseSeries(name,text,seriesId){
 const title=String(name||'').trim();
 if(!title||title.length>80)throw Error('请输入 1–80 字的剧名');
 if(String(text||'').length>60000)throw Error('剧集链接内容过长，请分批添加');
 const lines=String(text||'').split(/\r?\n/).map(x=>x.trim()).filter(Boolean);
 if(!lines.length||lines.length>100)throw Error('请填写 1–100 集，每行一个视频直链');
 const seen=new Set();
 return lines.map((line,index)=>{
  const separator=line.indexOf('|');
  const label=separator<0?`第 ${index+1} 集`:line.slice(0,separator).trim();
  const raw=separator<0?line:line.slice(separator+1).trim();
  if(!label||label.length>160)throw Error(`第 ${index+1} 行集名需为 1–160 字`);
  const url=normalizeUrl(raw);
  if(seen.has(url))throw Error(`第 ${index+1} 行链接重复`);seen.add(url);
  return {id:`${seriesId}-${index}`,seriesId,seriesName:title,episode:index+1,name:label,url,kind:'url',position:0};
 });
}
export function episodesFor(items,current){
 if(!current)return [];
 if(!current.seriesId)return [current];
 const group=items.filter(x=>x.seriesId===current.seriesId&&x.kind===current.kind);
 return group.some(x=>x.url===current.url)?group:[current];
}
