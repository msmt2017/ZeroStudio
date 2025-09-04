from docx import Document

def extract_text_from_docx(file_path):
    document = Document(file_path)
    paragraphs = document.paragraphs
    text = ""
    for paragraph in paragraphs:
        text += paragraph.text + "\n"
    return text