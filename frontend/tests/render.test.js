import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {mkdtemp,writeFile,rm} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createElement} from 'react';
import {renderToString} from 'react-dom/server';
test('actual app renders player and bookmarks without removed AI or missing icons',async()=>{
 const folder=await mkdtemp(fileURLToPath(new URL('../node_modules/.quanneng-render-',import.meta.url)));
 globalThis.window={};globalThis.location={hash:''};globalThis.localStorage={getItem:()=>null};
 try{
  const result=await build({entryPoints:[fileURLToPath(new URL('../src/main.jsx',import.meta.url))],bundle:true,format:'esm',platform:'node',write:false,loader:{'.css':'empty'},external:['react','react-dom/client','@phosphor-icons/react','hls.js']});
  const file=folder+'/app.mjs';await writeFile(file,result.outputFiles[0].text);const {App}=await import(pathToFileURL(file));
  const html=renderToString(createElement(App));assert.ok(html.includes('网址收藏'));assert.ok(html.includes('添加第一个网址'));assert.ok(html.includes('还没有视频'));for(const name of ['百度网盘','哔哩哔哩','抖音','快手','腾讯视频','爱奇艺'])assert.ok(html.includes(name),name);assert.ok(html.includes('导入并保存到手机'));assert.ok(!html.includes('全能 AI'));assert.ok(!html.includes('API Key'));assert.ok(html.includes('功能中心'));assert.equal((html.match(/class="feature-card"/g)||[]).length,12);assert.ok(html.includes('网页内核'));assert.ok(html.includes('原生内核'));assert.ok(html.includes('私人加密保险箱'));
 }finally{delete globalThis.window;delete globalThis.location;delete globalThis.localStorage;await rm(folder,{recursive:true,force:true});}
});
