from PIL import Image
import os

output = 'src/main/resources/static/chord-images'

# 目标统一尺寸（宽:高 约 2:3 的比例，看起来更像标准和弦图）
TARGET_W = 90
TARGET_H = 130

for key in ['C', 'D', 'E', 'F', 'G', 'A', 'B']:
    for level in range(1, 8):
        filepath = os.path.join(output, f'{key}_{level}.png')
        img = Image.open(filepath)
        w, h = img.size
        
        # 创建白色背景画布
        canvas = Image.new('RGB', (TARGET_W, TARGET_H), (255, 255, 255))
        
        # 计算缩放比例，保持宽度填满，高度按比例
        scale = TARGET_W / w
        new_w = TARGET_W
        new_h = int(h * scale)
        
        if new_h > TARGET_H:
            scale = TARGET_H / h
            new_w = int(w * scale)
            new_h = TARGET_H
        
        resized = img.resize((new_w, new_h), Image.LANCZOS)
        
        # 居中放置
        x_offset = (TARGET_W - new_w) // 2
        y_offset = (TARGET_H - new_h) // 2
        canvas.paste(resized, (x_offset, y_offset))
        
        canvas.save(filepath)
        print(f'{key}_{level}.png: {w}x{h} -> {new_w}x{new_h} on {TARGET_W}x{TARGET_H}')

print('\nAll 49 images resized!')
