import test from 'node:test';
import assert from 'node:assert/strict';
import {parseSeries,episodesFor} from '../src/episodes.js';
test('one named drama preserves episode order, names and group identity',()=>{
 const a=parseSeries(' 测试剧 ','第一集|https://video.example/e1.mp4\n第二集|https://video.example/e2.m3u8','a');
 assert.deepEqual(a.map(x=>[x.seriesName,x.episode,x.name]),[['测试剧',1,'第一集'],['测试剧',2,'第二集']]);
 const b=parseSeries('另一部剧','https://video.example/other.mp4','b');
 assert.deepEqual(episodesFor([...a,...b,{kind:'url',url:'https://video.example/movie.mp4'}],a[0]),a);
 assert.equal(episodesFor([...a,...b],b[0]).length,1);
 assert.equal(episodesFor(a,{kind:'url',url:'https://video.example/movie.mp4'}).length,1);
 assert.equal(episodesFor(JSON.parse(JSON.stringify(a)),a[0]).length,2);
});
test('invalid, duplicate or excessive episode links never partially import',()=>{
 assert.throws(()=>parseSeries('剧','https://video.example/1\nhttps://video.example/1','a'),/重复/);
 assert.throws(()=>parseSeries('剧','javascript:alert(1)','a'));
 assert.throws(()=>parseSeries('剧','https://user:password@video.example/a','a'));
 assert.throws(()=>parseSeries('','https://video.example/a','a'));
 assert.throws(()=>parseSeries('剧',Array.from({length:101},(_,i)=>`https://v.example/${i}`).join('\n'),'a'));
});
