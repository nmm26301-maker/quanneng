import {normalizeUrl} from './core.js';
const KEY='quanneng-bookmarks-v1';

function valid(item){
  if(!item||typeof item.id!=='string'||typeof item.name!=='string'||typeof item.url!=='string')return false;
  try{return !!item.name.trim()&&item.name.length<=60&&normalizeUrl(item.url)===item.url;}catch{return false;}
}
export function loadBookmarks(storage=localStorage){
  try{const value=JSON.parse(storage.getItem(KEY)||'[]');return Array.isArray(value)?value.filter(valid).slice(0,500):[];}catch{return [];}
}
function persist(items,storage){
  try{storage.setItem(KEY,JSON.stringify(items));}catch{throw Error('保存失败：本机存储不可用或空间不足，请清理后重试');}
  return items;
}
export function saveBookmark(items,draft,storage=localStorage){
  const name=String(draft.name||'').trim();if(!name||name.length>60)throw Error('请填写 1–60 字的名称');
  let url;try{url=normalizeUrl(String(draft.url||''));}catch{throw Error('请输入完整的 HTTPS 网址，例如 https://example.com');}
  if(url.length>4096)throw Error('网址过长，请使用网页的分享链接');
  const duplicate=items.find(x=>x.url===url&&x.id!==draft.id);
  const entry={id:duplicate?.id||draft.id,name,url};
  const next=items.filter(x=>x.id!==draft.id&&x.id!==entry.id);
  if(next.length>=500)throw Error('最多保存 500 个网址，请先删除不需要的收藏');
  return persist([entry,...next],storage);
}
export function removeBookmark(items,id,storage=localStorage){return persist(items.filter(x=>x.id!==id),storage);}
