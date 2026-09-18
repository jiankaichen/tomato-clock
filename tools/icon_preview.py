#!/usr/bin/env python3
"""Build tools/out/icon_preview.html: both icon variants under Android's adaptive-icon masks."""
import os

here = os.path.dirname(__file__)
out = os.path.join(here, "out")
svgs = {name: open(os.path.join(out, f"icon_{name}.svg")).read() for name in ("face", "digits")}

VARIANTS = [
    ("face", "A · Clock face", "Green rain of pixel digits behind a glowing analog face set to 10:10."),
    ("digits", "B · Pixel time", "The same rain, parted around a big 10:10 in the app's own pixel digits."),
]


def masked(name, size, radius):
    # Android shows the inner 72dp of the 108dp adaptive icon, so scale by 1.5 and centre.
    return (f'<div class="mask" style="width:{size}px;height:{size}px;border-radius:{radius}">'
            f'<div class="inner">{svgs[name]}</div></div>')


rows = []
for name, title, blurb in VARIANTS:
    rows.append(f'''
<section class="variant">
  <header><h2>{title}</h2><p>{blurb}</p></header>
  <div class="sizes">
    <figure>{masked(name, 200, "50%")}<figcaption>circle · 200px</figcaption></figure>
    <figure>{masked(name, 96, "24%")}<figcaption>rounded · 96px</figcaption></figure>
    <figure>{masked(name, 96, "50%")}<figcaption>circle · 96px</figcaption></figure>
    <figure>{masked(name, 48, "50%")}<figcaption>app drawer · 48px</figcaption></figure>
  </div>
  <div class="home">
    <div class="app">{masked(name, 56, "50%")}<span>Tomato Clock</span></div>
    <div class="app ghost"><div class="mask" style="width:56px;height:56px;border-radius:50%"></div><span>Messages</span></div>
    <div class="app ghost"><div class="mask" style="width:56px;height:56px;border-radius:50%"></div><span>Camera</span></div>
    <div class="app ghost"><div class="mask" style="width:56px;height:56px;border-radius:50%"></div><span>Maps</span></div>
  </div>
</section>''')

html = f'''<title>Tomato Clock Icon</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap">
<style>
  :root {{ --bg:#050a06; --panel:#0b120c; --line:#173a1c; --ink:#c9f7cf; --muted:#6fae78; --green:#39ff14; }}
  body {{ background:var(--bg); color:var(--ink); font-family:"Share Tech Mono", ui-monospace, Menlo, monospace; padding:32px 16px 48px; }}
  main {{ max-width:880px; margin:0 auto; display:grid; gap:40px; }}
  h1 {{ font-size:22px; margin:0; letter-spacing:.04em; color:var(--green); text-wrap:balance; }}
  h1 + p {{ margin:8px 0 0; color:var(--muted); max-width:60ch; }}
  h2 {{ font-size:16px; margin:0; color:var(--green); letter-spacing:.06em; text-transform:uppercase; }}
  header p {{ margin:6px 0 0; color:var(--muted); max-width:60ch; }}
  .variant {{ border:1px solid var(--line); border-radius:12px; padding:20px; background:var(--panel); display:grid; gap:24px; }}
  .sizes {{ display:flex; flex-wrap:wrap; gap:28px; align-items:flex-end; }}
  figure {{ margin:0; display:grid; gap:8px; justify-items:center; }}
  figcaption {{ font-size:12px; color:var(--muted); }}
  .mask {{ overflow:hidden; position:relative; background:#030806; flex:none; }}
  .inner {{ position:absolute; inset:-25%; }}
  .inner svg {{ width:100%; height:100%; display:block; }}
  .home {{ display:flex; gap:22px; padding:18px; border-radius:14px; background:#0d1a10; flex-wrap:wrap; }}
  .app {{ display:grid; justify-items:center; gap:8px; font-size:12px; color:var(--ink); }}
  .ghost .mask {{ background:#1a2a1d; }}
  .ghost span {{ color:var(--muted); }}
</style>
<main>
  <div>
    <h1>&gt;_ Tomato Clock launcher icon</h1>
    <p>Two Matrix-style candidates, shown the way a Pixel would mask them. Both share a near-black ground and #39ff14 rain built from the app's pixel digits.</p>
  </div>
  {"".join(rows)}
</main>
'''
with open(os.path.join(out, "icon_preview.html"), "w") as f:
    f.write(html)
print("wrote tools/out/icon_preview.html", len(html), "bytes")
