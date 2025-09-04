from pypdf import PdfReader

def extract_text_from_pdf(file_path):
    reader = PdfReader(file_path)
    text = ""
    for page in reader.pages:
        text += page.extract_text() or ""  # 防止有些页面没有文本内容
        text += "\n==== PAGE END ====\n"
    return text