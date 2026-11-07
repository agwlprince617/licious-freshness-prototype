import { BrowserDemo, COOKS, SLOTS, BATCHES } from './engine.mjs';

const $ = s => document.querySelector(s);
const runtime = $('meta[name="demo-runtime"]').content;
const local = new BrowserDemo();
const state = { cook:'tonight', slot:'express', held:false, quote:null, failure:null, receipt:null,
  lastReceipt:null, cart:crypto.randomUUID(), key:crypto.randomUUID(), stale:false, request:0, events:[] };
const ms = value => typeof value === 'number' ? value : Date.parse(value);
const reasonLabels = { ELIGIBLE:'Eligible', DELIVERY_AFTER_COOK:'Arrives after the meal', QUALITY_HOLD:'Quality hold',
  OUT_OF_STOCK:'No stock', NOT_RELEASED_FOR_SLOT:'Not released for this slot', COOK_TIME_NOT_COVERED:'Cooking time not covered' };
async function api(path, params={}, method='GET', key) {
  const response = await fetch(`/api/${path}?${new URLSearchParams(params)}`, { method,
    headers:key?{'Idempotency-Key':key}:{} });
  const value = await response.json();
  if (!response.ok) throw new Error(value.error || 'REQUEST_FAILED');
  return value;
}
const transport = runtime === 'java' ? {
  quote:() => api('quote',{cart:state.cart,cook:state.cook,slot:state.slot}),
  reserve:(id,key) => api('reserve',{cart:state.cart,quote:id},'POST',key),
  hold:value => api('quality',{held:value},'POST'), reset:() => api('reset',{},'POST')
} : {
  quote:async() => local.quote(state.cook,state.slot), reserve:async(id,key) => local.reserve(id,key),
  hold:async value => local.qualityHold(value), reset:async() => local.reset()
};
function log(message,error=false) {
  state.events.unshift({message,error}); state.events = state.events.slice(0,7);
  $('#events').replaceChildren(...state.events.map(e => { const li=document.createElement('li'); li.textContent=e.message; if(e.error)li.className='error'; return li; }));
}
function setControls() {
  document.querySelectorAll('[data-cook]').forEach(b => {
    b.setAttribute('aria-pressed',String(b.dataset.cook===state.cook)); b.disabled=!!state.receipt;
  });
  $('#slot').value=state.slot; $('#slot').disabled=!!state.receipt;
  $('#quality-hold').checked=state.held; $('#quality-hold').disabled=!!state.receipt;
}
function promise() {
  const q=state.quote, panel=$('#promise');
  panel.className='promise'; $('#reserve').disabled=true;
  if(!q) { panel.innerHTML='<span class="promise-icon">◷</span><div class="promise-content"><h3>Checking the demo inventory…</h3><p>Matching your cooking time to an eligible batch.</p></div>'; return; }
  if(state.receipt) {
    const remaining=Math.max(0,Math.ceil((ms(state.receipt.expiresAt)-Date.now())/1000));
    panel.innerHTML=`<span class="promise-icon">✓</span><div class="promise-content"><h3>Your demo pack is reserved</h3><p>The selected batch supports ${COOKS[state.cook].detail.toLowerCase()} in this fixture.</p><p class="hint">Demo hold: <span id="hold-seconds">${remaining}</span>s remaining. No order was placed.</p></div>`;
    $('#reserve').innerHTML='Demo pack reserved <span aria-hidden="true">✓</span>'; return;
  }
  if(state.failure) {
    panel.classList.add('failure');
    panel.innerHTML='<span class="promise-icon">!</span><div class="promise-content"><h3>This delivery option changed</h3><p>We could not honor the earlier promise. No pack has been reserved.</p><button class="change-slot" id="refresh-inline">Refresh available options →</button></div>';
    $('#refresh-inline').addEventListener('click',refresh); return;
  }
  $('#reserve').innerHTML='Reserve a demo pack <span aria-hidden="true">→</span>';
  if(q.batch) {
    panel.innerHTML=`<span class="promise-icon">✓</span><div class="promise-content"><h3>A promise for ${state.cook==='tonight'?'tonight’s':state.cook==='tomorrow'?'tomorrow’s':'Monday’s'} meal</h3><p>Delivery ${SLOTS[state.slot].label.toLowerCase()} meets your selected cooking time in this demo.</p><p class="hint">Subject to the fixture’s storage rules and a fresh check on acceptance.</p></div>`;
    $('#reserve').disabled=false;
  } else {
    panel.classList.add('warning');
    const next=q.alternatives[0];
    panel.innerHTML=`<span class="promise-icon">◷</span><div class="promise-content"><h3>${next?'Choose a different delivery':'This combination is unavailable'}</h3><p>${next?'The requested delivery cannot support your selected cooking time from the current demo batches.':'No current demo batch can support this selection. Try another cooking time.'}</p>${next?`<button class="change-slot" id="change-slot">Try ${SLOTS[next.slotKey].label.toLowerCase()} →</button>`:''}</div>`;
    if(next) $('#change-slot').addEventListener('click',()=>{state.slot=next.slotKey; refresh();});
  }
}
function engineering() {
  if(!state.quote)return;
  $('#stale-alert').hidden=!state.stale;
  $('#batch-rows').innerHTML=state.quote.diagnostics.map(d => {
    const b=BATCHES.find(x=>x.id===d.id), reason=state.stale&&d.id==='B'&&state.held?'QUALITY_HOLD':d.reason;
    const selected=state.quote.batch===d.id&&reason==='ELIGIBLE';
    const release={A:'Sat 7 Nov · 8 am',B:'Sat 7 Nov · 2 pm',C:'Sun 8 Nov · 9 am'}[d.id];
    const available=d.available-(state.receipt?.batch===d.id?1:0);
    return `<tr class="${selected?'chosen':''}"><td>Batch ${d.id}${selected?' · selected':''}</td><td>${release}</td><td>${b.useByLabel}</td><td>${available}</td><td><span class="reason ${reason==='ELIGIBLE'?'ok':reason==='QUALITY_HOLD'?'held':''}">${reasonLabels[reason]||reason}</span></td></tr>`;
  }).join('');
  $('#retry').disabled=!state.lastReceipt;
}
async function refresh() {
  const request=++state.request; state.failure=null; state.stale=false; state.key=crypto.randomUUID();
  setControls(); $('#reserve').disabled=true;
  try {
    const q=await transport.quote(); if(request!==state.request)return;
    state.quote=q; promise(); engineering();
    log(q.batch?`QUOTED · ${state.cook}/${state.slot} → batch ${q.batch}`:`NO_PROMISE · ${state.cook}/${state.slot}`);
  } catch(error) {
    if(request!==state.request)return;
    $('#promise').className='promise failure'; $('#promise').innerHTML='<span class="promise-icon">!</span><div><h3>The demo could not refresh</h3><p>Reset the demo or restart the local Java service.</p></div>';
    log(error.message,true);
  }
}
async function reset({silent=false}={}) {
  await transport.reset(); state.cook='tonight';state.slot='express';state.held=false;state.failure=null;state.receipt=null;state.lastReceipt=null;state.stale=false;
  state.cart=crypto.randomUUID(); state.events=[]; $('#events').replaceChildren();
  if(!silent)log('RESET · synthetic inventory restored');
  await refresh();
}
async function hold(value) {
  await transport.hold(value);state.held=value;state.stale=true;
  $('#quality-hold').checked=value; engineering(); log(value?'QUALITY_CHANGE · batch B held':'QUALITY_CHANGE · batch B released',value);
}
document.querySelectorAll('[data-cook]').forEach(b=>b.addEventListener('click',()=>{state.cook=b.dataset.cook;refresh();}));
$('#slot').addEventListener('change',event=>{state.slot=event.target.value;refresh();});
$('#reset').addEventListener('click',()=>reset()); $('#refresh').addEventListener('click',refresh);
$('#quality-hold').addEventListener('change',event=>hold(event.target.checked));
$('#reserve').addEventListener('click',async()=>{
  $('#reserve').disabled=true;
  try {
    const r=await transport.reserve(state.quote.quoteId,state.key);
    state.receipt=r; state.lastReceipt={receipt:r,key:state.key,quoteId:state.quote.quoteId};
    log(`RESERVED · batch ${r.batch} · 90s hold`); setControls();promise();engineering();
  } catch(error) {
    state.failure=error.message;log(`ACCEPTANCE_BLOCKED · ${error.message}`,true);promise();engineering();
  }
});
$('#retry').addEventListener('click',async()=>{
  const last=state.lastReceipt;
  try {
    const r=await transport.reserve(last.quoteId,last.key);
    const same=r.reservationId===last.receipt.reservationId;
    log(`RETRY · ${same?'same reservation':'unexpected receipt'} · ${r.status}`);
  }catch(error){log(error.message,true);}
});
$('#details-toggle').addEventListener('click',()=>{
  const open=$('#engineering').hidden; $('#engineering').hidden=!open;
  $('#details-toggle').setAttribute('aria-expanded',String(open));
  $('#details-toggle').innerHTML=`${open?'Hide the decision':'Inspect the decision'} <span aria-hidden="true">${open?'−':'+'}</span>`;
});
function openEngineering(){if($('#engineering').hidden)$('#details-toggle').click();}
document.querySelectorAll('[data-scenario]').forEach(b=>b.addEventListener('click',async()=>{
  await reset({silent:true});
  if(b.dataset.scenario==='tomorrow')state.cook='tomorrow';
  if(b.dataset.scenario==='quality'){state.cook='tomorrow';state.slot='evening';}
  await refresh(); openEngineering();
  if(b.dataset.scenario==='quality')await hold(true);
  document.querySelector('#demo').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});
}));
$('#runtime-label').textContent=runtime==='java'?'Java 21 service · local API':'Browser reference model · hosted demo';
$('#runtime-note').textContent=runtime==='java'
  ?'Requests on this local preview run against the Java implementation. State and locks exist in one JVM; no production integration, payment or order is involved.'
  :'This hosted version runs a JavaScript reference model in your browser. The downloadable project includes a Java 21 API and cross-language decision checks. No hosted Java backend or Licious connection is implied.';
setInterval(async()=>{
  if(!state.receipt)return;
  const remaining=Math.max(0,Math.ceil((ms(state.receipt.expiresAt)-Date.now())/1000));
  if($('#hold-seconds'))$('#hold-seconds').textContent=String(remaining);
  if(remaining===0){state.receipt=null;log('HOLD_EXPIRED · released on next state check');setControls();await refresh();}
},1000);
await refresh();
