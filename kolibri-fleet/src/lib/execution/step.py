from abc import ABC, abstractmethod
from typing import TypeVar, Generic

from src.lib.execution.processing_state import ProcessingState

U = TypeVar("U")
V = TypeVar("V")
W = TypeVar("W")


class Step(ABC, Generic[U, V]):
    """
    Step in a sequence of computations. Steps are chainable steps of execution, where output type of the previous
    step needs to match input element of the following step. Purely side-effect steps are also possible,
    yet if follow-up steps need a specific input type, we will need to preserve instead of resorting to unit type
    """

    @abstractmethod
    def execute(self, input_data: ProcessingState[U]) -> ProcessingState[V]:
        pass

    @abstractmethod
    def and_then(self, next_step: 'Step[V, W]') -> 'Step[U, W]':
        """
        method for step chaining. note that as soon as any step is not successful, we should stop executing
        any follow up step, thus breaking the step chain.
        Wrapping the types within the used delimiters in the method definition is needed to indicate to type checkers
        that it is a forward reference to the type of the herein defined class itself
        :param next_step:
        :return:
        """
        pass
