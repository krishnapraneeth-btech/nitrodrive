#!/usr/bin/env python3
"""Builds a single-file web playable port of Nitro Drive from the Android sources."""
import re, base64, os, subprocess, sys, io

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/com/arena/nitrodrive')
OUT = os.path.join(ROOT, 'web/index.html')
FONTS = os.path.join(ROOT, 'app/assets/fonts')

# ---------- fonts ----------
KEEP = (' !"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ'
        '[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~'
        '₹◆★▶◀✕✓·—…×')

def subset(fname, outname):
    tmp = '/var/tmp/fontsub'
    os.makedirs(tmp, exist_ok=True)
    dst = os.path.join(tmp, outname)
    cmd = [sys.executable, '-m', 'fontTools.subset', os.path.join(FONTS, fname),
           '--text=' + KEEP, '--output-file=' + dst, '--flavor=woff',
           '--layout-features=', '--no-hinting', '--desubroutinize']
    subprocess.run(cmd, check=True, capture_output=True)
    data = open(dst, 'rb').read()
    return base64.b64encode(data).decode()

print('subsetting fonts...')
fonts_css = ''
font_map = {}
for key, fname in [('Head', 'Audiowide-Regular.ttf'), ('Bold', 'Rajdhani-Bold.ttf'),
                   ('Semi', 'Rajdhani-SemiBold.ttf'), ('Med', 'Rajdhani-Medium.ttf')]:
    b64 = subset(fname, key + '.woff')
    fonts_css += "@font-face{font-family:'NRD%s';src:url(data:font/woff;base64,%s)format('woff');font-display:block}\n" % (key, b64)
    font_map[key] = key
    print('  ', key, len(b64) // 1024, 'KB b64')


def terminate_assignments(txt):
    """Ensure every top-level `X.y = function(){...}` block ends with `;` (ASI hazard)."""
    lines = txt.split('\n')
    depth = 0
    in_s = None
    out = []
    for line in lines:
        i = 0
        clean = ''
        while i < len(line):
            ch = line[i]
            if in_s:
                if ch == '\\':
                    i += 2
                    continue
                if ch == in_s:
                    in_s = None
            else:
                if ch in '"\'':
                    in_s = ch
                elif ch == '/' and i + 1 < len(line) and line[i + 1] == '/':
                    break
                else:
                    clean += ch
            i += 1
        stripped = line.strip()
        # line that closes a top-level block
        if depth == 1 and stripped.startswith('}') and not stripped.startswith('};'):
            line = line.replace('}', '};', 1)
            clean = clean.replace('}', '};', 1)
        depth += clean.count('{') - clean.count('}')
        if depth < 0:
            depth = 0
        out.append(line)
    return '\n'.join(out)

# ---------- java -> js ----------
def strip_comments(s):
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    s = re.sub(r'(?m)^\s*//.*$', '', s)
    return s

def typecast_int(s):
    """(int) expr -> Math.trunc(expr) with a primary-expression scan."""
    out = []
    i = 0
    n = len(s)
    while i < n:
        if s.startswith('(int)', i):
            j = i + 5
            while j < n and s[j] in ' \n\t':
                j += 1
            # parse a primary expression: parens group or identifier chain
            if j < n and s[j] == '(':
                depth = 0
                k = j
                while k < n:
                    if s[k] == '(':
                        depth += 1
                    elif s[k] == ')':
                        depth -= 1
                        if depth == 0:
                            break
                    k += 1
                expr = s[j:k + 1]
                i = k + 1
            else:
                k = j
                while k < n and (s[k].isalnum() or s[k] in '_.'):
                    k += 1
                # method call args
                while k < n and s[k] == '(':
                    depth = 0
                    while k < n:
                        if s[k] == '(':
                            depth += 1
                        elif s[k] == ')':
                            depth -= 1
                            if depth == 0:
                                k += 1
                                break
                        k += 1
                expr = s[j:k]
                i = k
            out.append('Math.trunc(' + expr + ')')
        else:
            out.append(s[i])
            i += 1
    return ''.join(out)

TYPE_DEFAULT = {'float': '0', 'double': '0', 'int': '0', 'long': '0', 'boolean': 'false',
                'String': 'null', 'char': "' '", 'byte': '0', 'short': '0'}

def conv_decls(s):
    """Convert field/local declarations of the form `Type name;` / `Type name = x;` / multi."""
    # strip generic type params on new ArrayList<...>
    s = s.replace('new ArrayList<float[]>()', '[]')
    s = re.sub(r'new ArrayList<[^>]*>\(\)', '[]', s)
    s = re.sub(r'ArrayList<[^>]*>', '', s)
    # array literal / array new
    s = re.sub(r'new (float|int|boolean|double|String)\[(\w+)\]',
               lambda m: 'new Array(%s).fill(%s)' % (m.group(2), TYPE_DEFAULT.get(m.group(1), '0').replace("' '", '""')), s)
    # object instantiation with generic <>  -> strip
    s = re.sub(r'new (ArrayList)<[^>]*>\(\)', r'[]', s)
    # declarations
    pat = re.compile(
        r'(?m)^(\s*)(?:(?:public|private|protected|static|final|volatile|transient|synchronized)\s+)*'
        r'((?:float|double|int|long|boolean|char|byte|short|String|'
        r'[A-Z][\w.]*)(?:\[\])*)'
        r'\s+([\w$]+(?:\s*,\s*[\w$]+)*)(\s*=\s*[^;]+)?;')
    def repl(m):
        indent, typ, names, init = m.group(1), m.group(2), m.group(3), m.group(4)
        parts = [x.strip() for x in names.split(',')]
        if init:
            base = typ.split('[')[0]
            dflt = TYPE_DEFAULT.get(base, 'null')
            v = init.strip()[1:].strip()
            if len(parts) == 1:
                return '%svar %s = %s;' % (indent, parts[0], v)
            out = []
            for p in parts:
                out.append('%s%s = %s;' % (indent, p, dflt))
            return '\n'.join(out)
        base = typ.split('[')[0]
        dflt = TYPE_DEFAULT.get(base, 'null')
        if len(parts) == 1:
            return '%svar %s = %s;' % (indent, parts[0], dflt)
        return '\n'.join('%svar %s = %s;' % (indent, p, dflt) for p in parts)
    s = pat.sub(repl, s)
    return s

def conv_methods(s, cls):
    """public/private [static] RetType name(params) { -> function"""
    pat = re.compile(
        r'(?m)^(\s*)(?:(?:public|private|protected)\s+)?(?:(?:static|final|synchronized)\s+)*'
        r'((?:void|float|double|int|long|boolean|String|char|short|byte|[A-Z][\w]*)(?:\[\])*)\s+([\w$]+)\s*\(')
    def repl(m):
        indent, ret, name = m.group(1), m.group(2), m.group(3)
        if name in ('if', 'for', 'while', 'switch', 'catch'):
            return m.group(0)
        return '%sfunction %s(' % (indent, name)
    s = pat.sub(repl, s)
    # strip parameter types
    def fix_params(m):
        params = m.group(1)
        if not params.strip():
            return '()'
        out = []
        for p in params.split(','):
            p = p.strip()
            p = re.sub(r'^(?:final\s+)?(?:float|double|int|long|boolean|char|byte|short|String|'
                       r'[A-Z][\w]*(?:\[\])*(?:\[\])?)\s+', '', p)
            out.append(p)
        return '(' + ', '.join(out) + ')'
    # find function signatures and fix their param list
    def fix_sig(m):
        head = m.group(1)
        # find matching paren
        return head
    s = s.replace('Paint.Align.CENTER', 'CENTER').replace('Paint.Align.LEFT', 'LEFT').replace('Paint.Align.RIGHT', 'RIGHT')
    s = s.replace('Paint.Style.FILL', "'fill'").replace('Paint.Style.STROKE', "'stroke'")
    s = s.replace('Paint.Cap.ROUND', "'round'").replace('Paint.Cap.BUTT', "'butt'")
    s = re.sub(r'function\s+[\w$]+\(([^()]*)\)', lambda m: 'function ' + m.group(0).split('(')[0][9:] + '(' + (
        ', '.join(re.sub(r'^(?:final\s+)?(?:float|double|int|long|boolean|char|byte|short|String|[A-Z][\w]*(?:\[\])*)\s+', '', p.strip()) if p.strip() else '' for p in m.group(1).split(',')) if m.group(1).strip() else ''
    ) + ')', s)
    return s

def to_js(java, cls, method_names=None, field_names=None, is_object=True):
    s = strip_comments(java)
    s = re.sub(r'(?m)^\s*package\s+[^;]+;', '', s)
    s = re.sub(r'(?m)^\s*import\s+[^;]+;', '', s)
    # remove class declaration line(s)
    s = re.sub(r'(?m)^\s*(?:public\s+)?class\s+\w+[^{]*\{', '', s)
    s = re.sub(r'(?m)^\s*(?:public\s+)?(?:static\s+)?(?:final\s+)?class\s+\w+[^{]*\{', '', s)
    # constructors: ClassName(params){ -> ClassName.init(params){
    def ctor_repl(m):
        params = ', '.join(re.sub(r'^(?:final\s+)?(?:float|double|int|long|boolean|char|byte|short|String|[A-Z][\w]*(?:\[\])*)\s+', '', p.strip()) if p.strip() else '' for p in m.group(2).split(',')) if m.group(2).strip() else ''
        return m.group(1) + cls + '.init = function(' + params + ') {'
    s = re.sub(r'(?m)^(\s*)(?:public|private|protected)?\s*' + cls + r'\s*\(([^)]*)\)\s*\{', ctor_repl, s)
    # nested classes handled separately; strip leftover class keyword lines
    s = s.replace('this.', cls + '.')
    s = conv_decls(s)
    s = conv_methods(s, cls)
    # keywords
    s = s.replace('Paint.Style.FILL', "'fill'").replace('Paint.Style.STROKE', "'stroke'")
    s = s.replace('Paint.Cap.ROUND', "'round'").replace('Paint.Cap.BUTT', "'butt'")
    s = s.replace('Paint.Align.CENTER', 'CENTER').replace('Paint.Align.LEFT', 'LEFT').replace('Paint.Align.RIGHT', 'RIGHT')
    s = s.replace('new Paint(', 'new Paint2(').replace('new Path()', 'new Path2()')
    s = s.replace('Shader.TileMode.CLAMP', "'clamp'")
    s = re.sub(r'\bnew Random\(\)', 'new Random2()', s)
    s = re.sub(r'\(float\)\s*', '', s)
    s = re.sub(r'\(long\)\s*', '', s)
    s = re.sub(r'\(double\)\s*', '', s)
    s = typecast_int(s)
    s = re.sub(r'\bfloat\[\]\[\]', '', s)
    s = re.sub(r'\bint\[\]\[\]', '', s)
    s = re.sub(r'\bfloat\[\]', '', s)
    s = re.sub(r'\bint\[\]', '', s)
    s = re.sub(r'\bboolean\[\]', '', s)
    s = re.sub(r'\bString\[\]', '', s)
    s = re.sub(r'\bString\[\]\[\]', '', s)
    s = re.sub(r'\b(long|int|float|double|boolean|char|short|byte)\b', 'var', s) if False else s
    s = re.sub(r'\bfinal\s+', '', s)
    s = s.replace('SystemClock.uptimeMillis()', 'nowMs()')
    # for-loop declarations
    s = re.sub(r'for\s*\(\s*(?:final\s+)?(?:int|float|long|double|boolean|char|byte|short|String)\s+', 'for (var ', s)
    # enhanced-for whose type was already stripped: for ( x : arr)
    s = re.sub(r'for\s*\(\s*([\w$]+)\s*:\s*([^)]+)\)', r'for (var \1 of \2)', s)
    # enhanced for  for (Type v : arr)  -> for (var v of arr)
    s = re.sub(r'for\s*\(\s*var\s+([\w$]+)\s*:\s*([^)]+)\)', r'for (var \1 of \2)', s)
    s = re.sub(r'for\s*\(\s*(?:float|int|double|long|boolean|String|char)\s+([\w$]+)\s*:\s*([^)]+)\)', r'for (var \1 of \2)', s)
    # method refs: this::name -> (function(){ }) bound later; convert to plain arrow-less callable
    s = re.sub(r'this::([\w$]+)', r'function(){ ' + 'this.' + r'\1(); }', s)
    # anonymous classes: new Runnable() { public void run() { BODY } }  =>  (function() { BODY })
    s = re.sub(r'new\s+Runnable\(\)\s*\{\s*(?:public\s+)?void\s+run\(\)\s*\{', '(function() {', s)
    s = re.sub(r'\n\s*r?Game\.run = function\(\) \{ (.*?) \}\n', r'\n\1\n', s)
    s = re.sub(r'(gv\.postDelayedAction\(\(function\(\) \{[\s\S]*?\n\s*)}\s*,\s*(\d+)\);', r'\1}), \2);', s)
    # lambdas: (args) -> { body }  =>  function(args){ body }
    s = re.sub(r'\(([^()]*)\)\s*->\s*\{', lambda m: 'function(' + m.group(1) + ') {', s)
    s = s.replace('else gv.finishApp();', 'else gv.finishApp();')
    # `new long[N]` etc. (array decl already stripped of types)
    s = re.sub(r'new\s+(?:long|int|float|double|boolean|char|byte|short|String)\s*\[\s*(\w+)\s*\]',
               lambda m: 'new Array(%s).fill(0)' % m.group(1), s)
    # leftover primitive keywords from array decls like `long[] x` inside signatures
    s = re.sub(r'\b(?:float|double|int|long|boolean|char|byte|short)\s*\[\]', '', s)
    # numeric suffixes 1f / 0.5f / 2.0f -> plain numbers
    s = re.sub(r'(?<![\w.])(\d+\.\d+|\d+)([fFdDlL])\b', r'\1', s)
    s = re.sub(r'(?<![\w.])0x([0-9A-Fa-f]+)[lL]\b', r'0x\1', s)
    # `new T[]{...}` / `new T[]{` -> [...] (type may already be stripped)
    s = s.replace('new int[]{', '[')
    s = s.replace('new {sk', '[sk')
    s = re.sub(r'new\s+[\w\.]+\s*\[\]\s*\{', '[', s)
    s = re.sub(r'new\s+[\w\.]+\s*\[\]', '', s)
    # `new T[]{...}` -> [...]
    s = re.sub(r'new\s+[\w\.]+\s*\[\]\s*\{', '[', s)
    s = s.replace('Paint.Align ', '').replace('Paint.Style ', '').replace('Paint.Cap ', '')
    s = s.replace('Paint.Align.CENTER', 'CENTER').replace('Paint.Align.LEFT', 'LEFT').replace('Paint.Align.RIGHT', 'RIGHT')
    s = brace_arrays_to_brackets(s)
    # leftover java braces on initializer lines become bracket arrays
    s = re.sub(r'(?m)^(\s*)(return\s+)(\{.*\});\s*$', r'\1\2[\3]'.replace('[\\3]','[\3]') + ';', s)
    return s

def brace_arrays_to_brackets(txt):
    out = []
    n = len(txt)
    i = 0
    stack = []           # char stack of '{' or '['
    line_start = 0
    while i < n:
        ch = txt[i]
        if ch == '\n':
            out.append(ch)
            i += 1
            line_start = len(out)
            continue
        if ch in '{}':
            if ch == '}' and stack and stack[-1] == '[':
                out.append(']')
                stack.pop()
                i += 1
                continue
            stack.append(ch)
            out.append(ch)
            i += 1
            continue
        if ch == '[':
            stack.append('[')
            out.append(ch)
            i += 1
            continue
        if ch == ']':
            if stack and stack[-1] == '[':
                stack.pop()
            out.append(ch)
            i += 1
            continue
        out.append(ch)
        i += 1
    res = ''.join(out)
    # now: statements like `X = {1,2,3};` or `X = {\n {...},\n {...}\n };` where X is an assignment (has '=' before '{' on same statement)
    # convert using regex on assignment contexts
    res = re.sub(r'=\s*\{([^{}]*(?:\{[^{}]*\}[^{}]*)*)\}', lambda m: '= [' + m.group(1).replace('{','[').replace('}',']') + ']', res)
    # return {a, b, c};  ->  return [a, b, c];
    res = re.sub(r'return\s+\{([^{}]*)\};', lambda m: 'return [' + m.group(1).replace('{','[').replace('}',']') + '];', res)
    return res

# ---------- hand-written JS for platform pieces ----------
JS_LIBS = r'''
// ---------- tiny platform shims ----------
var CENTER='center', LEFT='left', RIGHT='right';
function nowMs(){ return performance.now(); }
function Random2(){ this._s = 123456789; }
Random2.prototype.setSeed=function(v){ this._s=v>>>0; };
Random2.prototype._n=function(){ this._s=(this._s*1664525+1013904223)>>>0; return this._s/4294967296; };
Random2.prototype.nextFloat=function(){ return this._n(); };
Random2.prototype.nextBoolean=function(){ return this._n()>0.5; };
Random2.prototype.nextInt=function(n){ return Math.floor(this._n()*n); };
Random2.prototype.nextDouble=function(){ return this._n(); };
function hexColor(c){ return '#'+('000000'+((c>>>0)&0xFFFFFF).toString(16)).slice(-6); }
function rgba(c,a){ c=c>>>0; a=Math.max(0,Math.min(255,a|0)); return 'rgba('+((c>>16)&255)+','+((c>>8)&255)+','+(c&255)+','+(a/255)+')'; }
function dateStr(){ var d=new Date(); return ''+d.getFullYear()+('0'+(d.getMonth()+1)).slice(-2)+('0'+d.getDate()).slice(-2); }

var CTX=null;
function Paint2(){ this.color=0xFF000000; this.style='fill'; this.sw=1; this.shader=null; this.a=255; this.tf=null; this.ts=16; this.align='left'; this.cap='butt'; this.ls=0; }
Paint2.prototype.setColor=function(c){ this.color=c>>>0; this.a=(this.color>>>24)&255; };
Paint2.prototype.setStyle=function(s){ this.style=s; };
Paint2.prototype.setStrokeWidth=function(w){ this.sw=w; };
Paint2.prototype.setShader=function(s){ this.shader=s; };
Paint2.prototype.setAlpha=function(a){ this.a=a; };
Paint2.prototype.setStrokeCap=function(c){ this.cap=c; };
Paint2.prototype.setTypeface=function(f){ this.tf=f; };
Paint2.prototype.setTextSize=function(s){ this.ts=s; };
Paint2.prototype.setTextAlign=function(a){ this.align=a; };
Paint2.prototype.setLetterSpacing=function(l){ this.ls=l; };
Paint2.prototype.setShadowLayer=function(r,dx,dy,c){ this.shadowR=r; this.shadowC=c; };
Paint2.prototype.clearShadowLayer=function(){ this.shadowR=0; this.shadowC=null; };
Paint2.prototype.measureText=function(s){ applyFont(this); return CTX.measureText(s).width; };

function applyFont(p){
  var fam = p.tf ? p.tf.css : "'NRDBold',sans-serif";
  CTX.font = (p.style==='stroke'?'':'') + p.ts + 'px ' + fam;
  CTX.textAlign = p.align;
  CTX.letterSpacing = (p.ls*p.ts)+'px';
}
function applyColor(p){
  if (p.shader && p.shader.g) { CTX.fillStyle = p.shader.g; CTX.strokeStyle = p.shader.g; return; }
  CTX.fillStyle = rgba(p.color, p.a);
  CTX.strokeStyle = rgba(p.color, p.a);
}
function applyPaint(p){
  applyColor(p);
  CTX.lineWidth = Math.max(0.5, p.sw);
  CTX.lineCap = p.cap;
  if (p.shadowR>0){ CTX.shadowColor = hexColor(p.shadowC); CTX.shadowBlur = p.shadowR; }
  else { CTX.shadowBlur = 0; CTX.shadowColor='transparent'; }
}
function Path2(){ this.p = new Path2D(); }
Path2.prototype.reset=function(){ this.p=new Path2D(); };
Path2.prototype.moveTo=function(x,y){ this.p.moveTo(x,y); };
Path2.prototype.lineTo=function(x,y){ this.p.lineTo(x,y); };
Path2.prototype.quadTo=function(x1,y1,x2,y2){ this.p.quadraticCurveTo(x1,y1,x2,y2); };
Path2.prototype.cubicTo=function(x1,y1,x2,y2,x3,y3){ this.p.bezierCurveTo(x1,y1,x2,y2,x3,y3); };
Path2.prototype.close=function(){ this.p.closePath(); };

function LinearGradient2(x0,y0,x1,y1,c0,c1){ this.g = CTX.createLinearGradient(x0,y0,x1,y1); this.g.addColorStop(0, rgba(c0,(c0>>>24)&255)); this.g.addColorStop(1, rgba(c1,(c1>>>24)&255)); }
function RadialGradient2(x,y,r,c0,c1){ this.g = CTX.createRadialGradient(x,y,0,x,y,Math.max(0.1,r)); this.g.addColorStop(0, rgba(c0,(c0>>>24)&255)); this.g.addColorStop(1, rgba(c1,(c1>>>24)&255)); }
function Shader(){}

function Canvas2(){
  this._stack=[];
}
Canvas2.prototype.save=function(){ CTX.save(); };
Canvas2.prototype.restore=function(){ CTX.restore(); };
Canvas2.prototype.translate=function(x,y){ CTX.translate(x,y); };
Canvas2.prototype.rotate=function(a,cx,cy){ if (cx===undefined){ CTX.rotate(a*Math.PI/180); } else { CTX.translate(cx,cy); CTX.rotate(a*Math.PI/180); CTX.translate(-cx,-cy); } };
Canvas2.prototype.scale=function(sx,sy,cx,cy){ if (cx===undefined){ CTX.scale(sx,sy); } else { CTX.translate(cx,cy); CTX.scale(sx,sy); CTX.translate(-cx,-cy); } };
Canvas2.prototype.skew=function(sx,sy){ CTX.transform(1, Math.tan(sy), Math.tan(sx), 1, 0, 0); };
Canvas2.prototype.drawRect=function(l,t,r,b,p){ applyPaint(p); CTX.fillRect(l,t,r-l,b-t); };
Canvas2.prototype.drawRoundRect=function(l,t,r,b,rx,ry,p){ applyPaint(p); CTX.beginPath(); CTX.roundRect(l,t,r-l,b-t,Math.min(rx,ry)); if (p.style==='stroke') CTX.stroke(); else CTX.fill(); };
Canvas2.prototype.drawCircle=function(x,y,r,p){ applyPaint(p); CTX.beginPath(); CTX.arc(x,y,Math.max(0.01,r),0,6.283185307); if (p.style==='stroke') CTX.stroke(); else CTX.fill(); };
Canvas2.prototype.drawOval=function(l,t,r,b,p){ applyPaint(p); CTX.beginPath(); CTX.ellipse((l+r)/2,(t+b)/2,Math.max(0.01,(r-l)/2),Math.max(0.01,(b-t)/2),0,0,6.283185307); if (p.style==='stroke') CTX.stroke(); else CTX.fill(); };
Canvas2.prototype.drawArc=function(l,t,r,b,s0,sw,uc,p){ applyPaint(p); CTX.beginPath(); CTX.ellipse((l+r)/2,(t+b)/2,Math.max(0.01,(r-l)/2),Math.max(0.01,(b-t)/2),0, s0*Math.PI/180, (s0+sw)*Math.PI/180); CTX.stroke(); };
Canvas2.prototype.drawPath=function(path,p){ applyPaint(p); if (p.style==='stroke') CTX.stroke(path.p); else CTX.fill(path.p); };
Canvas2.prototype.drawLine=function(x0,y0,x1,y1,a,b){ applyPaint(a); CTX.beginPath(); CTX.moveTo(x0,y0); CTX.lineTo(x1,y1); CTX.stroke(); };
Canvas2.prototype.drawText=function(str,x,y,p){ applyFont(p); applyPaint(p); if (p.style==='stroke') CTX.strokeText(str,x,y); else CTX.fillText(str,x,y); CTX.letterSpacing='0px'; };
Canvas2.prototype.drawColor=function(c){ CTX.fillStyle=hexColor(c); CTX.fillRect(0,0,CTX.canvas.width,CTX.canvas.height); };

function Typeface2(css){ this.css=css; }
Typeface2.createFromAsset=function(am,name){
  if (name.indexOf('Audiowide')>=0) return new Typeface2("'NRDHead',sans-serif");
  if (name.indexOf('Bold')>=0) return new Typeface2("'NRDBold',sans-serif");
  if (name.indexOf('SemiBold')>=0) return new Typeface2("'NRDSemi',sans-serif");
  return new Typeface2("'NRDMed',sans-serif");
};

// ---------- audio (WebAudio, hand-written to mirror Sfx/Music API) ----------
var Sfx = { enabled:true };
var AC=null;
function ac(){ if(!AC){ try{ AC=new (window.AudioContext||window.webkitAudioContext)(); }catch(e){} } if(AC&&AC.state==='suspended')AC.resume(); return AC; }
Sfx._tone=function(f0,f1,dur,type,vol,delay){
  var a=ac(); if(!a||!Sfx.enabled) return;
  var t=a.currentTime+(delay||0);
  var o=a.createOscillator(), g=a.createGain();
  o.type=type||'square'; o.frequency.setValueAtTime(f0,t);
  if (f1&&f1!==f0) o.frequency.exponentialRampToValueAtTime(Math.max(20,f1), t+dur);
  g.gain.setValueAtTime(0.0001,t); g.gain.exponentialRampToValueAtTime(Math.max(0.001,vol), t+0.008);
  g.gain.exponentialRampToValueAtTime(0.0001, t+dur);
  o.connect(g); g.connect(a.destination); o.start(t); o.stop(t+dur+0.02);
};
Sfx._noise=function(dur,vol,delay,freq){
  var a=ac(); if(!a||!Sfx.enabled) return;
  var len=Math.floor(a.sampleRate*dur), buf=a.createBuffer(1,len,a.sampleRate), d=buf.getChannelData(0);
  for(var i=0;i<len;i++) d[i]=(Math.random()*2-1)*Math.pow(1-i/len,2);
  var s=a.createBufferSource(); s.buffer=buf;
  var f=a.createBiquadFilter(); f.type='lowpass'; f.frequency.value=freq||1200;
  var g=a.createGain(); g.gain.value=vol;
  s.connect(f); f.connect(g); g.connect(a.destination); s.start(a.currentTime+(delay||0));
};
Sfx.init=function(){};
Sfx.coin=function(){ this._tone(988,988,0.09,'square',0.10); this._tone(1568,1568,0.12,'square',0.09,0.07); };
Sfx.hit=function(){ this._noise(0.25,0.5,0,900); this._tone(160,40,0.25,'square',0.2); };
Sfx.pass=function(){ this._tone(300,800,0.12,'triangle',0.10); };
Sfx.near=function(){ this._tone(900,320,0.1,'sawtooth',0.06); };
Sfx.click=function(){ this._tone(1200,1200,0.05,'triangle',0.10); };
Sfx.power=function(){ this._tone(220,1040,0.35,'square',0.10); this._tone(440,1560,0.35,'triangle',0.07); };
Sfx.buy=function(){ this._tone(500,900,0.15,'triangle',0.12); this._tone(900,1200,0.15,'triangle',0.09,0.12); };
Sfx.crash=function(){ this._noise(0.7,0.6,0,600); this._tone(160,30,0.5,'sawtooth',0.22); };
Sfx.shield=function(){ this._tone(660,660,0.2,'triangle',0.10); this._tone(990,990,0.25,'triangle',0.08,0.05); };
Sfx.levelUp=function(){ var n=[523,659,784,1046]; for(var i=0;i<4;i++) this._tone(n[i],n[i],0.18,'square',0.09,i*0.09); };
Sfx.countdown=function(){ this._tone(660,660,0.1,'triangle',0.12); };
Sfx.go=function(){ this._tone(1180,1180,0.25,'triangle',0.12); };
Sfx.clickSfx=function(){ this.click(); };
Sfx.coinSfx=function(){ this.coin(); };
Sfx.hitSfx=function(){ this.hit(); };
Sfx.passSfx=function(){ this.pass(); };
Sfx.nearSfx=function(){ this.near(); };
Sfx.powerSfx=function(){ this.power(); };
Sfx.buySfx=function(){ this.buy(); };
Sfx.crashSfx=function(){ this.crash(); };
Sfx.shieldSfx=function(){ this.shield(); };
Sfx.levelUpPlay=function(){ this.levelUp(); };
Sfx.countdownSfx=function(){ this.countdown(); };
Sfx.goSfx=function(){ this.go(); };
Sfx.setEngine=function(freq,vol){
  var a=ac(); if(!a) return;
  if(!this._eng){
    this._eng=a.createOscillator(); this._eng2=a.createOscillator();
    this._engF=a.createBiquadFilter(); this._engG=a.createGain();
    this._eng.type='sawtooth'; this._eng2.type='square';
    this._engF.type='lowpass'; this._engF.frequency.value=520;
    this._engG.gain.value=0;
    this._eng.connect(this._engF); this._eng2.connect(this._engF);
    this._engF.connect(this._engG); this._engG.connect(a.destination);
    this._eng.start(); this._eng2.start();
  }
  var on = Sfx.enabled && vol>0.004;
  this._engG.gain.setTargetAtTime(on?Math.min(0.05,vol*0.06):0, a.currentTime, 0.08);
  this._eng.frequency.setTargetAtTime(Math.max(30,freq), a.currentTime, 0.08);
  this._eng2.frequency.setTargetAtTime(Math.max(20,freq*0.5), a.currentTime, 0.08);
};

var Music = {};
Music._playing=false; Music._timer=null;
Music.init=function(){};
Music._step=function(self){
  var a=ac(); if(!a) return;
  var t=a.currentTime+0.02;
  var scale=[0,3,5,7,10], root=45;
  var bar=self._bar||0;
  for(var s=0;s<16;s++){
    var tt=t+s*0.127;
    if(s%4===0){ // kick
      var o=a.createOscillator(),g=a.createGain();
      o.frequency.setValueAtTime(130,tt); o.frequency.exponentialRampToValueAtTime(42,tt+0.16);
      g.gain.setValueAtTime(0.22,tt); g.gain.exponentialRampToValueAtTime(0.001,tt+0.18);
      o.connect(g); g.connect(a.destination); o.start(tt); o.stop(tt+0.2);
    }
    if(s%2===1){ self._hat(a,tt); }
    if(s%2===0){ // bass
      var note=root+[0,0,3,0,5,0,3,0][(s/2)%8];
      self._note(a,tt,0.24,440*Math.pow(2,(note-69)/12),0.055,'sawtooth');
    }
    var arp=[0,1,2,3,4,3,2,1][s%8];
    self._note(a,tt,0.10,440*Math.pow(2,(root+24+scale[arp]-69)/12),0.014,'square');
  }
  self._bar=(bar+1)%2;
};
Music._note=function(a,tt,dur,freq,vol,type){
  if(!Sfx.enabled) return;
  var o=a.createOscillator(),g=a.createGain();
  o.type=type; o.frequency.value=freq;
  g.gain.setValueAtTime(0.0001,tt); g.gain.exponentialRampToValueAtTime(vol,tt+0.01);
  g.gain.exponentialRampToValueAtTime(0.0001,tt+dur);
  o.connect(g); g.connect(a.destination); o.start(tt); o.stop(tt+dur+0.02);
};
Music._hat=function(a,tt){
  var len=Math.floor(a.sampleRate*0.03), buf=a.createBuffer(1,len,a.sampleRate), d=buf.getChannelData(0);
  for(var i=0;i<len;i++) d[i]=(Math.random()*2-1)*(1-i/len);
  var s=a.createBufferSource(); s.buffer=buf; var g=a.createGain(); g.gain.value=0.035;
  var f=a.createBiquadFilter(); f.type='highpass'; f.frequency.value=6000;
  s.connect(f); f.connect(g); g.connect(a.destination); s.start(tt);
};
Music.setEnabled=function(on){
  var a=ac(); if(!a) return;
  if (on && !this._playing){
    this._playing=true;
    var self=this;
    this._step(self);
    this._timer=setInterval(function(){ if(self._playing) self._step(self); }, 2032);
  } else if (!on && this._playing){
    this._playing=false;
    if (this._timer) clearInterval(this._timer);
    this._timer=null;
  }
};
Music.start=function(){ this.setEnabled(true); };
Music.stop=function(){ this.setEnabled(false); };

// ---------- adb bridge (GameView equivalent) ----------
var gv = {
  haptic: function(ms){ try{ if (Store.hapticsOn && navigator.vibrate && ms>8) navigator.vibrate(Math.min(ms,60)); }catch(e){} },
  postDelayedAction: function(fn,ms){ setTimeout(fn,ms); },
  applyQuality: function(){},
  finishApp: function(){}
};
'''

# ---------- convert sources ----------
def convert_store():
    src = open(os.path.join(SRC, 'Store.java')).read()
    # Replace platform save/load with localStorage versions before conversion
    src = re.sub(r'    public static String today\(\) \{.*?\n    \}', '''    public static String today() {
        return dateStr();
    }''', src, flags=re.S)
    src = re.sub(r'    public static void load\(Context c\) \{.*?\n    \}', '''    public static void load(Context c) {
        var raw = null;
        try { raw = localStorage.getItem('nitrodrive_save'); } catch (e) { }
        Store.sp = raw ? JSON.parse(raw) : {};
        var S = Store.sp;
        Store.coins = S.coins || 500;
        Store.bestDist = S.bestDist || 0;
        Store.totalDist = S.totalDist || 0;
        Store.totalCoinsEarned = S.tc || 0;
        Store.runCount = S.runs || 0;
        Store.selectedCar = S.car || 0;
        Store.upgSpeed = S.u0 || 0; Store.upgNitro = S.u1 || 0;
        Store.upgMagnet = S.u2 || 0; Store.upgArmor = S.u3 || 0;
        Store.sfxOn = S.sfx !== false; Store.musicOn = S.music !== false;
        Store.hapticsOn = S.hap !== false; Store.quality = S.qual || 1;
        Store.streak = S.streak || 0; Store.missionDay = S.mday || '';
        Store.selectedSkin = S.skin || 0;
        Store.unlocked[0] = true;
        for (var i = 1; i < 6; i++) Store.unlocked[i] = !!(S['uCar' + i]);
        for (var i = 0; i < 6; i++) Store.stars[i] = S['star' + i] || 0;
        for (var i = 0; i < 7; i++) { Store.bestMission[i] = S['m' + i] || 0; Store.claimed[i] = S['md' + i] || 0; }
        for (var i = 0; i < 18; i++) Store.skinOwned[i] = S['sk' + i] !== false;
        if (Store.selectedSkin < 0 || Store.selectedSkin >= 18) Store.selectedSkin = 0;
    }
    public static void save() {
    }''', src, flags=re.S)
    src = re.sub(r'    public static void save\(\) \{.*?\n    \}', '''    public static void save() {
        try {
            var S = {};
            S.coins = Store.coins; S.bestDist = Store.bestDist; S.totalDist = Store.totalDist;
            S.tc = Store.totalCoinsEarned; S.runs = Store.runCount; S.car = Store.selectedCar;
            S.u0 = Store.upgSpeed; S.u1 = Store.upgNitro; S.u2 = Store.upgMagnet; S.u3 = Store.upgArmor;
            S.sfx = Store.sfxOn; S.music = Store.musicOn; S.hap = Store.hapticsOn; S.qual = Store.quality;
            S.streak = Store.streak; S.mday = Store.missionDay; S.skin = Store.selectedSkin;
            for (var i = 1; i < 6; i++) S['uCar' + i] = Store.unlocked[i];
            for (var i = 0; i < 6; i++) S['star' + i] = Store.stars[i];
            for (var i = 0; i < 7; i++) { S['m' + i] = Store.bestMission[i]; S['md' + i] = Store.claimed[i]; }
            for (var i = 0; i < 18; i++) S['sk' + i] = Store.skinOwned[i];
            localStorage.setItem('nitrodrive_save', JSON.stringify(S));
        } catch (e) { }
    }''', src, flags=re.S)
    # Hand-convert the two nested classes to constructor functions first
    car_body = re.search(r'public static final class Car \{.*?\n    \}', src, re.S).group(0)
    theme_body = re.search(r'public static final class Theme \{.*?\n    \}', src, re.S).group(0)
    src = src.replace(car_body, '')
    src = src.replace(theme_body, '')
    js = to_js(src, 'Store')
    js = re.sub(r'(?m)^\}?\s*$', '', js, count=0)
    # remove trailing class brace remnants
    js = js.rstrip()
    if js.endswith('}'):
        js = js[:-1]
    # ---- Car class ----
    car_js = '''
function Car(n, tag, pow, body, body2, accent, glass, price, ts, h, np) {
  this.name=n; this.tagline=tag; this.power=pow;
  this.body=body; this.body2=body2; this.accent=accent; this.glass=glass;
  this.price=price; this.topSpeedMul=ts; this.handling=h; this.nitroPow=np;
}
function Theme(n, st, sb, g1, g2, ne, ne2, ln) {
  this.name=n; this.skyTop=st; this.skyBot=sb; this.ground1=g1; this.ground2=g2;
  this.neon=ne; this.neon2=ne2; this.lane=ln;
}
'''
    # Store methods become Store.x = function(){...}
    js = re.sub(r'(?m)^\s*function ([\w$]+)\(', lambda m: '\nStore.' + m.group(1) + ' = function(', js)
    # static fields: bare declarations became `var x = ...` -> Store.x
    for f in ['sp','coins','bestDist','totalDist','totalCoinsEarned','runCount','selectedCar','upgSpeed',
              'upgNitro','upgMagnet','upgArmor','sfxOn','musicOn','hapticsOn','quality','unlocked','stars',
              'bestMission','claimed','missionDay','streak','skinOwned','selectedSkin',
              'totalPassed','totalNearMiss','totalNitro','totalScore','UPG_COST_BASE',
              'UPG_MAX','CARS','THEMES','M_NAMES','M_DESC','M_BASE','M_GROW','M_REWARD','SKIN_NAMES',
              'SKIN_PRICE','SKIN_COLORS']:
        js = re.sub(r'(?m)^(\s*)var ' + f + r' = ', r'\1Store.' + f + ' = ', js)
        js = re.sub(r'(?<![.\w])' + f + r'\b(?!\s*:)', 'Store.' + f, js) if f in ('unlocked','stars','bestMission','claimed','skinOwned') else js
    # unqualified internal method calls
    for m in ['theme','missionTarget','upgCost','upg','load','save','carColors','carSkinOffset','init']:
        js = re.sub(r'(?<![.\w])' + m + r'\(', 'Store.' + m + '(', js)
    js = js.replace('Store.Store.', 'Store.')
    for f in ['M_GROW','M_BASE','M_REWARD','M_DESC','M_NAMES','SKIN_COLORS','SKIN_PRICE','SKIN_NAMES',
              'THEMES','CARS','UPG_COST_BASE','UPG_MAX','UPG_MAX']:
        js = re.sub(r'(?<![\.\w])' + f + r'(?![\w$])', 'Store.' + f, js)
    js = js.replace('Store.Store.', 'Store.')
    js = terminate_assignments(js)
    defaults = ['totalPassed','totalNearMiss','totalNitro','totalScore','runCount','coins','bestDist',
                'totalDist','totalCoinsEarned','totalEarned']
    head = 'var Store = {};\n' + ''.join('Store.%s = 0;\n' % f for f in defaults)
    return car_js + '\n' + head + js

def convert_simple(cls, extra_methods=()):
    src = open(os.path.join(SRC, cls + '.java')).read()
    js = to_js(src, cls)
    js = js.rstrip()
    if js.endswith('}'):
        js = js[:-1]
    js = re.sub(r'(?m)^\s*function ([\w$]+)\(', lambda m: '\n' + cls + '.' + m.group(1) + ' = function(', js)
    names = extra_methods
    for m in names:
        js = re.sub(r'(?<![.\w])' + m + r'\(', cls + '.' + m + '(', js)
    js = re.sub(r'(?m)^(\s*)var (F_HEAD|F_BOLD|F_SEMI|F_MED|quality) = ', r'\1Draw.\2 = ', js)
    js = js.replace('new Paint(', 'new Paint2(').replace('new Path()', 'new Path2()').replace('new RectF()', '{}')
    js = js.replace('Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG', '0').replace('Paint.ANTI_ALIAS_FLAG', '0')
    js = js.replace('Typeface.createFromAsset', 'Typeface2.createFromAsset')
    js = js.replace(cls + '.' + cls + '.', cls + '.')
    js = terminate_assignments(js)
    return 'var ' + cls + ' = {};\n' + js


def _strip_types(txt):
    return txt

def protect_params(js, names):
    """Rename function parameters that collide with object field names to p_<name>,
    so that later 'bare name -> Obj.name' qualification cannot shadow them."""
    import re as _re
    out = []
    i = 0
    n = len(js)
    pat = _re.compile(r'function\s*\(')
    while i < n:
        m = pat.search(js, i)
        if not m:
            out.append(js[i:]); break
        out.append(js[i:m.start()])
        k = m.end() - 1            # index of '('
        depth = 0; j = k
        while j < n:
            ch = js[j]
            if ch == '(': depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0: break
            j += 1
        params = js[k+1:j]
        b = js.find('{', j)
        if b < 0:
            out.append(js[m.start():]); break
        depth = 0; e = b
        while e < n:
            ch = js[e]
            if ch == '{': depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0: break
            e += 1
        body = js[b:e+1]
        renames = {}
        for name in names:
            if _re.search(r'(?<![\w$])' + name + r'(?![\w$])', params):
                renames[name] = 'p_' + name
                params = _re.sub(r'(?<![\w$])' + name + r'(?![\w$])', 'p_' + name, params)
        for old, new in renames.items():
            body = _re.sub(r'(?<![\w$.])' + old + r'(?![\w$])', new, body)
        out.append('function(' + params + ')' + js[j+1:b+1] + body[1:-1] + '}')
        i = e + 1
    return ''.join(out)

def build():
    store_js = convert_store()

    draw_js = convert_simple('Draw', ['clamp','lerp','lerpColor','alpha','lighten','darken','text','textGlow',
                                      'textLS','measure','rrect','rrectStroke','glowRRect','line','lg','fill',
                                      'iconPlay','iconPause','iconChevron','iconBolt','iconShield','iconStar',
                                      'iconClock','iconTrophy','iconGift','iconSound','iconMusic','iconHeart',
                                      'iconFlag','iconGear','iconCoin','fmt','fmt2','init','s'])
    # icons internally call bare helpers like `rr(` inside Draw? Draw uses s(...) helper etc.
    draw_js = draw_js.replace('Draw.s(', 'Draw.s(')
    # Rewrite fmt/fmt2 (String.format) with JS versions
    draw_js = re.sub(r'Draw\.fmt = function\(n\) \{.*?\n\}', r'''Draw.fmt = function(n) {
  n = Math.trunc(n);
  if (n < 1000) return '' + n;
  if (n < 1000000) return Math.trunc(n/1000) + ',' + ('00'+(n%1000)).slice(-3);
  return Math.trunc(n/1000000) + ',' + ('00'+Math.trunc((n/1000)%1000)).slice(-3) + ',' + ('00'+(n%1000)).slice(-3);
}''', draw_js, flags=re.S)
    draw_js = re.sub(r'Draw\.fmt2 = function\(n\) \{.*?\n\}', r'''Draw.fmt2 = function(n) {
  n = Math.trunc(n);
  if (n < 1000) return '' + n;
  if (n < 100000) return (n/1000 < 10 ? (n/1000).toFixed(1) : Math.trunc(n/1000)) + 'k';
  return (n/1000000 < 10 ? (n/1000000).toFixed(1) : Math.trunc(n/1000000)) + 'M';
}''', draw_js, flags=re.S)
    draw_js = re.sub(r"Draw\.init = function\(am\) \{.*?\n\}", r'''Draw.init = function(am) {
  Draw.F_HEAD = Typeface2.createFromAsset(null, 'Audiowide');
  Draw.F_BOLD = Typeface2.createFromAsset(null, 'Rajdhani-Bold');
  Draw.F_SEMI = Typeface2.createFromAsset(null, 'Rajdhani-SemiBold');
  Draw.F_MED = Typeface2.createFromAsset(null, 'Rajdhani-Medium');
  Draw.t.setTypeface(Draw.F_BOLD);
}''', draw_js, flags=re.S)
    draw_js = draw_js.replace('new LinearGradient(', 'new LinearGradient2(')
    draw_js = draw_js.replace('Draw.p = new Paint2(Paint.ANTI_ALIAS_FLAG);', 'Draw.p = new Paint2();')
    draw_js = draw_js.replace('Draw.t = new Paint2(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);', 'Draw.t = new Paint2();')
    draw_js = draw_js.replace('Draw.s = new Paint2(Paint.ANTI_ALIAS_FLAG);', 'Draw.s = new Paint2();')
    draw_js = draw_js.replace('Draw.path = new Path2();', 'Draw.path = new Path2();')
    draw_js = draw_js.replace('Draw.rect = new RectF();', 'Draw.rect = {};')
    # alpha uses >>> handling
    draw_js = re.sub(r'Draw\.alpha = function\(color, a\) \{.*?\n\}',
        'Draw.alpha = function(color, a) { return (((color>>>0)&0x00FFFFFF) | ((a&0xFF)<<24))>>>0; }', draw_js, flags=re.S)
    draw_js = re.sub(r'Draw\.lerpColor = function\(a, b, f\) \{.*?\n\}', r'''Draw.lerpColor = function(a, b, f) {
  a=a>>>0; b=b>>>0;
  var aa = Math.trunc(Draw.lerp((a>>>24)&0xFF, (b>>>24)&0xFF, f));
  var rr = Math.trunc(Draw.lerp((a>>16)&0xFF, (b>>16)&0xFF, f));
  var gg = Math.trunc(Draw.lerp((a>>8)&0xFF, (b>>8)&0xFF, f));
  var bb = Math.trunc(Draw.lerp(a&0xFF, b&0xFF, f));
  return ((aa<<24)|(rr<<16)|(gg<<8)|bb)>>>0;
}''', draw_js, flags=re.S)

    world_js = convert_simple('World', [
        'start','tapLane','steerTo','crashed','combo','speedBase','update','hitTraffic','applyPickup',
        'comboInc','addScoreCoins','spawnRow','freeLane','spawnCarRow','spawnDouble','spawnAt','spawnCoins',
        'spawnPickup','addParticle','addBurst','addText','updateParticles','banner','playerX','cX','depth',
        'laneWidthAt','draw','drawSky','drawRoad','drawTraffic','drawTruck','drawPolice','drawCoin',
        'drawPickup','pickupColor','drawPlayer','drawParticles','drawTexts','drawVignette','mod','resize'])
    world_js = world_js.replace('new RadialGradient(', 'new RadialGradient2(')
    world_js = world_js.replace('new LinearGradient(', 'new LinearGradient2(')
    # constructor
    world_js = world_js.replace('World = function(gv) {' if False else 'World.init = function(gv) {', 'World.init = function(gv) {')
    world_js = world_js.replace('World.init(gv)', 'World.init(gv)')

    # ---- lift World instance fields onto the World object (HUD + probes read them) ----
    import re as _re3
    WFIELDS = ['W','H','laneW','roadL','roadR','horizonY','py','over','wrecked','overNotified','paused',
               'crashShake','level','levelDist','levelGoal','meters','speedPx','speedKmh','score','lives',
               'invuln','nitro','nitroActive','nitroT','coinRun','passRun','nearRun','nitroUses','levelsDone',
               'coinMul','coinMulT','magnetT','shieldT','comboShown','comboT','comboMax','carLane','carLaneVis',
               'tilt','tick','bannerT','bannerBig','bannerText','totalEarned','scrollFar','scrollMid','dashOff','TH',
               'stripeOff','sideOff','spawnAcc','gapBase','nextType','carIdx','carCol','gv']
    world_js = protect_params(world_js, WFIELDS)
    for f in WFIELDS:
        world_js = _re3.sub(r'(?m)^(\s*)var ' + f + r' = ', r'\1World.' + f + ' = ', world_js)
    for f in WFIELDS:
        world_js = _re3.sub(r'(?<![.\w$])' + f + r'\b(?![\w$])', 'World.' + f, world_js)
        world_js = world_js.replace('World.World.', 'World.')
    world_js = world_js.replace('World.World.', 'World.')
    world_js = _re3.sub(r'(?<![.\w$])var (World\.)', r'\1', world_js)
    # sibling method calls inside World need the World. prefix
    for wm in ['laneBusy','freeLane','spawnRow','spawnCarRow','spawnDouble','spawnAt','spawnCoins','spawnPickup',
               'addParticle','addBurst','addText','updateParticles','banner','playerX','cX','depth','scaleAt',
               'laneWidthAt','hitTraffic','applyPickup','comboInc','addScoreCoins','speedBase','pickupColor',
               'drawSky','drawRoad','drawTraffic','drawTruck','drawPolice','drawCoin','drawPickup','drawPlayer',
               'drawParticles','drawTexts','drawVignette','tapLane','steerTo','mod','resize','start','update','draw']:
        world_js = _re3.sub(r'(?<![.\w$])' + wm + r'\s*\(', 'World.' + wm + '(', world_js)
    world_js = world_js.replace('World.World.', 'World.')
    world_js = world_js.replace('function World.', 'function ')

    # Store.upg() switch returns bare field names -> qualify them
    store_js = store_js.replace(
        "switch (kind) { case 0: return upgSpeed; case 1: return upgNitro; case 2: return upgMagnet; default: return upgArmor; }",
        "switch (kind) { case 0: return Store.upgSpeed; case 1: return Store.upgNitro; case 2: return Store.upgMagnet; default: return Store.upgArmor; }")

    carart_js = convert_simple('CarArt', ['draw','rr','hero'])
    carart_js = carart_js.replace('new RadialGradient(', 'new RadialGradient2(')
    carart_js = carart_js.replace('CarArt = {};\n', 'var CarArt = {};\n', 1) if carart_js.startswith('CarArt = {};') else carart_js

    game_js = convert_simple('Game', [
        'resize','update','storeHasDailyClaim','refreshDaily','checkDailyReset','onDown','onMove','onTap',
        'startRun','menuTap','playTap','fireNitro','pauseTap','onBack','garageTap','missionTap','settingsTap',
        'resetProgress','endRun','gotoMenu','onRunOver','overTap','hit','toast','burstConfetti','draw','drawFx',
        'missionClaimable','missionReward','drawToast','drawBackground','drawTopBar','drawButton','drawMenu','drawHud','drawPause','drawGameOver',
        'drawGarage','previewProgress','drawMissions','drawSettings','drawCredits'])
    game_js = game_js.replace('new RadialGradient(', 'new RadialGradient2(')
    game_js = game_js.replace('new LinearGradient(', 'new LinearGradient2(')
    # constructor & collection ops
    game_js = game_js.replace('Game.init = function(gv) {', 'Game.init = function(gv) {')
    game_js = re.sub(r'new GameView\(this\)', 'null', game_js)
    game_js = game_js.replace('new World(gv)', 'World.init(gv), World')
    game_js = re.sub(r'fx\.get\(i\)', 'fx[i]', game_js)
    game_js = re.sub(r'fx\.remove\(i\)', 'fx.splice(i,1)', game_js)
    game_js = re.sub(r'fx\.size\(\)', 'fx.length', game_js)
    game_js = game_js.replace('for (var f of fx)', 'for (var f of fx)')
    game_js = game_js.replace('new float[5]', 'new Array(5).fill(0)')
    game_js = game_js.replace('var f = new Array(5).fill(0);', 'var f = new Array(5).fill(0);')
    # date + mission string format
    game_js = re.sub(r'var today = new java\.text\.SimpleDateFormat\("yyyyMMdd", java\.util\.Locale\.US\)\.format\(new java\.util\.Date\(\)\);',
                     'var today = dateStr();', game_js)
    game_js = re.sub(r'String\.format\(Store\.M_DESC\[i\], Draw\.fmt\(tgt\)\)',
                     "Store.M_DESC[i].replace('%s', Draw.fmt(tgt))", game_js)
    game_js = game_js.replace('Math.max(0, Math.min(255', 'Math.max(0, Math.min(255')
    # Screen flow constants: keep as var
    game_js = re.sub(r'(?m)^(\s*)var (MENU|PLAY|GARAGE|MISSIONS|SETTINGS|GAMEOVER|CREDITS) = ', r'\1Game.\2 = ', game_js)
    game_js = game_js.replace('Game.MENU = Game.', 'Game.MENU = ')
    def qualify_outside_strings(txt, words):
        out = []
        for line in txt.split('\n'):
            parts = re.split(r'("(?:[^"\\]|\\.)*")', line)
            for k in range(0, len(parts), 2):
                for con in words:
                    parts[k] = re.sub(r'(?<![.\w])' + con + r'\b', 'Game.' + con, parts[k])
            out.append(''.join(parts))
        return '\n'.join(out)
    game_js = qualify_outside_strings(game_js, ['MENU','PLAY','GARAGE','MISSIONS','SETTINGS','GAMEOVER','CREDITS'])
    game_js = game_js.replace('Game.Game.', 'Game.')
    game_js = game_js.replace('Game.PLAY = Game.', 'Game.PLAY = ')
    game_js = game_js.replace('world = World.init(gv), World;', 'Game.world = (World.init(gv), World);')
    game_js = game_js.replace('world = new World.init(gv)', 'Game.world = (World.init(gv), World);')
    # all bare `world.` refs inside Game methods refer to Game.world
    game_js = re.sub(r'(?<![.\w])world\.', 'Game.world.', game_js)
    game_js = game_js.replace('Game.Game.', 'Game.')
    import re as _re2
    METHODS = ['menuBtnY','menuBtnH','menuCarY','cycleCar','pauseY','nitroBtnX','nitroBtnY','nitroBtnW','nitroBtnH',
               'fireNitro','garageActY','garageSkinY','garageSkinSize','garageUpgY','missionRowY','missionRowH','dailyY',
               'setRowY','previewProgress','overBtnY','toast','burstConfetti','hit','drawFx','drawToast','drawBackground',
               'drawTopBar','drawButton','drawMenu','drawHud','drawPause','drawGameOver','drawGarage','drawMissions',
               'drawSettings','drawCredits','startRun','endRun','gotoMenu','resetProgress','onRunOverDelay','updateExternal',
               'checkDailyReset']
    for m in METHODS:
        game_js = _re2.sub(r'(?<![.\w])' + m + r'\s*\(', 'Game.' + m + '(', game_js)
    game_js = game_js.replace('Game.Game.', 'Game.')
    game_js = _re2.sub(r'(?<![\w.])fx\b', 'Game.fx', game_js)
    game_js = game_js.replace('Game.Game.', 'Game.')
    game_js = game_js.replace('Game.fx = [];', 'Game.fx = [];')
    game_js = _re2.sub(r'(?<![.\w])fx\.push\(', 'Game.fx.push(', game_js)
    game_js = 'var Game = {};\n' + game_js
    # Game/World instance fields that were converted to bare vars need Game.x / Game.world.x
    FIELDS = ['W','H','screen','world','gv','downX','downY','toast','toastT','toastCol','carPage',
              'dailyEarn','dailyDone','dailyClaimed','goMenuAfter','t']
    game_js = protect_params(game_js, FIELDS)
    # 1) declarations: var X = ... -> Game.X = ...
    for f in FIELDS:
        game_js = _re2.sub(r'(?m)^(\s*)var ' + f + r' = ', r'\1Game.' + f + ' = ', game_js)
    # 2) bare uses -> Game.X (single pass, params already stripped of types)
    for f in FIELDS:
        game_js = _re2.sub(r'(?<![\.\w$])' + f + r'\b(?![\w$])', 'Game.' + f, game_js)
        game_js = game_js.replace('Game.Game.', 'Game.')
    game_js = game_js.replace('Game.Game.', 'Game.')
    game_js = game_js.replace('Game.dailyEarn[', 'Game.dailyEarn[')
    game_js = game_js.replace('Game.Game.', 'Game.')
    # static const arrays live on Game too
    game_js = game_js.replace('var DAILY_GOAL = [8000, 25, 300];', 'Game.DAILY_GOAL = [8000, 25, 300];')
    game_js = game_js.replace('var DAILY_REW = [150, 150, 200];', 'Game.DAILY_REW = [150, 150, 200];')
    game_js = _re2.sub(r'(?<![.\w$])DAILY_GOAL\b', 'Game.DAILY_GOAL', game_js)
    game_js = _re2.sub(r'(?<![.\w$])DAILY_REW\b', 'Game.DAILY_REW', game_js)
    game_js = game_js.replace('Game.Game.', 'Game.')
    return {'store': store_js, 'draw': draw_js, 'world': world_js, 'carart': carart_js, 'game': game_js}

parts = build()

def fix_draw_section(js):
    import re as _re
    js = _re.sub(r'(?m)^(\s*)var (p|t|s|path|rect) = ', r'\1Draw.\2 = ', js)
    js = js.replace('t.setTypeface(F_BOLD);', 'Draw.t.setTypeface(Draw.F_BOLD);')
    # word-boundary qualify Draw's paint singletons inside the Draw section only
    js = re.sub(r'(?<![\w.])t\.', 'Draw.t.', js)
    js = re.sub(r'(?<![\w.])p\.', 'Draw.p.', js)
    js = re.sub(r'(?<![\w.])s\.', 'Draw.s.', js)
    js = re.sub(r'(?<![\w.])path\.', 'Draw.path.', js)
    js = js.replace('var Draw.Draw', 'var Draw')
    # 1) proper init
    i = js.find('Draw.init = function(am) {')
    if i >= 0:
        j = js.find('};', i)
        js = js[:i] + ('Draw.init = function(am) {\n'
                       '        Draw.F_HEAD = Typeface2.createFromAsset(null, "Audiowide");\n'
                       '        Draw.F_BOLD = Typeface2.createFromAsset(null, "Rajdhani-Bold");\n'
                       '        Draw.F_SEMI = Typeface2.createFromAsset(null, "Rajdhani-SemiBold");\n'
                       '        Draw.F_MED = Typeface2.createFromAsset(null, "Rajdhani-Medium");\n'
                       '        Draw.t.setTypeface(Draw.F_BOLD);\n'
                       '    };') + js[j + 2:]
    # 2) aliases so converted method bodies that use bare p/t/s/path still work
    js = js.replace('Draw.rect = {};', 'Draw.rect = {};\nvar p = Draw.p, t = Draw.t, s = Draw.s;')
    return js


# --- replace Draw.fmt / Draw.fmt2 / Draw.alpha / Draw.lerpColor bodies by marker parsing ---
def replace_fn(js, name, body, nextname):
    i = js.find(name + ' = function')
    if i < 0:
        print('WARN missing', name); return js
    j = js.find(nextname + ' = function', i)
    if j < 0:
        print('WARN no terminator for', name); return js
    return js[:i] + name + ' = function' + body + '\n\n' + js[j:]

_d = parts['draw']
_d = replace_fn(_d, 'Draw.fmt',
    '(n) {\n    n = Math.trunc(n);\n    if (n < 1000) return String(n);\n    if (n < 1000000) return Math.trunc(n/1000) + "," + ("00" + Math.abs(n % 1000)).slice(-3);\n    return Math.trunc(n/1000000) + "," + ("00" + Math.trunc((n/1000)%1000)).slice(-3) + "," + ("00" + Math.abs(n % 1000)).slice(-3);\n}',
    'Draw.fmt2')
_d = replace_fn(_d, 'Draw.fmt2',
    '(n) {\n    n = Math.trunc(n);\n    if (n < 1000) return String(n);\n    if (n < 100000) return (n < 10000 ? (n/1000).toFixed(1) : String(Math.trunc(n/1000))) + "k";\n    return (n < 10000000 ? (n/1000000).toFixed(1) : String(Math.trunc(n/1000000))) + "M";\n}',
    'Draw.p')
_d = replace_fn(_d, 'Draw.alpha',
    '(color, a) { return ((((color>>>0) & 0x00FFFFFF)) | ((a & 0xFF) << 24)) >>> 0; }',
    'Draw.lerpColor')
parts['draw'] = _d
parts['draw'] = fix_draw_section(parts['draw'])

def unqualify_locals(js):
    js = js.replace('Draw.p = Draw.p;', '')
    js = js.replace('Draw.t = 0;', 't = 0;')
    js = js.replace('Draw.s = 1 + 0.05 * Math.sin(t * 12);', 'var sc = 1 + 0.05 * Math.sin(t * 12);')
    js = js.replace('Draw.s = 1 + 0.09 * Math.max(0, Game.world.bannerT - 0.9);', 'var sc = 1 + 0.09 * Math.max(0, Game.world.bannerT - 0.9);')
    js = js.replace('Draw.s = 1 + 0.05 * Math.sin(t * 6);', 'var sc = 1 + 0.05 * Math.sin(t * 6);')
    js = js.replace('Draw.s = 1 + 0.02 * Math.sin(t * 5);', 'var sc = 1 + 0.02 * Math.sin(t * 5);')
    return js
for k in ('game','world','carart'):
    parts[k] = unqualify_locals(parts[k])
# screen constant block is declared as `Game.MENU = 0, PLAY = 1, ...` -> make all qualified
import re as _re
parts['game'] = _re.sub(r'Game\.MENU = 0, PLAY = 1, GARAGE = 2, MISSIONS = 3, SETTINGS = 4, GAMEOVER = 5, CREDITS = 6;',
    'Game.MENU = 0; Game.PLAY = 1; Game.GARAGE = 2; Game.MISSIONS = 3; Game.SETTINGS = 4; Game.GAMEOVER = 5; Game.CREDITS = 6;',
    parts['game'])

def brace_balance(txt):
    depth = 0; i = 0; n = len(txt); mode = None
    while i < n:
        ch = txt[i]
        if mode:
            if ch == '\\' and i + 1 < n: i += 2; continue
            if ch == mode: mode = None
            i += 1; continue
        if ch in '"\'':
            mode = ch; i += 1; continue
        if ch == '/' and i + 1 < n and txt[i+1] == '/':
            j = txt.find('\n', i)
            i = j if j >= 0 else n; continue
        if ch == '/' and i + 1 < n and txt[i+1] == '*':
            j = txt.find('*/', i)
            i = (j + 2) if j >= 0 else n; continue
        if ch == '{': depth += 1
        elif ch == '}': depth -= 1
        i += 1
    return depth

for k in parts:
    b = brace_balance(parts[k])
    print('part', k, 'brace balance', b)
    if b > 0:
        parts[k] = parts[k].rstrip() + ('\n' + '}\n' * b)
    elif b < 0:
        for _ in range(-b):
            idx = parts[k].rstrip().rfind('}')
            if idx < 0: break
            parts[k] = parts[k][:idx] + parts[k][idx+1:]

js_all = '\n'.join([JS_LIBS, parts['store'], parts['draw'], parts['carart'], parts['world'], parts['game']])

def _js_sprintf(m):
    return '(' + m.group(1) + ').toFixed(' + m.group(2) + ')'
import re as _re4
js_all = _re4.sub(r'String\.format\("%\.(\d)f",\s*([^)]+)\)',
                  lambda m: '(' + m.group(2) + ').toFixed(' + m.group(1) + ')', js_all)
js_all = _re4.sub(r'String\.format\("%0(\d)d",\s*([^)]+)\)',
                  lambda m: '("0".repeat(' + m.group(1) + ') + Math.trunc(' + m.group(2) + ')).slice(-' + m.group(1) + ')', js_all)

js_all = js_all.replace('Game.fx.add(', 'Game.fx.push(').replace('fx.add(', 'Game.fx.push(')
js_all = js_all.replace('var path = Draw.path;', '')
# path alias must be created lazily (Draw.path exists only after the Draw section runs)
js_all = js_all.replace('var Draw = {};', 'var Draw = {};\nvar path = null;')
js_all = js_all.replace('Draw.path = new Path2();', 'Draw.path = new Path2(); path = Draw.path;')
print('total balance', brace_balance(JS_LIBS) + sum(brace_balance(v) for v in parts.values()))

# sanity
open('/var/tmp/game_all.js', 'w').write(js_all)
try:
    subprocess.run(['node', '--check', '/var/tmp/game_all.js'], check=True, capture_output=True, text=True)
    print('node --check OK')
except subprocess.CalledProcessError as e:
    print('NODE CHECK FAILED')
    print(e.stderr[:4000])

def img_b64(p):
    import base64 as b64
    return 'data:image/jpeg;base64,' + b64.b64encode(open(os.path.join(ROOT, p), 'rb').read()).decode()

hero_b64 = img_b64('art/hero_web.jpg') if os.path.exists(os.path.join(ROOT, 'art/hero_web.jpg')) else ''
logo_b64 = img_b64('art/logo_web.jpg') if os.path.exists(os.path.join(ROOT, 'art/logo_web.jpg')) else ''

html_tpl = open(os.path.join(ROOT, 'tools/web_template.html')).read()
html = (html_tpl.replace('/*__FONTS__*/', fonts_css)
                .replace('/*__GAME__*/', js_all)
                .replace('/*__HERO__*/', hero_b64)
                .replace('/*__LOGO__*/', logo_b64))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w').write(html)
print('wrote', OUT, len(html)//1024, 'KB')
