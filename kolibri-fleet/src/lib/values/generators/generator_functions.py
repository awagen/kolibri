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
from typing import TypeVar

from src.lib.values.generators.indexed_generator import IndexedGenerator, ByFunctionIndexedGenerator

T = TypeVar('T')


class GeneratorFunctions:
    """
    Convenience functions for the creation of generators
    """

    @staticmethod
    def range_generator(start: int | float, end: int | float, step_size: int | float) -> IndexedGenerator[int | float]:
        """
        Create generator that generates each element in a given range
        :param start: start value
        :param end: end value
        :param step_size: step size for element generation
        :return: IndexedGenerator[int | float]
        """
        size: int = int((end - start) / step_size) + 1
        return ByFunctionIndexedGenerator(
            size,
            lambda index: start + step_size * index if (size > index >= 0) else None
        )

    @staticmethod
    def generator_from_list(values: list[T]) -> IndexedGenerator[T]:
        """
        Create generator for all elements in passed list, in order of the list
        """
        size = len(values)
        return ByFunctionIndexedGenerator(
            size,
            lambda index: values[index] if index < size else None
        )


