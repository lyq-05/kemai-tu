# -*- coding: utf-8 -*-
"""
把模拟器真实界面截图加工成操作指南配图：
  - 左侧留一条标号栏，序号圆点放在栏里，不遮挡界面内容
  - 每个步骤在界面上画一条高亮横带，并用一小段引线连到序号
输出到 res/drawable-nodpi（宽度 720 + 96 标号栏）
"""
import os
from PIL import Image, ImageDraw, ImageFont

SRC = r"D:\25007\deepseek-workspace\Application_development_1\工具\shots"
DST = r"D:\25007\deepseek-workspace\Application_development_1\KemaiTu\app\src\main\res\drawable-nodpi"

OUT_W = 720          # 截图区宽度
GUTTER = 96          # 左侧标号栏宽度
BG = (251, 250, 247)
ACCENT = (47, 110, 106)
BAND_A = 44
BORDER_A = 145

# (输出名, 源截图, [(序号, 原图 y 中心)])
GUIDES = [
    ("guide_1_main.png", "G1-主界面.png", [
        (1, 218), (2, 1090), (3, 1600), (4, 2060),
    ]),
    ("guide_2_editor.png", "G2-编辑面板.png", [
        (1, 514), (2, 752), (3, 960), (4, 1372), (5, 1650), (6, 1930),
    ]),
    ("guide_3_collapse.png", "G3-折叠与选中.png", [
        (1, 1090), (2, 1650), (3, 2193),
    ]),
    ("guide_4_visits.png", "G4-拜访清单.png", [
        (1, 169), (2, 365), (3, 503),
    ]),
    ("guide_5_reminder.png", "G5-提醒设置.png", [
        (1, 791), (2, 1080), (3, 1275), (4, 1570), (5, 1875), (6, 2113),
    ]),
    ("guide_6_check.png", "G6-通知自检.png", [
        (1, 586), (2, 745), (3, 900), (4, 1057), (5, 1258), (6, 1520),
    ]),
]


def load_font(size):
    for p in (r"C:\Windows\Fonts\arialbd.ttf", r"C:\Windows\Fonts\arial.ttf"):
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()


def annotate(src_path, dst_path, marks):
    shot = Image.open(src_path).convert("RGB")
    sw, sh = shot.size
    scale = OUT_W / float(sw)
    oh = int(sh * scale)
    shot = shot.resize((OUT_W, oh), Image.LANCZOS)

    canvas = Image.new("RGB", (OUT_W + GUTTER, oh), BG)
    canvas.paste(shot, (GUTTER, 0))

    ov = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    dr = ImageDraw.Draw(ov)

    band_h = max(48, int(104 * scale))
    r = int(38 * scale) + 6
    circ_x = GUTTER // 2
    band_x0 = GUTTER + int(10 * scale)
    band_x1 = canvas.size[0] - int(16 * scale)
    font = load_font(int(50 * scale) + 8)
    line_w = max(2, int(3 * scale))

    for idx, y_src in marks:
        y = int(y_src * scale)
        y = max(band_h // 2, min(oh - band_h // 2, y))
        top, bot = y - band_h // 2, y + band_h // 2

        # 引线：从序号圆点连到高亮带
        dr.line([(circ_x + r - 4, y), (band_x0 + 6, y)], fill=ACCENT + (110,), width=line_w)

        dr.rounded_rectangle(
            [band_x0, top, band_x1, bot],
            radius=band_h // 2,
            fill=ACCENT + (BAND_A,),
            outline=ACCENT + (BORDER_A,),
            width=line_w,
        )
        dr.ellipse([circ_x - r, y - r, circ_x + r, y + r], fill=ACCENT + (255,))
        t = str(idx)
        tb = dr.textbbox((0, 0), t, font=font)
        dr.text(
            (circ_x - (tb[2] - tb[0]) / 2 - tb[0], y - (tb[3] - tb[1]) / 2 - tb[1]),
            t, font=font, fill=(255, 255, 255, 255),
        )

    out = Image.alpha_composite(canvas.convert("RGBA"), ov).convert("RGB")
    out.save(dst_path, "PNG", optimize=True)
    return out.size, os.path.getsize(dst_path) / 1024


os.makedirs(DST, exist_ok=True)
for out_name, src_name, marks in GUIDES:
    src = os.path.join(SRC, src_name)
    if not os.path.exists(src):
        print("MISSING", src_name)
        continue
    size, kb = annotate(src, os.path.join(DST, out_name), marks)
    print("%-24s %dx%d  %.0f KB  %d 个标注" % (out_name, size[0], size[1], kb, len(marks)))
print("done")
