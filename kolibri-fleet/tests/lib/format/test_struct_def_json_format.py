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

from src.lib.format.json.struct_def_json_format import JsonKeys, StructDefTypes, StructDefJsonFormat
from src.lib.struct.struct_def import IntStructDef, FloatStructDef, StringStructDef, BooleanStructDef


class TestFormats:
    int_type_dict = {JsonKeys.TYPE_KEY: StructDefTypes.INT_TYPE}
    double_type_dict = {JsonKeys.TYPE_KEY: StructDefTypes.DOUBLE_TYPE}
    string_type_dict = {JsonKeys.TYPE_KEY: StructDefTypes.STRING_TYPE}
    bool_type_dict = {JsonKeys.TYPE_KEY: StructDefTypes.BOOLEAN_TYPE}


class TestStructDefJsonFormat(unittest.TestCase):

    def test_from_dict(self):
        # given, when
        int_format = StructDefJsonFormat.from_dict(TestFormats.int_type_dict)
        double_format = StructDefJsonFormat.from_dict(TestFormats.double_type_dict)
        string_format = StructDefJsonFormat.from_dict(TestFormats.string_type_dict)
        bool_format = StructDefJsonFormat.from_dict(TestFormats.bool_type_dict)
        # then
        self.assertTrue(int_format, IntStructDef)
        self.assertTrue(double_format, FloatStructDef)
        self.assertTrue(string_format, StringStructDef)
        self.assertTrue(bool_format, BooleanStructDef)


if __name__ == '__main__':
    unittest.main()