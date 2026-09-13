from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
import json
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*a): pass
 def do_POST(self):
  raw=self.rfile.read(int(self.headers['Content-Length']))
  if self.headers.get('Authorization')!='Bearer fixture-key':
   self.send_response(401); self.end_headers(); return
  if self.path=='/v1/audio/transcriptions':
   assert b'whisper-large-v3-turbo' in raw and b'RIFF' in raw and b'WAVE' in raw
   assert b'name="prompt"' not in raw
   payload={'text':'Custom endpoint salon is integration test'}
   print('speech: multipart WAV and requested model verified',flush=True)
  elif self.path=='/v1/chat/completions':
   req=json.loads(raw)
   assert req['model']=='qwen/qwen3.8-27b'
   if 'tools' not in req:
    data=json.loads(req['messages'][-1]['content'])
    assert data['transcription']=='Custom endpoint salon is integration test'
    assert data['custom_words']=='Celonis'
    message={'role':'assistant','content':json.dumps({'text':'Custom endpoint Celonis integration test'})}
    print('cleanup: separate request, raw transcript and custom words verified',flush=True)
   elif req['messages'][-1]['role']=='tool':
    message={'role':'assistant','content':'Saved the note.'}
    print('llm: tool result round verified',flush=True)
   else:
    assert any(m.get('content')=='Custom endpoint Celonis integration test' for m in req['messages'])
    tool=next(t['function'] for t in req['tools'] if t['function']['description'].startswith('builtin_note.create_note:'))
    message={'role':'assistant','content':None,'tool_calls':[{'id':'fixture_note_1','type':'function','function':{'name':tool['name'],'arguments':json.dumps({'text':'Custom endpoint integration test'})}}]}
    print('llm: model, messages and note tool verified',flush=True)
   payload={'choices':[{'message':message}]}
  else:
   self.send_response(404); self.end_headers(); return
  data=json.dumps(payload).encode(); self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data)
print('Fixture ready on loopback:8766',flush=True)
ThreadingHTTPServer(('127.0.0.1',8766),Handler).serve_forever()
