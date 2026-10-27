// Use HLS.js' maintained ABR/retry machinery with bounded memory and recovery.
export const HLS_BUFFER_CONFIG=Object.freeze({
 maxBufferLength:60,maxMaxBufferLength:120,backBufferLength:10,
 maxBufferSize:64*1024*1024,startLevel:-1,capLevelToPlayerSize:true,
});
export function createHlsRecovery({network,media,fatal,waiting,schedule=setTimeout,cancel=clearTimeout}){
 let retries=0,mediaRecovered=false,timer=null,disposed=false;
 return {
  error(type){
   if(disposed)return;
   if(type==='networkError'&&retries<3){
    if(timer!==null)return;
    waiting();const delay=1000*2**retries++;
    timer=schedule(()=>{timer=null;if(!disposed)network();},delay);return;
   }
   if(type==='mediaError'&&!mediaRecovered){mediaRecovered=true;media();return;}
   if(timer!==null){cancel(timer);timer=null;}disposed=true;fatal();
  },
  dispose(){disposed=true;if(timer!==null)cancel(timer);timer=null;},
 };
}
