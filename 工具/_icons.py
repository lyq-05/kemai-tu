# -*- coding: utf-8 -*-
import math, os

OUT = r"D:\25007\deepseek-workspace\Application_development_1\KemaiTu\app\src\main\res\drawable"
os.makedirs(OUT, exist_ok=True)

STROKE = "#FF1C2321"
SW = "1.7"

def polar(r, deg, cx=12.0, cy=12.0):
    a = math.radians(deg)
    return (cx + r * math.cos(a), cy + r * math.sin(a))

def f(p):
    return "%.2f,%.2f" % (p[0], p[1])

def stroke_path(d):
    return ('    <path\n'
            '        android:pathData="%s"\n'
            '        android:strokeColor="%s"\n'
            '        android:strokeWidth="%s"\n'
            '        android:strokeLineCap="round"\n'
            '        android:strokeLineJoin="round" />\n' % (d, STROKE, SW))

def fill_path(d):
    return ('    <path\n'
            '        android:pathData="%s"\n'
            '        android:fillColor="%s" />\n' % (d, STROKE))

def circle(cx, cy, r):
    return "M%.2f,%.2f a%.2f,%.2f 0 1,0 %.2f,0 a%.2f,%.2f 0 1,0 %.2f,0" % (
        cx - r, cy, r, r, 2 * r, r, r, -2 * r)

def write(name, body):
    doc = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- 客脉图自绘图标：细线描边风格（strokeWidth 1.7 / 圆角端点） -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="24dp"\n'
           '    android:height="24dp"\n'
           '    android:viewportWidth="24"\n'
           '    android:viewportHeight="24">\n'
           + body +
           '</vector>\n')
    with open(os.path.join(OUT, name + ".xml"), "w", encoding="utf-8") as fp:
        fp.write(doc)
    print("wrote", name)

# ---------- 齿轮 ----------
def gear_body():
    n, Ro, Ri = 8, 9.7, 7.0
    ho, hr = 13.0, 21.5
    d = ""
    for i in range(n):
        a = i * 360.0 / n
        p1 = polar(Ri, a - hr); p2 = polar(Ro, a - ho)
        p3 = polar(Ro, a + ho); p4 = polar(Ri, a + hr)
        if i == 0:
            d += "M" + f(p1) + " "
        else:
            d += "A%.2f,%.2f 0 0,1 %s " % (Ri, Ri, f(p1))
        d += "L%s L%s L%s " % (f(p2), f(p3), f(p4))
    p0 = polar(Ri, -hr)
    d += "A%.2f,%.2f 0 0,1 %s Z" % (Ri, Ri, f(p0))
    return stroke_path(d) + stroke_path(circle(12, 12, 3.1))

# ---------- 五角星 ----------
def star_body():
    pts = []
    for i in range(10):
        ang = -90 + i * 36
        pts.append(polar(9.3 if i % 2 == 0 else 3.9, ang))
    d = "M" + f(pts[0]) + " " + " ".join("L" + f(p) for p in pts[1:]) + " Z"
    return stroke_path(d)

# ---------- 逐个图标 ----------
write("ic_search", stroke_path(circle(11, 11, 7) + " M16.3,16.3 L20.6,20.6"))

write("ic_settings", gear_body())

write("ic_help",
      stroke_path(circle(12, 12, 9.4))
      + stroke_path("M9.0,9.3 a3.0,3.0 0 1,1 3.9,2.9 c-0.8,0.4 -1.1,0.9 -1.1,1.9 L11.8,15.0")
      + fill_path(circle(11.8, 17.9, 1.0)))

write("ic_add", stroke_path("M12,4.8 L12,19.2 M4.8,12 L19.2,12"))

write("ic_back", stroke_path("M20.2,12 L4.2,12 M10.4,5.4 L3.8,12 L10.4,18.6"))

write("ic_close", stroke_path("M6.2,6.2 L17.8,17.8 M17.8,6.2 L6.2,17.8"))

write("ic_check", stroke_path("M4.8,12.6 L9.8,17.6 L19.2,7.2"))

write("ic_calendar",
      stroke_path("M6.2,6.2 L17.8,6.2 A2.2,2.2 0 0,1 20,8.4 L20,17.8 A2.2,2.2 0 0,1 17.8,20 L6.2,20 A2.2,2.2 0 0,1 4,17.8 L4,8.4 A2.2,2.2 0 0,1 6.2,6.2 Z")
      + stroke_path("M8.4,3.6 L8.4,7.4 M15.6,3.6 L15.6,7.4")
      + stroke_path("M4,10.6 L20,10.6"))

write("ic_list",
      stroke_path("M9.2,7 L19.8,7 M9.2,12 L19.8,12 M9.2,17 L19.8,17")
      + fill_path(circle(5.6, 7, 1.1) + circle(5.6, 12, 1.1) + circle(5.6, 17, 1.1)))

write("ic_more", fill_path(circle(12, 5.6, 1.4) + circle(12, 12, 1.4) + circle(12, 18.4, 1.4)))

write("ic_delete",
      stroke_path("M4.4,6.9 L19.6,6.9")
      + stroke_path("M9.6,6.9 L9.6,5.2 A1.3,1.3 0 0,1 10.9,3.9 L13.1,3.9 A1.3,1.3 0 0,1 14.4,5.2 L14.4,6.9")
      + stroke_path("M6.6,6.9 L7.5,19.3 A1.7,1.7 0 0,0 9.2,20.9 L14.8,20.9 A1.7,1.7 0 0,0 16.5,19.3 L17.4,6.9")
      + stroke_path("M10.3,10.4 L10.3,17.4 M13.7,10.4 L13.7,17.4"))

write("ic_edit",
      stroke_path("M4.4,19.6 L5.6,15.2 L15.9,4.9 A2.25,2.25 0 0,1 19.1,8.1 L8.8,18.4 Z")
      + stroke_path("M14.4,6.4 L17.6,9.6"))

write("ic_rename",
      stroke_path("M8.6,6.4 L6.9,6.4 A1.6,1.6 0 0,0 5.3,8.0 L5.3,16.0 A1.6,1.6 0 0,0 6.9,17.6 L8.6,17.6")
      + stroke_path("M15.4,6.4 L17.1,6.4 A1.6,1.6 0 0,1 18.7,8.0 L18.7,16.0 A1.6,1.6 0 0,1 17.1,17.6 L15.4,17.6")
      + stroke_path("M12,7.6 L12,16.4"))

write("ic_export",
      stroke_path("M3.6,8.0 A1.9,1.9 0 0,1 5.5,6.1 L9.0,6.1 L10.9,8.4 L18.5,8.4 A1.9,1.9 0 0,1 20.4,10.3 L20.4,17.4 A1.9,1.9 0 0,1 18.5,19.3 L5.5,19.3 A1.9,1.9 0 0,1 3.6,17.4 Z")
      + stroke_path("M9.4,13.6 L14.6,13.6 M12.4,11.4 L14.8,13.6 L12.4,15.8"))

write("ic_import",
      stroke_path("M3.6,8.0 A1.9,1.9 0 0,1 5.5,6.1 L9.0,6.1 L10.9,8.4 L18.5,8.4 A1.9,1.9 0 0,1 20.4,10.3 L20.4,17.4 A1.9,1.9 0 0,1 18.5,19.3 L5.5,19.3 A1.9,1.9 0 0,1 3.6,17.4 Z")
      + stroke_path("M9.4,13.6 L14.6,13.6 M11.6,11.4 L9.2,13.6 L11.6,15.8"))

write("ic_star", star_body())

write("ic_chevron_down", stroke_path("M6.2,9.4 L12,15.2 L17.8,9.4"))
write("ic_chevron_right", stroke_path("M9.4,6.2 L15.2,12 L9.4,17.8"))

write("ic_collapse", stroke_path("M4.6,7.4 L12,14.8 L19.4,7.4") + stroke_path("M4.6,14.2 L12,21.6 L19.4,14.2"))
write("ic_expand", stroke_path("M4.6,16.6 L12,9.2 L19.4,16.6") + stroke_path("M4.6,9.8 L12,2.4 L19.4,9.8"))

write("ic_fit", stroke_path("M4,9 L4,4 L9,4 M15,4 L20,4 L20,9 M20,15 L20,20 L15,20 M9,20 L4,20 L4,15"))

print("全部完成")
