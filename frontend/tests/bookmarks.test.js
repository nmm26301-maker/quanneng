import test from 'node:test';
import assert from 'node:assert/strict';
import {loadBookmarks,saveBookmark,removeBookmark} from '../src/bookmarks.js';
const storage=()=>{const data=new Map();return {getItem:k=>data.get(k),setItem:(k,v)=>data.set(k,v)};};
test('named websites persist, rename, merge duplicate URLs and delete',()=>{
 const disk=storage();let items=saveBookmark([],{id:'a',name:' 示例 ',url:'https://example.com'},disk);
 assert.deepEqual(loadBookmarks(disk),[{id:'a',name:'示例',url:'https://example.com/'}]);
 items=saveBookmark(items,{id:'b',name:'新名称',url:'https://example.com/'},disk);assert.equal(items.length,1);assert.equal(items[0].id,'a');assert.equal(items[0].name,'新名称');
 items=saveBookmark(items,{...items[0],name:'重命名'},disk);assert.equal(loadBookmarks(disk)[0].name,'重命名');
 removeBookmark(items,'a',disk);assert.deepEqual(loadBookmarks(disk),[]);
});
test('bad imports and unsafe schemes never render or open; write failures stay visible',()=>{
 const disk=storage();disk.setItem('quanneng-bookmarks-v1',JSON.stringify([null,{id:'x',name:'unsafe',url:'javascript:alert(1)'}]));assert.deepEqual(loadBookmarks(disk),[]);
 assert.throws(()=>saveBookmark([],{id:'a',name:'',url:'https://example.com'},disk));
 assert.throws(()=>saveBookmark([],{id:'a',name:'unsafe',url:'https://user:pass@example.com'},disk));
 assert.throws(()=>saveBookmark([],{id:'a',name:'name',url:'https://example.com'},{setItem(){throw Error('full');}}),/保存失败/);
});
