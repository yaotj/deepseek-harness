"""文档一致性门禁：现行约束文档 MUST 指向当前口令真源，不得指向已删的 lib/。

2026-11-04 整体 review 发现：Python 化重构删掉 `scripts/lib/cloudos-auth.sh` 后，
`跨环境共性.md` / `红岛.md` / `环境总览.md` / `K8S迁移清单.md` / `构建与发布.md` /
`CONTRIBUTING.md` 里的「改密同步点」仍指向旧文件，会误导改密操作。
历史叙述（reviews / reports / tracking / CHANGELOG）不受本门禁约束，保留当时口径。
"""
from __future__ import annotations

import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
REPO = SCRIPTS_DIR.parents[3]
ENV_DIR = REPO / "docs/ops/environments"

_GONE = "scripts/lib/cloudos-auth.sh"
_CURRENT = "itpops/cloudos/auth.py"


class ActiveDocsTest(unittest.TestCase):
    def test_secret_sync_points_to_current_source(self):
        targets = (
            ENV_DIR / "跨环境共性.md",
            ENV_DIR / "红岛.md",
            ENV_DIR / "环境总览.md",
            REPO / "docs/ops/K8S迁移清单.md",
            REPO / "docs/ops/构建与发布.md",
            SCRIPTS_DIR / "CONTRIBUTING.md",
        )
        for p in targets:
            with self.subTest(doc=p.name):
                text = p.read_text(encoding="utf-8")
                self.assertNotIn(_GONE, text, f"{p.name} 仍指向已删除的口令文件")
                self.assertIn(_CURRENT, text, f"{p.name} 未指向当前真源")

    def test_readme_section_anchors_preserved(self):
        """§一~§六 是全仓 §引用机检锚点，拆文档后 MUST 保留标题。"""
        readme = (SCRIPTS_DIR / "README.md").read_text(encoding="utf-8")
        for title in ("## 一、结构", "## 二、快速上手", "## 三、CloudOS 知识沉淀",
                      "## 四、itpops 包纪律", "## 五、使用纪律", "## 六、重构与迁移记录"):
            self.assertIn(title, readme, title)


if __name__ == "__main__":
    unittest.main()
