import unittest
from typing import List

from src.lib.utils.permutation_utils import PermutationUtils


class TestPermutationUtils(unittest.TestCase):

    def test_find_first_non_max_index(self):
        # given, when
        all_at_max = PermutationUtils._find_first_non_max_index([2, 2, 3], [3, 3, 4])
        second_is_non_max = PermutationUtils._find_first_non_max_index([2, 1, 3], [3, 3, 4])
        # then
        self.assertEqual(all_at_max, None)
        self.assertEqual(second_is_non_max, 1)

    def test_iterate_till_max(self):
        # given, when
        values: List[List[int]] = PermutationUtils._iterate_first_till_max([1, 0, 2], [5, 4, 4])
        # then
        self.assertEqual(values, [[2, 0, 2], [3, 0, 2], [4, 0, 2]])

    def test_iterate_till_max_with_limit(self):
        # given, when
        values: List[List[int]] = PermutationUtils._iterate_first_till_max_size_limited([1, 0, 2], [10, 4, 4], 4)
        # then
        self.assertEqual(values, [[2, 0, 2], [3, 0, 2], [4, 0, 2], [5, 0, 2]])

    def test_increase_by_step_and_reset_previous(self):
        # given, when
        values: List[int] = PermutationUtils._increase_by_step_and_reset_previous([2, 3, 1, 4], 2)
        # then
        self.assertEqual(values, [0, 0, 2, 4])

    def test_generate_first_n_parameter_indices(self):
        # given, when
        values: List[List[int]] = PermutationUtils.generate_first_parameter_indices(4, [2, 3, 4])
        # then
        self.assertEqual(values, [[0, 0, 0], [1, 0, 0], [0, 1, 0], [1, 1, 0]])

    def test_generate_next_n_parameters(self):
        # given, when
        values: List[List[int]] = PermutationUtils.generate_next_parameters([1, 3, 2, 1], [2, 4, 4, 4], 5)
        # then
        self.assertEqual(values, [[0, 0, 3, 1], [1, 0, 3, 1], [0, 1, 3, 1], [1, 1, 3, 1], [0, 2, 3, 1]])

    def test_find_n_next_elements_from_position(self):
        # given, when
        values: List[List[int]] = PermutationUtils.find_n_next_elements_from_position(
            nr_of_elements_per_parameter=[2, 2, 2, 2],
            start_element_nr=0,
            nr_of_elements=4
        )
        # then
        self.assertEqual(values, [[0, 0, 0, 0], [1, 0, 0, 0], [0, 1, 0, 0], [1, 1, 0, 0]])

    def test_find_nth_element(self):
        # given, when
        values_0 = PermutationUtils.find_nth_element_forward_calc(nr_of_elements_per_parameter=[2, 2, 2, 2], n=0)
        values_3 = PermutationUtils.find_nth_element_forward_calc(nr_of_elements_per_parameter=[2, 2, 2, 2], n=3)
        values_4 = PermutationUtils.find_nth_element_forward_calc(nr_of_elements_per_parameter=[2, 2, 2, 2], n=4)
        # then
        self.assertEqual(values_0, [0, 0, 0, 0])
        self.assertEqual(values_3, [1, 1, 0, 0])
        self.assertEqual(values_4, [0, 0, 1, 0])

    def test_steps_for_nth_element_backward(self):
        # given, when
        values_0 = PermutationUtils.steps_for_nth_element_backward_calc(steps_per_param=[2, 2, 2, 2], n=0)
        values_1 = PermutationUtils.steps_for_nth_element_backward_calc(steps_per_param=[2, 2, 2, 2], n=1)
        values_2 = PermutationUtils.steps_for_nth_element_backward_calc(steps_per_param=[2, 2, 2, 2], n=2)
        values_3 = PermutationUtils.steps_for_nth_element_backward_calc(steps_per_param=[2, 2, 2, 2], n=3)
        values_4 = PermutationUtils.steps_for_nth_element_backward_calc(steps_per_param=[2, 2, 2, 2], n=4)
        # then
        self.assertEqual(values_0, [(0, 0)])
        self.assertEqual(values_1, [(0, 1)])
        self.assertEqual(values_2, [(0, 0), (1, 1)])
        self.assertEqual(values_3, [(0, 1), (1, 1)])
        self.assertEqual(values_4, [(0, 0), (2, 1)])


if __name__ == '__main__':
    unittest.main()
