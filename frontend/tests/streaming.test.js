import test from 'node:test';
import assert from 'node:assert/strict';
import {HLS_BUFFER_CONFIG,createHlsRecovery} from '../src/streaming.js';
function fixture(){
 const calls=[],pending=new Map();let id=0;
 const recovery=createHlsRecovery({network:()=>calls.push('network'),media:()=>calls.push('media'),fatal:()=>calls.push('fatal'),waiting:()=>calls.push('waiting'),schedule:(f,delay)=>{pending.set(++id,f);calls.push(delay);return id;},cancel:i=>pending.delete(i)});
 return {recovery,calls,pending,flush(){for(const [i,f] of pending){pending.delete(i);f();}}};
}
test('fatal network recovery is bounded, delayed and does not schedule duplicates',()=>{
 const f=fixture();f.recovery.error('networkError');f.recovery.error('networkError');assert.equal(f.pending.size,1);f.flush();f.recovery.error('networkError');f.flush();f.recovery.error('networkError');f.flush();f.recovery.error('networkError');
 assert.deepEqual(f.calls,['waiting',1000,'network','waiting',2000,'network','waiting',4000,'network','fatal']);
});
test('source cleanup cancels recovery and ignores late callbacks',()=>{const f=fixture();f.recovery.error('networkError');f.recovery.dispose();f.flush();f.recovery.error('networkError');assert.equal(f.pending.size,0);assert.deepEqual(f.calls,['waiting',1000]);});
test('decoder recovery is attempted once then fails visibly',()=>{const f=fixture();f.recovery.error('mediaError');f.recovery.error('mediaError');f.recovery.error('mediaError');assert.deepEqual(f.calls,['media','fatal']);});
test('buffer policy bounds backward and forward buffering',()=>{assert.equal(HLS_BUFFER_CONFIG.backBufferLength,10);assert.equal(HLS_BUFFER_CONFIG.maxBufferLength,60);assert.equal(HLS_BUFFER_CONFIG.maxMaxBufferLength,120);assert.equal(HLS_BUFFER_CONFIG.maxBufferSize,64*1024*1024);});
