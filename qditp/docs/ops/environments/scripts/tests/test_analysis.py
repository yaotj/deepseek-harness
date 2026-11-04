"""端点计数（原 analysis/count-endpoints.py 的规则与输出格式）。"""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from itpops.analysis import endpoints

CONTROLLER = '''\
package x;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/a")
    public String a() { return "a"; }

    @PostMapping("/b")
    public String b() { return "b"; }

    @RequestMapping("/c")
    public String c() { return "c"; }
}
'''


class EndpointsTest(unittest.TestCase):
    def _tree(self, td: str) -> Path:
        root = Path(td)
        pkg = root / "demo" / "controller"
        pkg.mkdir(parents=True)
        (pkg / "DemoController.java").write_text(CONTROLLER, encoding="utf-8")
        (pkg / "DemoService.java").write_text(CONTROLLER, encoding="utf-8")
        return root

    def test_class_level_request_mapping_excluded(self):
        with tempfile.TemporaryDirectory() as td:
            rows = endpoints.scan(self._tree(td))
        self.assertEqual(len(rows), 1)                     # 只统计 *Controller.java
        rel, method_level, class_level = rows[0]
        self.assertTrue(rel.endswith("DemoController.java"))
        self.assertEqual(class_level, 1)                   # 类级 @RequestMapping
        self.assertEqual(method_level, 3)                  # Get/Post/Request 三个方法级

    def test_render_text_and_csv(self):
        with tempfile.TemporaryDirectory() as td:
            root = self._tree(td)
            rows = endpoints.scan(root)
            text = endpoints.render(rows, str(root), csv=False)
            self.assertTrue(text[-1].startswith("方法级端点合计: 3"))
            csv = endpoints.render(rows, ".", csv=True)
        self.assertEqual(csv, ["./demo/controller/DemoController.java,3"])


if __name__ == "__main__":
    unittest.main()
