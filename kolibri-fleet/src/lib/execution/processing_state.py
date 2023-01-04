from typing import TypeVar, Generic, Dict
from collections import OrderedDict

T = TypeVar("T")


class ProcessingState(Generic[T]):

    def __init__(self, data: T, previous_step_results: OrderedDict[str, Dict[str, any]]):
        """
        TODO: bake the context dict above in actual ProcessingResult object and just make the above a sequence
        of those.
        This class encapsulates the current data state along with previous step results (optional) and
        a context map to be able to pass information between steps
        :param data:
        :param previous_step_results:
        """

        self.data = data
