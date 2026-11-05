import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker,{extractGrounding,validateAssessment,Budget} from '../src/worker.js';
test('health reports missing credentials honestly',async()=>{let r=await worker.fetch(new Request('https://test/health'),{});assert.equal((await r.json()).status,'unconfigured');});
test('never permit unauthenticated paid requests',async()=>{let r=await worker.fetch(new Request('https://test/verify',{method:'POST',body:'{}'}),{GEMINI_API_KEY:'test',ACCESS_TOKEN:'secret'});assert.equal(r.status,401);});
test('source must be https and linked to a grounded segment',()=>{assert.deepEqual(extractGrounding({candidates:[{groundingMetadata:{groundingChunks:[{web:{uri:'javascript:alert(1)',title:'x'}},{web:{uri:'https://example.org',title:'x'}}],groundingSupports:[]}}]}),[]);});
test('unsupported confident verdict is downgraded without repeating assertions',()=>{let r=validateAssessment({verdict:'SAI',shortExplanation:'Tin giả',detailedExplanation:'Sai chắc chắn',sourceIds:[99]},[],'Nước chanh không chữa ung thư.');assert.equal(r.verdict,'CHUA_DU_BANG_CHUNG');assert.equal(r.normalizedClaim,'Nước chanh không chữa ung thư.');assert.doesNotMatch(r.detailedExplanation,/Sai chắc/);});
test('only grounding sources selected by assessment survive',()=>{let r=validateAssessment({verdict:'DUNG',shortExplanation:'Có nguồn',detailedExplanation:'Chi tiết',sourceIds:[0,99]},[{id:0,url:'https://example.org'}],'Câu gốc');assert.equal(r.references.length,1);assert.equal(r.verdict,'DUNG');});
test('invalid verdict fails closed',()=>assert.throws(()=>validateAssessment({verdict:'FAKE'},[],'x')));
test('daily budget blocks after configured limit',async()=>{let value;let storage={transaction:async f=>f({get:async()=>value,put:async(k,v)=>{value=v;}})};let b=new Budget({storage},{DAILY_LIMIT:'1'});assert.equal((await b.fetch()).status,200);assert.equal((await b.fetch()).status,429);});
test('verification uses real grounding metadata and preserves original negation',async t=>{
 const originalFetch=globalThis.fetch;t.after(()=>{globalThis.fetch=originalFetch});let calls=0;
 globalThis.fetch=async(url,init)=>{
  calls++;const body=JSON.parse(init.body);
  if(calls===1){assert.ok(body.tools[0].google_search);return Response.json({candidates:[{content:{parts:[{text:'Nước chanh không chữa ung thư [1].'}]},groundingMetadata:{groundingChunks:[{web:{uri:'https://www.cancer.gov/about-cancer/treatment',title:'NCI'}}],groundingSupports:[{segment:{text:'Không có bằng chứng chữa khỏi.'},groundingChunkIndices:[0]}],searchEntryPoint:{renderedContent:'<div>Search</div>'}}}]});}
  return Response.json({candidates:[{content:{parts:[{text:JSON.stringify({verdict:'DUNG',shortExplanation:'Có bằng chứng',detailedExplanation:'Phân tích',evidence:'Nguồn NCI',sourceIds:[0]})}]}}]});
 };
 const env={GEMINI_API_KEY:'test',ACCESS_TOKEN:'secret',BUDGET:{idFromName:()=>0,get:()=>({fetch:async()=>new Response('{}')})}};
 const claim='Nước chanh không chữa khỏi ung thư.';
 const r=await worker.fetch(new Request('https://test/verify',{method:'POST',headers:{Authorization:'Bearer secret'},body:JSON.stringify({claim})}),env);
 assert.equal(r.status,200);const data=await r.json();assert.equal(data.normalizedClaim,claim);assert.equal(data.verdict,'DUNG');assert.equal(data.references[0].url,'https://www.cancer.gov/about-cancer/treatment');assert.equal(data.references[0].publishDate,'');assert.equal(data.searchEntryPoint,'<div>Search</div>');assert.equal(calls,2);
});
test('AI outage returns an error rather than a fabricated fact check',async t=>{
 const originalFetch=globalThis.fetch;t.after(()=>{globalThis.fetch=originalFetch});globalThis.fetch=async()=>new Response('{}',{status:429});
 const env={GEMINI_API_KEY:'test',ACCESS_TOKEN:'secret',BUDGET:{idFromName:()=>0,get:()=>({fetch:async()=>new Response('{}')})}};
 const r=await worker.fetch(new Request('https://test/verify',{method:'POST',headers:{Authorization:'Bearer secret'},body:JSON.stringify({claim:'Test'})}),env);
 assert.equal(r.status,429);const data=await r.json();assert.ok(data.error);assert.equal(data.verdict,undefined);
});
test('configured connection authenticates without spending quota',async()=>{
 const r=await worker.fetch(new Request('https://test/connection',{method:'POST',headers:{Authorization:'Bearer secret'},body:'{}'}),{GEMINI_API_KEY:'test',ACCESS_TOKEN:'secret'});
 assert.equal(r.status,200);assert.equal((await r.json()).status,'ready');
});
test('no linked evidence skips the second paid request and returns honest insufficiency',async t=>{
 const originalFetch=globalThis.fetch;t.after(()=>{globalThis.fetch=originalFetch});let calls=0;
 globalThis.fetch=async()=>{calls++;return Response.json({candidates:[{content:{parts:[{text:'Không tìm được nguồn.'}]}}]});};
 const env={GEMINI_API_KEY:'test',ACCESS_TOKEN:'secret',BUDGET:{idFromName:()=>0,get:()=>({fetch:async()=>new Response('{}')})}};
 const claim='Tin mới chưa có nguồn';const r=await worker.fetch(new Request('https://test/verify',{method:'POST',headers:{Authorization:'Bearer secret'},body:JSON.stringify({claim})}),env);
 const data=await r.json();assert.equal(r.status,200);assert.equal(calls,1);assert.equal(data.verdict,'CHUA_DU_BANG_CHUNG');assert.deepEqual(data.references,[]);assert.equal(data.normalizedClaim,claim);
});
test('malformed upstream JSON returns 502 without a verdict',async t=>{
 const originalFetch=globalThis.fetch;t.after(()=>{globalThis.fetch=originalFetch});globalThis.fetch=async()=>new Response('<html>outage</html>');
 const env={GEMINI_API_KEY:'test',ACCESS_TOKEN:'secret',BUDGET:{idFromName:()=>0,get:()=>({fetch:async()=>new Response('{}')})}};
 const r=await worker.fetch(new Request('https://test/verify',{method:'POST',headers:{Authorization:'Bearer secret'},body:JSON.stringify({claim:'Tin cần xác minh'})}),env);
 assert.equal(r.status,502);assert.equal((await r.json()).verdict,undefined);
});
test('string or fractional source IDs never authorize a factual verdict',()=>{
 for(const id of ['0',0.9,true,-1,99]) {
  const r=validateAssessment({verdict:'DUNG',shortExplanation:'Đúng',detailedExplanation:'Có nguồn',sourceIds:[id]},[{id:0,url:'https://example.org'}],'Original');
  assert.equal(r.verdict,'CHUA_DU_BANG_CHUNG');assert.deepEqual(r.references,[]);
 }
});
test('empty explanations and credential-bearing sources fail validation',()=>{
 assert.throws(()=>validateAssessment({verdict:'DUNG',shortExplanation:' ',detailedExplanation:'x'},[],'x'));
 assert.deepEqual(extractGrounding({candidates:[{groundingMetadata:{groundingChunks:[{web:{uri:'https://user:pass@example.org/',title:'Example'}}],groundingSupports:[{groundingChunkIndices:[0],segment:{text:'Text'}}]}}]}),[]);
});
test('long saved records can be discussed without the old 24000-character rejection',async t=>{
 const originalFetch=globalThis.fetch;t.after(()=>{globalThis.fetch=originalFetch});
 globalThis.fetch=async()=>Response.json({candidates:[{content:{parts:[{thought:true,text:'Private reasoning'},{text:'Trả lời theo hồ sơ [1].'}]}}]});
 const env={GEMINI_API_KEY:'test',ACCESS_TOKEN:'secret',BUDGET:{idFromName:()=>0,get:()=>({fetch:async()=>new Response('{}')})}};
 const r=await worker.fetch(new Request('https://test/chat',{method:'POST',headers:{Authorization:'Bearer secret'},body:JSON.stringify({question:'Giải thích nguồn',context:'a'.repeat(50000)})}),env);
 assert.equal(r.status,200);assert.equal((await r.json()).text,'Trả lời theo hồ sơ [1].');
});
test('invalid requests do not consume quota or call a provider',async()=>{
 let reservations=0;const env={GEMINI_API_KEY:'test',ACCESS_TOKEN:'secret',BUDGET:{idFromName:()=>0,get:()=>({fetch:async()=>{reservations++;return new Response('{}');}})}};
 for (const body of ['null','[]','{}',JSON.stringify({claim:' '.repeat(5)}),JSON.stringify({claim:'x'.repeat(12001)})]) {
  const r=await worker.fetch(new Request('https://test/verify',{method:'POST',headers:{Authorization:'Bearer secret'},body}),env);assert.equal(r.status,400);
 }
 assert.equal(reservations,0);
});
