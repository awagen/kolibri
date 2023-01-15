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

from src.lib.values.generators.generator_functions import GeneratorFunctions


class TestGeneratorFunctions(unittest.TestCase):

    def test_int_range_generator(self):
        # given
        range_gen = GeneratorFunctions.range_generator(0, 10, 1)
        # when, then
        self.assertEqual(range_gen.size, 11)
        self.assertEqual(list(range_gen.iterator()), list(range(0, 11, 1)))
        self.assertEqual(range_gen.get(10), 10)
        self.assertTrue(range_gen.get(11) is None)

    def test_float_range_generator(self):
        # given
        range_gen = GeneratorFunctions.range_generator(0, 1, 0.1)
        # when, then
        self.assertEqual(range_gen.size, 11)
        expected_values = [0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]
        for idx, value in enumerate(list(range_gen.iterator())):
            self.assertAlmostEqual(value, expected_values[idx])
        self.assertEqual(range_gen.get(10), 1)
        self.assertTrue(range_gen.get(11) is None)

    def test_generator_from_list(self):
        # given
        list_gen = GeneratorFunctions.generator_from_list([1, 2, 3, 4])
        # when, then
        self.assertEqual(list_gen.size, 4)
        self.assertEqual(list(list_gen.iterator()), [1, 2, 3, 4])
        self.assertEqual(list_gen.get(3), 4)
        self.assertEqual(list_gen.get(4), None)
