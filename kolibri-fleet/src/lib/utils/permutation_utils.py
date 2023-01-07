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

from typing import Optional, List, Tuple
from functools import reduce


class PermutationUtils:

    @staticmethod
    def _find_first_non_max_index(current_indices: List[int], nr_elements_per_parameter: List[int]) -> Optional[int]:
        """
        Zero-based. Given a seq of current indices and a seq of nr of elements
        (where indices in the first correspond to indices in the latter, e.g both must be of same length)
        find the first that is not yet at its max value
        :param current_indices: the current indices for each position
        :param nr_elements_per_parameter: the total nr of elements for each position
        :return: Option[Int]. None if all are at max, or Some(elementIndex) if elementIndex is not at its max
        """
        result = None
        for index in range(0, len(current_indices), 1):
            if current_indices[index] < nr_elements_per_parameter[index] - 1:
                result = index
                break
        return result

    @staticmethod
    def _iterate_first_till_max_size_limited(current_position: List[int], nr_elements_per_parameter: List[int], nr_elements: int):
        """
        Same as iterateFirstTillMax(current_position: List[int], nr_elements_per_parameter: List[int])
        but limiting the number of elements to nrOfElements
        :param current_position: current indices per parameter
        :param nr_elements_per_parameter: counts of different values per parameter
        :param nr_elements: overall number of elements requested
        :return:
        """
        index_sequences = PermutationUtils._iterate_first_till_max(current_position, nr_elements_per_parameter)
        return index_sequences[0 : min(len(index_sequences), nr_elements)]

    @staticmethod
    def _iterate_first_till_max(current_position: List[int], nr_elements_per_parameter: List[int]):
        """
        Takes all values of index for the first parameter (currentPosition till max value)
        and for each value appends the unchanged indices for the later elements.
        NOTE: result does not include the current position
        :param current_position:
        :param nr_elements_per_parameter:
        :return: List[List[Int]]: list of indices-per-element lists with all but index for first element
                 unchanged
        """
        used_range = range(current_position[0], nr_elements_per_parameter[0])
        values = []
        for index in used_range[1:]:
            value_arr = [index]
            value_arr.extend(current_position[1:])
            values.append(value_arr)
        return values

    @staticmethod
    def _increase_by_step_and_reset_previous(sequence: List[int], index: int) -> List[int]:
        """
        Increase parameter at position defined by index (0-based) by one step and reset the parameters defined by
        smaller index. Leave the others unchanged
        :param sequence: The start sequence
        :param index: The index (0-based), identifying the parameter
        :return: The newly generated sequence
        """
        result = []
        for i in range(len(sequence)):
            if i < index:
                result.append(0)
            elif i == index:
                result.append(sequence[i] + 1)
            else:
                result.append(sequence[i])
        return result

    @staticmethod
    def generate_first_parameter_indices(nr: int, nr_of_elements_per_parameter: List[int]) -> List[List[int]]:
        """
        Find the first nr elements for the set parameters, starting from start values
        :param nr: The nr of elements
        :param nr_of_elements_per_parameter:
        :return: The first n elements (at most of size nr)
        """
        start_value = [0] * len(nr_of_elements_per_parameter)
        result = [start_value]
        result.extend(PermutationUtils.generate_next_parameters(start_value, nr_of_elements_per_parameter, nr - 1))
        return result

    @staticmethod
    def generate_next_parameters(seq: List[int], nr_of_elements_per_parameter: List[int], nr: int) -> List[List[int]]:
        """
        Given a current index sequence, generate the next nr of index sequences, each giving the current element per
        parameter.
        Quite robust even for large nr of elements due to iterative procedure
        :param seq: The starting sequence
        :param nr_of_elements_per_parameter: count of distinct values for each parameter
        :param nr: The nr of next sequences (where the value n at ith position indicates that parameter i should be set
        to its nth value) to generate
        :return: The n next index sequences starting from seq (at most nr elements). Does not include the starting
        sequence
        """

        if nr <= 0:
            return []

        add_these = []
        current_start = seq
        do_break: bool = False

        while len(add_these) < nr and not do_break:
            first_non_max = PermutationUtils._find_first_non_max_index(current_start, nr_of_elements_per_parameter)
            if first_non_max == 0:
                add_these.extend(
                    PermutationUtils._iterate_first_till_max_size_limited(current_start, nr_of_elements_per_parameter,
                                                                          nr - len(add_these)))
                current_start = add_these[-1]
            elif first_non_max is not None:
                start_with = PermutationUtils._increase_by_step_and_reset_previous(current_start, first_non_max)
                add_these.append(start_with)
                if nr - len(add_these) > 0:
                    add_these.extend(
                        PermutationUtils._iterate_first_till_max_size_limited(start_with, nr_of_elements_per_parameter,
                                                                              nr - len(add_these))
                    )
                current_start = add_these[-1]
            else:
                do_break = True

        return add_these

    @staticmethod
    def find_n_next_elements_from_position(nr_of_elements_per_parameter: List[int],
                                           start_element_nr: int,
                                           nr_of_elements: int) -> List[List[int]]:
        """
        Given a position (0-based) of all the combinations, returns n next elements (at most nrOfElements elements),
        including the startElementNr-th element. Provides the index sequence for each parameter. To get the actual
        values those positions have to be requested from the values, e.g for ith index with value n in the result,
        wed get the actual values by values(i).getNthZeroBased(n)
        :param nr_of_elements_per_parameter:
        :param start_element_nr:
        :param nr_of_elements:
        :return:
        """
        if nr_of_elements == 0:
            return []
        start_element: Optional[List[int]] = PermutationUtils\
            .find_nth_element_forward_calc(nr_of_elements_per_parameter, start_element_nr)
        if nr_of_elements == 1:
            return [start_element] if (start_element is not None) else []
        if start_element is not None:
            return [start_element] + PermutationUtils.generate_next_parameters(start_element,
                                                                               nr_of_elements_per_parameter,
                                                                               nr_of_elements - 1)
        else:
            return []

    @staticmethod
    def element_product(elements: List[int], default_on_empty_input: int = 1) -> int:
        if elements is None or len(elements) == 0:
            return default_on_empty_input
        return reduce((lambda x, y: x * y), elements)

    @staticmethod
    def find_nth_element_forward_calc(nr_of_elements_per_parameter: List[int], n: int) -> List[int] | None:
        """
        Returns n-th element (0-based) of parameter sequence. None if no such element existing.
        Procedure: start from first element, increase till max, then go to next element, increase that
        and reset all element with smaller indices to first value (0) and continue like that
        :param nr_of_elements_per_parameter:
        :param n: Position of the requested element (0-based)
        :return:
        """

        overall_permutations = PermutationUtils.element_product(nr_of_elements_per_parameter)
        if n > overall_permutations - 1 or n < 0:
            return None

        current_value: List[int] = [0] * len(nr_of_elements_per_parameter)
        current_position: int = 0
        do_break: bool = False

        while current_position < n and not do_break:
            first_non_max = PermutationUtils._find_first_non_max_index(current_value, nr_of_elements_per_parameter)
            if first_non_max == 0:
                next_num_steps = min(nr_of_elements_per_parameter[0] - 1 - current_value[0], n - current_position)
                current_position += next_num_steps
                current_value = [current_value[0] + next_num_steps] + current_value[1:]
            elif first_non_max is not None:
                current_value = PermutationUtils._increase_by_step_and_reset_previous(current_value, first_non_max)
                current_position += 1
                if current_position < n and nr_of_elements_per_parameter[0] > 1:
                    next_num_steps = min(nr_of_elements_per_parameter[0] - 1 - current_value[0], n - current_position)
                    current_position += next_num_steps
                    current_value = [current_value[0] + next_num_steps] + current_value[1:]
            else:
                do_break = True
        return current_value if (current_position == n) else None

    @staticmethod
    def steps_for_nth_element_backward_calc(steps_per_param: List[int], n: int) -> List[Tuple[int, int]]:
        """
        NOTE: this provides a list of tuples where x._1 is the parameter index and x._2 is the
        element index for the given parameter. It just contains the relevant steps,
        e.g the first m parameters needed to satisfy the dependency. The indices for the remaining
        parameters ones would just need to be set to 0
        :param steps_per_param:
        :param n: number of element
        :return: List of tuples, where first element corresponds to index and second to the respective step
             (step is 0-based)
        """

        total_num_steps = PermutationUtils.element_product(steps_per_param)
        assert(total_num_steps >= n, "Number of combinations (%s) smaller than requested element number (%s)"
               % (total_num_steps, n))

        # find first index for which the total nr of possibly permutations is >= n
        param_index = None
        for ind in range(len(steps_per_param)):
            if PermutationUtils.element_product(steps_per_param[:ind + 1]) >= n + 1:
                param_index = ind
                break

        # excluding the determined index, calc how many permutations would be generated by
        # all combinations of indices < determined index (e.g in case of index = 0 this would be 1)
        start_combinations = PermutationUtils.element_product(steps_per_param[:param_index])

        # calculate first pair of index of element for the current index for which total number
        # of permutations >= needed permutations. Afterwards we will just remove the leftover from the
        # max of permutations from the previous indices and start the calculation anew
        index_and_num_combinations: (int, int) = None
        for step in range(steps_per_param[param_index]):
            combinations_count = start_combinations * (step + 1)
            if combinations_count >= n + 1:
                index_and_num_combinations = (step, combinations_count)
                break

        # if all combinations for current setting of current index are too many, find the setting for the previous
        # values where the too many elements are substracted from the number of their total combinations
        # removeFromMaxPermutationsOfRemainingIndices will always be <= 0
        remove_from_max_permutations_of_remaining_indices = n - index_and_num_combinations[1]

        # now limit search to the previous indices and find the nthElementOfPreviousIndices element
        # to find the right position
        nth_element_of_previous_indices: int = start_combinations + remove_from_max_permutations_of_remaining_indices

        if param_index == 0:
            return [(param_index, index_and_num_combinations[0])]
        else:
            return PermutationUtils.steps_for_nth_element_backward_calc(
                steps_per_param,
                nth_element_of_previous_indices) + [(param_index, index_and_num_combinations[0])]

