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
from typing import TypeVar, Callable

from src.lib.values.generators.generator_functions import GeneratorFunctions
from src.lib.values.generators.indexed_generator import ByFunctionIndexedGenerator, IndexedGenerator

T = TypeVar('T')

"""
Partitions give a grouping of data belonging together, such that it can be partitioned by.
The default implementation below is that each single element in the generator forms a partitioning.
This has to be overwritten in case the above assumption does not hold, as is the case if several values form
a logical group to group by. In that case each element provided by this generator should be a generator
providing all values belonging to the respective partition / logical grouping
"""
ValuePartitioner = Callable[[list[IndexedGenerator[T]]], IndexedGenerator[IndexedGenerator[T]]]


class SingleValuePartitioners:
    """
    Convenience functions for the creation of partitions for list of value providers,
    which means functions list[IndexedGenerator[T]] -> IndexedGenerator[IndexedGenerator[T]]
    """

    @staticmethod
    def by_size_partitioner(provider: IndexedGenerator[T], size: int) -> IndexedGenerator[IndexedGenerator[T]]:
        """
        Simple partitioner that produces int(provider.size/size) generators of given
        size, and an additional one with size provider.size % size
        """
        nr_of_full_partitions = int(provider.size / size)
        left_over_elements = provider.size - nr_of_full_partitions * size
        full_providers = [provider.get_part(x * size, (x + 1) * size) for x in range(0, nr_of_full_partitions, 1)]
        part_providers = [provider.get_part(provider.size - left_over_elements, provider.size)] \
            if left_over_elements > 0 else []
        return ByFunctionIndexedGenerator[T](
            len(full_providers) + len(part_providers),
            lambda idx: GeneratorFunctions.generator_from_list(
                full_providers + part_providers
            )
        )


class MultiValuePartitioners:
    """
    Convenience functions for the creation of partitions for list of value providers,
    which means functions list[IndexedGenerator[T]] -> IndexedGenerator[IndexedGenerator[T]]
    """

    @staticmethod
    def by_index_partitioner(providers: list[IndexedGenerator[T]], index: int) -> IndexedGenerator[IndexedGenerator[T]]:
        """
        Create one partition per value of the value provider as specified by index.
        Done by including per value of the selected provider a provider that only holds a single value
        and all other values of all other providers per partition.
        """
        partition_by_provider: IndexedGenerator[T] = providers[index]
        other_providers: list[IndexedGenerator[T]] = providers[:index] + providers[index + 1:]
        return ByFunctionIndexedGenerator[T](
            partition_by_provider.size,
            lambda idx: GeneratorFunctions.generator_from_list(
                other_providers + [GeneratorFunctions.generator_from_list([partition_by_provider.get(idx)])]
            )
        )
