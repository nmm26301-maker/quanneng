import test from 'node:test';
import assert from 'node:assert/strict';
import {formatTime,parseTime,clampSeek,validAB,normalizeUrl,srtToVtt} from '../src/core.js';
test('time inputs preserve subsecond AB points and reject malformed fields',()=>{
assert.equal(parseTime('01:02:03.25'),3723.25);assert.equal(parseTime('10.5'),10.5);
for(const v of ['','-1','00:61:00','1:60','abc','1::2'])assert.ok(Number.isNaN(parseTime(v)));
assert.equal(formatTime(3723.25,true),'01:02:03.25');assert.equal(formatTime(Infinity),'00:00:00');
});
test('seeking stays in the media and AB requires an ordered finite interval',()=>{
assert.equal(clampSeek(-600,30),0);assert.equal(clampSeek(600,30),30);
assert.ok(validAB(0,1,30));assert.ok(!validAB(10,10,30));assert.ok(!validAB(0,40,30));assert.ok(!validAB(0,1,Infinity));
});
test('network inputs exclude unsafe and mixed-content schemes',()=>{
assert.equal(normalizeUrl(' https://example.com/a.mp4?q=1 '),'https://example.com/a.mp4?q=1');
for(const s of ['javascript:alert(1)','file:///a','http://example.com','https://user:pass@example.com'])assert.throws(()=>normalizeUrl(s));
});
test('SRT conversion keeps cue text and handles BOM and Windows lines',()=>{
assert.equal(srtToVtt('\uFEFF1\r\n00:00:01,500 --> 00:00:02,000\r\n你好'),'WEBVTT\n\n1\n00:00:01.500 --> 00:00:02.000\n你好');
assert.equal(srtToVtt('WEBVTT\n\n'),'WEBVTT\n\n');
});
