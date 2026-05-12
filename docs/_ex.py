import fitz

pdf_path = r'e:\javaprogram\AndroidApp\论文修改稿(18120159_钟坤林).pdf'
out_path = r'e:\javaprogram\AndroidApp\docs\pdf_text.txt'

doc = fitz.open(pdf_path)
full = ''
for i, page in enumerate(doc):
    full += page.get_text()
doc.close()

with open(out_path, 'w', encoding='utf-8') as f:
    f.write(full)

print(f'DONE: {len(full)} chars saved to {out_path}')
