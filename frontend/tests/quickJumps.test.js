import test from 'node:test';
import assert from 'node:assert/strict';
import {loadJumpSteps,saveJumpSteps,parseJumpSteps,jumpDelta} from '../src/quickJumps.js';
test('four independent seconds/minutes actions persist with explicit units',()=>{
 const disk=new Map(),storage={getItem:key=>disk.get(key),setItem:(key,value)=>disk.set(key,value)};
 assert.deepEqual(loadJumpSteps(storage),[10,1,10,1]);const values=saveJumpSteps(['7','2','13','3'],storage);assert.deepEqual(values,[7,2,13,3]);assert.deepEqual(loadJumpSteps(storage),values);
 assert.deepEqual(values.map((_,index)=>jumpDelta(values,index)),[-7,-120,13,180]);
});
test('zero, negative, fractional and excessive step values cannot be saved',()=>{
 for(const values of [[0,1,10,1],[-1,1,10,1],[7.5,1,10,1],[10,121,10,1],[3601,1,10,1],[10,1,10],[true,1,10,1]])assert.throws(()=>parseJumpSteps(values));
 assert.throws(()=>saveJumpSteps([7,2,13,3],{setItem(){throw Error('full');}}),/保存失败/);
});
