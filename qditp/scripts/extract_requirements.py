#!/usr/bin/env python3
"""从Word文档提取需求并生成结构化需求文档"""

import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
import json
from datetime import datetime

def extract_docx_text(docx_path):
    """从docx文件中提取所有段落文本"""
    with zipfile.ZipFile(docx_path) as z:
        xml_content = z.read('word/document.xml')
    root = ET.fromstring(xml_content)
    paragraphs = []
    for p in root.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p'):
        texts = []
        for t in p.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t'):
            if t.text:
                texts.append(t.text)
        if texts:
            paragraphs.append(''.join(texts))
    return [p for p in paragraphs if p.strip()]

def extract_table_content(docx_path):
    """从docx文件中提取表格内容"""
    tables = []
    with zipfile.ZipFile(docx_path) as z:
        xml_content = z.read('word/document.xml')
    root = ET.fromstring(xml_content)
    
    for table in root.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}table'):
        table_data = []
        for row in table.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}tr'):
            row_data = []
            for cell in row.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}tc'):
                cell_text = []
                for p in cell.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p'):
                    for t in p.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t'):
                        if t.text:
                            cell_text.append(t.text)
                row_data.append(' '.join(cell_text))
            table_data.append(row_data)
        tables.append(table_data)
    return tables

def generate_requirement_doc(source_doc, output_path, doc_type):
    """生成结构化的需求文档"""
    text = extract_docx_text(source_doc)
    tables = extract_table_content(source_doc)
    
    # 构建需求文档
    requirement = {
        "metadata": {
            "source_file": str(source_doc),
            "extract_date": datetime.now().strftime("%Y-%m-%d"),
            "doc_type": doc_type,
            "total_paragraphs": len(text),
            "total_tables": len(tables)
        },
        "content": {
            "full_text": text,
            "tables": tables
        }
    }
    
    # 保存为JSON便于后续处理
    output_json = output_path.parent / (output_path.stem + ".json")
    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(requirement, f, ensure_ascii=False, indent=2)
    
    # 生成Markdown格式的需求文档
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(f"# 需求文档：{source_doc.stem}\n\n")
        f.write(f"> 来源：{source_doc.name}\n")
        f.write(f"> 提取日期：{datetime.now().strftime('%Y-%m-%d')}\n")
        f.write(f"> 类型：{doc_type}\n\n")
        
        # 添加原始内容
        f.write("## 原始内容\n\n")
        f.write('\n'.join(text[:200]))  # 限制长度避免文件过大
        if len(text) > 200:
            f.write(f"\n\n*... 共 {len(text)} 行，详见JSON文件 ...*\n")
        
        # 添加表格内容
        if tables:
            f.write("\n## 表格数据\n\n")
            for i, table in enumerate(tables):
                f.write(f"### 表格 {i+1}\n\n")
                for row in table:
                    f.write('| ' + ' | '.join(row) + ' |\n')
                f.write("\n")
    
    return requirement

def main():
    """主函数：提取所有Word文档的需求"""
    docs_dir = Path('docs')
    output_dir = docs_dir / '提取的需求'
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # 定义文档类型映射
    doc_types = {
        '功能需求文档': '业务需求',
        '业务接口文档': '接口需求',
        '系统设计文档': '架构需求',
        '技术规范': '技术规范'
    }
    
    extracted_count = 0
    
    for category, type_name in doc_types.items():
        category_dir = docs_dir / category
        if not category_dir.exists():
            continue
            
        for docx_file in category_dir.glob('*.docx'):
            output_path = output_dir / f"{type_name}_{docx_file.stem}.md"
            print(f"正在提取：{docx_file.name}")
            generate_requirement_doc(docx_file, output_path, type_name)
            extracted_count += 1
            
        for doc_file in category_dir.glob('*.doc'):
            # .doc文件需要先转换，这里跳过或标记
            output_path = output_dir / f"{type_name}_{doc_file.stem}_needs_conversion.md"
            with open(output_path, 'w', encoding='utf-8') as f:
                f.write(f"# 需求文档：{doc_file.stem}\n\n")
                f.write("> ⚠️ 注意：.doc格式需要转换为.docx后再提取\n")
                f.write(f"> 来源：{doc_file.name}\n")
                f.write(f"> 类型：{type_name}\n\n")
                f.write("此文件需要手动转换为.docx格式后再进行需求提取。")
            extracted_count += 1
    
    print(f"\n✅ 共提取 {extracted_count} 个文档的需求")
    print(f"📁 输出目录：{output_dir}")

if __name__ == '__main__':
    main()
