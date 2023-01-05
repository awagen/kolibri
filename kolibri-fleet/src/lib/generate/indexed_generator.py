from abc import ABC, abstractmethod
from typing import TypeVar, Generic, Iterator, Optional, Callable

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

    @property
    @abstractmethod
    def size(self) -> int:
        pass

    @abstractmethod
    def partitions(self) -> 'IndexedGenerator[IndexedGenerator[T]]':
        """
        Partitions give a grouping of data belonging together, such that it can be partitioned by.
        The default implementation below is that each single element in the generator forms a partitioning.
        This has to be overwritten in case the above assumption does not hold, as is the case if several values form
        a logical group to group by. In that case each element provided by this generator should be a generator
        providing all values belonging to the respective partition / logical grouping
        :return:
        """
        pass

    def iterator(self) -> Iterator[T]:
        """
        Iterator over contained elements
        :return:
        """
        return IndexedGenerator.ElementIterator(self)

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
    def get(self, index: int) -> Optional[T]:
        """
        Get the index-th element
        :param index:
        :return:
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



