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

from abc import ABC, abstractmethod
from typing import TypeVar, Iterator, Optional, Callable, List, Generic

T = TypeVar('T')
U = TypeVar('U')


class IndexedGenerator(ABC, Generic[T]):

    class ElementIterator(Iterator):

        def __init__(self, generator: 'IndexedGenerator[T]'):
            self.generator = generator
            self.current_position = 0

        def has_next(self):
            return self.current_position < self.generator.size

        def __next__(self):
            if not self.has_next():
                raise StopIteration
            element = self.generator.get(self.current_position)
            self.current_position += 1
            return element

    def iterator(self) -> Iterator[T]:
        """
        Iterator over contained elements
        :return:
        """
        return IndexedGenerator.ElementIterator(self)

    @property
    @abstractmethod
    def size(self) -> int:
        pass

    @abstractmethod
    def get(self, index: int) -> T | None:
        """
        Get the index-th element
        :param index:
        :return:
        """
        pass

    @abstractmethod
    def get_part(self, start_index: int, end_index: int) -> 'IndexedGenerator[T]':
        """
        create generator that only generates a part of the original generator.
        :param start_index: startIndex (inclusive)
        :param end_index: endIndex (exclusive)
        :return: generator generating the subpart of the generator as given by startIndex and endIndex
        """
        pass

    @abstractmethod
    def map_gen(self, func: Callable[[T], U]) -> 'IndexedGenerator[U]':
        """
        Provided a mapping function, create generator of new type where elements are created by current generator
        and then mapped by the provided function
        :param func: mapping function
        :return: new generator providing the new type
        """
        pass


class ByFunctionIndexedGenerator(IndexedGenerator[T]):

    def __init__(self, nr_of_elements: int, generator_function: Callable[[int], T]):
        self.nr_of_elements = nr_of_elements
        self.generator_function = generator_function

    @staticmethod
    def create_from_list(data: List[T]):
        return ByFunctionIndexedGenerator(len(data), lambda x: data[x])

    @property
    def size(self) -> int:
        return self.nr_of_elements

    def get_part(self, start_index: int, end_index: int) -> 'IndexedGenerator[T]':
        assert(start_index > 0)
        end = min(self.size, end_index)
        new_size = end - start_index
        return ByFunctionIndexedGenerator(new_size, lambda x: self.get(x + start_index))

    def get(self, index: int) -> Optional[T]:
        if index < self.size:
            return self.generator_function(index)
        return None

    def map_gen(self, func: Callable[[T], U]) -> 'IndexedGenerator[U]':
        def combined_callable() -> Callable[[int], U]:
            return lambda x: func(self.generator_function(x))
        return ByFunctionIndexedGenerator(self.size, combined_callable())
