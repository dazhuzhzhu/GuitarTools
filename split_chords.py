from PIL import Image
import os

# 打开图片
img = Image.open('src/main/resources/static/chord-images/chord-chart.png')
width, height = img.size

print(f"原图尺寸: {width}x{height}")

# 定义每个调的行范围（根据图片手动测量）
# 标题高度约60px，每行约105px
title_height = 60
row_height = 105
col_width = width // 7

# 调性和级数
keys = ['C', 'D', 'E', 'F', 'G', 'A', 'B']
levels = ['1', '2', '3', '4', '5', '6', '7']

# 创建输出目录
output_dir = 'src/main/resources/static/chord-images'
os.makedirs(output_dir, exist_ok=True)

# 切割并保存每个和弦
for row_idx, key in enumerate(keys):
    for col_idx, level in enumerate(levels):
        # 计算坐标
        x1 = col_idx * col_width
        y1 = title_height + row_idx * row_height
        x2 = x1 + col_width
        y2 = y1 + row_height
        
        # 裁剪
        chord_img = img.crop((x1, y1, x2, y2))
        
        # 保存
        filename = f'{key}_{level}.png'
        filepath = os.path.join(output_dir, filename)
        chord_img.save(filepath)
        print(f"Saved: {filename} ({x1},{y1})-({x2},{y2})")

print("\n所有49个和弦图片已生成！")
