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
import re
import unittest

from src.lib.struct.struct_def import RegexStructDef


class TestStructDef(unittest.TestCase):

    def test_regex_struct_def(self):
        # given
        regex_struct_def = RegexStructDef(re.compile("^a.*$"))
        # when
        should_be_false = regex_struct_def.cast_value_is_valid("ba")
        should_be_true = regex_struct_def.cast_value_is_valid("ab")
        # then
        self.assertFalse(should_be_false)
        self.assertTrue(should_be_true)


if __name__ == '__main__':
    unittest.main()