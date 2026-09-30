# -*- coding: utf-8 -*-
import os
from PIL import Image

SRC = r"D:\25007\deepseek-workspace\Application_development_1\工具\shots"
DST = r"D:\25007\deepseek-workspace\Application_development_1\KemaiTu\app\src\main\res\drawable-nodpi"
os.makedirs(DST, exist_ok=True)

pairs = [
    ("40-最终主界面.png",   "guide_1_main.png"),
    ("18-收键盘后.png",     "guide_2_editor.png"),
    ("09-折叠后.png",       "guide_3_collapse.png"),
    ("32-标记已拜访.png",   "guide_4_visits.png"),
    ("35-设置页上半.png",   "guide_5_reminder.png"),
]
for src, dst in pairs:
    p = os.path.join(SRC, src)
    if not os.path.exists(p):
        print("MISSING", src); continue
    im = Image.open(p).convert("RGB")
    w, h = im.size
    nw = 720
    nh = int(h * nw / w)
    im = im.resize((nw, nh), Image.LANCZOS)
    out = os.path.join(DST, dst)
    im.save(out, "PNG", optimize=True)
    print("wrote", dst, "%dx%d" % (nw, nh), "%.0f KB" % (os.path.getsize(out) / 1024))
