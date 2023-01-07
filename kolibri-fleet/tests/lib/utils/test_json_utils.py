import unittest

from src.lib.utils.json_utils import JsonUtils
import jsonpath_ng
import json


class TestJsonUtils(unittest.TestCase):

    TEST_JSON = """
    {
        "int_value": 10,
        "arr_value": [
            {
                "single_value": 4
            },
            {
                "single_value": 3
            },
            {
                "single_value": 2
            }
        ]
    }
    """

    TEST_JSON_VALUE = json.loads(TEST_JSON)

    def test_next_selector_and_remaining_path(self):
        # given, when
        json_path, remaining_path = JsonUtils.get_next_selector_and_remaining_path("\\\\ a \\ b")
        json_path_2, remaining_path_2 = JsonUtils.get_next_selector_and_remaining_path(remaining_path)
        json_path_3, remaining_path_3 = JsonUtils.get_next_selector_and_remaining_path(remaining_path_2)
        # then
        self.assertEqual("[*].a", json_path)
        self.assertEqual("\\ b", remaining_path)
        self.assertEqual(json_path_2, ".b")
        self.assertEqual(remaining_path_2, "")
        self.assertTrue(json_path_3 is None)
        self.assertTrue(remaining_path_3 is None)

    def test_convert_string_to_json_path(self):
        # given, when
        json_path: jsonpath_ng.JSONPath = JsonUtils.convert_string_to_json_path("\\\\ a \\ b \\\\ c \\ d")
        # then
        self.assertEqual(json_path, jsonpath_ng.parse("$[*].a.b[*].c.d"))

    def test_correct_multi_value_extract(self):
        # given, when
        json_path = JsonUtils.convert_string_to_json_path("\\ arr_value \\\\ single_value")
        result = json_path.find(TestJsonUtils.TEST_JSON_VALUE)
        extracted_result = [match.value for match in result]
        # then
        self.assertEqual(extracted_result, [4, 3, 2])

    def test_correct_single_value_extract(self):
        # given, when
        json_path = JsonUtils.convert_string_to_json_path("\\ int_value")
        result = json_path.find(TestJsonUtils.TEST_JSON_VALUE)
        extracted_result = [match.value for match in result]
        # then
        self.assertEqual(extracted_result, [10])

