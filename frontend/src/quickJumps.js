export const JUMP_SPECS=[
 {key:'backSeconds',label:'后退秒数',unit:'秒',max:3600,sign:-1,multiplier:1},
 {key:'backMinutes',label:'后退分钟数',unit:'分钟',max:120,sign:-1,multiplier:60},
 {key:'forwardSeconds',label:'前进秒数',unit:'秒',max:3600,sign:1,multiplier:1},
 {key:'forwardMinutes',label:'前进分钟数',unit:'分钟',max:120,sign:1,multiplier:60},
];
const DEFAULTS=[10,1,10,1],KEY='quanneng-jump-steps-v1';
export function parseJumpSteps(values){
 if(!Array.isArray(values)||values.length!==4)throw Error('请设置四个跳转时间');
 return values.map((value,i)=>{const n=typeof value==='string'||typeof value==='number'?Number(value):NaN;if(!Number.isInteger(n)||n<1||n>JUMP_SPECS[i].max)throw Error(`${JUMP_SPECS[i].label}需为 1–${JUMP_SPECS[i].max} 的整数`);return n;});
}
export function loadJumpSteps(storage=localStorage){try{return parseJumpSteps(JSON.parse(storage.getItem(KEY)||'null'));}catch{return [...DEFAULTS];}}
export function saveJumpSteps(values,storage=localStorage){const next=parseJumpSteps(values);try{storage.setItem(KEY,JSON.stringify(next));}catch{throw Error('设置保存失败，请检查本机存储空间');}return next;}
export function jumpDelta(values,index){const spec=JUMP_SPECS[index];return values[index]*spec.sign*spec.multiplier;}
