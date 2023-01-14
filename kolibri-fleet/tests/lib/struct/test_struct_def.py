# Copyright 2023 Andreas Wagenmann
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import unittest

from src.lib.struct.struct_def import RegexStructDef, ListRegexStructDef, ChoiceStructDef, ListChoiceStructDef


class TestStructDef(unittest.TestCase):

    def test_regex_struct_def(self):
        # given
        regex_struct_def = RegexStructDef("^a.*$")
        # when
        should_be_false = regex_struct_def.cast_value_is_valid("ba")
        should_be_true = regex_struct_def.cast_value_is_valid("ab")
        # then
        self.assertFalse(should_be_false)
        self.assertTrue(should_be_true)

    def test_list_regex_struct_def(self):
        # given
        list_regex_struct_def = ListRegexStructDef("^a.*$")
        # when
        should_be_false_1 = list_regex_struct_def.cast_value_is_valid(["ab", "ba"])
        should_be_false_2 = list_regex_struct_def.cast_value_is_valid("ab")
        should_be_true_1 = list_regex_struct_def.cast_value_is_valid(["ab", "aab", "acdef"])
        # then
        self.assertFalse(should_be_false_1)
        self.assertFalse(should_be_false_2)
        self.assertTrue(should_be_true_1)

    def test_choice_struct_def(self):
        # given
        choice_struct_def_str = ChoiceStructDef[str](["a", "b", "abc"])
        choice_struct_def_int = ChoiceStructDef[int]([1, 3, 4])
        # when
        should_be_false_1 = choice_struct_def_str.cast_value_is_valid("c")
        should_be_false_2 = choice_struct_def_str.cast_value_is_valid("ee")
        should_be_false_3 = choice_struct_def_int.cast_value_is_valid(10)
        should_be_true_1 = choice_struct_def_str.cast_value_is_valid("a")
        should_be_true_2 = choice_struct_def_str.cast_value_is_valid("b")
        should_be_true_3 = choice_struct_def_str.cast_value_is_valid("abc")
        should_be_true_4 = choice_struct_def_int.cast_value_is_valid(1)
        # then
        self.assertFalse(should_be_false_1)
        self.assertFalse(should_be_false_2)
        self.assertFalse(should_be_false_3)
        self.assertTrue(should_be_true_1)
        self.assertTrue(should_be_true_2)
        self.assertTrue(should_be_true_3)
        self.assertTrue(should_be_true_4)

    def test_list_choice_struct_def(self):
        # given
        list_choice_struct_def = ListChoiceStructDef[str](["eee", "bb"])
        # when
        should_be_false_1 = list_choice_struct_def.cast_value_is_valid(["a", "bb"])
        should_be_true_1 = list_choice_struct_def.cast_value_is_valid(["eee", "bb"])
        should_be_true_2 = list_choice_struct_def.cast_value_is_valid(["bb"])
        # then
        self.assertFalse(should_be_false_1)
        self.assertTrue(should_be_true_1)
        self.assertTrue(should_be_true_2)


if __name__ == '__main__':
    unittest.main()