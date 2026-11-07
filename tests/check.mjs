import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { webcrypto } from 'node:crypto';
import { BrowserDemo, evaluate } from '../dist/engine.mjs';
if(!globalThis.crypto)globalThis.crypto=webcrypto;
const root=resolve(import.meta.dirname,'..');const classes=mkdtempSync(join(tmpdir(),'freshness-tests-'));
try{
  const sources=readdirSync(join(root,'java/src/demo')).filter(x=>x.endsWith('.java')).map(x=>join(root,'java/src/demo',x));
  execFileSync('javac',['--release','21','--add-modules','jdk.httpserver','-d',classes,...sources],{stdio:'inherit'});
  const args=['--add-modules','jdk.httpserver','-cp',classes];
  process.stdout.write(execFileSync('java',[...args,'demo.PromiseServiceTest'],{encoding:'utf8'}));
  const matrix=JSON.parse(execFileSync('java',[...args,'demo.Server','matrix'],{encoding:'utf8'}));
  for(const row of matrix){
    const j=row.selection,b=evaluate(j.cookKey,j.slotKey,row.held);
    assert.equal(b.batch,j.batch,`${j.cookKey}/${j.slotKey}/${row.held}`);
    assert.deepEqual(b.diagnostics.map(d=>[d.id,d.available,d.reason]),j.diagnostics.map(d=>[d.id,d.available,d.reason]));
  }
  console.log(`PASS ${matrix.length} cross-language cooking/slot/quality combinations`);
  const b=new BrowserDemo(),q=b.quote('tonight','express'),r=b.reserve(q.quoteId,'browser-key-1');
  assert.equal(b.reserve(q.quoteId,'browser-key-1').reservationId,r.reservationId);assert.equal(b.counts.A,2);
  console.log('PASS browser reservation replay does not decrement twice');
  const h=new BrowserDemo(),hq=h.quote('tomorrow','evening');h.qualityHold(true);
  assert.throws(()=>h.reserve(hq.quoteId,'browser-key-2'),/QUALITY_HOLD/);
  assert.equal(h.quote('tomorrow','evening').alternatives[0].batch,'C');
  console.log('PASS browser quality hold blocks acceptance and offers a feasible alternative');
}finally{rmSync(classes,{recursive:true,force:true});}
