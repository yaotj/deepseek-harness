#!/usr/bin/env python3
"""从API.md和Word需求文档中提取并合并接口需求"""

import re
import json
from pathlib import Path
from collections import defaultdict
from datetime import datetime

def parse_api_md(md_path):
    """解析API.md提取所有接口"""
    content = md_path.read_text(encoding='utf-8')
    interfaces = []
    
    current_service = None
    current_controller = None
    current_interface = None
    current_details = []
    
    for line in content.split('\n'):
        original = line
        
        # Service header
        if line.startswith('## '):
            if current_interface and current_controller:
                current_interface['details'] = '\n'.join(current_details)
                interfaces.append({
                    'service': current_service,
                    'controller': current_controller,
                    **current_interface
                })
            current_service = line[3:].strip()
            current_controller = None
            current_interface = None
            current_details = []
            continue
        
        # Controller header
        if line.startswith('### '):
            if current_interface:
                current_interface['details'] = '\n'.join(current_details)
                interfaces.append({
                    'service': current_service,
                    'controller': current_controller,
                    **current_interface
                })
            current_controller = line[4:].strip()
            current_interface = None
            current_details = []
            continue
        
        # Interface header
        if line.startswith('#### '):
            if current_interface:
                current_interface['details'] = '\n'.join(current_details)
                interfaces.append({
                    'service': current_service,
                    'controller': current_controller,
                    **current_interface
                })
            
            num = line[5:].strip()
            name = num.split('.', 1)[1].strip() if '.' in num else num.strip()
            current_interface = {
                'number': num,
                'name': name,
                'path': '',
                'method': '',
                'return_type': '',
                'params': [],
                'response': [],
                'example_request': '',
                'example_response': ''
            }
            current_details = []
            continue
        
        # Collect details
        if current_interface:
            current_details.append(original)
    
    # Save last
    if current_interface:
        current_interface['details'] = '\n'.join(current_details)
        interfaces.append({
            'service': current_service,
            'controller': current_controller,
            **current_interface
        })
    
    return interfaces

def parse_interface_details(details_text):
    """从接口详情文本中提取结构化信息"""
    info = {
        'path': '',
        'method': '',
        'return_type': '',
        'params': [],
        'example_request': '',
        'example_response': ''
    }
    
    for line in details_text.split('\n'):
        line = line.strip()
        
        # Path
        if line.startswith('- **路径**：'):
            info['path'] = line.replace('- **路径**：', '').strip()
        
        # Method
        elif line.startswith('- **HTTP**：'):
            info['method'] = line.replace('- **HTTP**：', '').strip()
        
        # Return type
        elif line.startswith('- **返回类型**：'):
            info['return_type'] = line.replace('- **返回类型**：', '').strip()
        
        # Parameters
        elif line.startswith('- **参数**：'):
            param_text = line.replace('- **参数**：', '').strip()
            if param_text:
                info['params'].append(param_text)
        
        # Example request
        elif line.startswith('```json') or line.startswith('```JSON'):
            info['example_request'] = '```json'
        
        elif info['example_request'] and line == '```':
            info['example_request'] += '\n```'
        
        elif info['example_request']:
            info['example_request'] += '\n' + line
        
        # Example response
        elif line.startswith('**响应示例**：'):
            info['example_response'] = '```json'
        
        elif info['example_response'] and line == '```':
            info['example_response'] += '\n```'
        
        elif info['example_response']:
            info['example_response'] += '\n' + line
    
    return info

def extract_interfaces_from_word(req_dir):
    """从Word需求文档中提取接口定义"""
    word_interfaces = []
    
    # Read each JSON file
    for json_file in sorted(req_dir.glob('*.json')):
        with open(json_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        source = json_file.stem
        text = '\n'.join(data['content']['full_text'])
        
        # Extract interface patterns
        lines = data['content']['full_text']
        
        for i, line in enumerate(lines):
            # Interface name pattern
            if re.match(r'^\d+、', line):
                iface_name = line.strip('、')
                
                # Look ahead for details
                path = ''
                desc = ''
                params = []
                
                for j in range(i + 1, min(i + 20, len(lines))):
                    l = lines[j]
                    
                    if '接口地址' in l or '路径' in l:
                        match = re.search(r'[hH]ttp[sS]?://[^\s]+|/[a-zA-Z0-9/_-]+', l)
                        if match:
                            path = match.group(0)
                    
                    if '接口说明' in l:
                        if j + 1 < len(lines):
                            desc = lines[j + 1].strip()
                    
                    if l in ['请求参数', '入参', '字段', '类型', '说明']:
                        # Collect table-like params
                        for k in range(j + 1, min(j + 10, len(lines))):
                            if lines[k].strip() in ['示例报文', '应答', '返回']:
                                break
                            if lines[k] and not lines[k].startswith('{'):
                                params.append(lines[k].strip())
                        break
                
                word_interfaces.append({
                    'name': iface_name,
                    'path': path,
                    'description': desc,
                    'params': params[:5],  # Limit params
                    'source': source
                })
    
    return word_interfaces

def main():
    """主函数"""
    api_md = Path('API.md')
    req_dir = Path('docs/提取的需求')
    output_dir = Path('docs/接口需求清单')
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Parse API.md
    print("正在解析 API.md...")
    api_interfaces = parse_api_md(api_md)
    
    # Extract from Word docs
    print("正在解析 Word 需求文档...")
    word_interfaces = extract_interfaces_from_word(req_dir)
    
    # Merge and deduplicate
    all_interfaces = []
    seen = set()
    
    # Add API.md interfaces first
    for iface in api_interfaces:
        key = f"{iface['service']}_{iface['controller']}_{iface['name']}"
        if key not in seen:
            seen.add(key)
            # Parse details
            parsed = parse_interface_details(iface.get('details', ''))
            all_interfaces.append({
                **iface,
                **parsed,
                'source': 'API.md',
                'priority': 'P0'
            })
    
    # Add Word document interfaces
    for iface in word_interfaces:
        key = f"word_{iface['name']}"
        if key not in seen:
            seen.add(key)
            all_interfaces.append({
                'name': iface['name'],
                'path': iface.get('path', ''),
                'description': iface.get('description', ''),
                'params': iface.get('params', []),
                'service': '',
                'controller': '',
                'method': '',
                'return_type': '',
                'source': iface['source'],
                'priority': 'P1'
            })
    
    # Group by service
    by_service = defaultdict(list)
    for iface in all_interfaces:
        svc = iface.get('service', '未知服务')
        by_service[svc].append(iface)
    
    # Generate markdown for each service
    print("正在生成接口清单...")
    
    # Summary
    summary_lines = [
        '# 青岛地铁ITP系统 - 接口需求清单',
        '',
        f'> 生成日期：{datetime.now().strftime("%Y-%m-%d")}',
        f'> 总计接口数：{len(all_interfaces)}',
        f'> 涉及服务数：{len(by_service)}',
        '',
        '---',
        '',
        '## 接口总览',
        '',
        '| 序号 | 服务 | 控制器 | 接口名 | 路径 | 优先级 | 来源 |',
        '|------|------|--------|--------|------|--------|------|'
    ]
    
    for idx, iface in enumerate(all_interfaces, 1):
        summary_lines.append(
            f"| {idx} | {iface.get('service', '-')} | {iface.get('controller', '-')} "
            f"| {iface['name']} | {iface.get('path', '-')} | {iface.get('priority', '-')} "
            f"| {iface.get('source', '-')} |"
        )
    
    summary_lines.append('')
    summary_lines.append('---')
    summary_lines.append('')
    
    # Detailed sections by service
    for svc in sorted(by_service.keys()):
        interfaces = by_service[svc]
        summary_lines.append(f"## {svc}")
        summary_lines.append(f"共 {len(interfaces)} 个接口\n")
        
        for iface in interfaces:
            summary_lines.append(f"### {iface['name']}")
            summary_lines.append("")
            summary_lines.append(f"- **控制器**: {iface.get('controller', '-')}")
            summary_lines.append(f"- **路径**: `{iface.get('path', '-')}`")
            summary_lines.append(f"- **方法**: {iface.get('method', '-')}")
            summary_lines.append(f"- **返回类型**: {iface.get('return_type', '-')}")
            summary_lines.append(f"- **优先级**: {iface.get('priority', '-')}")
            summary_lines.append(f"- **来源**: {iface.get('source', '-')}")
            
            if iface.get('description'):
                summary_lines.append(f"- **说明**: {iface['description']}")
            
            if iface.get('params'):
                summary_lines.append("")
                summary_lines.append("**请求参数**:")
                for p in iface['params'][:10]:  # Limit params display
                    summary_lines.append(f"  - {p}")
            
            if iface.get('example_request'):
                summary_lines.append("")
                summary_lines.append("**请求示例**:")
                summary_lines.append("```json")
                # Extract JSON from details
                match = re.search(r'\{[^}]+\}', iface.get('details', ''))
                if match:
                    summary_lines.append(match.group(0))
                summary_lines.append("```")
            
            summary_lines.append("")
    
    # Write summary
    summary_path = output_dir / '接口总览.md'
    summary_path.write_text('\n'.join(summary_lines), encoding='utf-8')
    
    # Write per-service files
    for svc in sorted(by_service.keys()):
        interfaces = by_service[svc]
        lines = [
            f"# {svc} 接口清单",
            "",
            f"共 {len(interfaces)} 个接口",
            "",
            "---",
            ""
        ]
        
        for iface in interfaces:
            lines.append(f"## {iface['name']}")
            lines.append("")
            lines.append(f"| 属性 | 值 |")
            lines.append(f"|------|-----|")
            lines.append(f"| 控制器 | {iface.get('controller', '-')} |")
            lines.append(f"| 路径 | `{iface.get('path', '-')}` |")
            lines.append(f"| 方法 | {iface.get('method', '-')} |")
            lines.append(f"| 返回类型 | {iface.get('return_type', '-')} |")
            lines.append(f"| 优先级 | {iface.get('priority', '-')} |")
            lines.append(f"| 来源 | {iface.get('source', '-')} |")
            
            if iface.get('description'):
                lines.append(f"\n**说明**: {iface['description']}")
            
            if iface.get('params'):
                lines.append("\n**请求参数**:")
                lines.append("| 参数 | 说明 |")
                lines.append("|------|------|")
                for p in iface['params'][:5]:
                    parts = p.split()
                    if len(parts) >= 2:
                        lines.append(f"| {parts[0]} | {' '.join(parts[1:])} |")
                    else:
                        lines.append(f"| {p} | |")
            
            lines.append("")
            lines.append("---")
            lines.append("")
        
        safe_name = re.sub(r'[^\w\s-]', '', svc).replace(' ', '_')
        file_path = output_dir / f'{safe_name}_接口清单.md'
        file_path.write_text('\n'.join(lines), encoding='utf-8')
    
    # Write JSON data
    with open(output_dir / '所有接口.json', 'w', encoding='utf-8') as f:
        json.dump(all_interfaces, f, ensure_ascii=False, indent=2)
    
    print(f"\n✅ 完成！")
    print(f"📁 输出目录：{output_dir}")
    print(f"📄 接口总览：{summary_path}")
    print(f"📊 总接口数：{len(all_interfaces)}")

if __name__ == '__main__':
    main()
