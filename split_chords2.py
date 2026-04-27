from PIL import Image
import os
import shutil

base = '和弦图'
output = 'src/main/resources/static/chord-images'
os.makedirs(output, exist_ok=True)

# 1. 复制C和D已切好的图
for key in ['C', 'D']:
    for i in range(1, 8):
        src = os.path.join(base, key, f'{i}.png')
        dst = os.path.join(output, f'{key}_{i}.png')
        shutil.copy2(src, dst)
        print(f'Copied: {src} -> {dst}')

# 2. 切割E/F/G/A/B的截图
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
    print(f'\n{key}: {w}x{h}, col_w={col_w:.1f}')
    
    for i in range(7):
        x1 = int(i * col_w)
        x2 = int((i + 1) * col_w)
        chord_img = img.crop((x1, 0, x2, h))
        dst = os.path.join(output, f'{key}_{i+1}.png')
        chord_img.save(dst)
        print(f'  Saved: {key}_{i+1}.png ({x1},0)-({x2},{h})')

print('\nDone! All 49 chord images ready.')
