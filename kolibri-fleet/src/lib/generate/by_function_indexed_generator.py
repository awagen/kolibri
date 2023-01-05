from typing import Callable, Optional, List

from src.lib.generate.indexed_generator import IndexedGenerator, T, U


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

    def partitions(self) -> 'IndexedGenerator[IndexedGenerator[T]]':
        return ByFunctionIndexedGenerator(
            self.size,
            lambda x: ByFunctionIndexedGenerator.create_from_list([self.get(x)])
        )

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
