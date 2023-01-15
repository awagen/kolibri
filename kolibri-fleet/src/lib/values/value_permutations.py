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

import math
from typing import List, Tuple, TypeVar, Generic

from src.lib.utils.permutation_utils import PermutationUtils
from src.lib.values.generators.indexed_generator import IndexedGenerator

T = TypeVar('T')


class ValuePermutations(Generic[T]):

    def __init__(self, values: List[IndexedGenerator[T]]):
        self.values: list[IndexedGenerator[T]] = values
        self.nr_of_values_per_parameter: list[int] = [x.size for x in values]
        self.nr_of_permutations: int = math.prod(self.nr_of_values_per_parameter)

    def remove_value(self, value_name: str) -> Tuple['ValuePermutations', bool]:
        pass

    @staticmethod
    def original_value_index_of(n: int) -> int:
        return n

    def add_value(self, values: IndexedGenerator[T], prepend: bool) -> 'ValuePermutations':
        return ValuePermutations([values] + self.values) if prepend else ValuePermutations(self.values + [values])

    def add_values(self, values: List[IndexedGenerator[T]], prepend: bool) -> 'ValuePermutations':
        return ValuePermutations(values + self.values) if prepend else ValuePermutations(self.values + values)

    def add_permutation(self, values: 'ValuePermutations', prepend: bool) -> 'ValuePermutations':
        return ValuePermutations(values.values + self.values) if prepend else ValuePermutations(self.values + values.values)

    def steps_for_nth_element_starting_from_first_param(self, n: int) -> List[Tuple[int, int]]:
        return PermutationUtils.steps_for_nth_element_backward_calc(self.nr_of_values_per_parameter, n)

    def number_of_combination(self) -> int:
        return self.nr_of_permutations

    def find_nth_element(self, n: int) -> List[T] | None:
        parameter_indices = PermutationUtils.find_nth_element_forward_calc(self.nr_of_values_per_parameter, n)
        result = []
        for idx, value in enumerate(self.values):
            result.append(value.get(parameter_indices[idx]))
        return result

    def find_n_next_elements_from_position(self, start_element: int, nr_of_elements: int) -> list[list[T]]:
        value_indices: list[list[int]] = PermutationUtils.find_n_next_elements_from_position(
            self.nr_of_values_per_parameter,
            start_element,
            nr_of_elements
        )
        return [PermutationUtils.value_indices_to_values(self.values, x) for x in value_indices]

