import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from parse_game_master import (  # noqa: E402
    costume_display,
    costume_event_name,
    is_costume_form_key,
    load_costume_classification,
)


class CostumeClassificationTest(unittest.TestCase):
    def test_future_space_collaboration_is_costume_not_form(self):
        markers = load_costume_classification(ROOT / "costume_classification.json")
        self.assertTrue(is_costume_form_key("ASTRONAUT", markers))
        self.assertTrue(is_costume_form_key("ESA_SPACE_STATION", markers))

    def test_generic_future_outfit_tokens_are_costumes(self):
        markers = load_costume_classification(ROOT / "costume_classification.json")
        for form_key in ("FESTIVAL_OUTFIT", "COLLABORATION_UNIFORM", "SPACE_EXPO_SUIT"):
            self.assertTrue(is_costume_form_key(form_key, markers), form_key)

    def test_permanent_forms_are_not_reclassified(self):
        markers = load_costume_classification(ROOT / "costume_classification.json")
        for form_key in ("NORMAL", "MEGA", "DYNAMAX", "GIGANTAMAX", "CROWNED_SWORD", "ORIGIN"):
            self.assertFalse(is_costume_form_key(form_key, markers), form_key)

    def test_space_costume_labels_are_specific(self):
        self.assertIn("太空人", costume_display("ASTRONAUT", 1))
        self.assertIn("歐洲太空總署", costume_event_name("ASTRONAUT", "太空人裝扮"))


if __name__ == "__main__":
    unittest.main()

