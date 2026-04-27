from PIL import Image, ImageFilter
import os
import shutil

base = '和弦图'
output = 'src/main/resources/static/chord-images'
os.makedirs(output, exist_ok=True)

# 目标统一尺寸
TARGET_W = 100
TARGET_H = 140

# 页面背景色 (和弦卡片是白色/淡灰背景)
BG_COLOR = (248, 248, 252)

def process_image(img, key, level):
    """处理单张和弦图：统一尺寸、去白边、融合背景"""
    w, h = img.size
    
    # 转为RGBA处理透明度
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
    
    # 创建统一背景画布
    canvas = Image.new('RGBA', (TARGET_W, TARGET_H), (*BG_COLOR, 255))
    
    # 缩放：宽度填满画布，高度按比例
    scale = TARGET_W / w
    new_w = TARGET_W
    new_h = int(h * scale)
    
    if new_h > TARGET_H:
        scale = TARGET_H / h
        new_w = int(w * scale)
        new_h = TARGET_H
    
    resized = img.resize((new_w, new_h), Image.LANCZOS)
    
    # 居中偏下放置（让和弦图偏下，上方留空给和弦名）
    x_offset = (TARGET_W - new_w) // 2
    y_offset = (TARGET_H - new_h) // 2
    
    canvas.paste(resized, (x_offset, y_offset), resized)
    
    # 转为RGB
    result = canvas.convert('RGB')
    
    filepath = os.path.join(output, f'{key}_{level}.png')
    result.save(filepath, quality=95)
    return filepath

# 1. 处理C和D（已切好）
for key in ['C', 'D']:
    for i in range(1, 8):
        src = os.path.join(base, key, f'{i}.png')
        img = Image.open(src)
        path = process_image(img, key, i)
        print(f'Processed: {key}_{i}.png ({img.size[0]}x{img.size[1]})')

# 2. 切割并处理E/F/G/A/B
split_files = {
    'E': 'Snipaste_2026-04-26_20-00-18.png',
    'F': 'Snipaste_2026-04-26_20-00-25.png',
    'G': 'Snipaste_2026-04-26_20-00-34.png',
    'A': 'Snipaste_2026-04-26_20-00-45.png',
    'B': 'Snipaste_2026-04-26_20-00-53.png',
}

for key, filename in split_files.items():
    img_path = os.path.join(base, key, filename)
    img = Image.open(img_path)
    w, h = img.size
    col_w = w / 7
    
    for i in range(7):
        x1 = int(i * col_w)
        x2 = int((i + 1) * col_w)
        chord_img = img.crop((x1, 0, x2, h))
        path = process_image(chord_img, key, i + 1)
        print(f'Processed: {key}_{i+1}.png (cropped {x2-x1}x{h})')

print(f'\nDone! All 49 images processed to {TARGET_W}x{TARGET_H}')
