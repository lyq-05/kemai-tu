# -*- coding: utf-8 -*-
"""渲染「客脉图」占位图标，并展示 Android 三种遮罩下的效果"""
import math, os
from PIL import Image, ImageDraw, ImageFilter

SIZE = 1024
SS = 4                      # 超采样倍数
W = SIZE * SS

def grad_bg(w):
    """青瓷蓝绿斜向渐变 + 左上高光"""
    img = Image.new("RGB", (w, w), (47, 110, 106))
    px = img.load()
    c0 = (87, 168, 159)     # #57A89F
    c1 = (47, 110, 106)     # #2F6E6A
    c2 = (30, 78, 74)       # #1E4E4A
    for y in range(w):
        for x in range(0, w, 2):
            t = (x / w * 0.45 + y / w * 0.55)
            if t < 0.55:
                k = t / 0.55
                c = tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3))
            else:
                k = (t - 0.55) / 0.45
                c = tuple(int(c1[i] + (c2[i] - c1[i]) * k) for i in range(3))
            px[x, y] = c
            if x + 1 < w:
                px[x + 1, y] = c
    # 左上柔光
    glow = Image.new("L", (w, w), 0)
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-w * 0.25, -w * 0.55, w * 0.85, w * 0.35], fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(w * 0.10))
    img = Image.composite(Image.new("RGB", (w, w), (255, 255, 255)), img, glow.point(lambda v: int(v * 0.42)))
    return img

def draw_mark(img, w):
    """白色脉络图标记 + 暖色小点"""
    k = w / 108.0
    layer = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)

    def L(x1, y1, x2, y2, width, color):
        ld.line([(x1 * k, y1 * k), (x2 * k, y2 * k)], fill=color, width=int(width * k))
        r = width * k / 2
        for (px_, py_) in ((x1 * k, y1 * k), (x2 * k, y2 * k)):
            ld.ellipse([px_ - r, py_ - r, px_ + r, py_ + r], fill=color)

    # 投影
    shadow = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    def SL(x1, y1, x2, y2, width, color):
        sd.line([(x1 * k, (y1 + 1.6) * k), (x2 * k, (y2 + 1.6) * k)], fill=color, width=int(width * k))
    SL(54, 33, 54, 58, 5.2, (0, 0, 0, 70))
    SL(35, 58, 73, 58, 5.2, (0, 0, 0, 70))
    SL(35, 58, 35, 75, 5.2, (0, 0, 0, 70))
    SL(73, 58, 73, 75, 5.2, (0, 0, 0, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(w * 0.006))
    img.alpha_composite(shadow)

    L(54, 33, 54, 58, 4.4, (255, 255, 255, 255))
    L(35, 58, 73, 58, 4.4, (255, 255, 255, 255))
    L(35, 58, 35, 75, 4.4, (255, 255, 255, 255))
    L(73, 58, 73, 75, 4.4, (255, 255, 255, 255))

    def circle(cx, cy, r, color):
        ld.ellipse([(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k], fill=color)

    circle(54, 33, 10.5, (255, 255, 255, 255))
    circle(35, 75, 8.5, (255, 255, 255, 255))
    circle(73, 75, 8.5, (255, 255, 255, 255))
    circle(60.5, 26.5, 3.2, (242, 196, 107, 255))
    img.alpha_composite(layer)
    return img

def masked(img, shape, w):
    mask = Image.new("L", (w, w), 0)
    md = ImageDraw.Draw(mask)
    if shape == "circle":
        md.ellipse([0, 0, w - 1, w - 1], fill=255)
    elif shape == "squircle":
        md.rounded_rectangle([0, 0, w - 1, w - 1], radius=int(w * 0.30), fill=255)
    else:
        md.rounded_rectangle([0, 0, w - 1, w - 1], radius=int(w * 0.16), fill=255)
    out = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out

base = grad_bg(W).convert("RGBA")
base = draw_mark(base, W)
base = base.resize((SIZE, SIZE), Image.LANCZOS)

OUT = r"D:\25007\deepseek-workspace\Application_development_1\工具\应用图标设计素材"
os.makedirs(OUT, exist_ok=True)
base.save(os.path.join(OUT, "00-占位图标-1024.png"), "PNG")
print("wrote 00-占位图标-1024.png", base.size)

# 三种遮罩并排预览
pad = 60
tile = 360
sheet = Image.new("RGB", (tile * 3 + pad * 4, tile + pad * 2 + 70), (251, 250, 247))
sd = ImageDraw.Draw(sheet)
labels = ["圆形遮罩", "方圆遮罩", "圆角方形"]
for i, shape in enumerate(["circle", "squircle", "square"]):
    icon = masked(base, shape, SIZE).resize((tile, tile), Image.LANCZOS)
    x = pad + i * (tile + pad)
    sheet.paste(icon, (x, pad), icon)
    sd.text((x + tile // 2 - 34, pad + tile + 20), labels[i], fill=(60, 70, 68))
sheet.save(os.path.join(OUT, "00-占位图标-三种遮罩预览.png"), "PNG")
print("wrote 00-占位图标-三种遮罩预览.png", sheet.size)
