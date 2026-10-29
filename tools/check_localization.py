"""Validate Android translations without requiring the Android SDK.

Run: python tools/check_localization.py
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


MAIN = Path(__file__).resolve().parents[1] / "app/src/main"


def strings(folder):
    elements = ET.parse(MAIN / "res" / folder / "strings.xml").getroot()
    values = {element.attrib["name"]: element for element in elements}
    if len(values) != len(elements):
        raise AssertionError(f"Duplicate string names in {folder}")
    return values


class LocalizationTests(unittest.TestCase):
    def test_translation_coverage_and_format_arguments(self):
        default = strings("values")
        expected = {key for key, element in default.items()
                    if element.get("translatable") != "false"}
        for folder in ("values-zh", "values-ja"):
            translated = strings(folder)
            self.assertEqual(expected, set(translated), folder)
            for key in expected:
                with self.subTest(locale=folder, key=key):
                    text = translated[key].text
                    self.assertTrue(text and text.strip())
                    self.assertEqual(
                        sorted(re.findall(r"%\d+\$[ds]", default[key].text)),
                        sorted(re.findall(r"%\d+\$[ds]", text)),
                    )

    def test_string_references_exist(self):
        names = set(strings("values"))
        for path in MAIN.rglob("*"):
            if path.suffix not in (".kt", ".java", ".xml"):
                continue
            source = path.read_text(encoding="utf-8")
            references = re.findall(r"R\.string\.(\w+)|@string/(\w+)", source)
            for java, xml in references:
                self.assertIn(java or xml, names, str(path))

    def test_icon_picker_has_translated_labels(self):
        helper = (MAIN / "java/org/eu/suisei/xray/IconHelper.java").read_text(encoding="utf-8")
        available = re.search(r"AVAILABLE_ICONS = \{(.*?)\};", helper, re.S).group(1)
        icons = re.findall(r'"(\w+)"', available)
        for folder in ("values", "values-zh", "values-ja"):
            resources = strings(folder)
            for icon in icons:
                self.assertIn("icon_" + icon, resources)

    def test_product_names_use_shared_resources(self):
        default = strings("values")
        for key, value in (("app_name", "Xray"), ("tile_label_normal", "Xray"),
                           ("caffeine_tile_label", "Caffeine")):
            self.assertEqual(default[key].text, value)
            self.assertEqual(default[key].get("translatable"), "false")

    def test_system_language_selection_lists_all_supported_locales(self):
        config = ET.parse(MAIN / "res/xml/locales_config.xml").getroot()
        attribute = "{http://schemas.android.com/apk/res/android}name"
        self.assertEqual({element.get(attribute) for element in config}, {"en", "zh", "ja"})


if __name__ == "__main__":
    unittest.main()
